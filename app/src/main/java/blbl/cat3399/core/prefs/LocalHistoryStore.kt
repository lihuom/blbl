package blbl.cat3399.core.prefs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

/**
 * 本地观看历史存储（无痕模式 = local 时使用）。
 *
 * 用 SharedPreferences + JSON 数组持久化，避免引入 Room/SQLite。
 * 同一 stableKey 仅保留最新一条记录，按 lastPlayedAt 倒序排列，上限 [MAX_ITEMS] 条。
 */
object LocalHistoryStore {
    private const val PREFS_NAME = "blbl_local_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 200

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureInit() {
        check(::prefs.isInitialized) { "LocalHistoryStore 未初始化，请先调用 init(context)" }
    }

    /**
     * 写入或更新一条本地历史（同 stableKey 仅保留最新一条，移到列表头）。
     */
    fun upsert(item: LocalHistoryItem) {
        ensureInit()
        val list = getAll().toMutableList()
        // 同 stableKey 去重：移除旧记录
        list.removeAll { it.stableKey == item.stableKey }
        // 新记录插到头部
        list.add(0, item)
        // 截断到上限
        if (list.size > MAX_ITEMS) {
            list.subList(MAX_ITEMS, list.size).clear()
        }
        save(list)
    }

    fun getAll(): List<LocalHistoryItem> {
        ensureInit()
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                LocalHistoryItem(
                    stableKey = o.optString("stableKey"),
                    bvid = o.optString("bvid"),
                    aid = o.optLong("aid"),
                    cid = o.optLong("cid"),
                    epId = o.optLong("epId", 0L).takeIf { it > 0L },
                    seasonId = o.optLong("seasonId", 0L).takeIf { it > 0L },
                    title = o.optString("title"),
                    durationSec = o.optInt("durationSec", 0),
                    progressSec = o.optLong("progressSec", 0L),
                    lastPlayedAt = o.optLong("lastPlayedAt", 0L),
                    finished = o.optBoolean("finished", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun remove(stableKey: String) {
        ensureInit()
        val list = getAll().toMutableList()
        list.removeAll { it.stableKey == stableKey }
        save(list)
    }

    fun clear() {
        ensureInit()
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun save(list: List<LocalHistoryItem>) {
        ensureInit()
        val arr = JSONArray()
        list.forEach { item ->
            val o = JSONObject()
            o.put("stableKey", item.stableKey)
            o.put("bvid", item.bvid)
            o.put("aid", item.aid)
            o.put("cid", item.cid)
            item.epId?.let { o.put("epId", it) }
            item.seasonId?.let { o.put("seasonId", it) }
            o.put("title", item.title)
            o.put("durationSec", item.durationSec)
            o.put("progressSec", item.progressSec)
            o.put("lastPlayedAt", item.lastPlayedAt)
            o.put("finished", item.finished)
            arr.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    /**
     * 生成匿名访问令牌，用于本地历史列表的项点击跳转播放（无需登录态关联）。
     */
    fun newTraceId(): String = UUID.randomUUID().toString()
}

/**
 * 本地观看历史一条记录。仅存核心字段：标识 + 标题 + 进度 + 时间。
 * 不存封面/UP 主头像等网络资源（本地历史列表展示时按文字为主）。
 */
data class LocalHistoryItem(
    val stableKey: String,
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val epId: Long?,
    val seasonId: Long?,
    val title: String,
    val durationSec: Int,
    val progressSec: Long,
    val lastPlayedAt: Long,
    val finished: Boolean,
)
