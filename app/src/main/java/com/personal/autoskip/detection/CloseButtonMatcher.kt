package com.personal.autoskip.detection

/** 클릭 후보. [clickIndex]는 실제로 ACTION_CLICK을 보낼 노드(자신 또는 클릭 가능한 조상). */
data class CloseCandidate(
    val nodeIndex: Int,
    val clickIndex: Int,
    val label: String,
    val score: Int,
) {
    /** 클릭 직전 재탐색 결과가 같은 버튼인지 비교하는 데 쓴다. */
    val signature: String get() = label
}

/**
 * 화면에 이미 표시된 명확한 종료 버튼(Skip/Close/X/Done 등)만 찾는다.
 * 이 클래스는 "광고 화면인지"는 판단하지 않는다 — 그건 [AdContextDetector]의 몫.
 */
object CloseButtonMatcher {

    // 정규화(소문자, 공백/화살표 제거) 후 완전 일치해야 하는 문구.
    private val CLOSE_WORDS = setOf(
        "skip", "skipad", "skipads", "skipvideo", "close", "closead", "done", "dismiss",
        "건너뛰기", "광고건너뛰기", "닫기", "광고닫기", "완료",
    )
    private val X_GLYPHS = setOf("x", "×", "✕", "✖", "╳", "ⅹ")
    private val VIEW_ID_TOKENS = setOf("close", "skip", "dismiss")

    // 하나라도 포함되면 절대 누르지 않는다 (설치 CTA, 결제, 로그인 등).
    private val NEGATIVE = Regex(
        "(?i)(install|download|\\bopen\\b|learn ?more|\\bget\\b|play ?now|google ?play|\\bstore\\b|buy|purchase|" +
            "\\bpay\\b|subscribe|sign ?in|log ?in|verify|설치|다운로드|열기|자세히|알아보기|구매|결제|구독|스토어|로그인|인증)",
    )
    // "5초 후 건너뛰기", "Skip in 5" 같은 카운트다운은 아직 종료 가능 상태가 아니다.
    private val DIGIT = Regex("\\d")
    private val STRIP = Regex("[\\s▶►›»>→⏭.!·:]+")

    fun find(nodes: List<ScreenNode>): CloseCandidate? =
        nodes.indices.mapNotNull { evaluate(nodes, it) }
            .maxWithOrNull(compareBy<CloseCandidate> { it.score }.thenByDescending { nodes[it.nodeIndex].top })

    private fun evaluate(nodes: List<ScreenNode>, index: Int): CloseCandidate? {
        val node = nodes[index]
        if (!node.visible || !node.enabled || !node.hasArea) return null
        val clickIndex = node.clickTargetIndex ?: return null
        val target = nodes[clickIndex]
        if (!target.visible || !target.enabled || !target.hasArea) return null

        val texts = listOfNotNull(node.text, node.desc).map { it.trim() }.filter { it.isNotEmpty() }
        if (texts.any { NEGATIVE.containsMatchIn(it) || DIGIT.containsMatchIn(it) }) return null
        // 클릭 대상(조상)의 텍스트에 설치/결제 문구가 섞여 있으면 거부.
        if (clickIndex != index && listOfNotNull(target.text, target.desc).any { NEGATIVE.containsMatchIn(it) }) return null

        var score = 0
        var label: String? = null
        for (t in texts) {
            val norm = t.lowercase().replace(STRIP, "")
            when {
                norm in CLOSE_WORDS -> { score = maxOf(score, 3); label = label ?: t }
                norm in X_GLYPHS -> { score = maxOf(score, 1); label = label ?: t }
            }
        }
        val idTokens = viewIdTokens(node.viewId)
        if (idTokens.any { it in VIEW_ID_TOKENS }) {
            score += 2
            label = label ?: node.viewId
        }
        // 설치 CTA 등의 viewId는 거부 (예: cta_install, btn_download).
        if (idTokens.any { NEGATIVE.containsMatchIn(it) }) return null

        return if (score > 0 && label != null) CloseCandidate(index, clickIndex, label, score) else null
    }

    /** "com.pkg:id/btnCloseAd" → [btn, close, ad] */
    internal fun viewIdTokens(viewId: String?): List<String> {
        if (viewId.isNullOrEmpty()) return emptyList()
        val name = viewId.substringAfter(":id/")
        return name.replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .split('_', '-')
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
    }
}
