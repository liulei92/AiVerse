package com.aiverse.core.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.aiverse.R

/** 聊天气泡样式（作用于收发两侧消息气泡）。在设置页「聊天气泡」中可选，共 9 种。 */
enum class ChatBubbleStyle(val id: String, @androidx.annotation.StringRes val labelRes: Int) {
    NO_BUBBLE("no_bubble", R.string.bubble_no_bubble),
    CLASSIC_OUTLINE("classic_outline", R.string.bubble_classic_outline),
    SOFT_SHADOW("soft_shadow", R.string.bubble_soft_shadow),
    MINIMAL_WHITE("minimal_white", R.string.bubble_minimal_white),
    DARK_MODE("dark_mode", R.string.bubble_dark_mode),
    COMIC("comic", R.string.bubble_comic),
    FRESH_GREEN("fresh_green", R.string.bubble_fresh_green),
    HEARTBEAT("heartbeat", R.string.bubble_heartbeat),
    ORIGINAL("original", R.string.bubble_original);

    companion object {
        fun fromId(id: String?): ChatBubbleStyle =
            entries.firstOrNull { it.id == id } ?: CLASSIC_OUTLINE
    }
}

/** 聊天气泡所属角色：用户发出的消息（USER）与 AI 回复（ASSISTANT），两侧可各自独立配置样式。 */
enum class ChatBubbleRole { USER, ASSISTANT }

/** 用户消息气泡样式，全局可读（由 AIEditorTheme 注入）。 */
val LocalUserBubbleStyle = staticCompositionLocalOf { ChatBubbleStyle.CLASSIC_OUTLINE }

/** AI 回复气泡样式，全局可读（由 AIEditorTheme 注入）。 */
val LocalAssistantBubbleStyle = staticCompositionLocalOf { ChatBubbleStyle.CLASSIC_OUTLINE }
