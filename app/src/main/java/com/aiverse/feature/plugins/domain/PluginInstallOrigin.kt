package com.aiverse.feature.plugins.domain

/**
 * 一次安装的来源。本地 zip 只记 local；在线安装在宿主记录里绑定索引来源、
 * 插件源码仓库与包摘要，更新时沿同一来源检查。
 */
data class PluginInstallOrigin(
    val kind: String = KIND_LOCAL,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val indexUrl: String? = null,
    val repository: String? = null,
    val official: Boolean = false
) {
    companion object {
        const val KIND_LOCAL = "local"
        const val KIND_INDEX = "index"

        /**
         * 记忆「假插件」来源标记：不落盘、不参与安装/卸载，仅供已安装列表展示与启停
         * （启停写 [com.aiverse.feature.settings.data.repository.MemoryToggleRepository]）。
         * 取一个不会与真实来源冲突的值，避免被当成 local/index 插件误处理。
         */
        const val KIND_MEMORY = "memory-toggle"
    }
}
