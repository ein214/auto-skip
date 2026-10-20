package com.personal.autoskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import com.personal.autoskip.detection.CloseCandidate

/**
 * 검증된 후보만 클릭한다. 뒤로가기는 사용하지 않는다.
 * 좌표 탭은 노드가 없는 광고에서 이미지로 두 번 확인한 X에만 쓴다 (기획서 P2).
 */
object ClickExecutor {
    fun tap(service: AccessibilityService, x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private const val TAP_DURATION_MS = 50L

    fun click(snapshot: ScreenSnapshot, candidate: CloseCandidate): Boolean {
        val info = snapshot.infos.getOrNull(candidate.clickIndex) ?: return false
        // 탐색 이후 바뀌었을 수 있으므로 시스템에서 최신 상태를 다시 받아 확인한다.
        if (!info.refresh()) return false
        if (!info.isVisibleToUser || !info.isEnabled || !info.isClickable) return false
        return info.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
