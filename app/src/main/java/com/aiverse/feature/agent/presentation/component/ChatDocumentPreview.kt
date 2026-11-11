package com.aiverse.feature.agent.presentation.component

import android.content.Context
import android.view.ContextThemeWrapper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.presentation.AgentAttachment
import com.aiverse.feature.editor.presentation.MarkdownPreviewViewModel
import com.aiverse.feature.workspace.domain.FileAccessProvider
import compose.icons.FeatherIcons
import compose.icons.feathericons.Maximize2
import compose.icons.feathericons.Minimize2
import compose.icons.feathericons.X
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 单个待预览文档：已在 IO 线程读好的全文 + 供相对资源解析的容器路径。 */
data class ChatDocumentPreviewDoc(
    val content: String,
    val containerPath: String,
    val fileName: String,
    val isHtml: Boolean
)

/**
 * 会话附件 md/html 预览的句柄：调用方只管 [show]，不关心读文件与渲染容器。
 *
 * 与 `LocalImageViewer` 同一套路 —— 用 CompositionLocal 而不是一路加回调参数，
 * 否则 `MessageAttachmentList` / `ChatInputAttachments` 这些中间层都要为转发一个 lambda 改签名。
 * 默认空实现：没挂 [ChatDocumentPreviewHost] 的组合树里点 md/html 静默无反应，不会崩。
 */
fun interface ChatDocumentPreviewer {
    /** 弹出预览（内联底部弹层，可放大为全屏）。已在 IO 线程读完全文后再调，传入的是已就绪的 [ChatDocumentPreviewDoc]。 */
    fun show(doc: ChatDocumentPreviewDoc)
}

val LocalChatDocumentPreviewer = staticCompositionLocalOf<ChatDocumentPreviewer> { ChatDocumentPreviewer {} }

/**
 * 聊天区当前生效的文件访问后端：预览 md/html 时按容器路径读全文（远程 SSH 模式会 SFTP 拉回）。
 * 由聊天面板注入，与 `LocalImageViewer` 同一层级提供。
 */
val LocalChatFileAccess = staticCompositionLocalOf<FileAccessProvider> {
    error("LocalChatFileAccess 未提供：聊天附件 md/html 预览不可用")
}

/** [ChatDocumentPreviewer] 的状态实现：持有当前文档与是否全屏。 */
class ChatDocumentPreviewState : ChatDocumentPreviewer {
    var doc by mutableStateOf<ChatDocumentPreviewDoc?>(null)
        private set
    var expanded by mutableStateOf(false)

    override fun show(doc: ChatDocumentPreviewDoc) {
        this.doc = doc
        expanded = false
    }

    fun dismiss() {
        doc = null
        expanded = false
    }
}

@Composable
fun rememberChatDocumentPreviewState(): ChatDocumentPreviewState = remember { ChatDocumentPreviewState() }

private const val MAX_PREVIEW_BYTES = 4L * 1024 * 1024
private const val TAG = "ChatDocumentPreview"

/**
 * 内联预览弹层的最大高度：够看清一屏 md/html，超出部分在 WebView 里滚，不必切全屏。
 */
private val INLINE_PREVIEW_MAX_HEIGHT = 560.dp

/** 深浅两套预览页底色，与 Markdown 预览模板一致。 */
private const val BG_DARK = 0xFF0D1117.toInt()
private const val BG_LIGHT = 0xFFFFFFFF.toInt()

/**
 * 读取待预览附件全文。宿主本地文件直读，否则退回容器路径经 [FileAccessProvider] 取
 * （远程 SSH 模式会 SFTP 拉回）。返回 null 表示读不到或超出预览上限（已 toast 提示）。
 */
internal suspend fun readChatDocumentPreviewDoc(
    context: Context,
    attachment: AgentAttachment,
    fileAccess: FileAccessProvider
): ChatDocumentPreviewDoc? = readChatDocumentPreviewDoc(
    context = context,
    fileName = attachment.fileName,
    mimeType = attachment.mimeType,
    localPath = attachment.localPath,
    containerPath = attachment.containerPath,
    fileAccess = fileAccess
)

/** 待发送附件的读取入口：输入框里 md/html 点击预览时走这个，字段与 [AgentAttachment] 对齐。 */
internal suspend fun readChatDocumentPreviewDoc(
    context: Context,
    attachment: PendingUploadAttachment,
    fileAccess: FileAccessProvider
): ChatDocumentPreviewDoc? = readChatDocumentPreviewDoc(
    context = context,
    fileName = attachment.fileName,
    mimeType = attachment.mimeType,
    localPath = attachment.localPath,
    containerPath = attachment.containerPath,
    fileAccess = fileAccess
)

private suspend fun readChatDocumentPreviewDoc(
    context: Context,
    fileName: String,
    mimeType: String,
    localPath: String,
    containerPath: String,
    fileAccess: FileAccessProvider
): ChatDocumentPreviewDoc? = withContext(Dispatchers.IO) {
    val isHtml = isHtmlDocument(fileName, mimeType)
    val local = localPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    runCatching {
        if (local != null) {
            if (local.length() > MAX_PREVIEW_BYTES) {
                notifyTooLarge(context)
                return@withContext null
            }
            ChatDocumentPreviewDoc(local.readText(), containerPath, fileName, isHtml)
        } else {
            if (containerPath.isBlank() || !fileAccess.isFile(containerPath)) return@withContext null
            if (fileAccess.fileSize(containerPath) > MAX_PREVIEW_BYTES) {
                notifyTooLarge(context)
                return@withContext null
            }
            ChatDocumentPreviewDoc(fileAccess.readFile(containerPath), containerPath, fileName, isHtml)
        }
    }.onFailure {
        FileLogger.w(TAG, "读取预览附件失败: $containerPath", it)
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.chat_document_preview_failed),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }.getOrNull()
}

private fun notifyTooLarge(context: Context) {
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.chat_document_preview_too_large),
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

/** 是否为可在聊天页内直接预览的文档（Markdown / HTML）。 */
fun isChatPreviewableDocument(fileName: String, mimeType: String = ""): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in setOf("md", "markdown", "html", "htm") ||
        mimeType.equals("text/html", ignoreCase = true) ||
        mimeType.equals("text/markdown", ignoreCase = true)
}

private fun isHtmlDocument(fileName: String, mimeType: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in setOf("html", "htm") || mimeType.equals("text/html", ignoreCase = true)
}

/**
 * 会话内 md/html 预览宿主。挂在聊天组合树里任意位置即可 —— 内联时是底部弹层，
 * 放大后切为独立全屏 Dialog，两者共用同一个 WebView 实例（切态不重载、不丢滚动位置）。
 */
@Composable
fun ChatDocumentPreviewHost(
    state: ChatDocumentPreviewState,
    viewModel: MarkdownPreviewViewModel
) {
    val doc = state.doc ?: return
    val webView = rememberChatPreviewWebView(doc, viewModel)
    if (state.expanded) {
        ChatDocumentFullscreenPreview(
            doc = doc,
            webView = webView,
            onCollapse = { state.expanded = false }
        )
    } else {
        ChatDocumentInlinePreview(
            doc = doc,
            webView = webView,
            onExpand = { state.expanded = true },
            onDismiss = { state.dismiss() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDocumentInlinePreview(
    doc: ChatDocumentPreviewDoc,
    webView: WebView,
    onExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = INLINE_PREVIEW_MAX_HEIGHT)
        ) {
            ChatDocumentPreviewHeader(
                fileName = doc.fileName,
                expanded = false,
                onToggleExpand = onExpand,
                onClose = onDismiss
            )
            // WebView 自带跟随明暗的背景色，这里只兜一层主题背景防初始化闪白
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ChatDocumentFullscreenPreview(
    doc: ChatDocumentPreviewDoc,
    webView: WebView,
    onCollapse: () -> Unit
) {
    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(
            // 不设则 dialog window 是 WRAP_CONTENT，内容铺不满屏
            usePlatformDefaultWidth = false,
            // 不设则内容被系统栏 inset 挤进安全区，与 ImageViewer 全屏同一套处理
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ChatDocumentPreviewHeader(
                fileName = doc.fileName,
                expanded = true,
                onToggleExpand = onCollapse,
                onClose = onCollapse
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ChatDocumentPreviewHeader(
    fileName: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.xs)
            )
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (expanded) FeatherIcons.Minimize2 else FeatherIcons.Maximize2,
                    contentDescription = stringResource(
                        if (expanded) R.string.chat_document_preview_exit_fullscreen
                        else R.string.chat_document_preview_fullscreen
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = FeatherIcons.X,
                    contentDescription = stringResource(R.string.common_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 记住一个预览用 WebView：内联弹层与全屏 Dialog 切换时复用同一实例，不重载内容、不丢滚动位置。
 *
 * 安全边界与 Markdown 预览页一致：不暴露本地文件系统、只经 assetLoader 取资源、外链一律拦下。
 * JS 是否启用由渲染路径决定（Markdown 需要模板内解析，原生 HTML 一律禁用），装载时按文档类型切换。
 */
@Composable
private fun rememberChatPreviewWebView(
    doc: ChatDocumentPreviewDoc?,
    viewModel: MarkdownPreviewViewModel
): WebView {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val webView = remember {
        val themedContext = ContextThemeWrapper(
            context,
            if (dark) R.style.Theme_AICode_WebView_Dark else R.style.Theme_AICode_WebView
        )
        WebView(themedContext).apply {
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(if (dark) BG_DARK else BG_LIGHT)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = viewModel.assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    // 页内锚点：仅 fragment 变化、path 与当前页一致，放行交给 WebView 滚动
                    if (url.fragment != null && url.path == view.url?.let { android.net.Uri.parse(it).path }) return false
                    // 指向工作区的文件链接放行，让 WebView 就地导航
                    if (viewModel.containerPathForUrl(url) != null) return false
                    // 其余（外链等）一律拦下
                    return true
                }
            }
        }
    }

    // 深色切换时同步底色，避免滚动条等控件配色不匹配
    LaunchedEffect(dark) {
        (webView.context as? ContextThemeWrapper)?.setTheme(
            if (dark) R.style.Theme_AICode_WebView_Dark else R.style.Theme_AICode_WebView
        )
        webView.setBackgroundColor(if (dark) BG_DARK else BG_LIGHT)
    }

    LaunchedEffect(doc) {
        if (doc == null) return@LaunchedEffect
        val baseUrl = viewModel.baseUrl(doc.containerPath)
        if (doc.isHtml) {
            // 原生 HTML：直接 load，禁 JS（附件 HTML 不可信，禁 JS 杜绝脚本执行）
            webView.settings.javaScriptEnabled = false
            webView.loadDataWithBaseURL(baseUrl, doc.content, "text/html", "utf-8", null)
        } else {
            // Markdown：套内置模板由页面内 marked + DOMPurify 解析，必须开 JS
            webView.settings.javaScriptEnabled = true
            val rendered = viewModel.buildHtml(doc.content, dark)
            webView.loadDataWithBaseURL(baseUrl, rendered, "text/html", "utf-8", null)
        }
    }

    return webView
}
