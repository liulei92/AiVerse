package com.aiverse.feature.agent.domain.tool.browser

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 浏览器书签条目。 */
data class BrowserBookmark(
    val url: String,
    val title: String,
    val createdAt: Long
)

/** 浏览器历史记录条目。 */
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long
)

/**
 * 浏览器书签 / 历史 / 主页的轻量持久化仓库。
 *
 * 放在 domain 层（与 [BrowserManager] 同包），内部用 SharedPreferences + org.json 实现，
 * 不走 Room/AgentDatabase：既规避 domain→data 分层铁律（domain 不依赖 data 层 DAO），
 * 也避免数据库迁移风险（书签/历史量小，prefs 同步读写足够）。存储规模可控：
 * 书签不设上限（用户自管）、历史限 [MAX_HISTORY] 条。
 */
@Singleton
class BrowserDataStore @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("browser_data", Context.MODE_PRIVATE)

    companion object {
        /** 历史记录上限：限最近 N 条，避免 prefs 无限膨胀。 */
        const val MAX_HISTORY = 200

        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_HISTORY = "history"
        private const val KEY_HOMEPAGE = "homepage"
    }

    // ================= 书签 =================

    /** 全部书签，最近添加在前。 */
    fun listBookmarks(): List<BrowserBookmark> =
        readArray(KEY_BOOKMARKS).map { obj ->
            BrowserBookmark(
                url = obj.optString("url"),
                title = obj.optString("title"),
                createdAt = obj.optLong("createdAt", 0L)
            )
        }.filter { it.url.isNotBlank() }

    /** 添加书签（url 唯一，重复添加则更新 title 并提到最前）。返回是否成功。 */
    fun addBookmark(url: String, title: String): Boolean {
        val u = url.trim()
        if (u.isBlank()) return false
        val list = listBookmarks().filter { it.url != u }.toMutableList()
        list.add(0, BrowserBookmark(u, title.trim(), System.currentTimeMillis()))
        writeArray(KEY_BOOKMARKS, list.map { it.toJson() })
        return true
    }

    /** 按 url 删除书签。返回是否删除。 */
    fun removeBookmark(url: String): Boolean {
        val before = listBookmarks()
        val after = before.filter { it.url != url }
        if (after.size == before.size) return false
        writeArray(KEY_BOOKMARKS, after.map { it.toJson() })
        return true
    }

    /** 该 url 是否已收藏。 */
    fun isBookmarked(url: String): Boolean = listBookmarks().any { it.url == url }

    // ================= 历史 =================

    /** 全部历史，最近访问在前。 */
    fun getHistory(): List<BrowserHistoryEntry> =
        readArray(KEY_HISTORY).map { obj ->
            BrowserHistoryEntry(
                url = obj.optString("url"),
                title = obj.optString("title"),
                visitedAt = obj.optLong("visitedAt", 0L)
            )
        }.filter { it.url.isNotBlank() }

    /** 记录一次访问：url 去重（重复访问更新 title 并提到最前），限 [MAX_HISTORY] 条。 */
    fun recordHistory(url: String, title: String) {
        val u = url.trim()
        if (u.isBlank() || u.startsWith("about:")) return
        val list = getHistory().filter { it.url != u }.toMutableList()
        list.add(0, BrowserHistoryEntry(u, title.trim(), System.currentTimeMillis()))
        while (list.size > MAX_HISTORY) list.removeAt(list.size - 1)
        writeArray(KEY_HISTORY, list.map { it.toJson() })
    }

    /** 清空全部历史。 */
    fun clearHistory() {
        writeArray(KEY_HISTORY, emptyList())
    }

    // ================= 主页 =================

    /** 当前配置的主页（空串表示未设置）。 */
    fun getHomepage(): String = prefs.getString(KEY_HOMEPAGE, "").orEmpty()

    fun setHomepage(url: String) {
        prefs.edit().putString(KEY_HOMEPAGE, url.trim()).apply()
    }

    fun clearHomepage() {
        prefs.edit().remove(KEY_HOMEPAGE).apply()
    }

    // ================= 内部工具 =================

    private fun BrowserBookmark.toJson(): JSONObject =
        JSONObject().put("url", url).put("title", title).put("createdAt", createdAt)

    private fun BrowserHistoryEntry.toJson(): JSONObject =
        JSONObject().put("url", url).put("title", title).put("visitedAt", visitedAt)

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
