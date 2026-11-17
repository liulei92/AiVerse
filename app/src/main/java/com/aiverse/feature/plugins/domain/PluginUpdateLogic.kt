package com.aiverse.feature.plugins.domain

/**
 * 插件「可升级」判定的纯计算逻辑，供商店列表（[PluginStoreViewModel] 的 `isNewer` / `updatesOf`）
 * 与启动静默检查（[PluginUpdateChecker]）共用，避免版本比较规则在两处各写一份。
 *
 * 只依赖 [SemVer]，不依赖 data / presentation，可安全放在 domain 层。
 */
internal object PluginUpdateLogic {

    /**
     * 云端候选版本是否比本地已安装版本更新。
     *
     * 任一版本缺失（null）或无法解析为语义化版本时返回 false，
     * 与商店既有行为一致：解析不出就不提示升级。
     */
    fun isNewer(candidate: String?, installed: String?): Boolean {
        if (candidate == null || installed == null) return false
        val left = SemVer.parse(candidate) ?: return false
        val right = SemVer.parse(installed) ?: return false
        return left > right
    }

    /**
     * 把版本字符串解析为可比较的 [SemVer]；为空或非法时回落到 `0.0.0`，
     * 供「同一插件多来源取最高版本」时兜底比较（与商店 `updatesOf` 原实现一致）。
     */
    fun versionOrZero(version: String?): SemVer =
        version?.let(SemVer::parse) ?: SemVer(0, 0, 0)
}
