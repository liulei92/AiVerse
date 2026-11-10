package com.aiverse.feature.phonecontrol.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.aiverse.core.util.FileLogger

/**
 * 省电策略与电池优化跳转（参考 dsh-Go RuntimePlugin / VendorSettings + 现有 OemAutoStartGuide）。
 *
 * 分厂商跳转「省电策略」设置页：小米/Redmi 跳 HiddenAppsConfigActivity（省电策略：无限制），
 * vivo/iQOO 跳后台耗电管理，华为/荣耀跳应用启动管理；OPPO/一加/realme 与三星无专用页，回退
 * 应用详情页。所有跳转先 `resolveActivity` 校验存在性，失败统一回退应用详情页。
 */
object VendorSettings {
    private const val TAG = "VendorSettings"

    private data class VendorEntry(val component: String, val keywords: List<String>)

    // 各厂商「省电策略」页（包名/Activity）。顺序即优先级：命中的首个可解析组件被使用。
    private val vendorEntries = listOf(
        VendorEntry(
            "com.vivo.abe/com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity",
            listOf("vivo", "iqoo")
        ),
        VendorEntry(
            "com.miui.powerkeeper/com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            listOf("xiaomi", "redmi", "poco")
        ),
        VendorEntry(
            "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            listOf("huawei")
        ),
        VendorEntry(
            "com.hihonor.systemmanager/com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            listOf("honor", "hihonor")
        ),
        // 华为兜底：荣耀设备上鸿蒙系系统管理器包名可能与华为一致。
        VendorEntry(
            "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            listOf("honor", "hihonor")
        ),
        VendorEntry(
            "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity",
            listOf("oppo", "oneplus", "realme")
        )
    )

    /** 判断当前设备厂商命中的省电策略页（用于「其他品牌」列表定位当前品牌）。 */
    fun currentVendor(): String {
        val manufacturer = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "vivo" in manufacturer || "iqoo" in manufacturer -> "vivo"
            "xiaomi" in manufacturer || "redmi" in manufacturer || "poco" in manufacturer -> "xiaomi"
            "huawei" in manufacturer -> "huawei"
            "honor" in manufacturer || "hihonor" in manufacturer -> "honor"
            "oppo" in manufacturer || "oneplus" in manufacturer || "realme" in manufacturer -> "oppo"
            "samsung" in manufacturer -> "samsung"
            else -> "other"
        }
    }

    /** 尝试跳转厂商省电策略页；返回 `"vendor"` 成功，否则回退应用详情页并返回 `"details"`。 */
    fun openVendorBatterySettings(context: Context): String {
        val manufacturer = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        val entry = vendorEntries.firstOrNull { vendor ->
            vendor.keywords.any { manufacturer.contains(it) }
        }
        if (entry != null) {
            val (pkg, cls) = entry.component.split("/", limit = 2)
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            // 小米省电策略页需要包名参数。
            if (pkg == "com.miui.powerkeeper") {
                intent.putExtra("package_name", context.packageName)
                intent.putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
            }
            if (startResolved(context, intent)) return "vendor"
        }
        openAppDetails(context)
        return "details"
    }

    private fun startResolved(context: Context, intent: Intent): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) {
            FileLogger.w(TAG, "省电策略页不可用: ${intent.component}")
            return false
        }
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { FileLogger.e(TAG, "跳转省电策略页失败: ${intent.component}", it) }
            .isSuccess
    }

    /** 是否已忽略电池优化。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 申请忽略电池优化：依次尝试系统「忽略电池优化」弹窗 → 电池优化设置列表 → 应用详情页。
     */
    fun requestBatteryExempt(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        if (startResolved(context, direct)) return
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (startResolved(context, list)) return
        openAppDetails(context)
    }

    /** 跳转到应用系统详情页。 */
    fun openAppDetails(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { FileLogger.e(TAG, "跳转应用详情页失败", it) }
    }
}
