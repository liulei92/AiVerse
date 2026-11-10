package com.aiverse.feature.git.presentation.component

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.BatchDeleteConfirmDialog
import com.aiverse.feature.git.domain.model.GitHubComment
import com.aiverse.feature.git.domain.model.GitHubIssue
import com.aiverse.feature.settings.presentation.component.settingsPageBackground
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Lock
import compose.icons.feathericons.Unlock
import kotlinx.coroutines.launch

/**
 * GitHub issue 详情页（全屏覆盖）：标题、状态、元信息、标签、正文、评论列表，以及操作入口
 * （关闭/重开、锁定/解锁、标签编辑、受理人选择、评论发表）。写操作经 ViewModel
 * 触发，由 [actionBusy] 禁用重复提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GitIssueDetailPage(
    issue: GitHubIssue?,
    loading: Boolean,
    comments: List<GitHubComment>,
    commentsLoading: Boolean,
    contributors: List<String>,
    actionBusy: Boolean,
    actionError: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onClose: (String) -> Unit = {},
    onReopen: () -> Unit = {},
    onAddComment: (String) -> Unit = {},
    onDeleteComment: (Long) -> Unit = {},
    onAddLabels: (List<String>) -> Unit = {},
    onRemoveLabel: (String) -> Unit = {},
    onSetAssignees: (List<String>) -> Unit = {},
    onToggleLock: (Boolean) -> Unit = {},
    onLoadComments: () -> Unit = {},
    onLoadContributors: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = settingsPageBackground(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_issue_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settingsPageBackground())
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.git_issues_loading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            issue == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.git_issues_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }
            }
            else -> IssueDetailContent(
                issue = issue,
                comments = comments,
                commentsLoading = commentsLoading,
                contributors = contributors,
                actionBusy = actionBusy,
                actionError = actionError,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.padding(padding),
                onClose = onClose,
                onReopen = onReopen,
                onAddComment = onAddComment,
                onDeleteComment = onDeleteComment,
                onAddLabels = onAddLabels,
                onRemoveLabel = onRemoveLabel,
                onSetAssignees = onSetAssignees,
                onToggleLock = onToggleLock,
                onLoadComments = onLoadComments,
                onLoadContributors = onLoadContributors
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IssueDetailContent(
    issue: GitHubIssue,
    comments: List<GitHubComment>,
    commentsLoading: Boolean,
    contributors: List<String>,
    actionBusy: Boolean,
    actionError: String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onClose: (String) -> Unit,
    onReopen: () -> Unit,
    onAddComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onAddLabels: (List<String>) -> Unit,
    onRemoveLabel: (String) -> Unit,
    onSetAssignees: (List<String>) -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onLoadComments: () -> Unit,
    onLoadContributors: () -> Unit
) {
    val open = issue.state == "open"

    // 加载评论（进入即触发一次）。
    LaunchedEffect(issue.number) { onLoadComments() }
    // 懒加载受理人候选。
    LaunchedEffect(Unit) { onLoadContributors() }

    // 复制到剪贴板 + snackbar 反馈。
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copyText: (String) -> Unit = { text ->
        val t = text.trim()
        if (t.isNotEmpty()) {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("git-issue", t)))
                snackbarHostState.showSnackbar(context.getString(R.string.common_copy_success))
            }
        }
    }

    // 本地弹窗状态。
    var labelDialog by remember { mutableStateOf(false) }
    var assigneeDialog by remember { mutableStateOf(false) }
    // 待删除的评论 id；非 null 时显示二次确认弹窗。
    var deleteConfirmCommentId by remember { mutableStateOf<Long?>(null) }
    var commentInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IssueStateBadge(open = open)
                Text(
                    text = "#${issue.number}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (issue.locked) {
                    Icon(
                        FeatherIcons.Lock,
                        contentDescription = stringResource(R.string.git_issue_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (open) {
                    Box {
                        var closeMenuExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { closeMenuExpanded = true },
                            enabled = !actionBusy,
                            shape = RoundedCornerShape(Radius.pill)
                        ) {
                            Text(stringResource(R.string.git_issue_action_close))
                        }
                        DropdownMenu(
                            expanded = closeMenuExpanded,
                            onDismissRequest = { closeMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_issue_close_reason_completed)) },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                                onClick = {
                                    closeMenuExpanded = false
                                    onClose("completed")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_issue_close_reason_not_planned)) },
                                leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, contentDescription = null) },
                                onClick = {
                                    closeMenuExpanded = false
                                    onClose("not_planned")
                                }
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onReopen,
                        enabled = !actionBusy,
                        shape = RoundedCornerShape(Radius.pill)
                    ) {
                        Text(stringResource(R.string.git_issue_action_reopen))
                    }
                }
                OutlinedButton(
                    onClick = { onToggleLock(!issue.locked) },
                    enabled = !actionBusy,
                    shape = RoundedCornerShape(Radius.pill)
                ) {
                    Text(stringResource(if (issue.locked) R.string.git_issue_action_unlock else R.string.git_issue_action_lock))
                }
            }
        }
        item {
            Text(
                text = issue.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (issue.userLogin.isNotEmpty()) {
                    AuthorAvatar(name = issue.userLogin, size = 18.dp)
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    text = stringResource(R.string.git_issue_opened_by, issue.userLogin, formatIssueDate(issue.createdAt)) +
                        " • " + stringResource(R.string.git_issue_comments, issue.comments),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (issue.assigneeLogins.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.git_issue_assignees, issue.assigneeLogins.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (issue.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        issue.labels.forEach { label -> LabelChip(label) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { labelDialog = true }, enabled = !actionBusy) {
                    Text(stringResource(R.string.git_issue_edit_labels))
                }
                TextButton(onClick = { assigneeDialog = true }, enabled = !actionBusy) {
                    Text(stringResource(R.string.git_issue_choose_assignees))
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        item {
            val body = issue.body?.trim().orEmpty()
            if (body.isEmpty()) {
                Text(
                    text = stringResource(R.string.git_issue_body_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    // Markdown 渲染：远程图片（user-attachments 等）用 Coil 显示、点按放大。
                    GitIssueMarkdown(
                        text = body,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 正文复制入口（右下角低调按钮）。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { copyText(body) }) {
                            Text(
                                stringResource(R.string.common_copy),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // 评论区
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(
                text = stringResource(R.string.git_issue_comments_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (commentsLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else if (comments.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.git_issue_no_comments),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(comments.size) { idx ->
                val comment = comments[idx]
                val commentBody = comment.body?.trim().orEmpty()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (comment.userLogin.isNotEmpty()) {
                                AuthorAvatar(name = comment.userLogin, size = 18.dp)
                                Spacer(Modifier.width(Spacing.xs))
                            }
                            Text(
                                text = comment.userLogin,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = formatIssueDate(comment.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            if (commentBody.isNotEmpty()) {
                                TextButton(onClick = { copyText(commentBody) }) {
                                    Text(
                                        stringResource(R.string.common_copy),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            TextButton(
                                onClick = { deleteConfirmCommentId = comment.id },
                                enabled = !actionBusy
                            ) {
                                Text(
                                    stringResource(R.string.git_issue_delete_comment),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (commentBody.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.xs))
                            // Markdown 渲染：评论里的图片（user-attachments 等）可点按放大。
                            GitIssueMarkdown(
                                text = commentBody,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 评论输入
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    placeholder = { Text(stringResource(R.string.git_issue_comment_placeholder)) },
                    enabled = !actionBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.md)
                )
                TextButton(
                    onClick = {
                        val text = commentInput.trim()
                        if (text.isNotEmpty()) {
                            onAddComment(text)
                            commentInput = ""
                        }
                    },
                    enabled = !actionBusy && commentInput.isNotBlank()
                ) { Text(stringResource(R.string.git_issue_post_comment)) }
            }
        }

        if (!actionError.isNullOrBlank()) {
            item {
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (labelDialog) {
        LabelEditDialog(
            currentLabels = issue.labels.map { it.name },
            actionBusy = actionBusy,
            onDismiss = { labelDialog = false },
            onAdd = { names -> onAddLabels(names); labelDialog = false },
            onRemove = { name -> onRemoveLabel(name) }
        )
    }
    if (assigneeDialog) {
        AssigneeDialog(
            current = issue.assigneeLogins,
            candidates = contributors,
            actionBusy = actionBusy,
            onDismiss = { assigneeDialog = false },
            onConfirm = { list -> onSetAssignees(list); assigneeDialog = false }
        )
    }
    // 删除评论的二次确认弹窗：确认后才真正删除，避免误删。
    deleteConfirmCommentId?.let { commentId ->
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.git_issue_delete_comment_confirm_title),
            message = stringResource(R.string.git_issue_delete_comment_confirm_message),
            inProgress = actionBusy,
            onConfirm = {
                onDeleteComment(commentId)
                deleteConfirmCommentId = null
            },
            onDismiss = { deleteConfirmCommentId = null }
        )
    }
}

@Composable
private fun LabelEditDialog(
    currentLabels: List<String>,
    actionBusy: Boolean,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_issue_edit_labels)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (currentLabels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        currentLabels.forEach { name ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(Radius.pill),
                                onClick = { onRemove(name) }
                            ) {
                                Text(
                                    text = "$name ×",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.git_issue_label_names)) },
                    enabled = !actionBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.git_issue_label_remove_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val names = input.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                    if (names.isNotEmpty()) onAdd(names)
                },
                enabled = !actionBusy
            ) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun AssigneeDialog(
    current: List<String>,
    candidates: List<String>,
    actionBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(current.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_issue_choose_assignees)) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    stringResource(R.string.git_issue_no_contributors),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    candidates.forEach { login ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = login in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + login else selected - login
                                }
                            )
                            Text(login, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = !actionBusy) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun IssueStateBadge(open: Boolean) {
    val bg = if (open) Color(0xFF2DA44E) else Color(0xFF8250DF)
    Surface(color = bg, shape = RoundedCornerShape(Radius.pill)) {
        Text(
            text = stringResource(if (open) R.string.git_issues_open else R.string.git_issues_closed),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
