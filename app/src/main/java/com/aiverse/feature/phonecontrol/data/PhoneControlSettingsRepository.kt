package com.aiverse.feature.phonecontrol.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.phoneControlDataStore by preferencesDataStore(
    name = "phone_control_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 「手机控制」设置持久化（DataStore）。
 *
 * 保存总开关、应用名单（不碰/每步确认）、确认方式、解除权限开关、悬浮条配置与最近操作日志。
 * 应用名单用逗号分隔字符串存储（包名不含逗号，安全）；最近操作日志用单元分隔符连接，
 * 保留最近 [MAX_RECENT_ACTIONS] 条。
 */
@Singleton
class PhoneControlSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val RECENT_SEPARATOR = "\u001F"
        const val MAX_RECENT_ACTIONS = 20

        val ENABLED = booleanPreferencesKey("enabled")
        val DENY_PACKAGES = stringPreferencesKey("deny_packages")
        val CONFIRM_PACKAGES = stringPreferencesKey("confirm_packages")
        val CONFIRM_MODE = stringPreferencesKey("confirm_mode")
        val UNLOCK_SYSTEM = booleanPreferencesKey("unlock_system_pages")
        val UNLOCK_SECRETS = booleanPreferencesKey("unlock_secret_fields")
        val UNLOCK_OWN_APP = booleanPreferencesKey("unlock_own_app")
        val OVERLAY_IDLE_OPACITY = floatPreferencesKey("overlay_idle_opacity")
        val OVERLAY_AUTO_COLLAPSE = booleanPreferencesKey("overlay_auto_collapse")
        val OVERLAY_RETURN_AFTER_DONE = booleanPreferencesKey("overlay_return_after_done")
        val RECENT_ACTIONS = stringPreferencesKey("recent_actions")
    }

    /** 当前设置快照。 */
    val settingsFlow: Flow<PhoneControlSettings> = context.phoneControlDataStore.data.map { prefs ->
        PhoneControlSettings(
            enabled = prefs[ENABLED] ?: false,
            denyPackages = prefs[DENY_PACKAGES]?.let(::decodePackages)
                ?: PhoneControlSettings.DEFAULT_DENY_PACKAGES,
            confirmPackages = prefs[CONFIRM_PACKAGES]?.let(::decodePackages)
                ?: PhoneControlSettings.DEFAULT_CONFIRM_PACKAGES,
            confirmMode = prefs[CONFIRM_MODE]?.let { runCatching { ConfirmMode.valueOf(it) }.getOrNull() }
                ?: ConfirmMode.ASK,
            unlocks = PhoneUnlocks(
                systemPages = prefs[UNLOCK_SYSTEM] ?: false,
                secretFields = prefs[UNLOCK_SECRETS] ?: false,
                ownApp = prefs[UNLOCK_OWN_APP] ?: false
            ),
            overlay = OverlayPrefs(
                idleOpacity = (prefs[OVERLAY_IDLE_OPACITY] ?: OverlayPrefs.DEFAULT_IDLE_OPACITY)
                    .coerceIn(OverlayPrefs.MIN_OPACITY, OverlayPrefs.MAX_OPACITY),
                autoCollapse = prefs[OVERLAY_AUTO_COLLAPSE] ?: true,
                returnAfterDone = prefs[OVERLAY_RETURN_AFTER_DONE] ?: false
            )
        )
    }

    /** 最近操作日志（最新在前）。 */
    val recentActionsFlow: Flow<List<String>> = context.phoneControlDataStore.data.map { prefs ->
        prefs[RECENT_ACTIONS]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.phoneControlDataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setRule(packageName: String, rule: AppRule) {
        context.phoneControlDataStore.edit { prefs ->
            val deny = decodePackages(prefs[DENY_PACKAGES] ?: encodePackages(PhoneControlSettings.DEFAULT_DENY_PACKAGES))
                .toMutableSet()
            val confirm = decodePackages(prefs[CONFIRM_PACKAGES] ?: encodePackages(PhoneControlSettings.DEFAULT_CONFIRM_PACKAGES))
                .toMutableSet()
            deny.remove(packageName)
            confirm.remove(packageName)
            when (rule) {
                AppRule.DENY -> deny.add(packageName)
                AppRule.CONFIRM_EACH_STEP -> confirm.add(packageName)
                AppRule.NORMAL -> Unit
            }
            prefs[DENY_PACKAGES] = encodePackages(deny)
            prefs[CONFIRM_PACKAGES] = encodePackages(confirm)
        }
    }

    suspend fun setConfirmMode(mode: ConfirmMode) {
        context.phoneControlDataStore.edit { it[CONFIRM_MODE] = mode.name }
    }

    suspend fun setUnlocks(unlocks: PhoneUnlocks) {
        context.phoneControlDataStore.edit {
            it[UNLOCK_SYSTEM] = unlocks.systemPages
            it[UNLOCK_SECRETS] = unlocks.secretFields
            it[UNLOCK_OWN_APP] = unlocks.ownApp
        }
    }

    suspend fun setOverlayPrefs(overlay: OverlayPrefs) {
        context.phoneControlDataStore.edit {
            it[OVERLAY_IDLE_OPACITY] = overlay.idleOpacity.coerceIn(OverlayPrefs.MIN_OPACITY, OverlayPrefs.MAX_OPACITY)
            it[OVERLAY_AUTO_COLLAPSE] = overlay.autoCollapse
            it[OVERLAY_RETURN_AFTER_DONE] = overlay.returnAfterDone
        }
    }

    /** 追加一条最近操作日志（保留最近 [MAX_RECENT_ACTIONS] 条，最新在前）。 */
    suspend fun addRecentAction(action: String) {
        context.phoneControlDataStore.edit { prefs ->
            val current = prefs[RECENT_ACTIONS]?.split(RECENT_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(action) + current).distinct().take(MAX_RECENT_ACTIONS)
            prefs[RECENT_ACTIONS] = updated.joinToString(RECENT_SEPARATOR)
        }
    }

    suspend fun clearRecentActions() {
        context.phoneControlDataStore.edit { it.remove(RECENT_ACTIONS) }
    }

    /** 读取当前设置快照（一次性）。 */
    suspend fun currentSettings(): PhoneControlSettings = settingsFlow.first()

    private fun encodePackages(packages: Set<String>): String = packages.sorted().joinToString(",")

    private fun decodePackages(raw: String?): Set<String> =
        raw?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
}
