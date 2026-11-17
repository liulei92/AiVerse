package com.aiverse.feature.plugins.presentation

import com.aiverse.feature.agent.domain.container.RemoteSshConnection
// TODO(P2/P3): PluginFileWatchSource 由 AiCode 的 feature.plugin.domain 提供，
// AiVerse 的新插件宿主（com.aiverse.feature.plugins.domain）尚未落地该类型；
// P3d 页面环境接入时补齐 domain 层类型后即可直接编译。
import com.aiverse.feature.plugins.domain.PluginFileWatchSource
import com.aiverse.feature.plugins.domain.PluginHostPaths
import com.aiverse.feature.plugins.domain.PluginWatchTarget
import com.aiverse.feature.settings.data.repository.ExecutionMode
import com.aiverse.feature.settings.data.repository.ExecutionModeHolder
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import com.aiverse.feature.workspace.domain.PathHomeResolver
import com.aiverse.feature.workspace.domain.WorkspacePathMapper
import com.aiverse.feature.workspace.domain.repository.RemoteRepository
import com.aiverse.feature.workspace.domain.model.RemoteConnection
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 插件页面宿主环境：当前执行环境（本地 PRoot 容器 / 远程 SSH）下的路径空间、
 * 文件监听与页面环境身份。
 *
 * TODO(P2/P3): 依赖的 [PluginFileWatchSource] 由 AiVerse 新插件域补齐（AiCode 里它位于
 * feature.plugin.domain）；P3d 页面桥接入前，本类只作为页面环境的类型骨架存在。
 */
@Singleton
class PluginPageEnvironment @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val modeHolder: ExecutionModeHolder,
    private val connection: RemoteSshConnection,
    private val remoteRepository: RemoteRepository,
    private val pathMapper: WorkspacePathMapper,
    private val homeResolver: PathHomeResolver,
    private val watchSource: PluginFileWatchSource
) {
    /** 当前远程连接的轻量身份；AiVerse 用多挂载模型，取列表中的当前连接。 */
    private data class RemoteIdentity(val host: String, val port: Int, val username: String)

    private val _remoteIdentity = MutableStateFlow<RemoteIdentity?>(null)

    val changes: Flow<String> = combine(
        workspaceRepository.current,
        modeHolder.mode,
        connection.connectionState,
        remoteRepository.getConnections()
    ) { _, _, _, connections ->
        val remote = if (modeHolder.currentMode() == ExecutionMode.REMOTE_SSH) {
            connections.firstOrNull()
        } else {
            null
        }
        _remoteIdentity.value = remote?.let { RemoteIdentity(it.host, it.port, it.username) }
        contextKey()
    }.distinctUntilChanged()

    fun contextKey(): String {
        val remote = _remoteIdentity.value.takeIf { modeHolder.currentMode() == ExecutionMode.REMOTE_SSH }
        val identity = listOf(
            modeHolder.currentMode().name,
            workspaceRepository.currentPath(),
            remote?.host.orEmpty(),
            remote?.port?.toString().orEmpty(),
            remote?.username.orEmpty()
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun workspaceName(): String = workspaceRepository.currentPath()
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { "workspace" }

    /**
     * 当前工作区在插件可见路径空间里的根：远程直接用远端绝对路径，本地用容器内 `$HOME/workspace`。
     * 本地 `currentPath()` 是宿主路径，不能直接给插件。
     */
    fun workspaceRoot(): String =
        if (modeHolder.currentMode() == ExecutionMode.REMOTE_SSH) {
            workspaceRepository.currentPath()
        } else {
            homeResolver.home().trimEnd('/') + "/workspace"
        }

    fun watch(paths: PluginHostPaths, path: String, recursive: Boolean): Flow<JsonObject> {
        val key = contextKey()
        val target = paths.watchTarget(
            path = path,
            localFile = { containerPath ->
                if (modeHolder.currentMode() == ExecutionMode.LOCAL_PROOT) {
                    pathMapper.resolveFile(containerPath)
                } else {
                    null
                }
            },
            recursive = recursive
        )
        val root = when (target) {
            is PluginWatchTarget.Local -> target.file.absolutePath
            is PluginWatchTarget.Remote -> target.path
        }.trimEnd('/')
        val logicalRoot = path.trimEnd('/')
        return watchSource.watch(target, recursive).map { batch ->
            val changes = batch.changes.mapNotNull { change ->
                val actual = change.hostPath.trimEnd('/')
                val relative = when {
                    actual == root -> ""
                    actual.startsWith("$root/") -> actual.removePrefix("$root/")
                    else -> return@mapNotNull null
                }
                buildJsonObject {
                    put("path", if (relative.isEmpty()) logicalRoot else "$logicalRoot/$relative")
                    put("kind", change.kind.name.lowercase())
                }
            }
            buildJsonObject {
                put("contextKey", key)
                put("changes", JsonArray(changes))
                put("truncated", batch.truncated)
            }
        }
    }
}
