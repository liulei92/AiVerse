package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AppSwitch
import com.aiverse.core.ui.BatchDeleteConfirmDialog
import com.aiverse.core.ui.ListSelectionState
import com.aiverse.core.ui.SelectionActionBar
import com.aiverse.core.ui.SelectionActionButton
import com.aiverse.core.ui.SelectionActionIconButton
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.core.ui.RoleAvatar
import com.aiverse.core.ui.selectionListBottomContentPadding
import com.aiverse.feature.agent.domain.model.Role
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Trash2

/**
 * 角色管理页：角色列表。
 *
 * 每行一张卡片（头像 + 名称·描述 + 内置徽章），点击进入详情预览，左滑删除（内置角色不可删），
 * 行尾开关切换启停。顶栏「多选」进入批量模式：只有非内置角色能勾选，支持批量启停与批量删除。
 */
@Composable
internal fun RolesSection(
    roles: List<Role>,
    selection: ListSelectionState,
    onOpenDetail: (Role) -> Unit,
    onDelete: (Role) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onBatchDelete: (List<String>) -> Unit,
    onBatchSetEnabled: (List<String>, Boolean) -> Unit
) {
    var roleToDelete by remember { mutableStateOf<Role?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val t = LocalUIDensityTokens.current

    // 内置角色删不了，也就不参与多选：勾选框置灰、全选也不会选中它们。
    val selectableKeys = remember(roles) {
        roles.filter { !it.isBuiltin }.map { it.id }
    }

    // 删除后列表变化：剔除已消失的角色，全空了就退出多选。
    LaunchedEffect(selectableKeys, selection.isActive) {
        selection.retainOnly(selectableKeys.toSet())
        if (selection.isActive && selectableKeys.isEmpty()) selection.exit()
    }

    val allSelected = selection.selectedCount > 0 && selection.selectedCount == selectableKeys.size

    Box(modifier = Modifier.fillMaxSize()) {
        if (roles.isEmpty()) {
            Text(
                text = stringResource(R.string.roles_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
            )
        } else {
            RoleList(
                roles = roles,
                selection = selection,
                onOpenDetail = onOpenDetail,
                onDelete = { roleToDelete = it },
                onToggleEnabled = onToggleEnabled
            )
        }

        if (selection.isActive) {
            val enabled = selection.selectedCount > 0
            SelectionActionBar(
                selectedCount = selection.selectedCount,
                allSelected = allSelected,
                onToggleSelectAll = {
                    if (allSelected) selection.clear() else selection.replaceAll(selectableKeys)
                },
                onExit = { selection.exit() },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_enable),
                    enabled = enabled,
                    onClick = { onBatchSetEnabled(selection.selectedKeys, true) }
                )
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_disable),
                    enabled = enabled,
                    onClick = { onBatchSetEnabled(selection.selectedKeys, false) }
                )
                SelectionActionIconButton(
                    icon = FeatherIcons.Trash2,
                    contentDescription = stringResource(R.string.common_batch_delete),
                    enabled = enabled,
                    onClick = { showBatchDeleteConfirm = true },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    roleToDelete?.let { target ->
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.roles_delete_confirm_title),
            message = stringResource(R.string.roles_delete_confirm_message, target.name),
            onConfirm = {
                roleToDelete = null
                selection.exit()
                onDelete(target)
            },
            onDismiss = { roleToDelete = null }
        )
    }

    if (showBatchDeleteConfirm) {
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.roles_batch_delete_title),
            message = stringResource(R.string.roles_batch_delete_confirm, selection.selectedCount),
            onConfirm = {
                showBatchDeleteConfirm = false
                val ids = selection.selectedKeys
                selection.exit()
                onBatchDelete(ids)
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }
}

/** 角色列表。 */
@Composable
private fun RoleList(
    roles: List<Role>,
    selection: ListSelectionState,
    onOpenDetail: (Role) -> Unit,
    onDelete: (Role) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit
) {
    val t = LocalUIDensityTokens.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = t.xs,
            bottom = selectionListBottomContentPadding(selection.isActive)
        )
    ) {
        itemsIndexed(
            items = roles,
            key = { _, role -> role.id },
            contentType = { _, _ -> "role-row" }
        ) { index, role ->
            LazyGroupItem(index = index, lastIndex = roles.lastIndex) {
                RoleRow(
                    role = role,
                    onClick = { onOpenDetail(role) },
                    onDelete = { onDelete(role) },
                    selectionMode = selection.isActive,
                    selected = selection.isSelected(role.id),
                    onToggleSelect = { selection.toggle(role.id) },
                    onToggleEnabled = { onToggleEnabled(role.id, it) }
                )
            }
        }
    }
}

/** 单条角色行：头像 + 名称·描述 + 内置徽章 + 启停开关（内置角色不显示开关）；左滑删除（内置不可删），点击进详情。 */
@Composable
private fun RoleRow(
    role: Role,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onToggleEnabled: (Boolean) -> Unit = {}
) {
    val t = LocalUIDensityTokens.current
    val toggleDescription = stringResource(
        if (role.enabled) R.string.roles_disable else R.string.roles_enable
    )
    SwipeToDeleteRow(
        onDelete = onDelete,
        onClick = onClick,
        deleteEnabled = !role.isBuiltin,
        selectionMode = selectionMode,
        selected = selected,
        onToggleSelect = onToggleSelect,
        selectable = !role.isBuiltin
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = t.lg, end = t.xs, top = t.listCellPaddingVertical, bottom = t.listCellPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoleAvatar(role = role, size = t.scale(40))
            Spacer(modifier = Modifier.width(t.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(t.sm)
                ) {
                    Text(
                        text = role.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = if (role.isBuiltin || role.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.semanticColors.subtleText
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (role.isBuiltin) {
                        RoleBadge(
                            text = stringResource(R.string.roles_badge_builtin),
                            color = MaterialTheme.semanticColors.subtleText
                        )
                    }
                }
                if (role.description.isNotBlank()) {
                    Text(
                        text = role.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (role.isBuiltin || role.enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.semanticColors.subtleText
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(t.sm))
            if (selectionMode) {
                Icon(
                    imageVector = FeatherIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.semanticColors.subtleText,
                    modifier = Modifier.size(t.scale(18))
                )
            } else if (!role.isBuiltin) {
                AppSwitch(
                    checked = role.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.semantics { contentDescription = toggleDescription }
                )
            }
        }
    }
}

/** 小徽章：内置 / 自定义等只读标签。 */
@Composable
internal fun RoleBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(LocalUIDensityTokens.current.rSm))
            .padding(horizontal = LocalUIDensityTokens.current.sm, vertical = LocalUIDensityTokens.current.xs)
    )
}
