package com.aiverse.feature.phonecontrol.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手机控制的实时状态（供顶部灵动岛展示 AI 思考过程：当前操作 + 最近步骤）。
 *
 * 由 [PhoneControlTool] 每次执行读屏/点击/打开应用时上报；[OverlayBarService] 订阅
 * [state] 渲染灵动岛。仅手机控制操作期间显示悬浮球。
 */
@Singleton
class PhoneControlStatus @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class State(
        /** 是否有操作正在进行。 */
        val active: Boolean = false,
        /** 当前操作描述（如「正在读屏」），无则 null。 */
        val currentAction: String? = null,
        /** 最近几步操作（倒序，最新在前）。 */
        val recentSteps: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val maxSteps = 8

    /** 开始执行一个操作：记录步骤、更新当前操作并显示顶部灵动岛。 */
    fun begin(action: String) {
        val prev = _state.value
        _state.value = prev.copy(
            active = true,
            currentAction = action,
            recentSteps = (listOf(action) + prev.recentSteps).take(maxSteps)
        )
        OverlayBarService.show(context)
    }

    /** 当前操作完成（保留最近步骤，供点开查看；悬浮球不自动隐藏）。 */
    fun end() {
        _state.value = _state.value.copy(active = false, currentAction = null)
    }

    /** 清空并隐藏灵动岛。 */
    fun clear() {
        _state.value = State()
        OverlayBarService.hide(context)
    }
}
