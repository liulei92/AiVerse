package com.aiverse.feature.settings.presentation.component

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.ui.SegmentedTabs
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.feature.settings.data.repository.BackgroundMode
import com.aiverse.feature.settings.data.repository.BackgroundSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 按目标尺寸采样解码背景图（inSampleSize 2 的幂缩放，控制峰值内存），失败返回 null。
 * 设置弹窗缩略图预览用。
 */
internal fun decodeBackgroundBitmap(path: String, maxWidth: Int, maxHeight: Int): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2 || bounds.outHeight / sample > maxHeight * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }.getOrNull()

/**
 * 自定义背景图设置弹窗：按当前模式预览 + 蒙版强度滑块 + 模式切换（立绘模式下含立绘高度滑块）+ 选图/移除。
 *
 * @param imagePath 当前背景图路径，null 表示未设置。
 * @param mask 当前蒙版强度（底色 overlay 不透明度，0~0.85）。
 * @param mode 当前呈现方式。
 * @param portraitHeight 立绘高度比例（仅 PORTRAIT 生效）。
 * @param isDarkTheme 当前是否暗色主题（影响预览的额外压暗）。
 * @param onPickImage 用户选定图片后回调。
 * @param onMaskChange 蒙版强度变化回调（写 DataStore，落盘节流在 ViewModel 侧）。
 * @param onModeChange 模式切换回调。
 * @param onPortraitHeightChange 立绘高度变化回调。
 * @param onRemove 移除背景回调。
 * @param onDismiss 关闭弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundImageSheet(
    imagePath: String?,
    mask: Float,
    mode: BackgroundMode,
    portraitHeight: Float,
    isDarkTheme: Boolean,
    onPickImage: (Uri) -> Unit,
    onMaskChange: (Float) -> Unit,
    onModeChange: (BackgroundMode) -> Unit,
    onPortraitHeightChange: (Float) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickImage(uri) }
    val backgroundColor = MaterialTheme.colorScheme.background

    // 缩略图预览：按屏幕宽度采样，避免大图全量解码
    val previewBitmap by produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = imagePath?.let { path ->
            withContext(Dispatchers.IO) {
                decodeBackgroundBitmap(path, 1024, 1024)
            }
        }
    }

    // 滑块位置由本地状态驱动：直接绑外部值会让每次拖动都走「写 DataStore → 回读 → 整个设置页重组」
    // 的往返，滑块因此跟不上手指。弹窗每次打开都重建，初值取当前设置即可。
    var maskPercent by remember { mutableFloatStateOf(BackgroundSettingsRepository.maskToSlider(mask)) }
    var portraitPercent by remember(mode) {
        mutableFloatStateOf(
            ((portraitHeight - BackgroundSettingsRepository.MIN_PORTRAIT_HEIGHT) /
                (BackgroundSettingsRepository.MAX_PORTRAIT_HEIGHT - BackgroundSettingsRepository.MIN_PORTRAIT_HEIGHT))
                .coerceIn(0f, 1f) * 100f
        )
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.settings_background_image),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            if (previewBitmap != null) {
                ModePreview(
                    bitmap = previewBitmap!!,
                    mode = mode,
                    mask = BackgroundSettingsRepository.sliderToMask(maskPercent),
                    portraitHeight = portraitHeight,
                    isDarkTheme = isDarkTheme
                )
                Spacer(Modifier.height(Spacing.lg))
            }

            Text(
                text = stringResource(R.string.settings_background_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
            SegmentedTabs(
                selected = mode.ordinal,
                labels = BackgroundMode.entries.map { stringResource(it.labelRes()) },
                onSelect = { index ->
                    BackgroundMode.entries.getOrNull(index)?.let(onModeChange)
                },
                enabled = imagePath != null
            )
            Spacer(Modifier.height(Spacing.md))

            SliderRow(
                label = stringResource(R.string.settings_background_mask),
                percent = maskPercent,
                onPercentChange = { percent ->
                    maskPercent = percent
                    onMaskChange(BackgroundSettingsRepository.sliderToMask(percent))
                },
                enabled = imagePath != null
            )

            if (mode == BackgroundMode.PORTRAIT) {
                SliderRow(
                    label = stringResource(R.string.settings_background_portrait_height),
                    percent = portraitPercent,
                    onPercentChange = { percent ->
                        portraitPercent = percent
                        val ratio = BackgroundSettingsRepository.MIN_PORTRAIT_HEIGHT +
                            (BackgroundSettingsRepository.MAX_PORTRAIT_HEIGHT -
                                BackgroundSettingsRepository.MIN_PORTRAIT_HEIGHT) * (percent / 100f)
                        onPortraitHeightChange(ratio)
                    },
                    enabled = imagePath != null
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Button(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_background_image_pick))
                }
                OutlinedButton(
                    onClick = {
                        onRemove()
                        onDismiss()
                    },
                    enabled = imagePath != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_background_image_remove))
                }
            }
        }
    }
}

/**
 * 弹窗预览：按当前模式摆放图片，并叠上与聊天页一致的蒙版，让用户所见即所得。
 */
@Composable
private fun ModePreview(
    bitmap: ImageBitmap,
    mode: BackgroundMode,
    mask: Float,
    portraitHeight: Float,
    isDarkTheme: Boolean
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val previewHeight = 160.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
    ) {
        when (mode) {
            BackgroundMode.COVER_CROP -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            BackgroundMode.COVER_FIT -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            BackgroundMode.PORTRAIT -> {
                val portraitBoxHeight = previewHeight * portraitHeight.coerceIn(
                    BackgroundSettingsRepository.MIN_PORTRAIT_HEIGHT,
                    BackgroundSettingsRepository.MAX_PORTRAIT_HEIGHT
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(portraitBoxHeight)
                        .align(Alignment.BottomCenter),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(portraitBoxHeight)
                        .background(
                            Brush.verticalGradient(
                                0f to backgroundColor,
                                1f to backgroundColor.copy(alpha = 0f)
                            )
                        )
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor.copy(alpha = mask.coerceIn(0f, 1f)))
        )
        if (isDarkTheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    percent: Float,
    onPercentChange: (Float) -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${percent.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
        Slider(
            value = percent,
            onValueChange = onPercentChange,
            valueRange = 0f..100f,
            enabled = enabled
        )
    }
}
