package com.aiverse.feature.phonecontrol.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 无障碍服务开启状态维护（写回系统 `enabled_accessibility_services`）。
 *
 * 真正的系统写入需要 [Manifest.permission.WRITE_SECURE_SETTINGS]；该权限由外部以
 * shizuku / sui / root / adb 四种方式授予（见 [PermissionGranter]），此处只负责读写。
 * 服务条目格式 `pkg/cls`（完整类名），读取时同时兼容 `pkg/.Short` 短类名形式。
 */
object A11yKeeper {

    /** 无障碍服务类名（与 AndroidManifest 注册一致）。 */
    const val SERVICE_CLASS = "com.aiverse.feature.phonecontrol.domain.PhoneAccessibilityService"

    /** 完整组件条目 `pkg/cls`。 */
    fun serviceId(context: Context): String = "${context.packageName}/$SERVICE_CLASS"

    /** 短类名条目 `pkg/.Short`（用于兼容匹配）。 */
    private fun shortServiceId(context: Context): String =
        "${context.packageName}/.${SERVICE_CLASS.substringAfterLast('.')}"

    /** 系统写入权限是否已授予。 */
    fun hasSecureSettingsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

    /** 本服务当前是否已开启。 */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val id = serviceId(context)
        val short = shortServiceId(context)
        return enabled.split(':').any { token ->
            token.equals(id, ignoreCase = true) || token.equals(short, ignoreCase = true)
        }
    }

    /** 开启本服务；无系统写入权限时返回 false。 */
    fun enable(context: Context): Boolean {
        if (!hasSecureSettingsPermission(context)) return false
        val id = serviceId(context)
        val current = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val tokens = current.split(':').filter { it.isNotBlank() }.toMutableList()
        if (tokens.none { it.equals(id, ignoreCase = true) || it.equals(shortServiceId(context), ignoreCase = true) }) {
            tokens.add(id)
        }
        return write(context, tokens.joinToString(":"), enable = true)
    }

    /** 关闭本服务；无系统写入权限时返回 false。 */
    fun disable(context: Context): Boolean {
        if (!hasSecureSettingsPermission(context)) return false
        val id = serviceId(context)
        val short = shortServiceId(context)
        val current = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return true
        val tokens = current.split(':')
            .filter { it.isNotBlank() }
            .filterNot { it.equals(id, ignoreCase = true) || it.equals(short, ignoreCase = true) }
        return write(context, tokens.joinToString(":"), enable = false)
    }

    private fun write(context: Context, value: String, enable: Boolean): Boolean = runCatching {
        val resolver = context.contentResolver
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)
        if (enable) {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
        true
    }.getOrDefault(false)
}
