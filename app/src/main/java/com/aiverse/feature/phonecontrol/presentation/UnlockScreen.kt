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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AppSwitch
import com.aiverse.feature.phonecontrol.data.PhoneUnlocks
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.Key
import compose.icons.feathericons.Lock
import compose.icons.feathericons.Settings

/**
 * 「解除权限」子页：让 AI 去做默认由你来做的事。
 * 系统设置 / 密码和验证码 / 应用本身 三项开关，默认关闭；开启后 AI 不再受对应限制。
 */
@Composable
fun UnlockScreen(
    viewModel: PhoneControlViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlocks = state.settings.unlocks

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SettingsGroupHeader(text = stringResource(R.string.phone_unlock))
        Text(
            text = stringResource(R.string.unlock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.semanticColors.subtleText,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Settings,
                title = stringResource(R.string.unlock_system),
                subtitle = stringResource(R.string.unlock_system_desc),
                trailing = {
                    AppSwitch(
                        checked = unlocks.systemPages,
                        onCheckedChange = { on -> viewModel.setUnlocks(unlocks.copy(systemPages = on)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Lock,
                title = stringResource(R.string.unlock_secrets),
                subtitle = stringResource(R.string.unlock_secrets_desc),
                trailing = {
                    AppSwitch(
                        checked = unlocks.secretFields,
                        onCheckedChange = { on -> viewModel.setUnlocks(unlocks.copy(secretFields = on)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Key,
                title = stringResource(R.string.unlock_own_app),
                subtitle = stringResource(R.string.unlock_own_app_desc),
                trailing = {
                    AppSwitch(
                        checked = unlocks.ownApp,
                        onCheckedChange = { on -> viewModel.setUnlocks(unlocks.copy(ownApp = on)) }
                    )
                }
            )
        }
        Text(
            text = stringResource(R.string.unlock_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.semanticColors.subtleText,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
    }
}
