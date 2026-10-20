package com.personal.autoskip.detection

/** Android 클래스에 의존하지 않는 노드 사본. 판정 로직은 이 타입만 사용한다. */
data class ScreenNode(
    val text: String? = null,
    val desc: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    /** 자기 자신 또는 가장 가까운 클릭 가능한 조상의 인덱스. */
    val clickTargetIndex: Int? = null,
) {
    val hasArea: Boolean get() = right > left && bottom > top
}
