package com.aiverse.feature.phonecontrol.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.feature.phonecontrol.data.ConfirmMode
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check

/** 确认方式选择底部弹层：三个档位，当前选中带对号，每项附规则介绍。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmModeSheet(
    current: ConfirmMode,
    onSelect: (ConfirmMode) -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = stringResource(R.string.confirm_mode_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            ConfirmRow(
                label = stringResource(R.string.confirm_ask),
                description = stringResource(R.string.confirm_ask_desc),
                selected = current == ConfirmMode.ASK
            ) { onSelect(ConfirmMode.ASK); onDismiss() }
            ConfirmRow(
                label = stringResource(R.string.confirm_task),
                description = stringResource(R.string.confirm_task_desc),
                selected = current == ConfirmMode.TASK
            ) { onSelect(ConfirmMode.TASK); onDismiss() }
            ConfirmRow(
                label = stringResource(R.string.confirm_always),
                description = stringResource(R.string.confirm_always_desc),
                selected = current == ConfirmMode.ALWAYS
            ) { onSelect(ConfirmMode.ALWAYS); onDismiss() }
        }
    }
}

@Composable
private fun ConfirmRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.semanticColors.subtleText
            )
        }
        if (selected) {
            Icon(
                imageVector = FeatherIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
