package com.aiverse.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.memoryToggleDataStore by preferencesDataStore(
    name = "memory_toggle",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 记忆「假插件」开关：控制设置页记忆入口显示与 AI 是否可调用记忆工具。
 *
 * 只控制 UI 与 AI 访问，不删除任何已存储的记忆文件（`~/.aiverse/memory` 与项目
 * `.aiverse/memory` 里的内容在关闭时原样保留，重新开启后即可恢复访问）。
 * 默认开启（true），DataStore 用法与 [GeneralSettingsRepository] 一致。
 */
@Singleton
class MemoryToggleRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** 记忆开关流；未设置时回退到 true（默认开启）。 */
    val enabledFlow: Flow<Boolean> =
        context.memoryToggleDataStore.data.map { it[ENABLED_KEY] ?: true }

    /** 冷读一次当前记忆开关状态。 */
    suspend fun isEnabled(): Boolean = enabledFlow.first()

    /** 设置记忆开关。 */
    suspend fun setEnabled(enabled: Boolean) {
        context.memoryToggleDataStore.edit { it[ENABLED_KEY] = enabled }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("enabled")
    }
}
