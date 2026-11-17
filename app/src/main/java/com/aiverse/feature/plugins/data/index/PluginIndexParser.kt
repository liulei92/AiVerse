package com.aiverse.feature.plugins.data.index

import com.aiverse.feature.plugins.domain.LocalizedText
import com.aiverse.feature.plugins.domain.PluginManifestParser
import com.aiverse.feature.plugins.domain.SemVer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 索引静态校验；不依赖 Android，便于单元测试。
 *
 * 同时支持两种格式：
 * - **新格式**（对象 + `schema_version`）：严格校验；
 * - **旧格式**（顶层数组，元素含 `creator` / `package` / `permission` 等字段）：
 *   宽松映射，缺少的 `license` / `repository` 等新字段降级为默认值，不报错。
 */
internal object PluginIndexParser {

    private val AUTHOR = Regex("^[a-z][a-z0-9-]*$")
    private val LANGUAGE_TAG = Regex("^[a-z]{2,3}(-[a-z0-9]{2,8})*$")

    fun parse(bytes: ByteArray): PluginIndexDocument {
        if (bytes.size > MAX_INDEX_BYTES) throw PluginIndexException("index.json 超过 2 MiB")
        val root = try {
            PluginIndexJson.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw PluginIndexException("index.json 解析失败", e)
        }
        return when (root) {
            is JsonArray -> parseLegacy(root)
            is JsonObject -> parseSchema(root)
            else -> throw PluginIndexException("index.json 顶层必须是对象或数组")
        }
    }

    /** 新格式：顶层对象，必须带 `schema_version`，逐字段严格校验。 */
    private fun parseSchema(root: JsonObject): PluginIndexDocument {
        if ("schema_version" !in root) throw PluginIndexException("index.json 缺少 schema_version")
        val index = try {
            PluginIndexJson.decodeFromJsonElement<PluginIndexDocument>(root)
        } catch (e: Exception) {
            throw PluginIndexException("index.json 解析失败", e)
        }
        if (index.schemaVersion != 1) throw PluginIndexException("不支持的 schema_version=${index.schemaVersion}")
        if (index.plugins.size > MAX_PLUGINS) throw PluginIndexException("插件数量超过 $MAX_PLUGINS")
        val ids = HashSet<String>()
        index.plugins.forEach { plugin ->
            if (!PluginManifestParser.isValidPluginId(plugin.id)) {
                throw PluginIndexException("非法插件 ID: ${plugin.id}")
            }
            if (!ids.add(plugin.id)) throw PluginIndexException("插件 ID 重复: ${plugin.id}")
            validateLocalized(plugin.id, "name", plugin.name, MAX_NAME, allowNewlines = false)
            validateLocalized(plugin.id, "description", plugin.description, MAX_DESCRIPTION, allowNewlines = true)
            if (!AUTHOR.matches(plugin.author)) throw PluginIndexException("${plugin.id} 的 author 非法")
            val repository = runCatching { java.net.URI(plugin.repository) }.getOrNull()
            if (repository?.scheme != "https" || repository.host != "github.com") {
                throw PluginIndexException("${plugin.id} 的 repository 不是 GitHub 地址")
            }
            plugin.version?.let { version ->
                if (version.startsWith("v") || SemVer.parse(version) == null) {
                    throw PluginIndexException("${plugin.id} 的 version 非法")
                }
            }
            plugin.minAppVersion?.let {
                if (SemVer.parse(it) == null) throw PluginIndexException("${plugin.id} 的 min_app_version 非法")
            }
            if (plugin.tags.any { it !in PluginTags.ALL }) {
                throw PluginIndexException("${plugin.id} 的 tags 含未知值")
            }
            if (plugin.tags.size > 4 || plugin.tags.distinct().size != plugin.tags.size) {
                throw PluginIndexException("${plugin.id} 的 tags 数量或重复项非法")
            }
            plugin.logo?.let { requireHttps(it, "${plugin.id} logo") }
            plugin.homepage?.let { requireHttps(it, "${plugin.id} homepage") }
        }
        return index
    }

    /**
     * 旧格式：顶层数组，元素形如
     * `{"name":"...","version":"...","creator":"...","lastUpdate":...,"permission":["clipboard"],"package":"com.x.y"}`。
     *
     * 映射规则：`id=package`、`name=LocalizedText(name)`（中英文取同一值）、`author=creator`、
     * `version=version`、`repository=""`、`license=null`、`tags=permission`、`lastUpdate` 原样保留。
     * 缺 `creator` 或 `package` 的条目视为无效，静默丢弃；新字段缺失一律降级为空，不报错。
     */
    private fun parseLegacy(root: JsonArray): PluginIndexDocument {
        if (root.size > MAX_PLUGINS) throw PluginIndexException("插件数量超过 $MAX_PLUGINS")
        val ids = HashSet<String>()
        val plugins = root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj["package"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?: return@mapNotNull null
            val author = (obj["creator"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?: return@mapNotNull null
            if (id.isEmpty() || author.isEmpty()) return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().ifEmpty { id }
            val version = (obj["version"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
            val tags = (obj["permission"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } }
                ?.distinct()
                ?.take(4)
                .orEmpty()
            if (!ids.add(id)) return@mapNotNull null
            PluginIndexPlugin(
                id = id,
                name = LocalizedText.single(name),
                description = LocalizedText.single(""),
                author = author,
                repository = "",
                license = null,
                tags = tags,
                version = version,
                lastUpdate = obj.longOrNull("lastUpdate")
            )
        }
        return PluginIndexDocument(schemaVersion = 1, plugins = plugins)
    }

    private fun JsonObject.longOrNull(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun validateLocalized(
        pluginId: String,
        field: String,
        text: LocalizedText,
        maxLength: Int,
        allowNewlines: Boolean
    ) {
        if (text.values.size > MAX_LOCALES) {
            throw PluginIndexException("$pluginId 的 $field 语言数量超过 $MAX_LOCALES")
        }
        text.values.forEach { (tag, value) ->
            if (tag != LocalizedText.DEFAULT_KEY && !LANGUAGE_TAG.matches(tag)) {
                throw PluginIndexException("$pluginId 的 $field 含非法语言标签: $tag")
            }
            if (value.isEmpty() || value.length > maxLength || hasControlChars(value, allowNewlines)) {
                throw PluginIndexException("$pluginId 的 $field 文本非法: $tag")
            }
        }
    }

    private fun hasControlChars(text: String, allowNewlines: Boolean) =
        text.any { it.isISOControl() && !(allowNewlines && it == '\n') }

    private fun requireHttps(url: String, field: String) {
        if (!url.startsWith("https://")) throw PluginIndexException("$field 必须是 https 地址")
    }

    private const val MAX_INDEX_BYTES = 2 * 1024 * 1024
    private const val MAX_PLUGINS = 5000
    private const val MAX_NAME = 120
    private const val MAX_DESCRIPTION = 2000
    private const val MAX_LOCALES = 16
}
