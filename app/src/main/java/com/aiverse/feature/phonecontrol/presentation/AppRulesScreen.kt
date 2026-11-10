package com.aiverse.feature.phonecontrol.presentation

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.feature.phonecontrol.data.AppRule
import com.aiverse.feature.phonecontrol.data.PhoneControlSettings
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.Smartphone

private data class AppEntry(val label: String, val packageName: String)

/**
 * 「应用名单」管理页：列出默认白名单与已安装应用，点击单项弹出 [AppRuleSheet] 选择规则。
 */
@Composable
fun AppRulesScreen(
    viewModel: PhoneControlViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val apps = remember {
        loadApps(context, state.settings).sortedBy { it.label }
    }
    var selectedPackage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SettingsGroupHeader(text = stringResource(R.string.phone_app_rules))
        SettingsGroup {
            apps.forEachIndexed { index, entry ->
                SettingsRow(
                    icon = FeatherIcons.Smartphone,
                    title = entry.label,
                    subtitle = entry.packageName,
                    onClick = { selectedPackage = entry.packageName },
                    trailing = {
                        Text(
                            text = ruleLabel(state.settings.ruleFor(entry.packageName)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.semanticColors.subtleText
                        )
                    }
                )
                if (index != apps.lastIndex) SettingsDivider()
            }
        }
    }

    selectedPackage?.let { pkg ->
        AppRuleSheet(
            packageName = pkg,
            current = state.settings.ruleFor(pkg),
            onSelect = { rule -> viewModel.setRule(pkg, rule) },
            onDismiss = { selectedPackage = null }
        )
    }
}

@Composable
private fun ruleLabel(rule: AppRule): String = stringResource(
    when (rule) {
        AppRule.DENY -> R.string.app_rule_deny
        AppRule.CONFIRM_EACH_STEP -> R.string.app_rule_confirm
        AppRule.NORMAL -> R.string.app_rule_normal
    }
)

private fun loadApps(context: Context, settings: PhoneControlSettings): List<AppEntry> {
    val pm = context.packageManager
    val installed = runCatching {
        pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { info ->
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull() ?: info.packageName
            AppEntry(label, info.packageName)
        }
    }.getOrDefault(emptyList())
    val byPackage = installed.associateBy { it.packageName }.toMutableMap()
    // 默认名单即使未安装也展示，保证用户可管理。
    (settings.denyPackages + settings.confirmPackages).forEach { pkg ->
        if (pkg !in byPackage) byPackage[pkg] = AppEntry(pkg.substringAfterLast('.'), pkg)
    }
    return byPackage.values.toList()
}
