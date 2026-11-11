package com.aiverse.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import com.aiverse.core.theme.FileNameDisplay
import com.aiverse.core.theme.FileTreeDisplayPrefs
import com.aiverse.core.theme.FileTreeIndentDensity
import com.aiverse.core.theme.UIDensity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(
    name = "appearance_settings",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 持久化外观文字大小设置（「外观设置」二级页）：
 * - 聊天字体大小：聊天气泡 / 消息正文 / Markdown 文本字号（sp）
 * - 输入框字体大小：底部聊天输入框字号（sp）
 * - 界面字体大小：全局 UI 缩放系数（1.0 为不缩放）
 *
 * 三档均持久化，App 重启后自动恢复；范围在 companion 中约束。
 */
@Singleton
class AppearanceSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val chatFontSizeSpFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[CHAT_FONT_SIZE_KEY] ?: DEFAULT_CHAT_FONT_SIZE_SP).coerceIn(MIN_CHAT_FONT_SIZE_SP, MAX_CHAT_FONT_SIZE_SP)
    }

    val inputFontSizeSpFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[INPUT_FONT_SIZE_KEY] ?: DEFAULT_INPUT_FONT_SIZE_SP).coerceIn(MIN_INPUT_FONT_SIZE_SP, MAX_INPUT_FONT_SIZE_SP)
    }

    val uiFontScaleFlow: Flow<Float> = context.appearanceDataStore.data.map { prefs ->
        (prefs[UI_FONT_SCALE_KEY] ?: DEFAULT_UI_FONT_SCALE).coerceIn(MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE)
    }

    /** 界面密度档（紧凑/标准/舒适），作用于间距、圆角、组件高度（dp）缩放。默认标准档。 */
    val uiDensityFlow: Flow<UIDensity> = context.appearanceDataStore.data.map { prefs ->
        UIDensity.fromId(prefs[UI_DENSITY_KEY])
    }

    /** 侧边栏文件树显示偏好：文件名显示 / 显示隐藏文件 / 缩进密度 / 弱化着色，默认与历史行为一致。 */
    val fileTreeDisplayPrefsFlow: Flow<FileTreeDisplayPrefs> = context.appearanceDataStore.data.map { prefs ->
        FileTreeDisplayPrefs(
            fileNameDisplay = FileNameDisplay.fromId(prefs[FILE_NAME_DISPLAY_KEY]),
            showHiddenFiles = prefs[SHOW_HIDDEN_FILES_KEY] ?: FileTreeDisplayPrefs.Default.showHiddenFiles,
            indentDensity = FileTreeIndentDensity.fromId(prefs[FILE_INDENT_DENSITY_KEY]),
            mutedColoring = prefs[FILE_MUTED_COLORING_KEY] ?: FileTreeDisplayPrefs.Default.mutedColoring
        )
    }

    suspend fun setChatFontSizeSp(sizeSp: Int) {
        context.appearanceDataStore.edit { it[CHAT_FONT_SIZE_KEY] = sizeSp.coerceIn(MIN_CHAT_FONT_SIZE_SP, MAX_CHAT_FONT_SIZE_SP) }
    }

    suspend fun setInputFontSizeSp(sizeSp: Int) {
        context.appearanceDataStore.edit { it[INPUT_FONT_SIZE_KEY] = sizeSp.coerceIn(MIN_INPUT_FONT_SIZE_SP, MAX_INPUT_FONT_SIZE_SP) }
    }

    suspend fun setUiFontScale(scale: Float) {
        context.appearanceDataStore.edit { it[UI_FONT_SCALE_KEY] = scale.coerceIn(MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE) }
    }

    /** 设置界面密度档。 */
    suspend fun setUiDensity(density: UIDensity) {
        context.appearanceDataStore.edit { it[UI_DENSITY_KEY] = density.id }
    }

    /** 设置文件树文件名显示方式。 */
    suspend fun setFileNameDisplay(display: FileNameDisplay) {
        context.appearanceDataStore.edit { it[FILE_NAME_DISPLAY_KEY] = display.id }
    }

    /** 设置是否显示隐藏文件。 */
    suspend fun setShowHiddenFiles(show: Boolean) {
        context.appearanceDataStore.edit { it[SHOW_HIDDEN_FILES_KEY] = show }
    }

    /** 设置文件树缩进密度。 */
    suspend fun setFileIndentDensity(density: FileTreeIndentDensity) {
        context.appearanceDataStore.edit { it[FILE_INDENT_DENSITY_KEY] = density.id }
    }

    /** 设置是否弱化着色 .gitignore / dotfile。 */
    suspend fun setFileMutedColoring(muted: Boolean) {
        context.appearanceDataStore.edit { it[FILE_MUTED_COLORING_KEY] = muted }
    }

    /** 备份快照：当前聊天字号（sp）。 */
    suspend fun chatFontSizeSnapshot(): Int = chatFontSizeSpFlow.first()

    /** 备份快照：当前输入框字号（sp）。 */
    suspend fun inputFontSizeSnapshot(): Int = inputFontSizeSpFlow.first()

    /** 备份快照：当前界面缩放系数。 */
    suspend fun uiFontScaleSnapshot(): Float = uiFontScaleFlow.first()

    /** 备份快照：当前界面密度档 id（未设置时回退标准档）。 */
    suspend fun uiDensitySnapshot(): String = uiDensityFlow.first().id

    /** 从备份还原三档字号与密度档；任一传 null 时清除该键回退默认。 */
    suspend fun restoreFontSizes(chatSp: Int?, inputSp: Int?, uiScale: Float?, uiDensity: String? = null) {
        context.appearanceDataStore.edit {
            if (chatSp == null) it.remove(CHAT_FONT_SIZE_KEY) else it[CHAT_FONT_SIZE_KEY] = chatSp.coerceIn(MIN_CHAT_FONT_SIZE_SP, MAX_CHAT_FONT_SIZE_SP)
            if (inputSp == null) it.remove(INPUT_FONT_SIZE_KEY) else it[INPUT_FONT_SIZE_KEY] = inputSp.coerceIn(MIN_INPUT_FONT_SIZE_SP, MAX_INPUT_FONT_SIZE_SP)
            if (uiScale == null) it.remove(UI_FONT_SCALE_KEY) else it[UI_FONT_SCALE_KEY] = uiScale.coerceIn(MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE)
            if (uiDensity == null) it.remove(UI_DENSITY_KEY) else it[UI_DENSITY_KEY] = uiDensity
        }
    }

    /** 清空文件树显示偏好，回退默认（与历史行为一致）。 */
    suspend fun restoreFileTreeDisplay() {
        context.appearanceDataStore.edit {
            it.remove(FILE_NAME_DISPLAY_KEY)
            it.remove(SHOW_HIDDEN_FILES_KEY)
            it.remove(FILE_INDENT_DENSITY_KEY)
            it.remove(FILE_MUTED_COLORING_KEY)
        }
    }

    companion object {
        private val CHAT_FONT_SIZE_KEY = intPreferencesKey("chat_font_size_sp")
        private val INPUT_FONT_SIZE_KEY = intPreferencesKey("input_font_size_sp")
        private val UI_FONT_SCALE_KEY = floatPreferencesKey("ui_font_scale")
        private val UI_DENSITY_KEY = stringPreferencesKey("ui_density")
        private val FILE_NAME_DISPLAY_KEY = stringPreferencesKey("file_name_display")
        private val SHOW_HIDDEN_FILES_KEY = booleanPreferencesKey("file_show_hidden")
        private val FILE_INDENT_DENSITY_KEY = stringPreferencesKey("file_indent_density")
        private val FILE_MUTED_COLORING_KEY = booleanPreferencesKey("file_muted_coloring")

        const val MIN_CHAT_FONT_SIZE_SP = 12
        const val MAX_CHAT_FONT_SIZE_SP = 22
        const val DEFAULT_CHAT_FONT_SIZE_SP = 16

        const val MIN_INPUT_FONT_SIZE_SP = 12
        const val MAX_INPUT_FONT_SIZE_SP = 22
        const val DEFAULT_INPUT_FONT_SIZE_SP = 16

        const val MIN_UI_FONT_SCALE = 0.85f
        const val MAX_UI_FONT_SCALE = 1.30f
        const val DEFAULT_UI_FONT_SCALE = 1.0f
    }
}
