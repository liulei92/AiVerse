package com.aiverse.feature.agent.domain.tool

import android.content.Context
import com.aiverse.R
import com.aiverse.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aiverse.feature.agent.domain.container.ContainerInstaller
import com.aiverse.feature.workspace.domain.FileAccessProvider
import com.aiverse.feature.workspace.domain.PathHomeResolver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class StoredToolOutput(
    val preview: String,
    val truncated: Boolean,
    val totalChars: Long,
    val outputPath: String? = null,
    val storageError: String? = null
)

@Singleton
class ToolOutputStore @Inject constructor(
    private val containerInstaller: ContainerInstaller,
    private val fileAccess: FileAccessProvider,
    private val pathHomeResolver: PathHomeResolver,
    @param:ApplicationContext private val appContext: Context
) {
    private companion object {
        const val TAG = "ToolOutputStore"
        const val OUTPUT_DIR = "tool-output"
        const val HEAD_CHARS = 20_000
        const val TAIL_CHARS = 20_000
        const val MAX_INLINE_CHARS = HEAD_CHARS + TAIL_CHARS
        val LARGE_TEXT_FIELDS = listOf("output", "content", "text", "stdout", "stderr", "body", "result")
        /** 结构内可截断字符串字段的最小长度，低于此值不值得为省预算而截断。 */
        const val MIN_TRUNCATE_FIELD_CHARS = 200
        /** 整体超限时最多执行的「截断最大字段」轮数，避免超大结果导致长时间循环。 */
        const val MAX_TRUNCATE_PASSES = 24
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }

    private val json = Json { encodeDefaults = true }

    /**
     * 设备本地存档目录（宿主路径），仅供本机存储占用统计与清理。
     * 写入落在当前执行环境（本地即此目录；远程经 SFTP 落到服务器 home 下的 `.aiverse/tool-output`），
     * 故远程模式下本目录为空、实际存档在远端。
     */
    val outputDir: File get() = File(containerInstaller.aiverseDir, OUTPUT_DIR)

    fun process(toolName: String, callId: String, result: ToolResult): ToolResult {
        return when (result) {
            is ToolResult.Success -> ToolResult.Success(processElement(toolName, callId, result.data))
            is ToolResult.Partial -> ToolResult.Partial(processElement(toolName, callId, result.data), result.message)
            is ToolResult.Error -> result
        }
    }

    fun boundText(toolName: String, callId: String, text: String): StoredToolOutput {
        if (text.length <= MAX_INLINE_CHARS) {
            return StoredToolOutput(
                preview = text,
                truncated = false,
                totalChars = text.length.toLong()
            )
        }

        val writeResult = writeFullOutput(toolName, callId, text)
        val preview = buildPreview(text, writeResult.outputPath)
        return StoredToolOutput(
            preview = preview,
            truncated = true,
            totalChars = text.length.toLong(),
            outputPath = writeResult.outputPath,
            storageError = writeResult.storageError
        )
    }

    private fun processElement(toolName: String, callId: String, element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive
        if (primitive?.isString == true) {
            val stored = boundText(toolName, callId, primitive.content)
            return if (stored.truncated) stored.toJsonObject("output") else element
        }

        val obj = element as? JsonObject
        if (obj != null) {
            val largeField = LARGE_TEXT_FIELDS
                .mapNotNull { key ->
                    val field = obj[key] as? JsonPrimitive
                    val value = if (field?.isString == true) field.content else null
                    if (value != null && value.length > MAX_INLINE_CHARS) key to value else null
                }
                .maxByOrNull { it.second.length }

            if (largeField != null) {
                val stored = boundText(toolName, callId, largeField.second)
                val updated = obj.toMutableMap()
                updated[largeField.first] = JsonPrimitive(stored.preview)
                addMetadata(updated, stored)
                return JsonObject(updated)
            }
        }

        val serialized = json.encodeToString(element)
        if (serialized.length <= MAX_INLINE_CHARS) return element

        // 整体超限：不要把结构化结果整体折叠成 {"content": ...}（那会让 getIssue 的
        // issue.title/body/state/labels 等内容字段无法按结构读取）。优先在结构内深挖最大的
        // 长字符串字段（如 getIssue 的 issue.body、listIssues 的 issues[i].body），把该字段
        // 截断为「头 + 省略提示 + 尾」并保留其余字段与 JSON 结构；无法收敛时才回退到折叠。
        val reduced = reduceOverBudget(toolName, callId, element)
        if (reduced != null) return reduced

        val stored = boundText(toolName, callId, serialized)
        return stored.toJsonObject("content")
    }

    private fun addMetadata(target: MutableMap<String, JsonElement>, stored: StoredToolOutput) {
        target["output_truncated"] = JsonPrimitive(stored.truncated)
        target["output_total_chars"] = JsonPrimitive(stored.totalChars)
        stored.outputPath?.let { target["output_path"] = JsonPrimitive(it) }
        stored.storageError?.let { target["output_storage_error"] = JsonPrimitive(it) }
    }

    private fun StoredToolOutput.toJsonObject(primaryField: String): JsonObject {
        val data = mutableMapOf<String, JsonElement>(
            primaryField to JsonPrimitive(preview),
            "output_truncated" to JsonPrimitive(truncated),
            "output_total_chars" to JsonPrimitive(totalChars)
        )
        outputPath?.let { data["output_path"] = JsonPrimitive(it) }
        storageError?.let { data["output_storage_error"] = JsonPrimitive(it) }
        return JsonObject(data)
    }

    private fun buildPreview(text: String, outputPath: String?): String {
        val omitted = text.length - HEAD_CHARS - TAIL_CHARS
        val storageHint = if (outputPath != null) {
            appContext.getString(R.string.tool_output_saved_hint, outputPath)
        } else {
            appContext.getString(R.string.tool_output_unsaved_hint)
        }
        return buildString {
            append(text.take(HEAD_CHARS))
            append(appContext.getString(R.string.tool_output_omitted, omitted, storageHint))
            append(text.takeLast(TAIL_CHARS))
        }
    }

    /** 结构内某个待截断字符串字段的定位：路径（对象键 / 数组下标）与全文。 */
    private data class DeepTextField(val path: List<Any>, val text: String)

    /**
     * 整体超限时的结构化降级：反复找出结构内最大、键名属于 [LARGE_TEXT_FIELDS] 的字符串字段，
     * 把它截断为「头 + 省略提示 + 尾」并保留其余字段与 JSON 结构，直到整体回到 [MAX_INLINE_CHARS] 内。
     * 找不到可截断字段（如巨大数组但每项都很小）或轮数耗尽时返回 null，由调用方回退为折叠。
     */
    private fun reduceOverBudget(toolName: String, callId: String, element: JsonElement): JsonElement? {
        var current = element
        var primary: StoredToolOutput? = null
        var passes = 0
        while (passes < MAX_TRUNCATE_PASSES) {
            val serialized = json.encodeToString(current)
            val over = serialized.length - MAX_INLINE_CHARS
            if (over <= 0) return current
            val target = findDeepTextField(current) ?: return current.takeIf { primary != null }
            if (target.text.length <= MIN_TRUNCATE_FIELD_CHARS) return current.takeIf { primary != null }
            val targetLen = (target.text.length - over).coerceAtLeast(MIN_TRUNCATE_FIELD_CHARS)
            val stored = truncateToBudget(toolName, callId, target.text, targetLen, persist = primary == null)
            current = replaceAt(current, target.path, JsonPrimitive(stored.preview))
            if (primary == null) {
                primary = stored
                val root = current as? JsonObject
                if (root != null) {
                    val updated = root.toMutableMap()
                    addMetadata(updated, stored)
                    current = JsonObject(updated)
                }
            }
            passes++
        }
        return current.takeIf { primary != null }
    }

    /** 深度优先找出结构内最大、键名属于 [LARGE_TEXT_FIELDS] 的字符串字段。 */
    private fun findDeepTextField(element: JsonElement, path: List<Any> = emptyList()): DeepTextField? {
        var best: DeepTextField? = null
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val candidate = if (value is JsonPrimitive && value.isString && key in LARGE_TEXT_FIELDS) {
                    DeepTextField(path + key, value.content)
                } else {
                    findDeepTextField(value, path + key)
                }
                if (candidate != null && (best == null || candidate.text.length > best.text.length)) best = candidate
            }
            is JsonArray -> element.forEachIndexed { index, value ->
                val candidate = findDeepTextField(value, path + index)
                if (candidate != null && (best == null || candidate.text.length > best.text.length)) best = candidate
            }
            else -> {}
        }
        return best
    }

    /** 按路径（对象键 / 数组下标）不可变地把 [element] 中的字段替换为 [newValue]。 */
    private fun replaceAt(element: JsonElement, path: List<Any>, newValue: JsonElement): JsonElement {
        if (path.isEmpty()) return newValue
        return when (element) {
            is JsonObject -> {
                val key = path[0] as String
                val updated = element.toMutableMap()
                updated[key] = replaceAt(element[key]!!, path.drop(1), newValue)
                JsonObject(updated)
            }
            is JsonArray -> {
                val index = path[0] as Int
                val updated = element.toMutableList()
                updated[index] = replaceAt(element[index], path.drop(1), newValue)
                JsonArray(updated)
            }
            else -> element
        }
    }

    /**
     * 把 [text] 截断为约 [targetLen] 字符的「头 + 省略提示 + 尾」。[persist] 为 true 时全文落盘供读取
     * （仅对第一个被截断的最大字段落盘并暴露路径，后续字段只留行内预览，避免过多文件写入）。
     */
    private fun truncateToBudget(
        toolName: String,
        callId: String,
        text: String,
        targetLen: Int,
        persist: Boolean
    ): StoredToolOutput {
        val writeResult = if (persist) writeFullOutput(toolName, callId, text) else StoredPathResult()
        return StoredToolOutput(
            preview = buildBudgetPreview(text, targetLen, writeResult.outputPath),
            truncated = true,
            totalChars = text.length.toLong(),
            outputPath = writeResult.outputPath,
            storageError = writeResult.storageError
        )
    }

    /** 按预算构建「头 + 省略提示 + 尾」预览，头尾对半分。 */
    private fun buildBudgetPreview(text: String, targetLen: Int, outputPath: String?): String {
        val keep = targetLen.coerceIn(1, text.length)
        val head = keep / 2
        val tail = keep - head
        val omitted = text.length - keep
        val storageHint = if (outputPath != null) {
            appContext.getString(R.string.tool_output_saved_hint, outputPath)
        } else {
            appContext.getString(R.string.tool_output_unsaved_hint)
        }
        return buildString {
            append(text.take(head))
            append(appContext.getString(R.string.tool_output_omitted, omitted, storageHint))
            append(text.takeLast(tail))
        }
    }

    private fun writeFullOutput(toolName: String, callId: String, text: String): StoredPathResult {
        return try {
            val dir = "${pathHomeResolver.aiverseRoot()}/$OUTPUT_DIR"
            fileAccess.mkdirs(dir)
            val path = uniqueOutputPath(dir, toolName, callId)
            fileAccess.writeFile(path, text, overwrite = false)
            FileLogger.i(TAG, "工具输出已保存: $path (${text.length} chars)")
            StoredPathResult(outputPath = path)
        } catch (e: Exception) {
            FileLogger.w(TAG, "保存工具输出失败: ${e.message}", e)
            StoredPathResult(storageError = e.message ?: "保存工具输出失败")
        }
    }

    private fun uniqueOutputPath(dir: String, toolName: String, callId: String): String {
        val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
        val baseName = buildString {
            append(timestamp)
            append('-')
            append(sanitize(toolName).ifBlank { "tool" })
            val id = sanitize(callId).take(12)
            if (id.isNotBlank()) {
                append('-')
                append(id)
            }
        }

        val dirSlash = dir.trimEnd('/')
        var candidate = "$dirSlash/$baseName.log"
        var index = 1
        while (fileAccess.exists(candidate)) {
            candidate = "$dirSlash/$baseName-$index.log"
            index++
        }
        return candidate
    }

    private fun sanitize(value: String): String {
        return value.map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-'
        }.joinToString("").trim('-')
    }

    private data class StoredPathResult(
        val outputPath: String? = null,
        val storageError: String? = null
    )
}
