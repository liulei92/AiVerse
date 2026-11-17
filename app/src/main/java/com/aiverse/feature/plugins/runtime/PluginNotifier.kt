package com.aiverse.feature.plugins.runtime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aiverse.MainActivity
import com.aiverse.feature.plugins.domain.PluginHostPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 插件发宿主通知的实现。
 *
 * 每个插件使用自己的 notification tag（`plugin:<pluginId>[:key]`），不会覆盖宿主或其他插件的通知；
 * 系统通知关闭、渠道被关闭或缺少权限时返回明确错误，不伪造成功。
 *
 * 通知渠道 [CHANNEL_ID] 在首次发送时幂等创建（Android 8+ 需要渠道已存在，否则通知不会展示）；
 * 渠道名/描述为系统设置可见文案，本移植受「只改 3 个文件」约束暂用英文内联字面量（不硬编码中文），
 * 若需双语请由 Lead 决定是否新增 strings.xml 资源并把渠道创建集中到 AIEditorApp。
 */
@Singleton
class PluginNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun notify(pluginId: String, params: JsonObject): JsonElement {
        val title = PluginHostPaths.requireString(params, "title").trim()
        val body = PluginHostPaths.requireString(params, "body").trim()
        if (title.isEmpty()) throw PluginProcessException("PARAM_INVALID", "empty title")
        if (title.length > MAX_TITLE_CHARS) {
            throw PluginProcessException("PARAM_INVALID", "title too long")
        }
        if (body.length > MAX_BODY_CHARS) {
            throw PluginProcessException("PARAM_INVALID", "body too long")
        }
        val key = (params["key"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        if (key != null && (key.length > MAX_KEY_CHARS || key.any { it.isISOControl() })) {
            throw PluginProcessException("PARAM_INVALID", "invalid key")
        }

        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw PluginProcessException("NOTIFICATION_PERMISSION_DENIED", "app has no notification permission")
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            throw PluginProcessException("NOTIFICATION_DISABLED", "system notifications disabled")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                throw PluginProcessException("NOTIFICATION_DISABLED", "plugin notification channel disabled")
            }
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val tag = "plugin:$pluginId" + (key?.let { ":$it" } ?: "")
        runCatching { manager.notify(tag, NOTIFICATION_ID, notification) }
            .getOrElse { error ->
                throw PluginProcessException(
                    "NOTIFICATION_FAILED",
                    "notify failed: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        return buildJsonObject { put("ok", true) }
    }

    /** 幂等创建插件通知渠道；Android 8+ 需要渠道已存在，否则通知不会展示。 */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java) ?: return
        if (systemManager.getNotificationChannel(CHANNEL_ID) != null) return
        systemManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESC
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "plugin_events"

        private const val NOTIFICATION_ID = 1
        private const val MAX_TITLE_CHARS = 100
        private const val MAX_BODY_CHARS = 1_000
        private const val MAX_KEY_CHARS = 64

        /** 渠道名/描述为系统设置可见文案；本移植受「只改 3 个文件」约束暂用英文内联字面量（不硬编码中文）。 */
        private const val CHANNEL_NAME = "Plugin notifications"
        private const val CHANNEL_DESC = "Notifications sent by plugins through host.notify"
    }
}
