package com.aiverse.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors

internal enum class AgentActionTone {
    Neutral,
    Success,
    Danger
}

@Composable
internal fun AgentActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: AgentActionTone = AgentActionTone.Neutral,
    /** 紧凑版：标准按钮高度（40dp）、文字适中（labelLarge），用于信息密集面板（如 AskUserQuestionPanel）。
     *  较普通版（44dp）略矮但仍是清晰可点的标准尺寸，避免上一版 36dp 极小文字按钮带来的「太小、难点」问题。
     *  默认 false 保持原 44dp。 */
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(Radius.sm)
    val success = MaterialTheme.semanticColors.success
    val (container, content, border) = when (tone) {
        AgentActionTone.Success -> Triple(
            if (enabled) success else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            if (enabled) MaterialTheme.semanticColors.onSuccess else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            if (enabled) success else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        AgentActionTone.Danger -> Triple(
            if (enabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            if (enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        AgentActionTone.Neutral -> Triple(
            if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    }

    Box(
        modifier = modifier
            .height(if (compact) 40.dp else 44.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) Spacing.sm else Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = content,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            style = if (compact) MaterialTheme.typography.labelLarge else LocalTextStyle.current
        )
    }
}
