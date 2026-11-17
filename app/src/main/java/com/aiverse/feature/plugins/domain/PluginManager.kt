package com.aiverse.feature.plugins.domain

import android.content.Context
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.skill.Skill
import com.aiverse.feature.agent.domain.subagent.AgentDefinition
import com.aiverse.feature.agent.domain.tool.ToolRegistry
import com.aiverse.feature.plugins.data.ExtractedPackage
import com.aiverse.feature.plugins.data.PackageReadResult
import com.aiverse.feature.plugins.data.PluginInstallTransaction
import com.aiverse.feature.plugins.data.PluginPackageReader
import com.aiverse.feature.plugins.data.PluginRecord
import com.aiverse.feature.plugins.data.PluginRegistryStore
import com.aiverse.feature.plugins.domain.newmodel.ContentLoadResult
import com.aiverse.feature.plugins.domain.newmodel.PluginContent
import com.aiverse.feature.plugins.domain.newmodel.PluginContentLoader
import com.aiverse.feature.plugins.runtime.PluginDependencyPreparer
import com.aiverse.feature.plugins.runtime.PluginDependencyResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 已安装插件的运行视图：宿主记录 + 从安装目录读出的清单与贡献。
 *
 * [manifest] 为可空：安装目录或清单文件丢失、摘要不一致或解析失败时置 null（此时 [isActive] 为 false，
 * 插件的全部贡献都不生效）。这一可空语义与 P2a 的 [com.aiverse.feature.plugins.runtime.PluginToolManager]
 * 一致，避免插件宿主把损坏的插件当成可用。
 */
data class InstalledPlugin(
    val manifest: PluginManifest?,
    val installDir: File,
    val enabled: Boolean,
    val content: PluginContent?,
    val source: PluginInstallOrigin,
    val record: PluginRecord
) {
    val id: String get() = manifest?.id ?: record.id

    /** 启用、清单可读且贡献已成功加载时，它声明的全部贡献才生效。 */
    val isActive: Boolean get() = enabled && manifest != null && content != null

    /**
     * 记忆「假插件」标记：它不是真实安装的插件，只是把内置记忆能力以「我的插件」形式
     * 露在已安装列表里，启停走 [com.aiverse.feature.settings.data.repository.MemoryToggleRepository]。
     * 用 [com.aiverse.feature.plugins.domain.PluginInstallOrigin.KIND_MEMORY] 标记，避免与真实插件混淆。
     */
    val isMemoryToggle: Boolean get() = source.kind == PluginInstallOrigin.KIND_MEMORY
}

/** 记忆「假插件」的插件 ID；启停写 DataStore（[com.aiverse.feature.settings.data.repository.MemoryToggleRepository]），不进注册表。 */
const val MEMORY_TOGGLE_PLUGIN_ID = "aiverse.memory"

/**
 * 构造记忆「假插件」：把内置记忆能力以「我的插件」形式露在已安装列表头部。
 *
 * - 不落盘、不进注册表、不参与安装/卸载；启停只写 [com.aiverse.feature.settings.data.repository.MemoryToggleRepository]。
 * - `manifest` / `content` 为 null：UI 据此跳过详情、图标、版本/作者标签与左滑卸载，只保留启停开关。
 * - [InstalledPlugin.record] 是满足注册表必填字段的占位（内存对象，不持久化），保证 `record.id` / `record.version` 有值。
 */
fun memoryTogglePlugin(enabled: Boolean, pluginsRoot: File): InstalledPlugin {
    val record = PluginRecord(
        id = MEMORY_TOGGLE_PLUGIN_ID,
        version = "1.0.0",
        enabled = enabled,
        installedAt = 0L,
        origin = PluginInstallOrigin.KIND_MEMORY,
        archiveSha256 = "",
        manifestSha256 = ""
    )
    return InstalledPlugin(
        manifest = null,
        installDir = pluginsRoot,
        enabled = enabled,
        content = null,
        source = PluginInstallOrigin(kind = PluginInstallOrigin.KIND_MEMORY),
        record = record
    )
}

/** 已通过校验、等待使用者确认安装的暂存包。 */
data class PluginPreview(
    val token: String,
    val manifest: PluginManifest,
    val content: PluginContent,
    /** 已安装的同 ID 插件版本；非空表示这次导入会替换它。 */
    val installedVersion: String?,
    /** 被替换安装的来源信息；新装时为 null，供界面判断来源是否变化。 */
    val installedSourceId: String? = null,
    val installedRepository: String? = null,
    val installedOfficial: Boolean? = null
)

sealed interface PluginPreviewResult {
    data class Ready(val preview: PluginPreview) : PluginPreviewResult
    data class Failed(val errors: List<PluginError>) : PluginPreviewResult
}

sealed interface PluginInstallResult {
    data class Installed(val pluginId: String) : PluginInstallResult
    data class Failed(val error: PluginError) : PluginInstallResult
}

/**
 * 插件的安装、启停与贡献聚合（新版插件宿主，替代旧 QuickJS 脚本插件系统）。
 *
 * 插件包与数据保存在宿主私有目录 `filesDir/aiverse/plugins`（由 PRoot 绑定到容器内 `~/.aiverse/plugins`），
 * 本地容器里的命令可以直接访问插件文件与将来的依赖。安装记录留在宿主私有目录，不随包进入容器，
 * 避免容器内进程改写启停状态。
 *
 * 依赖的 P2 运行时服务（[com.aiverse.feature.plugins.runtime.PluginToolManager] /
 * [com.aiverse.feature.plugins.runtime.PluginRuntimeManager] /
 * [com.aiverse.feature.plugins.runtime.PluginHookDispatcher]）落地前，AI 工具同步、hook 分发等
 * 由对应服务独立负责（P2a 的 PluginToolManager 监听 [plugins] 流），本类只维护安装/启停/贡献聚合。
 */
@Singleton
class PluginManager internal constructor(
    private val rootDir: File,
    private val hostApi: SemVer,
    private val clock: () -> Long,
    private val toolRegistry: ToolRegistry? = null,
    private val registryFile: File = File(rootDir, "registry.json"),
    private val legacyRootDir: File? = null,
    private val dependencyPreparer: PluginDependencyPreparer
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        toolRegistry: ToolRegistry,
        dependencyPreparer: PluginDependencyPreparer
    ) : this(
        File(File(context.filesDir, "aiverse"), PLUGINS_DIR),
        PluginApi.VERSION,
        System::currentTimeMillis,
        toolRegistry,
        File(context.filesDir, REGISTRY_FILE),
        null,
        dependencyPreparer
    )

    private val installedDir = File(rootDir, "installed")
    private val stagingDir = File(rootDir, "staging")
    private val dataRoot = File(rootDir, "data")
    private val store = PluginRegistryStore(registryFile)

    /** 包读取与贡献加载器；占位字段，供宿主聚合使用。 */
    private val loaders = PluginLoaders()

    // 插件 AI 工具同步由 AIEditorApp 注入的 PluginToolManager 负责（onCreate 调 start(appScope)），
    // 其内部监听本类 plugins 流把生效插件工具注册进 ToolRegistry；本类不再自持 toolManager，
    // 保留该占位字段仅为兼容既有调用方（避免 Hilt 循环依赖：PluginToolManager 依赖本类监听 plugins 流）。
    val toolManager: com.aiverse.feature.plugins.runtime.PluginToolManager? = null

    // 插件运行时进程管理（[com.aiverse.feature.plugins.runtime.PluginRuntimeManager]）与
    // hook 分发（[com.aiverse.feature.plugins.runtime.PluginHookDispatcher]）由对应服务独立负责并注入使用方；
    // 本类不再持有二者（保留 null 占位仅为兼容既有调用方，当前无消费方）。
    val runtimeManager: com.aiverse.feature.plugins.runtime.PluginRuntimeManager? = null
    val hookDispatcher: com.aiverse.feature.plugins.runtime.PluginHookDispatcher? = null

    private val mutex = Mutex()
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    private val _plugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val plugins: StateFlow<List<InstalledPlugin>> = _plugins.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** 安装记录是否已从磁盘读完。读完之前 [plugins] 为空不代表没有插件，界面据此避免闪一下空状态。 */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val revisionCounter = AtomicLong(0)

    /** 插件集合每变化一次递增，供提示词缓存等据此失效。 */
    val revision: Long get() = revisionCounter.get()

    private class StagedPackage(
        val pkg: ExtractedPackage,
        val manifest: PluginManifest,
        val content: PluginContent
    )

    private class LoadedRegistry(
        val records: List<PluginRecord>,
        val failed: Boolean
    )

    private val staged = ConcurrentHashMap<String, StagedPackage>()

    /** 插件清单目录（已安装插件包所在根）。 */
    val manifestDir: File get() = installedDir

    /** 首次调用时从磁盘读取安装记录；会做文件 IO，调用方不要放在主线程。 */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            migrateLegacyRoot()
            // 进程重启后不可能存在进行中的安装，暂存目录里的都是残留
            cleanup { deleteTree(stagingDir) }
            val registry = loadRecords()
            val recovered = !registry.failed && recoverInterruptedInstall(registry.records)
            publish(registry.records.map { loadInstalled(it) })
            // registry 或事务恢复失败时保留目录，避免把待恢复的备份当成孤儿。
            if (recovered) sweepOrphans(registry.records.map { it.id }.toSet())
            loaded = true
            _isLoaded.value = true
        }
    }

    /** 重新读取安装记录与清单；只更新界面快照，不重启已启用的插件运行时。 */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            val loaded = loadRecords()
            val recovered = !loaded.failed && recoverInterruptedInstall(loaded.records)
            publish(loaded.records.map { loadInstalled(it) })
            if (recovered) sweepOrphans(loaded.records.map { it.id }.toSet())
        }
    }

    /** 当前生效的插件技能：插件启用即可用。 */
    fun activeSkills(): List<Skill> {
        ensureLoaded()
        return _plugins.value
            .filter { it.isActive }
            .flatMap { plugin -> plugin.content?.skills?.map { it.skill } ?: emptyList() }
    }

    /** 全部已读出的插件技能，不论插件是否启用。供设置页全量展示使用。 */
    fun allSkills(): List<Skill> {
        ensureLoaded()
        return _plugins.value.flatMap { plugin -> plugin.content?.skills?.map { it.skill } ?: emptyList() }
    }

    /** 当前生效的插件子代理定义，条件同 [activeSkills]。 */
    fun activeAgents(): List<AgentDefinition> {
        ensureLoaded()
        return _plugins.value
            .filter { it.isActive }
            .flatMap { plugin -> plugin.content?.agents?.map { it.definition } ?: emptyList() }
    }

    /**
     * 全部已读出的插件子代理定义，不论插件是否启用。已经派出去的子会话要靠它还原自己的提示词与工具集，
     * 停用只该拦住新的派发。
     */
    fun allAgents(): List<AgentDefinition> {
        ensureLoaded()
        return _plugins.value.flatMap { plugin -> plugin.content?.agents?.map { it.definition } ?: emptyList() }
    }

    /** 当前生效的插件工作区工具：插件启用即可用。 */
    fun activeWorkspaceTools(): List<com.aiverse.feature.plugins.domain.newmodel.PluginWorkspaceTool> {
        ensureLoaded()
        return _plugins.value
            .filter { it.isActive }
            .flatMap { it.content?.workspaceTools ?: emptyList() }
    }

    /** 全部插件声明的工作区工具，供全部工具库展示添加。 */
    fun allWorkspaceTools(): List<com.aiverse.feature.plugins.domain.newmodel.PluginWorkspaceTool> {
        ensureLoaded()
        return _plugins.value.flatMap { it.content?.workspaceTools ?: emptyList() }
    }

    /** 当前生效的插件 AI 工具：插件启用即可用。 */
    fun activeTools(): List<com.aiverse.feature.plugins.domain.newmodel.PluginTool> {
        ensureLoaded()
        return _plugins.value
            .filter { it.isActive }
            .flatMap { it.content?.tools ?: emptyList() }
    }

    /** 全部插件声明的 AI 工具，不论插件是否启用；供设置页与调试展示。 */
    fun allTools(): List<com.aiverse.feature.plugins.domain.newmodel.PluginTool> {
        ensureLoaded()
        return _plugins.value.flatMap { it.content?.tools ?: emptyList() }
    }

    /** 当前生效的插件生命周期 hook：插件启用即可用。 */
    fun activeHooks(): List<com.aiverse.feature.plugins.domain.newmodel.PluginHook> {
        ensureLoaded()
        return _plugins.value
            .filter { it.isActive }
            .flatMap { it.content?.hooks ?: emptyList() }
    }

    /** 全部插件声明的生命周期 hook，不论插件是否启用或已授权；供设置页与调试展示。 */
    fun allHooks(): List<com.aiverse.feature.plugins.domain.newmodel.PluginHook> {
        ensureLoaded()
        return _plugins.value.flatMap { it.content?.hooks ?: emptyList() }
    }

    /** 当前生效的插件 MCP server：插件启用即可用。 */
    fun activeMcpServers(): List<com.aiverse.feature.plugins.domain.newmodel.PluginMcpServer> {
        ensureLoaded()
        return _plugins.value
            .filter { it.isActive }
            .flatMap { it.content?.mcp ?: emptyList() }
    }

    /** 全部插件声明的 MCP server，不论插件是否启用；供设置页展示。 */
    fun allMcpServers(): List<com.aiverse.feature.plugins.domain.newmodel.PluginMcpServer> {
        ensureLoaded()
        return _plugins.value.flatMap { it.content?.mcp ?: emptyList() }
    }

    /** 插件专属数据目录，对应桥里的 `data://` 根。 */
    fun dataDirFor(pluginId: String): File = File(dataRoot, pluginId)

    /**
     * 开发者侧载目录（宿主 `filesDir/aiverse/plugin-dev`，对应容器内 `~/.aiverse/plugin-dev`）。
     * 宿主 [rootDir] 的父目录是 `filesDir/aiverse`，由 PRoot 绑定到容器 `~/.aiverse`，
     * 故该目录在本地容器里可见为 `~/.aiverse/plugin-dev`，与插件安装目录同根。
     */
    val devDir: File get() = File(rootDir.parentFile, "plugin-dev")

    /**
     * 准备插件依赖：在本地容器、插件安装目录执行。返回状态供 UI 展示；
     * 插件未安装或未声明运行时按失败处理。
     */
    suspend fun prepareDependencies(pluginId: String): PluginDependencyResult = withContext(Dispatchers.IO) {
        val runtime = getPlugin(pluginId)?.content?.runtime
            ?: return@withContext PluginDependencyResult.Failed("插件未安装或未声明运行时")
        dependencyPreparer.prepare(runtime)
    }

    /** 已安装插件的安装目录。 */
    fun pluginDir(id: String): File = File(installedDir, id)

    /** 已安装插件列表（同步读取，调用方应确保已 [ensureLoaded] 或处于 IO 线程）。 */
    fun installedPlugins(): List<InstalledPlugin> {
        ensureLoaded()
        return _plugins.value
    }

    /**
     * 收集插件目录下全部文件的（相对插件根路径, 字节）映射，用于备份导出/还原。
     * 目录不存在或读取失败返回空映射（调用方据此提示失败）。
     */
    fun collectPluginFiles(id: String): Map<String, ByteArray> {
        val root = pluginDir(id)
        if (!root.isDirectory) return emptyMap()
        return root.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val rel = file.relativeTo(root).path
                runCatching { rel to file.readBytes() }.getOrNull()
            }
            .toMap()
    }

    /** 读取并校验 [input] 中的插件包，通过后暂存等待 [install]。同一时刻只保留一个待确认的暂存包。 */
    suspend fun preview(input: InputStream): PluginPreviewResult = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                ensureLoaded()
                discardAll()
            }
        } catch (e: IOException) {
            FileLogger.w(TAG, "清理插件暂存包失败", e)
            return@withContext PluginPreviewResult.Failed(listOf(PluginError(PluginErrorCode.STORAGE_FAILED)))
        }
        val token = UUID.randomUUID().toString()
        val dir = File(stagingDir, token)
        when (val read = loaders.packageReader.read(input, dir)) {
            is PackageReadResult.Failed -> PluginPreviewResult.Failed(listOf(read.error))
            is PackageReadResult.Ok -> previewExtracted(token, read.pkg)
        }
    }

    private fun previewExtracted(token: String, pkg: ExtractedPackage): PluginPreviewResult {
        val manifest = when (val parsed = PluginManifestParser.parse(pkg.manifestText, hostApi)) {
            is ManifestParseResult.Failure -> return rejected(pkg, parsed.errors)
            is ManifestParseResult.Success -> parsed.manifest
        }
        val content = when (val loadedContent = PluginContentLoader.load(pkg.dir, manifest)) {
            is ContentLoadResult.Failed -> return rejected(pkg, loadedContent.errors)
            is ContentLoadResult.Ok -> loadedContent.content
        }
        staged[token] = StagedPackage(pkg, manifest, content)
        val existing = _plugins.value.firstOrNull { it.id == manifest.id }?.record
        return PluginPreviewResult.Ready(
            PluginPreview(
                token = token,
                manifest = manifest,
                content = content,
                installedVersion = existing?.version,
                installedSourceId = existing?.sourceId,
                installedRepository = existing?.repository,
                installedOfficial = existing?.official
            )
        )
    }

    private fun rejected(pkg: ExtractedPackage, errors: List<PluginError>): PluginPreviewResult {
        val cleaned = cleanup { deleteTree(pkg.dir) }
        return PluginPreviewResult.Failed(
            if (cleaned) errors else errors + PluginError(PluginErrorCode.STORAGE_FAILED)
        )
    }

    /** 放弃一个待确认的暂存包。 */
    suspend fun discard(token: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pending = staged[token] ?: return@withLock false
            if (!cleanup { deleteTree(pending.pkg.dir) }) return@withLock false
            staged.remove(token)
            true
        }
    }

    private fun discardAll() {
        staged.entries.forEach { (token, pending) ->
            deleteTree(pending.pkg.dir)
            staged.remove(token)
        }
    }

    /**
     * 安装暂存包。插件清单里的贡献随启用生效，安装不再逐项授权。
     * 同 ID 已安装时整体替换并沿用原启用状态，来源记录为本次安装的来源。
     */
    suspend fun install(
        token: String,
        origin: PluginInstallOrigin = PluginInstallOrigin()
    ): PluginInstallResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                val pending = staged[token]
                    ?: return@withLock PluginInstallResult.Failed(PluginError(PluginErrorCode.PREVIEW_EXPIRED))
                val manifest = pending.manifest
                try {
                    commitInstall(token, pending, origin)
                    PluginInstallResult.Installed(manifest.id)
                } catch (e: IOException) {
                    FileLogger.e(TAG, "安装插件失败: ${manifest.id}", e)
                    PluginInstallResult.Failed(PluginError(PluginErrorCode.STORAGE_FAILED, e.javaClass.simpleName))
                }
            }
        }

    private fun commitInstall(token: String, pending: StagedPackage, origin: PluginInstallOrigin) {
        val manifest = pending.manifest
        if (!recoverInterruptedInstall(_plugins.value.map { it.record })) {
            throw IOException("上次安装事务尚未恢复")
        }
        installedDir.mkdirs()
        val target = File(installedDir, manifest.id)
        val backup = File(installedDir, "${manifest.id}$REPLACED_SUFFIX")
        deleteTree(backup)

        val existing = _plugins.value.firstOrNull { it.id == manifest.id }
        if (existing == null) deleteTree(dataDirFor(manifest.id))
        val installRevision = UUID.randomUUID().toString()
        val record = PluginRecord(
            id = manifest.id,
            version = manifest.version.toString(),
            enabled = existing?.record?.enabled ?: true,
            installedAt = clock(),
            origin = origin.kind,
            archiveSha256 = pending.pkg.archiveSha256,
            manifestSha256 = pending.pkg.manifestSha256,
            sourceId = origin.sourceId,
            sourceName = origin.sourceName,
            indexUrl = origin.indexUrl,
            repository = origin.repository,
            official = origin.official,
            installRevision = installRevision
        )

        val transaction = PluginInstallTransaction(manifest.id, installRevision, entryExists(target))
        store.saveInstallTransaction(transaction)
        var movedOld = false
        var movedNew = false
        val others = _plugins.value.filter { it.id != manifest.id }
        val installed: InstalledPlugin
        try {
            if (transaction.hadPrevious) {
                moveDirectory(target, backup)
                movedOld = true
            }
            moveDirectory(pending.pkg.dir, target)
            movedNew = true
            installed = loadInstalled(record)
            store.save(others.map { it.record } + record)
        } catch (e: IOException) {
            // 只在 registry 提交前回滚；恢复失败时保留私有事务与备份供下次重试。
            try {
                if (movedNew) moveDirectory(target, pending.pkg.dir)
                if (movedOld) moveDirectory(backup, target)
                store.clearInstallTransaction()
            } catch (rollback: IOException) {
                e.addSuppressed(rollback)
            }
            throw e
        }
        staged.remove(token)
        publish(others + installed)
        cleanup {
            deleteTree(backup)
            store.clearInstallTransaction()
        }
    }

    /** 卸载：移除记录、删除安装目录与数据目录。 */
    suspend fun uninstall(id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            val current = _plugins.value
            if (current.none { it.id == id }) return@withLock false
            val next = current.filter { it.id != id }
            if (!cleanup { store.save(next.map { it.record }) }) return@withLock false
            publish(next)
            val installedDeleted = cleanup { deleteTree(File(installedDir, id)) }
            val dataDeleted = cleanup { deleteTree(dataDirFor(id)) }
            installedDeleted && dataDeleted
        }
    }

    /** 启用/停用插件（保持安装目录与贡献）。 */
    suspend fun setEnabled(id: String, enabled: Boolean): Boolean = updateRecord(id) { record, _ ->
        record.copy(enabled = enabled)
    }

    /** 启用/停用插件，[setEnabled] 的别名（任务要求的启停接口）。 */
    suspend fun setActive(id: String, active: Boolean): Boolean = setEnabled(id, active)

    /** 插件当前是否启用且清单/贡献可读。 */
    fun isActive(id: String): Boolean = getPlugin(id)?.isActive == true

    /** 返回指定已安装插件的运行视图；不存在返回 null。 */
    fun getPlugin(id: String): InstalledPlugin? {
        ensureLoaded()
        return _plugins.value.firstOrNull { it.id == id }
    }

    /** 重新加载单个插件的清单与贡献并发布快照；不存在返回 null。 */
    suspend fun loadPlugin(id: String): InstalledPlugin? = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            val current = _plugins.value.firstOrNull { it.id == id } ?: return@withLock null
            val reloaded = loadInstalled(current.record)
            publish(_plugins.value.map { if (it.id == id) reloaded else it })
            reloaded
        }
    }

    /**
     * 应用启动同步：确保安装记录已读取。AI 工具同步由 P2a 的 PluginToolManager 监听 [plugins] 流完成，
     * 本类只负责把插件集合暴露出去，不在启动时直接注册 plugin__ 工具。
     */
    fun syncEnabled() {
        ensureLoaded()
    }

    private suspend fun updateRecord(
        id: String,
        transform: (PluginRecord, InstalledPlugin) -> PluginRecord
    ): Boolean = mutateRecords { current ->
        val target = current.firstOrNull { it.id == id } ?: return@mutateRecords null
        val updated = transform(target.record, target)
        if (updated == target.record) return@mutateRecords current
        current.map { if (it.id == id) it.copy(record = updated) else it }.also { next ->
            store.save(next.map { it.record })
        }
    }

    /** 串行执行一次变更；[block] 返回新的插件列表，返回 null 表示目标不存在。IO 失败返回 false。 */
    private suspend fun mutateRecords(block: (List<InstalledPlugin>) -> List<InstalledPlugin>?): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                try {
                    val next = block(_plugins.value) ?: return@withLock false
                    if (next != _plugins.value) publish(next)
                    true
                } catch (e: IOException) {
                    FileLogger.e(TAG, "更新插件记录失败", e)
                    false
                }
            }
        }

    private fun publish(next: List<InstalledPlugin>) {
        _plugins.value = next.sortedBy { it.id }
        revisionCounter.incrementAndGet()
    }

    private fun loadInstalled(record: PluginRecord): InstalledPlugin {
        val dir = File(installedDir, record.id)
        val source = PluginInstallOrigin(
            kind = record.origin,
            sourceId = record.sourceId,
            sourceName = record.sourceName,
            indexUrl = record.indexUrl,
            repository = record.repository,
            official = record.official
        )
        fun broken(): InstalledPlugin =
            InstalledPlugin(manifest = null, installDir = dir, enabled = record.enabled, content = null, source = source, record = record)

        val manifestFile = File(dir, PluginPackageReader.MANIFEST_FILE)
        if (!manifestFile.isFile || manifestFile.length() > PluginManifestParser.MAX_MANIFEST_BYTES) return broken()

        val bytes = manifestFile.readBytes()
        if (PluginPackageReader.sha256Hex(bytes) != record.manifestSha256) return broken()

        val manifest = when (val parsed = PluginManifestParser.parse(String(bytes, Charsets.UTF_8), hostApi)) {
            is ManifestParseResult.Failure -> return broken()
            is ManifestParseResult.Success -> parsed.manifest
        }
        if (manifest.id != record.id || manifest.version.toString() != record.version) return broken()

        val content = when (val loaded = PluginContentLoader.load(dir, manifest)) {
            is ContentLoadResult.Failed -> null
            is ContentLoadResult.Ok -> loaded.content
        }
        return InstalledPlugin(
            manifest = manifest,
            installDir = dir,
            enabled = record.enabled,
            content = content,
            source = source,
            record = record
        )
    }

    /** 记录尚未搬到宿主私有目录时（迁移未完成），退回到包目录里的副本读取。 */
    private fun loadRecords(): LoadedRegistry {
        if (registryFile.isFile) {
            val records = store.load()
            val failed = store.loadFailed || hasCorruptRegistryBackup() || hasLegacyCorruptBackup()
            return LoadedRegistry(records, failed)
        }
        val inside = File(rootDir, "registry.json")
        if (!inside.isFile) {
            return LoadedRegistry(emptyList(), failed = hasCorruptRegistryBackup() || hasLegacyCorruptBackup())
        }
        val legacyStore = PluginRegistryStore(inside)
        return LoadedRegistry(
            legacyStore.load(),
            legacyStore.loadFailed || hasCorruptRegistryBackup() || hasLegacyCorruptBackup()
        )
    }

    /** 上一次读取失败会留下备份；此时 registry 文件已不存在，但安装目录不能当孤儿清理。 */
    private fun hasCorruptRegistryBackup(): Boolean =
        registryFile.parentFile?.listFiles().orEmpty().any {
            it.name.startsWith("${registryFile.name}.corrupt-")
        }

    private fun hasLegacyCorruptBackup(): Boolean {
        val backupPrefix = "registry.json.corrupt-"
        return rootDir.listFiles().orEmpty().any { it.name.startsWith(backupPrefix) }
    }

    /**
     * 安装/升级在“旧目录已移走、新目录尚未就位或 registry 尚未落盘”之间崩溃时，
     * 按 registry 仍指向的版本恢复，避免下一次 sweep 把备份当孤儿删掉。
     */
    private fun recoverInterruptedInstall(records: List<PluginRecord>): Boolean = cleanup {
        val transaction = store.loadInstallTransaction()
        if (transaction != null) {
            val target = File(installedDir, transaction.id)
            val backup = File(installedDir, "${transaction.id}$REPLACED_SUFFIX")
            val committed = records.any {
                it.id == transaction.id && it.installRevision == transaction.revision
            }
            when {
                committed -> deleteTree(backup)
                transaction.hadPrevious && entryExists(backup) -> {
                    deleteTree(target)
                    moveDirectory(backup, target)
                }
                !transaction.hadPrevious -> deleteTree(target)
                !entryExists(target) -> throw IOException("安装事务的旧目录已丢失: ${transaction.id}")
            }
            store.clearInstallTransaction()
        }
        // 兼容尚未写事务记录的旧安装：清单相同时优先保留 registry 对应的备份。
        records.forEach { record ->
            val target = File(installedDir, record.id)
            val backup = File(installedDir, "${record.id}$REPLACED_SUFFIX")
            if (!Files.isDirectory(backup.toPath(), NOFOLLOW_LINKS)) return@forEach
            when {
                !entryExists(target) -> moveDirectory(backup, target)
                manifestMatches(backup, record) -> {
                    deleteTree(target)
                    moveDirectory(backup, target)
                }
                manifestMatches(target, record) -> deleteTree(backup)
                else -> throw IOException("无法确定中断安装的旧目录: ${record.id}")
            }
        }
    }

    private fun manifestMatches(dir: File, record: PluginRecord): Boolean {
        val manifestFile = File(dir, PluginPackageReader.MANIFEST_FILE)
        if (!manifestFile.isFile || manifestFile.length() > PluginManifestParser.MAX_MANIFEST_BYTES) {
            return false
        }
        val bytes = runCatching { manifestFile.readBytes() }.getOrNull() ?: return false
        if (PluginPackageReader.sha256Hex(bytes) != record.manifestSha256) return false
        val parsed = PluginManifestParser.parse(String(bytes, Charsets.UTF_8), hostApi)
        val manifest = (parsed as? ManifestParseResult.Success)?.manifest ?: return false
        return manifest.id == record.id && manifest.version.toString() == record.version
    }

    /** 没有安装记录的目录不具备任何授权，直接清掉（多半是安装中途崩溃留下的）。 */
    private fun sweepOrphans(knownIds: Set<String>) {
        cleanup {
            if (Files.isSymbolicLink(installedDir.toPath())) {
                deleteTree(installedDir)
            } else if (entryExists(installedDir)) {
                Files.newDirectoryStream(installedDir.toPath()).use { entries ->
                    entries.filter { it.fileName.toString() !in knownIds }.forEach { deleteTree(it.toFile()) }
                }
            }
        }
    }

    private fun entryExists(file: File): Boolean = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        true
    } catch (_: NoSuchFileException) {
        false
    }

    private fun checkCleanupParents(file: File) {
        val root = rootDir.toPath().toAbsolutePath().normalize()
        val path = file.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(root)) throw IOException("清理路径不在插件目录内")
        var parent = path.parent
        while (parent != null && parent.startsWith(root)) {
            if (Files.isSymbolicLink(parent)) throw IOException("清理路径的父目录是符号链接: $parent")
            parent = parent.parent
        }
    }

    private fun deleteTree(file: File) {
        checkCleanupParents(file)
        if (!entryExists(file)) return
        // walkFileTree 默认不跟随链接，目录链接（包括悬空链接）只作为文件删除。
        Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private inline fun cleanup(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: IOException) {
        FileLogger.w(TAG, "插件存储操作失败", e)
        false
    }

    private fun moveDirectory(from: File, to: File) {
        checkCleanupParents(from)
        checkCleanupParents(to)
        if (!from.renameTo(to)) throw IOException("无法移动 ${from.name} 到 ${to.name}")
    }

    /**
     * 旧版本把插件放在 `filesDir/aiverse/plugins`，安装记录也在包目录内；本版记录迁到宿主私有目录。
     * 当前宿主私有目录与包目录同源（都在 `filesDir/aiverse` 下），迁移仅在目录被移动后生效；
     * 失败时保留旧目录，下次启动重试。
     */
    private fun migrateLegacyRoot() {
        val legacy = legacyRootDir ?: return
        rootDir.parentFile?.mkdirs()
        if (!rootDir.exists() && legacy.isDirectory) {
            if (!legacy.renameTo(rootDir)) {
                FileLogger.w(TAG, "插件目录迁移失败，继续使用旧目录: ${legacy.absolutePath}")
                return
            }
        }
        val insideRegistry = File(rootDir, "registry.json")
        val source = when {
            insideRegistry.isFile -> insideRegistry
            File(legacy, "registry.json").isFile -> File(legacy, "registry.json")
            else -> null
        }
        if (!registryFile.exists() && source != null) {
            registryFile.parentFile?.mkdirs()
            if (!source.renameTo(registryFile)) {
                runCatching { source.copyTo(registryFile, overwrite = true) }
                    .onFailure { FileLogger.w(TAG, "插件安装记录迁移失败: ${source.absolutePath}", it) }
            }
        }
        if (registryFile.exists() && insideRegistry.isFile) insideRegistry.delete()
        if (legacy.isDirectory && legacy.listFiles().isNullOrEmpty()) legacy.delete()
    }

    private class PluginLoaders {
        val packageReader = PluginPackageReader()
    }

    companion object {
        private const val TAG = "PluginManager"
        private const val REPLACED_SUFFIX = ".replaced"
        private const val PLUGINS_DIR = "plugins"
        private const val REGISTRY_FILE = "plugin-registry.json"
    }
}
