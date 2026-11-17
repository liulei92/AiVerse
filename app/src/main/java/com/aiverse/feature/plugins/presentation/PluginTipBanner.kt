package com.aiverse.feature.plugins.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import compose.icons.FeatherIcons
import compose.icons.feathericons.Package
import compose.icons.feathericons.X

/**
 * 对话内的插件提示条：显示插件来源、文本与可选动作按钮。
 * 当前只展示队首一条，关闭后自动露出下一条。
 *
 * 密度：全部间距/圆角/尺寸走 [LocalUIDensityTokens] 与 [Spacing]/[Radius]，不引入硬编码 dp。
 */
@Composable
fun PluginTipBanner(
    modifier: Modifier = Modifier,
    viewModel: PluginTipsViewModel = hiltViewModel()
) {
    val tips by viewModel.tips.collectAsStateWithLifecycle()
    val tip = tips.firstOrNull() ?: return

    val t = LocalUIDensityTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xs),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = t.scale(2)
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = t.scale(2), end = Spacing.xs, bottom = Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = FeatherIcons.Package,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = t.scale(2))
                    .size(t.scale(18))
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(t.scale(2))) {
                Text(
                    text = tip.pluginName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                if (tip.title.isNotBlank()) {
                    Text(
                        text = tip.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = tip.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.semanticColors.subtleText
                )
                if (tip.actionLabel != null) {
                    TextButton(
                        onClick = { viewModel.invokeAction(tip.id) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = t.scale(0), vertical = t.scale(2))
                    ) {
                        Text(tip.actionLabel)
                    }
                }
            }
            IconButton(onClick = { viewModel.dismiss(tip.id) }, modifier = Modifier.size(t.scale(32))) {
                Icon(
                    imageVector = FeatherIcons.X,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(t.scale(16))
                )
            }
        }
    }
}
