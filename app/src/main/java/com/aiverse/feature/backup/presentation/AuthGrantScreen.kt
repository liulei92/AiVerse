package com.aiverse.feature.backup.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.feature.backup.domain.HandoffChannel
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.Book
import compose.icons.feathericons.Key
import compose.icons.feathericons.Lock
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Server
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Shield
import compose.icons.feathericons.Sliders
import compose.icons.feathericons.Star
import compose.icons.feathericons.Upload
import compose.icons.feathericons.Users
import compose.icons.feathericons.X
import compose.icons.feathericons.Check

/**
 * 正式版侧的「备份授权」完整页面：测试版通过 `aiverse://auth/grant-backup` 拉起后展示。
 *
 * 用户在这里挑一条传输通道再点「允许」，[HandoffChannel] 由调用方（AuthGrantActivity）落地：
 * - [HandoffChannel.FileProviderUri] 文件全程留在正式版私有目录，临时授权读完即收回；
 * - [HandoffChannel.HttpServer] 文件经 127.0.0.1 回环传输，用完即关服务器。
 *
 * 内容较多（风险说明 + 通道选择 + 数据清单 + 兜底入口），用 [AdaptiveModalBottomSheet] 承接，
 * 手机上是底栏、平板上自动降级为居中弹窗，内容区可滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthGrantScreen(
    onAllow: (HandoffChannel) -> Unit,
    onDeny: () -> Unit,
    onOpenExistingBackup: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    var selectedChannel by remember { mutableStateOf(HandoffChannel.FileProviderUri) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    AdaptiveModalBottomSheet(
        onDismissRequest = onDeny,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0.dp) },
        dialogMaxWidth = 600.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.88f)
        ) {
            AuthGrantHeader(
                title = stringResource(R.string.auth_grant_title),
                onDismiss = onDeny
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = t.lg)
                        .padding(bottom = t.md)
                        .navigationBarsPadding()
                ) {
                    // 通道安全说明：两条通道各自的风险边界，放在选择之前
                    SettingsGroupHeader(text = stringResource(R.string.auth_grant_risk_header))
                    SettingsGroup {
                        AuthGrantRiskRow(
                            icon = FeatherIcons.Lock,
                            title = stringResource(R.string.auth_grant_channel_file_provider),
                            detail = stringResource(R.string.auth_grant_risk_file_provider)
                        )
                        SettingsDivider()
                        AuthGrantRiskRow(
                            icon = FeatherIcons.Globe,
                            title = stringResource(R.string.auth_grant_channel_http),
                            detail = stringResource(R.string.auth_grant_risk_http)
                        )
                    }

                    // 通道选择：通道 1 推荐
                    SettingsGroupHeader(text = stringResource(R.string.auth_grant_channel_header))
                    SettingsGroup {
                        AuthGrantChannelRow(
                            icon = FeatherIcons.Lock,
                            title = stringResource(R.string.auth_grant_channel_file_provider),
                            recommended = true,
                            selected = selectedChannel == HandoffChannel.FileProviderUri,
                            onSelect = { selectedChannel = HandoffChannel.FileProviderUri }
                        )
                        SettingsDivider()
                        AuthGrantChannelRow(
                            icon = FeatherIcons.Globe,
                            title = stringResource(R.string.auth_grant_channel_http),
                            recommended = false,
                            selected = selectedChannel == HandoffChannel.HttpServer,
                            onSelect = { selectedChannel = HandoffChannel.HttpServer }
                        )
                    }

                    // 将被共享的数据清单
                    SettingsGroupHeader(text = stringResource(R.string.auth_grant_data_header))
                    SettingsGroup {
                        val items = listOf(
                            FeatherIcons.Book to R.string.backup_data_skills,
                            FeatherIcons.Server to R.string.backup_data_mcp,
                            FeatherIcons.Users to R.string.backup_data_roles,
                            FeatherIcons.Shield to R.string.backup_data_permissions,
                            FeatherIcons.Settings to R.string.backup_data_app_settings,
                            FeatherIcons.Sliders to R.string.common_ai_providers,
                            FeatherIcons.Key to R.string.auth_grant_data_git_credentials
                        )
                        items.forEachIndexed { index, (icon, titleRes) ->
                            if (index > 0) SettingsDivider()
                            SettingsRow(icon = icon, title = stringResource(titleRes))
                        }
                    }

                    // 兜底：更习惯传统导入导出时直接跳到现有备份页
                    SettingsGroupHeader(text = stringResource(R.string.auth_grant_fallback_header))
                    SettingsGroup {
                        SettingsRow(
                            icon = FeatherIcons.Upload,
                            title = stringResource(R.string.auth_grant_fallback),
                            subtitle = stringResource(R.string.auth_grant_fallback_hint),
                            onClick = onOpenExistingBackup
                        )
                    }
                }
            }

            AuthGrantFooter(
                onAllow = { onAllow(selectedChannel) },
                onDeny = onDeny
            )
        }
    }
}

/** 授权页标题行：居中标题 + 右侧关闭。 */
@Composable
private fun AuthGrantHeader(title: String, onDismiss: () -> Unit) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = t.lg, end = t.sm, top = t.sm, bottom = t.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = FeatherIcons.X,
                contentDescription = stringResource(R.string.common_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(t.scale(20))
            )
        }
    }
}

/**
 * 通道安全说明行：标题 + 多行风险边界描述。
 *
 * 不复用 [SettingsRow] 的 subtitle 槽位——那里限一行省略，装不下完整风险说明。
 */
@Composable
private fun AuthGrantRiskRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String
) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.lg, vertical = t.listCellPaddingVertical)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.semanticColors.warning,
            modifier = Modifier.size(t.scale(16))
        )
        Spacer(Modifier.width(t.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(t.xs))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 通道选择行：可点击（[Modifier.clickable] 挂在 modifier 上、onClick 传 null，
 * 因此不会出现 [SettingsRow] 自带的右箭头），右侧显示「推荐」标签或选中态。
 */
@Composable
private fun AuthGrantChannelRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    recommended: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    SettingsRow(
        icon = icon,
        title = title,
        enabled = true,
        modifier = Modifier.clickable(onClick = onSelect),
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(t.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recommended) {
                    Surface(
                        shape = t.chipShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = t.sm, vertical = t.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(t.scale(12))
                            )
                            Spacer(Modifier.width(t.xs))
                            Text(
                                text = stringResource(R.string.common_recommended),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = FeatherIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(t.scale(16))
                    )
                }
            }
        }
    )
}

/** 授权页底部操作：拒绝（描边）+ 允许并共享（主按钮）。 */
@Composable
private fun AuthGrantFooter(onAllow: () -> Unit, onDeny: () -> Unit) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.lg)
            .padding(top = t.sm, bottom = t.xl)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(t.sm)
    ) {
        OutlinedButton(
            onClick = onDeny,
            modifier = Modifier.weight(1f).height(t.touchTarget)
        ) {
            Text(stringResource(R.string.common_deny))
        }
        Button(
            onClick = onAllow,
            modifier = Modifier.weight(1f).height(t.touchTarget)
        ) {
            Text(
                text = stringResource(R.string.auth_grant_allow),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}