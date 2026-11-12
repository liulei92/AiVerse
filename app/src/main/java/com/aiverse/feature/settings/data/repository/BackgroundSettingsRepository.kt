package com.aiverse.feature.settings.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backgroundDataStore by preferencesDataStore(
    name = "background_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 聊天页背景图的呈现方式。
 */
enum class BackgroundMode {
    /** 铺满裁切：等比缩放填满面板，超出部分裁掉。 */
    COVER_CROP,

    /** 完整显示：等比缩放完整放进面板，留边透出底色。 */
    COVER_FIT,

    /** 底部立绘：图片锚定面板底部，高度按立绘高度比例，顶部渐变过渡到底色。 */
    PORTRAIT;

    companion object {
        fun fromId(id: String?): BackgroundMode =
            entries.firstOrNull { it.name == id } ?: COVER_CROP
    }
}

/**
 * 聊天页自定义背景图：图片文件处理（EXIF 复位 + 降采样 + 重编码）后落到应用私有目录持久化
 * （不依赖 content URI 授权），路径与呈现参数存 DataStore。路径为 null 表示未设置背景。
 *
 * 背景只在聊天页作底图（不再做全局水印），可读性由「蒙版强度」控制。
 */
@Singleton
class BackgroundSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        /** 蒙版强度默认值：底色 overlay 的不透明度，兼顾背景可见与文字可读。 */
        const val DEFAULT_MASK = 0.35f
        /** 蒙版强度上限：再高背景几乎被底色盖没，失去意义。 */
        const val MAX_MASK = 0.85f
        /** 蒙版强度下限：0 表示不加底色 overlay（纯图，暗色主题下仍会额外压暗）。 */
        const val MIN_MASK = 0f

        /** 立绘高度默认值（占面板高度比例）。 */
        const val DEFAULT_PORTRAIT_HEIGHT = 0.45f
        const val MIN_PORTRAIT_HEIGHT = 0.2f
        const val MAX_PORTRAIT_HEIGHT = 0.8f

        /** 落盘图最长边（像素）：超过屏幕分辨率的细节对底图无意义，只占内存。 */
        private const val MAX_EDGE = 1920
        private const val DECODE_MAX_PIXELS = 12_000_000L
        private const val JPEG_QUALITY = 85

        private val IMAGE_PATH_KEY = stringPreferencesKey("background_image_path")
        private val MASK_KEY = floatPreferencesKey("background_mask")
        /** 旧版「透明度」key：仅用于把已有用户迁移到新蒙版语义的初值，不再写入。 */
        private val LEGACY_ALPHA_KEY = floatPreferencesKey("background_alpha")
        private val MODE_KEY = stringPreferencesKey("background_mode")
        private val PORTRAIT_HEIGHT_KEY = floatPreferencesKey("background_portrait_height")

        /** 把 UI 百分比（0~100）线性映射为实际蒙版强度（0~MAX_MASK）。 */
        fun sliderToMask(percent: Float): Float =
            MAX_MASK * (percent / 100f).coerceIn(0f, 1f)

        /** 把实际蒙版强度反映射为 UI 百分比（0~100）。 */
        fun maskToSlider(mask: Float): Float =
            (mask / MAX_MASK).coerceIn(0f, 1f) * 100f
    }

    /** 当前背景图文件绝对路径；null 表示未设置。 */
    val imagePathFlow: Flow<String?> = context.backgroundDataStore.data.map { it[IMAGE_PATH_KEY] }

    /** 蒙版强度（底色 overlay 不透明度，0~MAX_MASK）。 */
    val maskFlow: Flow<Float> = context.backgroundDataStore.data.map { prefs ->
        prefs[MASK_KEY]
            // 兼容旧版用户：没有蒙版值时把历史透明度夹到新区间当作初值，否则用默认。
            ?: prefs[LEGACY_ALPHA_KEY]?.coerceIn(MIN_MASK, MAX_MASK)
            ?: DEFAULT_MASK
    }

    /** 背景呈现方式。 */
    val backgroundModeFlow: Flow<BackgroundMode> = context.backgroundDataStore.data.map {
        BackgroundMode.fromId(it[MODE_KEY])
    }

    /** 立绘高度（占面板高度比例，仅 PORTRAIT 模式生效）。 */
    val portraitHeightFlow: Flow<Float> = context.backgroundDataStore.data.map {
        (it[PORTRAIT_HEIGHT_KEY] ?: DEFAULT_PORTRAIT_HEIGHT)
            .coerceIn(MIN_PORTRAIT_HEIGHT, MAX_PORTRAIT_HEIGHT)
    }

    /**
     * 选择新背景图：解码（采样控内存）→ 按最长边 [MAX_EDGE] 降采样 → 重编码 JPEG → 写私有目录。
     * 先写新文件成功再更新 DataStore，失败时旧背景保持不变。
     *
     * 统一转 JPEG：底图不需要透明通道，且避免大 PNG 常驻内存。
     */
    suspend fun setBackgroundImage(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                }
            }.getOrNull()
        } ?: return
        val encoded = withContext(Dispatchers.IO) {
            runCatching { normalizeToJpeg(bytes) }.getOrNull()
        } ?: return
        val target = File(context.filesDir, "backgrounds/bg_${System.currentTimeMillis()}.jpg")
        target.parentFile?.mkdirs()
        val written = withContext(Dispatchers.IO) {
            runCatching {
                target.outputStream().use { output -> output.write(encoded) }
                target.length() > 0
            }.getOrDefault(false)
        }
        if (!written) return
        val oldPath = context.backgroundDataStore.data.first()[IMAGE_PATH_KEY]
        context.backgroundDataStore.edit { it[IMAGE_PATH_KEY] = target.absolutePath }
        oldPath?.let { runCatching { File(it).delete() } }
    }

    /** 移除背景：删图片文件并清路径。 */
    suspend fun clearBackground() {
        val oldPath = context.backgroundDataStore.data.first()[IMAGE_PATH_KEY]
        context.backgroundDataStore.edit { it.remove(IMAGE_PATH_KEY) }
        oldPath?.let { runCatching { File(it).delete() } }
    }

    suspend fun setBackgroundMask(mask: Float) {
        context.backgroundDataStore.edit {
            it[MASK_KEY] = mask.coerceIn(MIN_MASK, MAX_MASK)
        }
    }

    suspend fun setBackgroundMode(mode: BackgroundMode) {
        context.backgroundDataStore.edit {
            it[MODE_KEY] = mode.name
        }
    }

    suspend fun setPortraitHeight(ratio: Float) {
        context.backgroundDataStore.edit {
            it[PORTRAIT_HEIGHT_KEY] = ratio.coerceIn(MIN_PORTRAIT_HEIGHT, MAX_PORTRAIT_HEIGHT)
        }
    }

    /**
     * 把原图字节规整成最长边不超过 [MAX_EDGE] 的 JPEG。
     * 先按采样档位解码控峰值内存，再用 Matrix 精确缩放到目标尺寸（不改方向，源图按原方向落盘）。
     */
    private fun normalizeToJpeg(source: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("无法读取图片尺寸")
        }
        var sample = 1
        while (sample < 32 &&
            (bounds.outWidth / (sample * 2) >= MAX_EDGE || bounds.outHeight / (sample * 2) >= MAX_EDGE ||
                bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > DECODE_MAX_PIXELS)
        ) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            source, 0, source.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalArgumentException("图片解码失败")
        val longest = maxOf(decoded.width, decoded.height)
        val scale = if (longest > MAX_EDGE) MAX_EDGE.toFloat() / longest else 1f
        val bitmap = if (scale < 1f) {
            val matrix = Matrix().apply { postScale(scale, scale) }
            val scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (scaled != decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
