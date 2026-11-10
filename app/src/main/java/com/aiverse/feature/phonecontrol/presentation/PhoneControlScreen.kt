package com.aiverse.feature.phonecontrol.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.ui.AppSwitch
import com.aiverse.feature.phonecontrol.data.ConfirmMode
import com.aiverse.feature.phonecontrol.data.OverlayPrefs
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.BarChart
import compose.icons.feathericons.Bell
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.Circle
import compose.icons.feathericons.Clock
import compose.icons.feathericons.Eye
import compose.icons.feathericons.Key
import compose.icons.feathericons.Moon
import compose.icons.feathericons.Power
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.RotateCcw
import compose.icons.feathericons.Shield
import compose.icons.feathericons.Sliders
import compose.icons.feathericons.Smartphone
import compose.icons.feathericons.Type
import compose.icons.feathericons.Zap
import kotlin.math.roundToInt

/**
 * 「手机控制」设置主页。
 *
 * 顶部总开关：关闭后隐藏全部模块。开启后展示六个模块：
 * 准备（无障碍/通知/自动恢复）、试一下（读当前屏幕）、规则（应用名单/确认方式/解除权限）、
 * 悬浮条（不透明度/自动收起/完成后返回）、最近操作、进阶（不打扰/后台运行）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneControlScreen(
    viewModel: PhoneControlViewModel,
    onOpenAccessibilityGrant: () -> Unit,
    onOpenUnlock: () -> Unit,
    onOpenAppRules: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showConfirmModeSheet by remember { mutableStateOf(false) }
    var showOpacitySheet by remember { mutableStateOf(false) }
    var showBackgroundConfirmDialog by remember { mutableStateOf(false) }
    var showBrands by remember { mutableStateOf(false) }
    var showDndDialog by remember { mutableStateOf(false) }
    var showScreenTextDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // 总开关
        SettingsGroupHeader(text = stringResource(R.string.phone_control_title))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Power,
                title = stringResource(R.string.phone_control_title),
                subtitle = stringResource(R.string.phone_control_enabled_desc),
                trailing = {
                    AppSwitch(
                        checked = state.settings.enabled,
                        onCheckedChange = viewModel::setEnabled
                    )
                }
            )
        }

        if (!state.settings.enabled) return@Column

        // 模块1 准备
        SettingsGroupHeader(text = stringResource(R.string.phone_section_prep))
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
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Bell,
                title = stringResource(R.string.phone_notification),
                subtitle = stringResource(R.string.phone_notification_desc),
                onClick = viewModel::requestNotificationPermission,
                trailing = {
                    Text(
                        text = stringResource(
                            if (state.notificationsEnabled) R.string.phone_allowed else R.string.phone_denied
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.RefreshCw,
                title = stringResource(R.string.phone_a11y_restore),
                subtitle = stringResource(R.string.phone_a11y_restore_desc),
                onClick = onOpenAccessibilityGrant
            )
        }

        // 模块2 试一下
        SettingsGroupHeader(text = stringResource(R.string.phone_section_test))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Eye,
                title = stringResource(R.string.phone_test),
                subtitle = stringResource(R.string.phone_test_desc),
                onClick = viewModel::readScreen,
                trailing = {
                    if (state.readingScreen) {
                        Text(
                            text = stringResource(R.string.phone_test_reading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.semanticColors.subtleText
                        )
                    }
                }
            )
        }

        // 模块3 规则
        SettingsGroupHeader(text = stringResource(R.string.phone_section_rules))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Shield,
                title = stringResource(R.string.phone_app_rules),
                subtitle = stringResource(
                    R.string.phone_app_rules_desc,
                    state.settings.denyPackages.size,
                    state.settings.confirmPackages.size
                ),
                onClick = onOpenAppRules
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.CheckCircle,
                title = stringResource(R.string.phone_confirm_mode),
                subtitle = confirmModeLabel(state.settings.confirmMode),
                onClick = { showConfirmModeSheet = true }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Key,
                title = stringResource(R.string.phone_unlock),
                subtitle = stringResource(R.string.phone_unlock_desc, state.settings.unlocks.openedCount),
                onClick = onOpenUnlock
            )
        }

        // 模块4 悬浮条
        SettingsGroupHeader(text = stringResource(R.string.phone_section_overlay))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Sliders,
                title = stringResource(R.string.phone_overlay_idle_opacity),
                onClick = { showOpacitySheet = true },
                trailing = {
                    Text(
                        text = stringResource(
                            R.string.phone_percent, (state.settings.overlay.idleOpacity * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Circle,
                title = stringResource(R.string.phone_overlay_auto_collapse),
                subtitle = stringResource(R.string.phone_overlay_auto_collapse_desc),
                trailing = {
                    AppSwitch(
                        checked = state.settings.overlay.autoCollapse,
                        onCheckedChange = { on ->
                            viewModel.setOverlayPrefs(state.settings.overlay.copy(autoCollapse = on))
                        }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.RotateCcw,
                title = stringResource(R.string.phone_overlay_return_after_done),
                subtitle = stringResource(R.string.phone_overlay_return_after_done_desc),
                trailing = {
                    AppSwitch(
                        checked = state.settings.overlay.returnAfterDone,
                        onCheckedChange = { on ->
                            viewModel.setOverlayPrefs(state.settings.overlay.copy(returnAfterDone = on))
                        }
                    )
                }
            )
        }

        // 模块5 最近操作
        SettingsGroupHeader(text = stringResource(R.string.phone_section_recent))
        SettingsGroup {
            if (state.recentActions.isEmpty()) {
                SettingsRow(
                    icon = FeatherIcons.Clock,
                    title = stringResource(R.string.phone_recent_empty),
                    enabled = false
                )
            } else {
                state.recentActions.take(8).forEachIndexed { index, action ->
                    SettingsRow(
                        icon = FeatherIcons.Clock,
                        title = action,
                        enabled = false
                    )
                    if (index != state.recentActions.take(8).lastIndex) SettingsDivider()
                }
            }
        }

        // 模块6 进阶
        SettingsGroupHeader(text = stringResource(R.string.phone_section_advanced))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Moon,
                title = stringResource(R.string.phone_dnd),
                subtitle = stringResource(R.string.phone_dnd_desc),
                onClick = { showDndDialog = true }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Power,
                title = stringResource(R.string.phone_background_run),
                subtitle = stringResource(R.string.phone_background_run_desc)
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Zap,
                title = stringResource(R.string.phone_xiaomi_battery),
                subtitle = stringResource(R.string.phone_xiaomi_battery_desc),
                onClick = viewModel::openVendorBatterySettings
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Activity,
                title = stringResource(R.string.phone_system_battery),
                subtitle = stringResource(R.string.phone_stay_online),
                onClick = viewModel::requestBatteryExempt,
                trailing = {
                    Text(
                        text = stringResource(
                            if (state.batteryExempt) R.string.phone_on else R.string.phone_off
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Smartphone,
                title = stringResource(R.string.phone_other_brands),
                onClick = { showBrands = !showBrands }
            )
            if (showBrands) {
                SettingsDivider()
                BrandGuideRow(stringResource(R.string.phone_brand_vivo))
                BrandGuideRow(stringResource(R.string.phone_brand_xiaomi))
                BrandGuideRow(stringResource(R.string.phone_brand_huawei))
                BrandGuideRow(stringResource(R.string.phone_brand_oppo))
                BrandGuideRow(stringResource(R.string.phone_brand_samsung))
            }
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.AlertCircle,
                title = stringResource(R.string.phone_background_confirm),
                subtitle = stringResource(R.string.phone_background_confirm_desc),
                onClick = { showBackgroundConfirmDialog = true }
            )
        }
    }

    if (showConfirmModeSheet) {
        ConfirmModeSheet(
            current = state.settings.confirmMode,
            onSelect = viewModel::setConfirmMode,
            onDismiss = { showConfirmModeSheet = false }
        )
    }

    if (showOpacitySheet) {
        OpacitySheet(
            current = state.settings.overlay,
            onApply = viewModel::setOverlayPrefs,
            onDismiss = { showOpacitySheet = false }
        )
    }

    if (showBackgroundConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundConfirmDialog = false },
            title = { Text(stringResource(R.string.phone_background_confirm_dialog_title)) },
            text = { Text(stringResource(R.string.phone_background_confirm_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundConfirmDialog = false
                    viewModel.openVendorBatterySettings()
                }) { Text(stringResource(R.string.phone_go_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundConfirmDialog = false }) {
                    Text(stringResource(R.string.phone_ok))
                }
            }
        )
    }

    if (showDndDialog) {
        AlertDialog(
            onDismissRequest = { showDndDialog = false },
            title = { Text(stringResource(R.string.phone_dnd)) },
            text = { Text(stringResource(R.string.phone_dnd_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDndDialog = false
                    viewModel.openAccessibilitySettings()
                }) { Text(stringResource(R.string.phone_go_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showDndDialog = false }) {
                    Text(stringResource(R.string.phone_ok))
                }
            }
        )
    }

    if (showScreenTextDialog) {
        AlertDialog(
            onDismissRequest = {
                showScreenTextDialog = false
                viewModel.dismissScreenText()
            },
            title = { Text(stringResource(R.string.phone_test)) },
            text = { Text(state.screenText.ifBlank { stringResource(R.string.phone_test_empty) }) },
            confirmButton = {
                TextButton(onClick = {
                    showScreenTextDialog = false
                    viewModel.dismissScreenText()
                }) { Text(stringResource(R.string.phone_ok)) }
            }
        )
    }

    // 读屏完成后自动弹窗展示（关闭时清空结果）。
    LaunchedEffect(state.screenText) {
        if (state.screenText.isNotBlank()) showScreenTextDialog = true
    }
}

@Composable
private fun confirmModeLabel(mode: ConfirmMode): String = stringResource(
    when (mode) {
        ConfirmMode.ASK -> R.string.confirm_ask
        ConfirmMode.TASK -> R.string.confirm_task
        ConfirmMode.ALWAYS -> R.string.confirm_always
    }
)

@Composable
private fun BrandGuideRow(text: String) {
    SettingsRow(
        icon = null,
        title = text,
        enabled = false
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpacitySheet(
    current: OverlayPrefs,
    onApply: (OverlayPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    var opacity by remember { mutableStateOf(current.idleOpacity) }
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = stringResource(R.string.phone_overlay_idle_opacity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = OverlayPrefs.MIN_OPACITY..OverlayPrefs.MAX_OPACITY
            )
            Text(
                text = stringResource(R.string.phone_percent, (opacity * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            SettingsRow(
                icon = null,
                title = stringResource(R.string.phone_apply),
                onClick = {
                    onApply(current.copy(idleOpacity = opacity))
                    onDismiss()
                }
            )
        }
    }
}
