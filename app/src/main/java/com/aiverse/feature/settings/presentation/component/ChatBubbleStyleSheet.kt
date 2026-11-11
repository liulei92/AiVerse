package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiverse.R
import com.aiverse.core.theme.ChatBubbleStyle
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.feature.agent.presentation.component.MarkdownContent
import com.aiverse.feature.agent.presentation.component.chatBlockColorsFor
import com.aiverse.feature.agent.presentation.component.chatBubbleColorFor
import com.aiverse.feature.agent.presentation.component.chatBubbleTextColorFor
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check

/** 聊天气泡样式选择弹窗：列出 9 种，每种带模式名预览气泡。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatBubbleStyleSheet(
    title: String,
    selected: ChatBubbleStyle,
    onSelect: (ChatBubbleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl)
        ) {
            SectionLabel(title)
            ChatBubbleStyle.entries.forEach { style ->
                val isSelected = style == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(style) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChatBubblePreview(style)
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = stringResource(style.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    if (isSelected) {
                        Icon(
                            imageVector = FeatherIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 迷你预览气泡：按样式渲染，气泡内放一行文案 + 迷你 Markdown 表格 + 代码块（检验表格/代码块
 * 配色随气泡底色联动），右侧才是样式名，固定宽度保证各行标签对齐。
 */
@Composable
private fun ChatBubblePreview(style: ChatBubbleStyle) {
    // 气泡底色/文字色/块级元素配色全部复用实际气泡的推导，避免预览与真实渲染两套不一致。
    val color = chatBubbleColorFor(style)
    val textColor = chatBubbleTextColorFor(style)
    val (blockBackground, blockTextColor) = chatBlockColorsFor(style)
    val border = when (style) {
        ChatBubbleStyle.CLASSIC_OUTLINE -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface)
        ChatBubbleStyle.COMIC -> BorderStroke(2.dp, Color.Black)
        ChatBubbleStyle.HEARTBEAT -> BorderStroke(1.5.dp, Color(0xFFBDBDBD))
        // 极简纯白：白底在白弹窗上看不出，预览加浅灰描边保证可见（真实聊天区背景非纯白，故真实气泡不加）。
        ChatBubbleStyle.MINIMAL_WHITE -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        // 无气泡：真实渲染不套容器（正文直接铺在页面底色上），预览同样无描边，保持一致。
        ChatBubbleStyle.NO_BUBBLE -> null
        else -> null
    }
    val shape = if (style == ChatBubbleStyle.FRESH_GREEN) RoundedCornerShape(24.dp) else RoundedCornerShape(10.dp)
    val elevation = if (style == ChatBubbleStyle.SOFT_SHADOW) 4.dp else 0.dp

    val previewContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.bubble_preview_text),
                color = textColor,
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            MarkdownContent(
                text = stringResource(R.string.bubble_preview_markdown),
                color = textColor,
                blockBackground = blockBackground,
                blockTextColor = blockTextColor,
                compact = true,
                fontSizeSp = 10,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 预览区固定宽度：气泡 + 漫画箭头都在固定区内，右侧样式名起始位置每行对齐。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(148.dp)
    ) {
        if (style == ChatBubbleStyle.NO_BUBBLE) {
            // 无气泡预览：正文直接铺在弹窗底色上，无容器无描边（与真实渲染一致）。
            Box(modifier = Modifier.width(140.dp)) {
                previewContent()
            }
        } else {
            Surface(
                shape = shape,
                color = color,
                border = border,
                shadowElevation = elevation,
                modifier = Modifier.width(140.dp)
            ) {
                previewContent()
            }
        }
        if (style == ChatBubbleStyle.COMIC) {
            Canvas(
                modifier = Modifier
                    .size(8.dp, 12.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, Color.Black)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.sm)
    )
}
