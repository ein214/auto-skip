package com.personal.autoskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 광고 화면에 나타난 스토어 이동 버튼 위치를 테두리로 강조한다. 누르는 것은 사용자다.
 * 접근성 오버레이 창(TYPE_ACCESSIBILITY_OVERLAY)을 쓰며, 터치는 그대로 아래 화면으로 통과시킨다.
 */
class StoreButtonOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: HighlightView? = null

    /** [rects]는 화면 좌표. 비어 있으면 숨긴다. */
    fun show(rects: List<RectF>) {
        if (rects.isEmpty()) {
            hide()
            return
        }
        val current = view
        if (current != null) {
            current.rects = rects
            return
        }
        val v = HighlightView(service).also { it.rects = rects }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(v, params)
        view = v
        vibrate()
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        view = null
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            service.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private class HighlightView(context: Context) : View(context) {
        var rects: List<RectF> = emptyList()
            set(value) { field = value; invalidate() }

        private val density = context.resources.displayMetrics.density
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            color = 0xFFFF6D00.toInt()
        }
        private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FF6D00.toInt() }
        private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 13 * density
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            val pad = 6 * density
            for (r in rects) {
                val box = RectF(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
                canvas.drawRoundRect(box, 10 * density, 10 * density, border)
                // 라벨은 상자 아래에 둔다 (화면 위쪽 모서리의 X 이미지 탐지에 섞이지 않도록).
                val label = "👆 스토어 이동"
                val w = labelText.measureText(label) + 12 * density
                val h = 22 * density
                val top = (box.bottom + 4 * density).coerceAtMost(height - h)
                val left = box.left.coerceIn(0f, (width - w).coerceAtLeast(0f))
                canvas.drawRoundRect(RectF(left, top, left + w, top + h), 6 * density, 6 * density, labelBg)
                canvas.drawText(label, left + 6 * density, top + h - 6 * density, labelText)
            }
        }
    }
}
