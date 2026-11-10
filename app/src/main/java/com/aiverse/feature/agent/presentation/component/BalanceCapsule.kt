package com.aiverse.feature.agent.presentation.component

import com.aiverse.feature.settings.domain.model.BalanceConfig

/**
 * 余额胶囊状态（按 provider id 缓存于 ViewModel）。
 * 余额胶囊 UI 已从输入框快捷栏移除（固定到顶栏的是上下文用量环），
 * 状态保留供模型编辑页余额查询链路复用。
 */
data class BalanceCapsuleState(
    val providerId: String,
    val enabled: Boolean,
    /** 余额文本（如 `110.00`）；null 表示尚未加载成功。 */
    val text: String?,
    val loading: Boolean,
    /** 错误信息（非空时余额显示为错误态）。 */
    val error: String?,
    val config: BalanceConfig
)
