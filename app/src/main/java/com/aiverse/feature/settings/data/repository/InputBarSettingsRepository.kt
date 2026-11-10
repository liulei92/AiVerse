package com.aiverse.feature.settings.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 输入框快捷栏的可开关项类型（用于显示顺序排序）。
 *
 * 只包含可在设置页开关、且参与快捷栏排序的项；余额等宿主级组件（顶栏）不在此列。
 */
enum class InputBarQuickAction {
    /** 切换模型。 */
    MODEL,
    /** 思考强度。 */
    THINKING,
    /** 联网搜索开关。 */
    WEB_SEARCH,
    /** 切换文件夹（工作区）。 */
    FOLDER,
    /** MCP 服务器入口。 */
    MCP,
    /** 清空当前对话。 */
    CLEAR
}

/**
 * 输入框快捷栏默认显示顺序（从左到右）。
 *
 * 按使用频率排列：高频靠前（切换模型、思考强度、联网搜索、文件夹），低频靠后（MCP、清空）。
 * 设置页快捷栏列表与输入框快捷栏共用此常量，保证两处顺序一致。
 */
val DEFAULT_QUICK_ACTION_ORDER: List<InputBarQuickAction> = listOf(
    InputBarQuickAction.MODEL,
    InputBarQuickAction.THINKING,
    InputBarQuickAction.WEB_SEARCH,
    InputBarQuickAction.FOLDER,
    InputBarQuickAction.MCP,
    InputBarQuickAction.CLEAR
)

/**
 * 输入框快捷栏的可开关项快照（加号/模式必留，不在此）。
 */
data class InputBarPrefs(
    val showMcp: Boolean = true,
    val showThinking: Boolean = true,
    val showFolder: Boolean = false,
    val showModel: Boolean = true,
    val showClear: Boolean = false,
    val showWebSearch: Boolean = false
)

/**
 * 输入框快捷栏的按钮显示偏好：哪些快捷功能在输入框上方显示。
 *
 * 加号（上传文件/图片）与模式切换是必留项，不在此存储；这里只存可开关项。
 * 默认全部开启（保持原布局，新增功能默认可见），用户可在设置里关闭不用的。
 *
 * 单一真源：[inputBarPrefs] StateFlow；设置页与对话页订阅同一流，改动即时同步。
 */
@Singleton
class InputBarSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("input_bar_prefs", Context.MODE_PRIVATE)

    private val _inputBarPrefs = MutableStateFlow(loadFromPrefs())

    /** 全部可开关项快照（实时）。 */
    val inputBarPrefs: StateFlow<InputBarPrefs> = _inputBarPrefs.asStateFlow()

    private fun loadFromPrefs(): InputBarPrefs = InputBarPrefs(
        showMcp = prefs.getBoolean(KEY_SHOW_MCP, true),
        showThinking = prefs.getBoolean(KEY_SHOW_THINKING, true),
        showFolder = prefs.getBoolean(KEY_SHOW_FOLDER, false),
        showModel = prefs.getBoolean(KEY_SHOW_MODEL, true),
        showClear = prefs.getBoolean(KEY_SHOW_CLEAR, false),
        showWebSearch = prefs.getBoolean(KEY_SHOW_WEB_SEARCH, false)
    )

    /** 当前快照。 */
    fun load(): InputBarPrefs = _inputBarPrefs.value

    /** 写回全部可开关项快照（持久化 + 即时发布）。 */
    fun updateInputBarPrefs(prefs: InputBarPrefs) {
        persistPrefs(prefs)
        _inputBarPrefs.value = prefs
    }

    private fun persistPrefs(prefs: InputBarPrefs) {
        this.prefs.edit()
            .putBoolean(KEY_SHOW_MCP, prefs.showMcp)
            .putBoolean(KEY_SHOW_THINKING, prefs.showThinking)
            .putBoolean(KEY_SHOW_FOLDER, prefs.showFolder)
            .putBoolean(KEY_SHOW_MODEL, prefs.showModel)
            .putBoolean(KEY_SHOW_CLEAR, prefs.showClear)
            .putBoolean(KEY_SHOW_WEB_SEARCH, prefs.showWebSearch)
            .apply()
    }

    /** MCP 快捷按钮。 */
    var showMcp: Boolean
        get() = _inputBarPrefs.value.showMcp
        set(value) { updateInputBarPrefs(_inputBarPrefs.value.copy(showMcp = value)) }

    /** 思考强度快捷按钮。 */
    var showThinking: Boolean
        get() = _inputBarPrefs.value.showThinking
        set(value) { updateInputBarPrefs(_inputBarPrefs.value.copy(showThinking = value)) }

    /** 切换文件夹（工作区）快捷按钮。 */
    var showFolder: Boolean
        get() = _inputBarPrefs.value.showFolder
        set(value) { updateInputBarPrefs(_inputBarPrefs.value.copy(showFolder = value)) }

    /** 切换模型快捷按钮。 */
    var showModel: Boolean
        get() = _inputBarPrefs.value.showModel
        set(value) { updateInputBarPrefs(_inputBarPrefs.value.copy(showModel = value)) }

    /** 清空当前对话快捷按钮。 */
    var showClear: Boolean
        get() = _inputBarPrefs.value.showClear
        set(value) { updateInputBarPrefs(_inputBarPrefs.value.copy(showClear = value)) }

    /** 联网搜索开关快捷按钮（websearch 工具启用）。 */
    var showWebSearch: Boolean
        get() = _inputBarPrefs.value.showWebSearch
        set(value) { updateInputBarPrefs(_inputBarPrefs.value.copy(showWebSearch = value)) }

    /** 联网搜索工具是否启用（默认开启，保持原有 websearch 行为）。 */
    var webSearchEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_WEB_SEARCH_ENABLED, value).apply() }

    private companion object {
        const val KEY_SHOW_MCP = "show_mcp"
        const val KEY_SHOW_THINKING = "show_thinking"
        const val KEY_SHOW_FOLDER = "show_folder"
        const val KEY_SHOW_MODEL = "show_model"
        const val KEY_SHOW_CLEAR = "show_clear"
        const val KEY_SHOW_WEB_SEARCH = "show_web_search"
        const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
    }
}
