package com.aiverse.core.theme

import androidx.compose.runtime.Immutable

/**
 * 侧边栏文件树的显示偏好（「外观设置 → 文件」模块）。
 *
 * [Default] 与历史版本行为完全一致（单行横向滚动、显示隐藏文件、标准缩进、弱化着色），
 * 老用户升级无感。
 */
@Immutable
data class FileTreeDisplayPrefs(
    /** 文件名显示方式。 */
    val fileNameDisplay: FileNameDisplay = FileNameDisplay.SINGLE_LINE_SCROLL,
    /** 是否显示 .gitignore、dotfile 等隐藏文件。 */
    val showHiddenFiles: Boolean = true,
    /** 文件树层级缩进密度。 */
    val indentDensity: FileTreeIndentDensity = FileTreeIndentDensity.STANDARD,
    /** 是否用颜色弱化 .gitignore / dotfile。 */
    val mutedColoring: Boolean = true
) {
    companion object {
        /** 默认档：与历史版本行为完全一致。 */
        val Default = FileTreeDisplayPrefs()
    }
}

/** 文件名显示方式（「外观设置 → 文件」可选项）。 */
enum class FileNameDisplay(val id: String) {
    /** 单行不换行，超出部分随整树横向滚动（历史默认）。 */
    SINGLE_LINE_SCROLL("single_line_scroll"),
    /** 最多两行，超长自动换行。 */
    TWO_LINES("two_lines"),
    /** 单行，超长末尾省略号。 */
    SINGLE_LINE_ELLIPSIS("single_line_ellipsis");

    companion object {
        fun fromId(id: String?): FileNameDisplay = entries.firstOrNull { it.id == id } ?: SINGLE_LINE_SCROLL
    }
}

/** 文件树层级缩进密度：标准 = 历史 16dp，紧凑 = 10dp。 */
enum class FileTreeIndentDensity(val id: String, val indentDp: Int) {
    COMPACT("compact", 10),
    STANDARD("standard", 16);

    companion object {
        fun fromId(id: String?): FileTreeIndentDensity = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
