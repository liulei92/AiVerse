package com.aiverse.feature.git.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.aiverse.R
import com.aiverse.core.ui.ImageSource
import com.aiverse.core.ui.ImageViewerRequest
import com.aiverse.core.ui.LocalImageViewer
import com.aiverse.feature.agent.presentation.component.LocalMarkdownImageTransformer
import com.aiverse.feature.agent.presentation.component.MarkdownContent
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/**
 * GitHub issue 正文 / 评论的 Markdown 渲染。
 *
 * 复用聊天区的 [MarkdownContent] 排版与代码高亮，只替换图片处理：
 * 聊天流为「不渲染 HTML」会把 `<` 用零宽空格中和，导致 issue 正文里 GitHub 常见的
 * 原始 `<img src="https://github.com/user-attachments/assets/...">` 显示成字面源码；
 * 这里先把 `<img>` 还原成 `![alt](src)` Markdown 图片语法，再交给渲染器，并由
 * [GitIssueImageTransformer] 用 Coil 把远程图片显示出来（点按拉起全屏查看器）。
 *
 * @param color 正文文字色（与 MarkdownContent 的 text 色一致）。
 */
@Composable
internal fun GitIssueMarkdown(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val processed = remember(text) { preprocessGitIssueHtml(text) }
    CompositionLocalProvider(
        LocalMarkdownImageTransformer provides GitIssueImageTransformer
    ) {
        MarkdownContent(
            text = processed,
            color = color,
            modifier = modifier
        )
    }
}

/**
 * 把 issue 正文/评论里的原始 `<img ...>` HTML 标签转成 Markdown 图片语法。
 *
 * 只处理 `<img>` 标签（含大小写、单双引号、任意属性顺序）；无 src 或解析失败原样保留。
 * 其余 HTML（`<br>`、`<details>` 等）保持原样，由 [MarkdownContent] 内部的预处理按字面展示，
 * 与聊天流一致，不影响图片。
 */
internal fun preprocessGitIssueHtml(text: String): String {
    if (!text.contains('<')) return text
    return IMG_TAG_REGEX.replace(text) { m ->
        val tag = m.value
        val src = SRC_ATTR_REGEX.find(tag)?.groupValues?.get(1)
        if (src.isNullOrBlank()) {
            m.value
        } else {
            val alt = ALT_ATTR_REGEX.find(tag)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "Image"
            "![$alt]($src)"
        }
    }
}

private val IMG_TAG_REGEX = Regex("""<img\b[^>]*?>""", RegexOption.IGNORE_CASE)
private val SRC_ATTR_REGEX = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val ALT_ATTR_REGEX = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

/**
 * Git issue 正文的 Markdown 图片 transformer。
 *
 * 只渲染 http/https 远程图片（GitHub user-attachments 及其重定向目标无需鉴权，直接 Coil 加载）；
 * 其它（本地路径、自定义 scheme）一律返回 null 不渲染，与聊天区「不渲染本地路径」的语义保持
 * 一致 —— issue 正文一般只有远程图片。加载中 Coil 显示占位，点按拉起全屏查看器看原图。
 */
private object GitIssueImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        val url = link.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val viewer = LocalImageViewer.current
        val clickLabel = stringResource(R.string.common_image_preview)
        val painter = rememberAsyncImagePainter(model = url)
        // remember 住 modifier：ImageData 是 data class，每帧换新 Modifier 会让 equals 恒不成立，
        // 白白触发下游重组。
        val modifier = remember(url, viewer, clickLabel) {
            Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_HEIGHT_DP)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = clickLabel) {
                    viewer.show(
                        ImageViewerRequest(
                            sources = listOf(ImageSource.Url(url)),
                            title = url.trimEnd('/').substringAfterLast('/')
                        )
                    )
                }
        }
        return ImageData(painter = painter, modifier = modifier, contentScale = ContentScale.Fit)
    }

    /** 显示最大高度（dp），超长图等比缩放。与聊天区 MarkdownImageTransformer 一致。 */
    private val MAX_HEIGHT_DP = 320.dp
}
