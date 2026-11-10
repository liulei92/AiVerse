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
import com.aiverse.feature.phonecontrol.data.AppRule
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check

/** 应用名单管理：单个应用规则选择底部弹层（完全不允许 / 每步确认 / 允许）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleSheet(
    packageName: String,
    current: AppRule,
    onSelect: (AppRule) -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = stringResource(R.string.app_rule_title, packageName),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            RuleRow(
                label = stringResource(R.string.app_rule_deny),
                description = stringResource(R.string.app_rule_deny_desc),
                selected = current == AppRule.DENY
            ) { onSelect(AppRule.DENY); onDismiss() }
            RuleRow(
                label = stringResource(R.string.app_rule_confirm),
                description = stringResource(R.string.app_rule_confirm_desc),
                selected = current == AppRule.CONFIRM_EACH_STEP
            ) { onSelect(AppRule.CONFIRM_EACH_STEP); onDismiss() }
            RuleRow(
                label = stringResource(R.string.app_rule_normal),
                description = stringResource(R.string.app_rule_normal_desc),
                selected = current == AppRule.NORMAL
            ) { onSelect(AppRule.NORMAL); onDismiss() }
        }
    }
}

@Composable
private fun RuleRow(
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
