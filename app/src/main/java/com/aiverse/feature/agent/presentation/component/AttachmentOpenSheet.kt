package com.aiverse.feature.agent.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import compose.icons.FeatherIcons
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.Eye

/**
 * 附件点击选择弹层：在「应用内预览」与「用其他应用打开」之间二选一。
 *
 * 只对可预览文档（md / html / txt 等文本类）弹出；图片走全屏查看器、APK 走系统安装器，
 * 两者保持原行为不经过这里。顶部显示文件名，两个选项左侧带图标：
 * 预览（眼睛）、用其他应用打开（外链）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentOpenSheet(
    fileName: String,
    onPreview: () -> Unit,
    onOpenExternal: () -> Unit,
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
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = fileName.ifBlank { stringResource(R.string.common_file) },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            AttachmentOpenOption(
                icon = FeatherIcons.Eye,
                label = stringResource(R.string.attachment_open_preview),
                onClick = {
                    onDismiss()
                    onPreview()
                }
            )
            AttachmentOpenOption(
                icon = FeatherIcons.ExternalLink,
                label = stringResource(R.string.attachment_open_external),
                onClick = {
                    onDismiss()
                    onOpenExternal()
                }
            )
        }
    }
}

@Composable
private fun AttachmentOpenOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
