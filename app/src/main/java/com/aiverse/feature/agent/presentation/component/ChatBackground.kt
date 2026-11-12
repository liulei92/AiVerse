package com.aiverse.feature.agent.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.MaterialTheme
import com.aiverse.core.ui.decodeSampledBitmap
import com.aiverse.feature.settings.data.repository.BackgroundMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聊天页底图 + 蒙版。仅聊天路由挂载（置于内容之下），[backgroundPath] 为 null 时不绘制任何东西。
 *
 * 图片始终以全透明度绘制，可读性全靠蒙版强度 [mask] 控制的底色 overlay 调节；
 * 暗色主题在底色 overlay 之上再额外压暗一档，避免亮图在暗色界面里突兀。
 *
 * @param backgroundPath 背景图文件路径，null 表示未设置。
 * @param mask 蒙版强度（底色 overlay 不透明度，0~0.85）。
 * @param mode 呈现方式（铺满裁切 / 完整显示 / 底部立绘）。
 * @param portraitHeight 立绘高度占面板高度比例，仅 [BackgroundMode.PORTRAIT] 生效。
 * @param isDarkTheme 当前是否暗色主题。
 */
@Composable
internal fun ChatBackground(
    backgroundPath: String?,
    mask: Float,
    mode: BackgroundMode,
    portraitHeight: Float,
    isDarkTheme: Boolean
) {
    if (backgroundPath == null) return
    val backgroundColor = MaterialTheme.colorScheme.background
    val view = LocalView.current
    val density = LocalDensity.current
    // 解码档位跟当前窗口走：底图最长边取窗口长边像素即可，不必按原图分辨率常驻内存。
    // path 或窗口尺寸变化才重解（remember/produceState 的 key）。
    val maxEdgePx = remember(view.width, view.height) {
        maxOf(view.width, view.height).coerceIn(720, 1920)
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, backgroundPath, maxEdgePx) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(backgroundPath, maxEdgePx)
        }
    }
    val image = bitmap ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelHeightPx = constraints.maxHeight
        when (mode) {
            BackgroundMode.COVER_CROP -> {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            BackgroundMode.COVER_FIT -> {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            BackgroundMode.PORTRAIT -> {
                val portraitHeightPx = (panelHeightPx * portraitHeight).toInt().coerceAtLeast(1)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { portraitHeightPx.toDp() }),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 顶部渐变过渡：从底色淡出到透明，让立绘上沿自然融入背景色。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { portraitHeightPx.toDp() })
                        .background(
                            Brush.verticalGradient(
                                0f to backgroundColor,
                                1f to backgroundColor.copy(alpha = 0f)
                            )
                        )
                )
            }
        }

        // 蒙版：整块底色 overlay，强度即 alpha。
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
