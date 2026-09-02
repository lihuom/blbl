package blbl.cat3399.feature.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.prefs.LocalHistoryItem
import blbl.cat3399.core.prefs.LocalHistoryStore
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.feature.video.VideoDetailActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地观看历史（无痕模式 = 本地有痕·网络无痕 时使用）。
 *
 * 数据来源：[LocalHistoryStore]（SharedPreferences + JSON 列表）。
 * 列表项点击 → [VideoDetailActivity]（用 bvid/aid/cid 启动，详情页会从 B 站拉完整信息）。
 */
class LocalHistoryActivity : BaseActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var btnClear: View
    private lateinit var btnBack: View
    private val adapter = LocalHistoryAdapter { item -> openDetail(item) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_history)

        recycler = findViewById(R.id.recycler)
        emptyView = findViewById(R.id.tv_empty)
        btnClear = findViewById(R.id.btn_clear)
        btnBack = findViewById(R.id.btn_back)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnBack.setOnClickListener { finish() }
        btnClear.setOnClickListener {
            if (adapter.itemCount == 0) {
                AppToast.show(this, "无可清空的本地历史")
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("清空本地历史")
                .setMessage("确认清空所有本地历史记录？此操作不可撤销。")
                .setPositiveButton("清空") { _, _ ->
                    LocalHistoryStore.clear()
                    refresh()
                    AppToast.show(this, "已清空本地历史")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        // 切回此页时刷新（可能在 PlayerActivity 写入了新历史）
        refresh()
    }

    private fun refresh() {
        val list = LocalHistoryStore.getAll()
        adapter.submit(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openDetail(item: LocalHistoryItem) {
        val intent = Intent(this, VideoDetailActivity::class.java)
            .putExtra(VideoDetailActivity.EXTRA_BVID, item.bvid)
            .putExtra(VideoDetailActivity.EXTRA_AID, item.aid)
            .putExtra(VideoDetailActivity.EXTRA_CID, item.cid)
            .putExtra(VideoDetailActivity.EXTRA_TITLE, item.title)
        item.epId?.let { intent.putExtra("ep_id", it) }
        item.seasonId?.let { intent.putExtra("season_id", it) }
        startActivity(intent)
    }
}

private class LocalHistoryAdapter(
    private val onClick: (LocalHistoryItem) -> Unit,
) : RecyclerView.Adapter<LocalHistoryAdapter.VH>() {

    private val items = mutableListOf<LocalHistoryItem>()

    fun submit(newItems: List<LocalHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting_entry, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(
        view: View,
        onClick: (LocalHistoryItem) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tv_title)
        private val tvValue: TextView = view.findViewById(R.id.tv_value)
        private val tvDesc: TextView = view.findViewById(R.id.tv_desc)
        private var current: LocalHistoryItem? = null

        init {
            view.setOnClickListener {
                current?.let(onClick)
            }
            tvDesc.visibility = View.VISIBLE
        }

        fun bind(item: LocalHistoryItem) {
            current = item
            tvTitle.text = item.title
            tvValue.text = formatTime(item.lastPlayedAt)
            tvDesc.text = formatProgress(item)
        }

        private fun formatTime(ts: Long): String {
            if (ts <= 0L) return "--"
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(ts))
        }

        private fun formatProgress(item: LocalHistoryItem): String {
            val played = item.progressSec.coerceAtLeast(0L)
            val total = item.durationSec.coerceAtLeast(0).toLong()
            val playedText = formatDuration(played)
            return if (total > 0L) {
                val totalText = formatDuration(total)
                val percent = ((played * 100L) / total).coerceIn(0L, 100L)
                "进度 $playedText / $totalText（$percent%）" + if (item.finished) " · 已看完" else ""
            } else {
                "进度 $playedText"
            }
        }

        private fun formatDuration(sec: Long): String {
            if (sec <= 0L) return "00:00"
            val h = sec / 3600L
            val m = (sec % 3600L) / 60L
            val s = sec % 60L
            return if (h > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.US, "%02d:%02d", m, s)
            }
        }
    }
}
