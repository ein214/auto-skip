package com.personal.autoskip.detection

/**
 * 허용 앱 화면에서 '광고 보기' 항목의 남은 쿨타임(초)을 읽는다.
 * 예: 뮤니버스 "영상보고 5 받기 오늘 17회 남음 04:59", TIN "동영상 광고 시청하기" + "6 팬 포인트 받기 | 695초".
 * 광고 문구와 시간이 같은 노드에 있거나, 세로로 가까운(같은 줄) 노드에 있어야 한다.
 */
object CooldownParser {

    private val AD_WORDS = Regex("(?i)(동영상|영상|광고|video|\\bad\\b|ads)")
    private val EXHAUSTED = Regex("(?<!\\d)0\\s*회\\s*남음")
    private val HMS = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?![\\d:])")
    private val KOREAN = Regex("(?:(\\d+)\\s*시간)?\\s*(?:(\\d+)\\s*분)?\\s*(?:(\\d+)\\s*초)?")
    private const val SAME_ROW_PX = 140
    /** 하루 횟수 소진 후 자정 초기화처럼 긴 대기는 알리지 않는다. */
    const val MAX_SECONDS = 3 * 60 * 60

    fun findRemainingSeconds(nodes: List<ScreenNode>): Int? {
        val texts = nodes.filter { it.visible && !it.text.isNullOrBlank() }
        val adRows = texts.filter { AD_WORDS.containsMatchIn(it.text!!) }
        if (adRows.isEmpty()) return null
        return texts.mapNotNull { node ->
            val seconds = parseSeconds(node.text!!) ?: return@mapNotNull null
            val related = adRows.any { ad -> ad === node || abs(ad.centerY - node.centerY) <= SAME_ROW_PX }
            val exhausted = adRows.any { abs(it.centerY - node.centerY) <= SAME_ROW_PX && EXHAUSTED.containsMatchIn(it.text!!) } ||
                EXHAUSTED.containsMatchIn(node.text!!)
            seconds.takeIf { related && !exhausted && it in 1..MAX_SECONDS }
        }.minOrNull()
    }

    /** 텍스트 속 남은 시간(초). 시간 표현이 없으면 null. */
    fun parseSeconds(text: String): Int? {
        HMS.find(text)?.let { m ->
            val (a, b, c) = m.destructured
            return if (c.isEmpty()) a.toInt() * 60 + b.toInt() else a.toInt() * 3600 + b.toInt() * 60 + c.toInt()
        }
        for (m in KOREAN.findAll(text)) {
            if (m.value.isBlank()) continue
            val (h, mi, s) = m.destructured
            if (h.isEmpty() && mi.isEmpty() && s.isEmpty()) continue
            return (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        }
        return null
    }

    private val ScreenNode.centerY get() = (top + bottom) / 2
    private fun abs(x: Int) = if (x < 0) -x else x
}
