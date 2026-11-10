package com.aiverse.feature.phonecontrol.data

/**
 * 「手机控制」领域模型。
 *
 * 参考 dsh-Go 的 SafetyGate / OverlayPrefs / phone_channel 定义，规则与默认值保持一致：
 * - 默认「不碰」：银行、支付、券商类（AI 不读也不操作）。
 * - 默认「每步确认」：微信（可读，每个写入动作先确认）。
 * - 确认方式默认「每次都询问」。
 * - 悬浮条：闲置不透明度 70%、闲置自动收起开、完成后返回应用关（按产品要求覆盖 dsh 默认）。
 */

/** 单个应用的访问规则。 */
enum class AppRule {
    /** 不读也不操作（银行/支付/券商默认）。 */
    DENY,

    /** 可自由读；每个写入动作（点击/输入/滑动）先确认（微信默认）。 */
    CONFIRM_EACH_STEP,

    /** 仅一般规则：只有发送等敏感操作才确认。 */
    NORMAL
}

/** 确认方式（任务级确认策略）。 */
enum class ConfirmMode {
    /** 每次都询问：进新 App、发送、删除、付款前都先确认。 */
    ASK,

    /** 本任务免确认：每个任务只问一次。 */
    TASK,

    /** 始终免确认：从不询问，直接操作。 */
    ALWAYS
}

/**
 * 解除权限：让 AI 去做默认由你来做的事。
 *
 * 三类开关默认全关；开启后 AI 不再受对应限制。
 */
data class PhoneUnlocks(
    /** 系统设置、权限页、安装器、安全中心、通知栏的点按。 */
    val systemPages: Boolean = false,
    /** 密码、验证码、支付码输入框的填写。 */
    val secretFields: Boolean = false,
    /** 本应用自身界面的读取与操作。 */
    val ownApp: Boolean = false
) {
    /** 已开启的条数（供「解除权限」入口副标题展示）。 */
    val openedCount: Int
        get() = listOf(systemPages, secretFields, ownApp).count { it }
}

/** 悬浮条配置。 */
data class OverlayPrefs(
    /** 闲置时不透明度（0.3~1，默认 0.7）。 */
    val idleOpacity: Float = DEFAULT_IDLE_OPACITY,
    /** 闲置时自动收起成小球（默认开启）。 */
    val autoCollapse: Boolean = true,
    /** 完成后返回本应用（默认关闭）。 */
    val returnAfterDone: Boolean = false
) {
    companion object {
        const val DEFAULT_IDLE_OPACITY = 0.7f
        const val MIN_OPACITY = 0.3f
        const val MAX_OPACITY = 1f
    }
}

/** 手机控制全部设置快照。 */
data class PhoneControlSettings(
    /** 总开关：关闭后手机控制页面内容隐藏，读屏/点击不生效。 */
    val enabled: Boolean = false,
    /** 「不碰」应用包名集合。 */
    val denyPackages: Set<String> = DEFAULT_DENY_PACKAGES,
    /** 「每步确认」应用包名集合。 */
    val confirmPackages: Set<String> = DEFAULT_CONFIRM_PACKAGES,
    /** 当前确认方式。 */
    val confirmMode: ConfirmMode = ConfirmMode.ASK,
    /** 解除权限开关。 */
    val unlocks: PhoneUnlocks = PhoneUnlocks(),
    /** 悬浮条配置。 */
    val overlay: OverlayPrefs = OverlayPrefs()
) {
    /** 查询某应用规则：不碰 > 每步确认 > 一般。 */
    fun ruleFor(packageName: String): AppRule = when {
        packageName in denyPackages -> AppRule.DENY
        packageName in confirmPackages -> AppRule.CONFIRM_EACH_STEP
        else -> AppRule.NORMAL
    }

    companion object {
        /** 默认「不碰」：银行、支付、券商（与 dsh-Go SafetyGate.DEFAULT_DENY 一致）。 */
        val DEFAULT_DENY_PACKAGES: Set<String> = setOf(
            // payment
            "com.eg.android.AlipayGphone", "com.unionpay", "com.chinatelecom.bestpayclient", "com.jd.jrapp",
            "cn.gov.pbc.dcep", "com.paypal.android.p2pmobile", "com.mybank.android.phone", "com.webank.wemoney",
            // banks
            "com.icbc", "com.chinamworld.main", "com.android.bankabc", "com.chinamworld.bocmbci",
            "com.bankcomm.Bankcomm", "cmb.pb", "com.cmbchina.ccd.pluto.cmbActivity", "com.yitong.mbank.psbc",
            "cn.com.spdb.mobilebank.per", "cn.com.cmbc.newmbank", "com.ecitic.bank.mobile",
            "com.cebbank.mobile.cemb", "com.pingan.paces.ccms", "com.cgbchina.xpt",
            "com.bankofbeijing.mobilebanking",
            // brokers
            "com.hexin.plat.android", "com.eastmoney.android.berlin", "com.lphtsccft", "com.xueqiu.android",
            "cn.futu.trader", "com.tigerbrokers.stock"
        )

        /** 默认「每步确认」：微信。 */
        val DEFAULT_CONFIRM_PACKAGES: Set<String> = setOf("com.tencent.mm")
    }
}
