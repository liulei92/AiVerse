package com.aiverse.feature.plugins.data.index

import android.content.Context
import com.aiverse.feature.plugins.domain.SemVer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

data class PluginIndexSnapshot(
    val index: PluginIndexDocument,
    val stale: Boolean,
    val fetchedAt: Long
)

data class PluginReleaseAsset(
    val version: String,
    val name: String,
    val url: String,
    val size: Long
)

data class DownloadedPluginPackage(
    val file: File,
    val asset: PluginReleaseAsset
)

class PluginIndexException(message: String, cause: Throwable? = null) : IOException(message, cause)

class NoMatchingReleaseException(message: String) : IOException(message)

/**
 * 插件索引客户端：拉取 / 缓存 / 校验索引，并解析 Release 资产、下载插件包。
 *
 * 网络客户端复用 `AgentModule` 提供的共享 [OkHttpClient]（覆盖对话、SSE 等），
 * 此处再克隆一份独立超时与关闭缓存，避免互相影响。
 *
 * 注意：AiCode 原版在此注入「下载镜像源设置」，但镜像设置位于 `feature/settings`，
 * 违反「跨 feature 只能依赖 core/」的依赖铁律；本移植版默认直连（与原版镜像为空时的默认行为一致）。
 * 若后续需要镜像加速，应先把镜像读取能力下沉到 `core/`，再在此接入。
 */
@Singleton
class PluginIndexClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    client: OkHttpClient
) {
    private val http = client.newBuilder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .cache(null)
        .build()

    private val cacheRoot = File(context.filesDir, "plugin-index-cache")

    private val hostAppVersion: SemVer? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName?.let(SemVer::parse)
    }.getOrNull()

    suspend fun fetchIndex(source: PluginIndexSource, forceRefresh: Boolean = false): PluginIndexSnapshot =
        withContext(Dispatchers.IO) {
            val cached = cacheFile(source)
            val url = source.indexUrl
            val bytes = try {
                getBytes(url, MAX_INDEX_BYTES, noCache = forceRefresh)
            } catch (e: IOException) {
                if (forceRefresh) throw e
                val fallback = readCache(cached) ?: throw e
                return@withContext PluginIndexSnapshot(fallback, stale = true, fetchedAt = cached.lastModified())
            }
            val index = PluginIndexParser.parse(bytes)
            writeCache(cached, bytes)
            PluginIndexSnapshot(index, stale = false, fetchedAt = System.currentTimeMillis())
        }

    fun isCompatible(plugin: PluginIndexPlugin): Boolean {
        val minimum = plugin.minAppVersion?.let { SemVer.parse(it) ?: return false } ?: return true
        val current = hostAppVersion ?: return true
        // 开发 / RC 构建按同一 major.minor.patch 的正式版看待；
        // 最低版本本身是预发布时，仍按完整版本比较。
        val comparable = if (minimum.preRelease.isEmpty()) {
            SemVer(current.major, current.minor, current.patch)
        } else {
            current
        }
        return comparable >= minimum
    }

    /**
     * 解析插件对应的插件包。
     * 插件包统一放在商店仓库 main 分支的 `packages/` 目录，文件名 `{id}_{version}.zip`。
     * version 为空时取 `packages/` 中 `{id}_*.zip` 的最高版本；有 version 时精确查找 `{id}_{version}.zip`。
     * 不再回退 GitHub Release：`packages/` 目录不存在或没有目标文件时直接报错。
     */
    suspend fun resolveRelease(plugin: PluginIndexPlugin): PluginReleaseAsset = withContext(Dispatchers.IO) {
        val (owner, repo) = parseGitHubRepo(plugin.repository)
            ?: throw NoMatchingReleaseException("不是 GitHub 仓库: ${plugin.repository}")
        val expectedName = plugin.version?.let { assetName(plugin.id, it) }
        val listing = apiGet<List<GitHubContent>>(
            "$GITHUB_API/repos/$owner/$repo/contents/$PACKAGES_DIR?ref=$DEFAULT_BRANCH"
        )
        val candidates = listing.filter { it.type == "file" && it.name.endsWith(".zip") }
        val matched = when {
            expectedName != null -> candidates.firstOrNull { it.name == expectedName }
            else -> candidates
                .mapNotNull { content ->
                    val version = assetVersion(plugin.id, content.name) ?: return@mapNotNull null
                    content to version
                }
                .maxByOrNull { SemVer.parse(it.second) ?: SemVer(0, 0, 0) }
                ?.let { it.first }
        } ?: throw NoMatchingReleaseException("未找到插件包: ${expectedName ?: "${plugin.id}_*.zip"}")
        val version = expectedName?.let { plugin.version!! } ?: assetVersion(plugin.id, matched.name)!!
        PluginReleaseAsset(
            version = version,
            name = matched.name,
            url = rawUrl(owner, repo, matched.path),
            size = matched.size
        )
    }

    suspend fun download(asset: PluginReleaseAsset): DownloadedPluginPackage = withContext(Dispatchers.IO) {
        if (asset.size <= 0 || asset.size > MAX_ARCHIVE_BYTES) {
            throw PluginIndexException("资产大小非法: ${asset.size}")
        }
        val bytes = getBytes(asset.url, asset.size, noCache = true, expectedSize = asset.size)
        val file = File(context.cacheDir, "plugin-${sha256(bytes).take(16)}.zip")
        file.writeBytes(bytes)
        DownloadedPluginPackage(file, asset)
    }

    private fun assetName(id: String, version: String): String = "${id}_$version.zip"

    private fun assetVersion(id: String, name: String): String? {
        val prefix = "${id}_"
        if (!name.startsWith(prefix) || !name.endsWith(".zip")) return null
        val version = name.removePrefix(prefix).removeSuffix(".zip")
        return version.takeIf { !it.startsWith("v") && SemVer.parse(it) != null }
    }

    private fun parseGitHubRepo(repository: String): Pair<String, String>? {
        val uri = runCatching { URI(repository) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "github.com") return null
        val segments = uri.path.trim('/').split('/')
        if (segments.size != 2 || segments.any { it.isBlank() }) return null
        return segments[0] to segments[1].removeSuffix(".git")
    }

    private inline fun <reified T> apiGet(url: String): T {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "aiverse-plugin-index")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw NoMatchingReleaseException("GitHub 请求失败: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw NoMatchingReleaseException("GitHub 响应为空")
            return try {
                PluginIndexJson.decodeFromString<T>(body)
            } catch (e: Exception) {
                throw NoMatchingReleaseException("GitHub 响应解析失败")
            }
        }
    }

    private fun getBytes(
        url: String,
        maxBytes: Long,
        noCache: Boolean,
        expectedSize: Long? = null
    ): ByteArray {
        val builder = Request.Builder().url(url).header("User-Agent", "aiverse-plugin-index")
        if (noCache) builder.cacheControl(CacheControl.Builder().noCache().build())
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
            val body = response.body ?: throw IOException("空响应: $url")
            val declared = body.contentLength()
            if (declared > maxBytes) throw IOException("响应超过大小限制: $url")
            if (expectedSize != null && declared > 0 && declared != expectedSize) {
                throw IOException("响应大小 $declared 与声明 $expectedSize 不一致")
            }
            val stream = body.byteStream()
            val output = java.io.ByteArrayOutputStream(if (declared in 1..maxBytes) declared.toInt() else 8192)
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("响应超过大小限制: $url")
                output.write(buffer, 0, read)
            }
            if (expectedSize != null && total != expectedSize) {
                throw IOException("响应大小 $total 与声明 $expectedSize 不一致")
            }
            return output.toByteArray()
        }
    }

    private fun cacheFile(source: PluginIndexSource): File = File(File(cacheRoot, source.id), "index.json")

    private fun writeCache(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        temp.renameTo(file)
    }

    private fun readCache(file: File): PluginIndexDocument? {
        if (!file.isFile) return null
        return runCatching { PluginIndexParser.parse(file.readBytes()) }.getOrNull()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Serializable
    private data class GitHubContent(
        val name: String,
        val path: String,
        val type: String,
        val size: Long = 0
    )

    private fun rawUrl(owner: String, repo: String, path: String): String =
        "$GITHUB_RAW/$owner/$repo/$DEFAULT_BRANCH/$path"

    private companion object {
        const val GITHUB_API = "https://api.github.com"
        const val GITHUB_RAW = "https://raw.githubusercontent.com"
        const val DEFAULT_BRANCH = "main"
        const val PACKAGES_DIR = "packages"
        const val MAX_INDEX_BYTES = 2L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024
    }
}
