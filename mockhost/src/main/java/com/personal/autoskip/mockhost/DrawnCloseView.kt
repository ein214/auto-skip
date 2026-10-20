package com.personal.autoskip.mockhost

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * WebView 광고처럼 X를 캔버스에 그림으로만 그린다. 텍스트/설명/id가 없어 접근성으로는 X를 알 수 없다.
 * 크기와 위치는 실기기 AppLovin 엔드카드(1080px 폭 기준 (998,164), 약 23px)에 맞췄다.
 */
@SuppressLint("ViewConstructor")
class DrawnCloseView(context: Context, private val onClose: () -> Unit) : View(context) {

    var showX = false
        set(value) { field = value; invalidate() }

    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202020.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val cx get() = width - 82f * width / 1080f
    private val cy get() = 164f * width / 1080f
    private val half get() = 11f * width / 1080f

    override fun onDraw(canvas: Canvas) {
        if (!showX) return
        canvas.drawCircle(cx, cy, half * 3.5f, backdrop)
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, stroke)
        canvas.drawLine(cx + half, cy - half, cx - half, cy + half, stroke)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showX) return false
        val hit = kotlin.math.abs(event.x - cx) < half * 3 && kotlin.math.abs(event.y - cy) < half * 3
        if (hit && event.action == MotionEvent.ACTION_UP) onClose()
        return hit // X 밖의 터치는 아래 엔드카드(설치하기 등)로 넘긴다.
    }
}
