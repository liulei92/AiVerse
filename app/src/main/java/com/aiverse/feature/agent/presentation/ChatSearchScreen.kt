package com.aiverse.feature.agent.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.feature.agent.presentation.component.ChatSearchField
import com.aiverse.feature.agent.presentation.component.ChatSearchResults
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft

/**
 * 聊天记录搜索页：限当前会话内搜索关键词，命中消息高亮显示，点结果直接定位并高亮。
 * 复用侧边栏的搜索框与结果列表组件与 [AIAgentViewModel] 的搜索状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    viewModel: AIAgentViewModel,
    onNavigateBack: () -> Unit,
    onOpenHit: (ChatSearchHit) -> Unit
) {
    val query by viewModel.chatSearchQuery.collectAsStateWithLifecycle()
    val state by viewModel.chatSearchState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.chat_search_hint),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = FeatherIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ChatSearchField(
                query = query,
                onQueryChange = viewModel::updateChatSearchQuery,
                onClear = viewModel::clearChatSearch
            )
            ChatSearchResults(state = state, onOpenHit = onOpenHit)
        }
    }
}
