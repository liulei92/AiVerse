package com.aiverse.feature.backup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.betaHandoffDataStore by preferencesDataStore(
    name = "beta_handoff_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 测试版（beta）与正式版并存时的数据接引导判定与持久化。
 *
 * 引导卡只在四个条件同时成立时展示：当前是 beta 包、本机已装正式版、自身还没有数据、
 * 用户没点过「暂不」。判定集中在 [promptVisibleFlow]，页面侧只消费这一个 Flow。
 *
 * 偏好存储方式与 [com.aiverse.feature.agreement.data.AgreementRepository] 保持一致：
 * DataStore + boolean key + [preferencesCorruptionHandler] 兜底。
 */
@Singleton
class BetaHandoffRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 是否展示「从正式版导入数据」引导卡。 */
    val promptVisibleFlow: Flow<Boolean> =
        context.betaHandoffDataStore.data
            .map { prefs ->
                val dismissed = prefs[PROMPT_DISMISSED] ?: false
                !dismissed && isBetaPackage() && isReleaseInstalled() && hasNoData()
            }
            .distinctUntilChanged()

    /** 用户点「暂不」后不再提示（同一次安装内持久）。 */
    suspend fun markPromptDismissed() {
        context.betaHandoffDataStore.edit { it[PROMPT_DISMISSED] = true }
    }

    /** 当前包是否为测试版：applicationId 以 `.beta` 结尾（与 [com.aiverse.core.ui.BetaWatermark] 同一判定）。 */
    fun isBetaPackage(): Boolean = context.packageName.endsWith(BETA_PACKAGE_SUFFIX)

    /** 本机是否已安装正式版。 */
    fun isReleaseInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(RELEASE_PACKAGE, 0) }.isSuccess

    /** 自身还没有数据：主数据库文件不存在或为空（首次安装、卸载重装）。 */
    fun hasNoData(): Boolean {
        val dbFile = context.getDatabasePath(MAIN_DATABASE_FILE)
        return !dbFile.exists() || dbFile.length() <= 0L
    }

    companion object {
        private val PROMPT_DISMISSED = booleanPreferencesKey("beta_handoff_prompt_dismissed")

        /** Room 主库文件名（未显式指定 name 时取 @Database 类名）。 */
        const val MAIN_DATABASE_FILE = "AgentDatabase"

        /** 正式版包名：引导卡发 intent 与正式版侧授权页互相对齐。 */
        const val RELEASE_PACKAGE = "com.aiverse"

        /** 测试版 applicationId 后缀。 */
        const val BETA_PACKAGE_SUFFIX = ".beta"

        /** 授权页 deep link，由正式版 Manifest 的 intent-filter 承接。 */
        const val AUTH_GRANT_URI = "aiverse://auth/grant-backup"

        /** 请求方包名（发起接管的测试版包）。 */
        const val EXTRA_REQUESTER = "requester"
    }
}