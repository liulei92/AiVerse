package com.aiverse.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.aiverse.core.theme.LocalUIDensityTokens

/**
 * 统一可点击容器（issue #58）：
 *
 * 把「按 shape 裁剪 + clickable + 按压反馈」收成一套，让点击高亮贴合视觉容器，
 * 不再出现方块矩形高亮溢出到相邻元素的问题。各处可点击项一律复用它，
 * 不再各写一套 `clip(...) + clickable(...)`。
 *
 * 关键顺序：先 `clip(shape)` 再 `clickable`，ripple 按压反馈被裁剪进 shape，
 * 高亮与容器同圆角；可选 [color] 给容器底色，[contentPadding] 给高亮留呼吸空间。
 *
 * @param onClick 点击回调。
 * @param enabled 是否可点击；false 时不响应点击、无按压反馈。
 * @param shape 圆角 Shape；null 时取当前密度令牌的按钮圆角 [com.aiverse.core.theme.UIDensityTokens.buttonShape]。
 * @param color 容器底色（默认透明，纯靠 ripple 呈现按压）。
 * @param contentPadding 内容内边距（同时是高亮与内容之间的呼吸空间）。
 * @param contentAlignment 内容对齐方式，默认居中。
 */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    color: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedShape = shape ?: LocalUIDensityTokens.current.buttonShape
    Box(
        modifier = modifier
            .clip(resolvedShape)
            // 背景先于 clickable：ripple 画在色块之上，且被 clip 裁剪成 shape，高亮贴合容器。
            .background(color, resolvedShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content
    )
}
