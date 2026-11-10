package com.aiverse.feature.phonecontrol.presentation

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AppSwitch
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Eye
import compose.icons.feathericons.Key
import compose.icons.feathericons.Server
import compose.icons.feathericons.Terminal

/**
 * 「无障碍权限保存」子页：用 shizuku / sui / root / adb 四种方式授予系统写入权限，
 * 再由 A11yKeeper 把无障碍服务写回系统。独立页面（非弹窗套壳）。
 */
@Composable
fun AccessibilityGrantScreen(
    viewModel: PhoneControlViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val adbCommand = remember(viewModel) { viewModel.adbCommand() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SettingsGroupHeader(text = stringResource(R.string.a11y_grant_title))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Eye,
                title = stringResource(R.string.phone_a11y),
                subtitle = stringResource(R.string.phone_a11y_desc),
                onClick = viewModel::openAccessibilitySettings,
                trailing = {
                    Text(
                        text = stringResource(
                            if (state.accessibilityEnabled) R.string.phone_on else R.string.phone_off
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
        }

        SettingsGroupHeader(text = stringResource(R.string.a11y_grant_methods))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Terminal,
                title = stringResource(R.string.a11y_grant_shizuku),
                subtitle = stringResource(R.string.a11y_grant_shizuku_desc),
                onClick = viewModel::grantViaShizuku
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Server,
                title = stringResource(R.string.a11y_grant_sui),
                subtitle = stringResource(R.string.a11y_grant_sui_desc),
                onClick = viewModel::grantViaShizuku
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Key,
                title = stringResource(R.string.a11y_grant_root),
                subtitle = stringResource(R.string.a11y_grant_root_desc),
                onClick = viewModel::grantViaRoot
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Copy,
                title = stringResource(R.string.a11y_grant_adb),
                subtitle = adbCommand,
                onClick = {
                    clipboard.setText(AnnotatedString(adbCommand))
                }
            )
        }

        SettingsGroupHeader(text = stringResource(R.string.a11y_grant_allow))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Eye,
                title = stringResource(R.string.a11y_grant_allow_action),
                subtitle = stringResource(R.string.a11y_grant_allow_desc),
                onClick = viewModel::enableAccessibilityNow,
                trailing = {
                    AppSwitch(
                        checked = state.accessibilityEnabled,
                        onCheckedChange = {
                            if (it) viewModel.enableAccessibilityNow()
                            else viewModel.disableAccessibility()
                        }
                    )
                }
            )
        }
    }
}
