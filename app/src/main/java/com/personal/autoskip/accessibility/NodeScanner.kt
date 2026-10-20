package com.personal.autoskip.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.personal.autoskip.detection.ScreenNode

/** 한 번의 화면 탐색 결과. [infos]는 [nodes]와 같은 인덱스를 가진다(클릭용). */
class ScreenSnapshot(
    val packageName: String,
    /** 탐색한 창의 id. 전경 Activity의 창과 같은지 비교하는 데 쓴다. */
    val windowId: Int,
    val nodes: List<ScreenNode>,
    internal val infos: List<AccessibilityNodeInfo>,
)

/** AccessibilityNodeInfo 트리를 JVM에서 테스트 가능한 [ScreenNode] 목록으로 펼친다. */
object NodeScanner {
    private const val MAX_NODES = 600
    private const val MAX_DEPTH = 40

    fun scan(root: AccessibilityNodeInfo): ScreenSnapshot {
        val nodes = ArrayList<ScreenNode>()
        val infos = ArrayList<AccessibilityNodeInfo>()
        val rect = Rect()

        fun visit(info: AccessibilityNodeInfo, depth: Int, clickableAncestor: Int?) {
            if (nodes.size >= MAX_NODES || depth > MAX_DEPTH) return
            val index = nodes.size
            info.getBoundsInScreen(rect)
            val clickTarget = if (info.isClickable) index else clickableAncestor
            nodes += ScreenNode(
                text = info.text?.toString(),
                desc = info.contentDescription?.toString(),
                viewId = info.viewIdResourceName,
                className = info.className?.toString(),
                clickable = info.isClickable,
                enabled = info.isEnabled,
                visible = info.isVisibleToUser,
                left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom,
                clickTargetIndex = clickTarget,
            )
            infos += info
            for (i in 0 until info.childCount) {
                val child = info.getChild(i) ?: continue
                visit(child, depth + 1, clickTarget)
            }
        }

        visit(root, 0, null)
        return ScreenSnapshot(root.packageName?.toString().orEmpty(), root.windowId, nodes, infos)
    }
}
