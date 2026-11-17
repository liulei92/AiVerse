package com.aiverse.feature.plugins.runtime

import com.aiverse.feature.plugins.domain.PluginHostPaths
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.terminal.domain.TerminalSessionProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 插件终端调用失败；[code] 是稳定的错误码，页面桥与进程 API 原样转交。 */
class PluginTerminalException(val code: String, override val message: String) : Exception(message)

/** 一次插件终端轮询的结果；[offset] 是下一次轮询应传入的偏移。 */
data class PluginTerminalPoll(
    val output: String,
    val offset: Long,
    val state: String,
    val exitCode: Int?,
    val truncated: Boolean
)

/**
 * 管理插件创建的共享终端标签。
 *
 * 标签归属创建它的插件，只能由该插件轮询、写入、列出、停止或关闭。输出通过
 * [TerminalSessionProvider.getTabOutput] 的整块快照拉取，插件按偏移轮询增量。
 *
 * TODO(适配): AiVerse 的 [TerminalSessionProvider] 不暴露插件级字节流与插件归属，且
 * [com.aiverse.feature.terminal.domain.TabInfo] 不带 exitCode；本适配采用「整块快照 + 偏移」的
 * 拉取式轮询，exitCode 暂为 null。终端缓冲回卷（滚出历史）时从头给全量并标记 truncated。
 * 插件创建的标签在宿主终端列表可见（宿主未按插件隔离标签），待宿主补插件级标签再收紧。
 */
@Singleton
class PluginTerminalRunner @Inject constructor(
    private val provider: TerminalSessionProvider,
    private val pluginManager: PluginManager
) {

    private class Run(
        val pluginId: String,
        val tabId: String,
        val title: String,
        val command: String
    )

    private val runs = ConcurrentHashMap<String, Run>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 新建插件命令标签；命令结束后标签保留为已结束，由插件调用 [close]。 */
    suspend fun start(pluginId: String, command: String, title: String?): String {
        requireActive(pluginId)
        if (command.isBlank()) throw PluginTerminalException("PARAM_INVALID", "empty command")
        if (command.length > PluginHostPaths.MAX_COMMAND_CHARS) {
            throw PluginTerminalException("PARAM_INVALID", "command too long")
        }
        val tabId = withContext(Dispatchers.Main) {
            provider.startBackgroundCommand(command, title)
        }
        runs[tabId] = Run(pluginId, tabId, title ?: tabId, command)
        return tabId
    }

    /** 从 [offset] 读取新增输出；缓冲回卷时从头给全量并标记 truncated。 */
    suspend fun poll(pluginId: String, tabId: String, offset: Long): PluginTerminalPoll {
        val run = owned(pluginId, tabId)
        val snapshot = withContext(Dispatchers.Main) { provider.getTabOutput(tabId) }.orEmpty()
        val from = offset.coerceAtLeast(0)
        val output: String
        val next: Long
        val truncated: Boolean
        if (from > snapshot.length) {
            // 缓冲已回卷（历史滚出），从头给全量并标记截断。
            output = snapshot
            next = snapshot.length.toLong()
            truncated = true
        } else {
            output = snapshot.substring(from.toInt())
            next = snapshot.length.toLong()
            truncated = false
        }
        return PluginTerminalPoll(
            output = output,
            offset = next,
            state = stateOf(run),
            exitCode = null,
            truncated = truncated
        )
    }

    /** 发送 Ctrl-C；超时仍未退出则关闭标签。返回是否已发出停止。 */
    suspend fun stop(pluginId: String, tabId: String): Boolean {
        val run = owned(pluginId, tabId)
        withContext(Dispatchers.Main) { provider.writeBytesToTab(tabId, 0x03) }
        val stopped = withTimeoutOrNull(STOP_TIMEOUT_MS) {
            while (true) {
                val running = withContext(Dispatchers.Main) { tabRunning(tabId) }
                if (!running) break
                delay(100)
            }
            true
        } ?: false
        if (!stopped) {
            withContext(Dispatchers.Main) { provider.closeTab(tabId) }
        }
        return true
    }

    /** 向自己的标签发送输入；[appendNewline] 为 true 时由终端补回车。 */
    suspend fun write(pluginId: String, tabId: String, text: String, appendNewline: Boolean = true): Boolean {
        owned(pluginId, tabId)
        if (text.isEmpty()) throw PluginTerminalException("PARAM_INVALID", "empty input")
        if (text.length > MAX_INPUT_CHARS) throw PluginTerminalException("PARAM_INVALID", "input too long")
        return withContext(Dispatchers.Main) { provider.sendInput(tabId, text, appendNewline) }
    }

    /** 列出当前插件自己创建的标签及其运行状态。 */
    suspend fun list(pluginId: String): JsonElement {
        val mine = runs.values.filter { it.pluginId == pluginId }.sortedBy { it.tabId }
        val entries = mine.map { run ->
            buildJsonObject {
                put("tabId", run.tabId)
                put("title", run.title)
                put("command", run.command)
                put("state", stateOf(run))
            }
        }
        return JsonArray(entries)
    }

    /** 关闭标签并释放输出收集；标签已结束也可以调用。 */
    suspend fun close(pluginId: String, tabId: String): Boolean {
        owned(pluginId, tabId)
        runs.remove(tabId)
        return withContext(Dispatchers.Main) { provider.closeTab(tabId) }
    }

    /** 插件停用或卸载时关闭它创建的全部标签。 */
    fun closePlugin(pluginId: String) {
        runs.values
            .filter { it.pluginId == pluginId }
            .map { it.tabId }
            .forEach { tabId ->
                runs.remove(tabId)
                scope.launch {
                    withContext(Dispatchers.Main) { provider.closeTab(tabId) }
                }
            }
    }

    private fun owned(pluginId: String, tabId: String): Run {
        val run = runs[tabId]
            ?: throw PluginTerminalException("TAB_NOT_FOUND", "unknown terminal tab: $tabId")
        if (run.pluginId != pluginId) {
            throw PluginTerminalException("TAB_FORBIDDEN", "terminal tab belongs to another plugin")
        }
        return run
    }

    private suspend fun tabRunning(tabId: String): Boolean {
        val status = provider.listTabs().firstOrNull { it.id == tabId }
        return status?.running == true
    }

    private fun stateOf(run: Run): String = if (tabRunningSync(run.tabId)) "running" else "finished"

    private fun tabRunningSync(tabId: String): Boolean =
        runCatching { provider.listTabs().firstOrNull { it.id == tabId }?.running == true }
            .getOrDefault(false)

    private suspend fun requireActive(pluginId: String) {
        pluginManager.ensureLoaded()
        val active = pluginManager.plugins.value.any { it.id == pluginId && it.isActive }
        if (!active) throw PluginTerminalException("PLUGIN_INACTIVE", "plugin is not active: $pluginId")
    }

    private companion object {
        /** 单次写入的字符上限。 */
        const val MAX_INPUT_CHARS = 4000
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
