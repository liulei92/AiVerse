package com.aiverse.feature.phonecontrol.domain

import android.content.Context
import com.aiverse.feature.agent.domain.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 授予 [android.Manifest.permission.WRITE_SECURE_SETTINGS] 的四种提权方式。
 *
 * 该权限授予后，[A11yKeeper] 才能把无障碍服务写回系统 `enabled_accessibility_services`。
 * - shizuku / sui：复用 [ShizukuManager]（Shizuku 以 adb shell 身份执行；Sui 是 Shizuku 的实现）。
 * - root：`su -c pm grant`。
 * - adb：输出标准命令，由用户在已连接的 adb（电脑或无线调试）执行；若本机 Shizuku 就绪亦可直接走它。
 */
object PermissionGranter {

    /** 授予命令（shell 语法）。 */
    fun grantCommand(packageName: String): String =
        "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

    /** 标准 adb 命令（供用户在电脑/无线调试执行）。 */
    fun adbCommand(packageName: String): String =
        "adb shell ${grantCommand(packageName)}"

    /** 经 Shizuku（或 Sui）执行 `pm grant`；需 Shizuku 就绪。 */
    suspend fun grantViaShizuku(
        shizukuManager: ShizukuManager,
        packageName: String
    ): Result<Boolean> = runCatching {
        val result = shizukuManager.runCommand(
            command = grantCommand(packageName),
            timeoutMs = 30_000L
        )
        result.exitCode == 0
    }

    /** 经 root 执行 `su -c pm grant`；需设备已 root（Magisk/KernelSU 等）。 */
    suspend fun grantViaRoot(context: Context, packageName: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder(
                    "su", "-c", grantCommand(packageName)
                ).redirectErrorStream(true).start()
                val exit = process.waitFor()
                exit == 0
            }
        }
}
