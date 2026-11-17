package com.aiverse.feature.plugins.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件「待升级」提示的持有者：App 启动时静默检查一次，把结果暴露给界面。
 *
 * 进程级单例：启动检查在任意页面都能触发一次，结果对整个 App 可见，
 * 与 APP 自身的更新检查（UpdateCheckService / SettingsViewModel）互不干扰。
 */
@Singleton
class PluginUpdateNotifier @Inject constructor(
    private val checker: PluginUpdateChecker
) {

    private val _pending = MutableStateFlow<List<PendingPluginUpdate>>(emptyList())
    val pending: StateFlow<List<PendingPluginUpdate>> = _pending.asStateFlow()

    /** 当前待升级插件数，供角标/入口展示。 */
    val count: Int get() = _pending.value.size

    /**
     * 后台静默刷新待升级列表。任何异常都吞掉，保持空值或上一次结果不变，
     * 绝不让启动路径因检查失败而中断。
     */
    suspend fun refresh() {
        try {
            _pending.value = checker.check()
        } catch (_: Exception) {
            // 静默：检查失败不打扰使用者，保持当前值。
        }
    }

    /** 本次启动不再提示，清空待升级列表。 */
    fun dismiss() {
        _pending.value = emptyList()
    }
}
