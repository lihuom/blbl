package blbl.cat3399.feature.player.danmaku

import blbl.cat3399.core.model.Danmaku

/**
 * 合并弹幕：将一段时间窗口内文本相同、模式相同的弹幕合并为一条，并在文本末尾追加 " ×N" 表示数量。
 *
 * 例如在 5 秒内出现了 3 条 "哈哈哈哈"（中间可能隔着其他弹幕），合并后显示为 "哈哈哈哈 ×3"。
 * 仅当相同文本的弹幕数量 >= 2 时才合并。
 */
object DanmakuMerger {

    /** 合并的时间窗口（毫秒），在此时间差内的相同弹幕会被合并。 */
    private const val MERGE_WINDOW_MS = 5000L

    private data class MergeGroup(val first: Danmaku, var count: Int)

    /**
     * 合并弹幕列表。输入无需排序，函数内部会按 timeMs 升序排序。
     *
     * @param list 弹幕列表
     * @return 合并后的弹幕列表（按 timeMs 升序）
     */
    fun merge(list: List<Danmaku>): List<Danmaku> {
        if (list.size < 2) return list

        val sorted = list.sortedBy { it.timeMs }
        val groups = ArrayList<MergeGroup>()
        // text|mode -> 该 key 最新 group 在 groups 中的下标
        val latestIndex = HashMap<String, Int>(sorted.size)

        for (danmaku in sorted) {
            val key = "${danmaku.text}|${danmaku.mode}"
            val idx = latestIndex[key]
            if (idx != null) {
                val group = groups[idx]
                // 与该组首条弹幕的时间差在窗口内 → 合并
                if (danmaku.timeMs - group.first.timeMs <= MERGE_WINDOW_MS) {
                    group.count++
                    continue
                }
            }
            // 超出窗口或首次出现 → 开新组
            latestIndex[key] = groups.size
            groups.add(MergeGroup(danmaku, 1))
        }

        if (groups.size == sorted.size) return sorted // 无合并，直接返回排序后的列表

        return groups.map { group ->
            if (group.count >= 2) {
                group.first.copy(text = "${group.first.text} ×${group.count}")
            } else {
                group.first
            }
        }
    }
}
