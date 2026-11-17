package com.aiverse.feature.plugins.presentation.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.feature.plugins.domain.newmodel.PluginWorkspaceTool
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Check
import compose.icons.feathericons.MoreVertical
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Search

/**
 * 界面 ③：全部配置 / 工具库（展示所有可用插件提供的工具，支持添加到当前项目）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(
    availableTools: List<PluginWorkspaceTool>,
    enabledToolKeys: Set<String>,
    languageTag: String = "zh-CN",
    onBack: () -> Unit,
    onAddTool: (PluginWorkspaceTool) -> Unit,
    onRemoveTool: (PluginWorkspaceTool) -> Unit,
    onViewDetail: (PluginWorkspaceTool) -> Unit
) {
    val t = LocalUIDensityTokens.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredTools = remember(availableTools, searchQuery) {
        if (searchQuery.isBlank()) availableTools
        else availableTools.filter {
            it.title.resolve(languageTag).contains(searchQuery, ignoreCase = true) ||
                it.description.resolve(languageTag).contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.plugins_all_tools),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = FeatherIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.common_back),
                            modifier = Modifier.size(t.scale(22))
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = FeatherIcons.MoreVertical,
                            contentDescription = null,
                            modifier = Modifier.size(t.scale(20))
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.lg)
        ) {
            // 搜索栏
            AppTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                placeholder = stringResource(R.string.plugins_search_tools),
                leadingIcon = {
                    Icon(
                        imageVector = FeatherIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(t.scale(18))
                    )
                }
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            if (filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.plugins_no_available_tools),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(t.scale(10))
                ) {
                    items(items = filteredTools, key = { it.key }) { tool ->
                        val isAdded = tool.key in enabledToolKeys
                        AllToolItemRow(
                            tool = tool,
                            isAdded = isAdded,
                            languageTag = languageTag,
                            onAdd = { onAddTool(tool) },
                            onRemove = { onRemoveTool(tool) },
                            onClick = { onViewDetail(tool) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllToolItemRow(
    tool: PluginWorkspaceTool,
    isAdded: Boolean,
    languageTag: String,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(Radius.mdLarge)
    if (isAdded) {
        SwipeToDeleteRow(
            onDelete = onRemove,
            onClick = onClick,
            modifier = Modifier.clip(cardShape)
        ) {
            AllToolCard(
                tool = tool,
                isAdded = true,
                languageTag = languageTag,
                onAdd = onAdd,
                onClick = null
            )
        }
    } else {
        AllToolCard(
            tool = tool,
            isAdded = false,
            languageTag = languageTag,
            onAdd = onAdd,
            onClick = onClick
        )
    }
}

@Composable
private fun AllToolCard(
    tool: PluginWorkspaceTool,
    isAdded: Boolean,
    languageTag: String,
    onAdd: () -> Unit,
    onClick: (() -> Unit)?
) {
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.mdLarge))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(Radius.mdLarge),
        color = MaterialTheme.semanticColors.cardSurface,
        tonalElevation = t.scale(1)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = t.scale(14)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类别/图标
            val iconColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(t.scale(44))
                    .background(
                        color = iconColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(Radius.md)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = resolveToolIcon(tool.icon),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(t.scale(24))
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.title.resolve(languageTag),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(t.scale(2)))
                Text(
                    text = "${tool.category} · ${tool.description.resolve(languageTag)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            if (isAdded) {
                Box(
                    modifier = Modifier.size(t.scale(48)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FeatherIcons.Check,
                        contentDescription = stringResource(R.string.plugins_tool_added),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(t.scale(20))
                    )
                }
            } else {
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = FeatherIcons.Plus,
                        contentDescription = stringResource(R.string.plugins_tool_add),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(t.scale(20))
                    )
                }
            }
        }
    }
}