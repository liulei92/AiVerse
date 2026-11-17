package com.aiverse.feature.plugins.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aiverse.core.theme.AppSemanticColors
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 插件页面可见的设计 token。颜色、间距、圆角都从当前 Compose 主题计算，
 * 页面只依赖 CSS 变量；主题切换时宿主更新变量即可，不重新加载页面。
 *
 * 变量名是插件作者可见的稳定接口，改动等于破坏插件页面，新增变量只能追加。
 * 移植到 AiVerse：变量名由 AiCode 的 `--aicode-*` 改为 `--aiverse-*`，页面桥（P3d）落地时统一沿用。
 */
object PluginThemeTokens {

    fun isDark(scheme: ColorScheme): Boolean = scheme.background.luminance() < 0.5f

    fun variables(
        scheme: ColorScheme,
        semantic: AppSemanticColors,
        fontScale: Float
    ): Map<String, String> {
        val aiverse = linkedMapOf(
        "--aiverse-bg" to scheme.background.toCss(),
        "--aiverse-surface" to scheme.surface.toCss(),
        "--aiverse-surface-variant" to scheme.surfaceVariant.toCss(),
        "--aiverse-on-surface" to scheme.onSurface.toCss(),
        "--aiverse-on-surface-variant" to scheme.onSurfaceVariant.toCss(),
        "--aiverse-primary" to scheme.primary.toCss(),
        "--aiverse-on-primary" to scheme.onPrimary.toCss(),
        "--aiverse-primary-container" to scheme.primaryContainer.toCss(),
        "--aiverse-primary-soft" to scheme.primary.copy(alpha = 0.12f).toCss(),
        "--aiverse-tertiary" to scheme.tertiary.toCss(),
        "--aiverse-tertiary-soft" to scheme.tertiary.copy(alpha = 0.12f).toCss(),
        "--aiverse-outline" to scheme.outline.toCss(),
        "--aiverse-outline-soft" to scheme.outline.copy(alpha = 0.12f).toCss(),
        "--aiverse-error" to scheme.error.toCss(),
        "--aiverse-success" to semantic.success.toCss(),
        "--aiverse-success-soft" to semantic.success.copy(alpha = 0.12f).toCss(),
        "--aiverse-warning" to semantic.warning.toCss(),
        "--aiverse-warning-soft" to semantic.warning.copy(alpha = 0.12f).toCss(),
        "--aiverse-page-bg" to semantic.pageBackground.toCss(),
        "--aiverse-card-surface" to semantic.cardSurface.toCss(),
        "--aiverse-subtle-text" to semantic.subtleText.toCss(),
        "--aiverse-subtle-border" to semantic.subtleBorder.toCss(),
        "--aiverse-space-xs" to px(Spacing.xs.value),
        "--aiverse-space-sm" to px(Spacing.sm.value),
        "--aiverse-space-md" to px(Spacing.md.value),
        "--aiverse-space-lg" to px(Spacing.lg.value),
        "--aiverse-space-xl" to px(Spacing.xl.value),
        "--aiverse-radius-xs" to px(Radius.xs.value),
        "--aiverse-radius-sm" to px(Radius.sm.value),
        "--aiverse-radius-md" to px(Radius.md.value),
        "--aiverse-radius-md-large" to px(Radius.mdLarge.value),
        "--aiverse-radius-lg" to px(Radius.lg.value),
        "--aiverse-radius-xl" to px(Radius.xl.value),
        "--aiverse-font-scale" to String.format(Locale.US, "%.2f", fontScale),
        "--aiverse-touch-target" to "44px",
        "--aiverse-switch-track" to if (isDark(scheme)) "#334155" else "#E2E8F0",
        "--aiverse-switch-thumb" to if (isDark(scheme)) "#F8FAFC" else "#FFFFFF",
        "--aiverse-font-sans" to "system-ui, -apple-system, \"Noto Sans CJK SC\", sans-serif",
        "--aiverse-font-mono" to "ui-monospace, \"Roboto Mono\", monospace",
        "--aiverse-text-field-border" to if (isDark(scheme)) {
            scheme.outlineVariant.copy(alpha = 0.6f).toCss()
        } else {
            scheme.outlineVariant.toCss()
        },
        "--aiverse-text-field-focused-bg" to if (isDark(scheme)) {
            scheme.surfaceVariant.copy(alpha = 0.8f).toCss()
        } else {
            semantic.cardSurface.toCss()
        },
        "--aiverse-text-field-disabled-border" to scheme.outlineVariant
            .copy(alpha = if (isDark(scheme)) 0.3f else 0.5f).toCss(),
        "--aiverse-text-field-disabled-text" to scheme.onSurface.copy(alpha = 0.38f).toCss(),
        "--aiverse-dialog-surface" to scheme.surfaceContainerHigh.toCss()
        )
        return buildMap {
            putAll(aiverse)
            aiverse.forEach { (k, v) ->
                if (k.startsWith("--aiverse-")) put("--aicode-" + k.removePrefix("--aiverse-"), v)
            }
            // demo.provider 等 AiCode 示例页面用 --aicode-text/--aicode-background/--aicode-text-secondary。
            put("--aicode-text", aiverse["--aiverse-on-surface"] ?: "#1f2328")
            put("--aicode-background", aiverse["--aiverse-bg"] ?: "#ffffff")
            put("--aicode-text-secondary", aiverse["--aiverse-on-surface-variant"] ?: "#59636e")
        }
    }

    fun css(variables: Map<String, String>): String =
        ":root{" + variables.entries.joinToString("") { (key, value) -> "$key:$value;" } + "}"

    /** 主题变化时注入页面；页面侧由 [aiverse.js] 的 applyTheme 写到根元素。 */
    fun applyScript(variables: Map<String, String>): String {
        val obj = variables.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"$key\":\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        return "window.aiverse&&window.aiverse.applyTheme($obj)"
    }

    private fun px(value: Float): String = "${value.roundToInt()}px"

    private fun Color.toCss(): String {
        val r = (red * 255f).roundToInt().coerceIn(0, 255)
        val g = (green * 255f).roundToInt().coerceIn(0, 255)
        val b = (blue * 255f).roundToInt().coerceIn(0, 255)
        if (alpha >= 1f) return "#%02X%02X%02X".format(r, g, b)
        val a = String.format(Locale.US, "%.3f", alpha.coerceIn(0f, 1f))
        return "rgba($r,$g,$b,$a)"
    }
}
