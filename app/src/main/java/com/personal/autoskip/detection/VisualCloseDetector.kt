package com.personal.autoskip.detection

import kotlin.math.abs

enum class VisualKind {
    /** X: 광고를 닫는다. */
    CLOSE,
    /** »: 광고의 다음 단계로 넘긴다(건너뛰기). 스토어로 가는 버튼이 아니다. */
    NEXT,
}

/** 화면 이미지에서 찾은 X/» 모양. 좌표는 화면 픽셀 기준. */
data class VisualCandidate(
    val centerX: Int,
    val centerY: Int,
    val size: Int,
    val score: Float,
    val kind: VisualKind = VisualKind.CLOSE,
)

/** 화면 이미지. [argb]는 위에서부터 최소 [VisualCloseDetector.requiredRows] 행을 담는다. */
class ScreenFrame(val argb: IntArray, val width: Int, val height: Int)

/**
 * 접근성 노드가 없는 광고(예: WebView에 그림으로만 그려진 X)를 위한 이미지 기반 X 탐지.
 *
 * 화면 위쪽 양 모서리에서만, 주변과 떨어져 있는 작은 'X' 모양(대각선 두 줄이 교차)을 찾는다.
 * X가 없으면 건너뛰기 버튼 '»'(오른쪽으로 꺾인 '>' 두 개가 나란히)를 찾는다 — 실기기 BidMachine 광고에서 관찰.
 * 외부 라이브러리 없이 밝기 이진화 → 연결 요소 → 모양 검사 순서로 판정한다.
 * 광고 화면인지는 판단하지 않는다 — 호출하는 쪽에서 광고 컨텍스트를 먼저 확인해야 한다.
 */
object VisualCloseDetector {

    private const val TOP_BAND = 0.16f        // 화면 위쪽 16%
    private const val SIDE_BAND = 0.25f       // 좌우 각 25%
    private const val STATUS_BAR = 0.03f      // 상태 표시줄 아이콘 영역(중심 기준)은 제외
    private const val LIGHT_MIN = 190         // 어두운 배경 위 밝은 X
    private const val DARK_MAX = 60           // 밝은 배경 위 어두운 X

    /** 판정에 필요한 위쪽 행 수. 캡처한 화면 전체를 복사하지 않아도 된다. */
    fun requiredRows(height: Int): Int = (height * TOP_BAND).toInt()

    fun find(frame: ScreenFrame): VisualCandidate? = find(frame.argb, frame.width, frame.height)

    fun find(argb: IntArray, width: Int, height: Int): VisualCandidate? {
        val scale = width / 1080f
        val minSize = (14 * scale).toInt()
        val maxSize = (90 * scale).toInt()
        val bandH = requiredRows(height)
        val sideW = (width * SIDE_BAND).toInt()
        val regions = listOf(0 until sideW, (width - sideW) until width)

        var best: VisualCandidate? = null
        var bestNext: VisualCandidate? = null
        for (xs in regions) {
            for (light in listOf(true, false)) {
                val region = Region(argb, width, xs.first, 0, xs.last - xs.first + 1, bandH, light)
                val components = region.components(minSize / 2, maxSize)
                for (c in components) {
                    val cand = evaluate(region, c, height, minSize) ?: continue
                    if (best == null || cand.score > best.score) best = cand
                }
                val next = findDoubleChevron(region, components, height, minSize, maxSize)
                if (next != null && (bestNext == null || next.score > bestNext.score)) bestNext = next
            }
        }
        // X가 있으면 X를 우선한다.
        return best ?: bestNext
    }

    private class Chevron(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val w get() = maxX - minX + 1
        val h get() = maxY - minY + 1
        val cy get() = (minY + maxY) / 2f
    }

    /** '»': 높이가 비슷한 '>' 두 개가 가로로 붙어 있고, 묶음 전체가 주변과 떨어져 있어야 한다. */
    private fun findDoubleChevron(r: Region, components: List<Component>, screenHeight: Int, minSize: Int, maxSize: Int): VisualCandidate? {
        val chevrons = components.mapNotNull { asChevron(r, it, minSize) }.sortedBy { it.minX }
        for (i in 0 until chevrons.size - 1) {
            val a = chevrons[i]
            val b = chevrons[i + 1]
            val h = maxOf(a.h, b.h)
            if (abs(a.h - b.h) > h * 0.25f || abs(a.cy - b.cy) > h * 0.2f) continue
            val gap = b.minX - a.maxX
            // 실기기 BidMachine '»'는 두 획이 가로로 약 20~30% 겹친다.
            if (gap < -a.w * 0.5f || gap > a.w * 1.2f) continue
            val minX = a.minX
            val maxX = b.maxX
            val minY = minOf(a.minY, b.minY)
            val maxY = maxOf(a.maxY, b.maxY)
            val size = maxOf(maxX - minX + 1, maxY - minY + 1)
            if (size !in minSize..maxSize) continue
            if (!isIsolated(r, minX, minY, maxX, maxY)) continue
            val cx = r.x0 + (minX + maxX) / 2
            val cy = r.y0 + (minY + maxY) / 2
            if (cy < screenHeight * STATUS_BAR) continue
            return VisualCandidate(cx, cy, size, 1f, VisualKind.NEXT)
        }
        return null
    }

    /** '>' 한 획: (왼쪽 위 → 오른쪽 가운데) + (왼쪽 아래 → 오른쪽 가운데) 두 선분으로 이루어진 세로로 긴 모양. */
    private fun asChevron(r: Region, c: Component, minSize: Int): Chevron? {
        val w = c.maxX - c.minX + 1
        val h = c.maxY - c.minY + 1
        if (h < minSize * 0.8f) return null
        val aspect = w.toFloat() / h
        if (aspect < 0.3f || aspect > 0.85f) return null
        val fill = c.pixels.size.toFloat() / (w * h)
        if (fill < 0.12f || fill > 0.6f) return null

        val x0 = c.minX.toFloat()
        val x1 = c.maxX.toFloat()
        val yTop = c.minY.toFloat()
        val yMid = (c.minY + c.maxY) / 2f
        val yBot = c.maxY.toFloat()
        val tol = maxOf(2f, h * 0.2f)
        var near = 0
        for (p in c.pixels) {
            val px = (p % r.w).toFloat()
            val py = (p / r.w).toFloat()
            val d = minOf(segmentDistance(px, py, x0, yTop, x1, yMid), segmentDistance(px, py, x0, yBot, x1, yMid))
            if (d <= tol) near++
        }
        if (near.toFloat() / c.pixels.size < 0.85f) return null

        val steps = (1..9).map { it / 10f }
        val upper = steps.count { t -> r.isFgNear(x0 + t * (x1 - x0), yTop + t * (yMid - yTop)) }
        val lower = steps.count { t -> r.isFgNear(x0 + t * (x1 - x0), yBot + t * (yMid - yBot)) }
        if (upper < 8 || lower < 8) return null
        // 왼쪽 가운데(꺾인 안쪽)와 오른쪽 위/아래 모서리는 비어 있어야 한다 (채워진 ▶, 네모 제외).
        // '»'의 두 '>'는 가로로 겹칠 수 있으므로 이웃 모양이 아닌 이 획 자신의 픽셀만 본다.
        val own = c.pixels.toHashSet()
        fun ownAt(x: Float, y: Float) = own.contains(y.toInt() * r.w + x.toInt())
        if (ownAt(x0 + 0.1f * w, yMid)) return null
        if (ownAt(x1 - 0.1f * w, yTop + 0.1f * h)) return null
        if (ownAt(x1 - 0.1f * w, yBot - 0.1f * h)) return null
        return Chevron(c.minX, c.minY, c.maxX, c.maxY)
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return kotlin.math.hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * 주변 원형 고리(반지름: 크기의 0.75~1.1배)에 다른 전경이 거의 없어야 한다 (글자 속 기호 제외).
     * 원형인 이유: 원형 버튼 배경(예: 흰 화면 위 회색 원 안의 흰 X)의 바깥 모서리를 주변으로 오인하지 않도록.
     */
    private fun isIsolated(r: Region, minX: Int, minY: Int, maxX: Int, maxY: Int): Boolean {
        val size = maxOf(maxX - minX + 1, maxY - minY + 1)
        val ccx = (minX + maxX) / 2f
        val ccy = (minY + maxY) / 2f
        val inner = size * 0.75f
        val outer = size * 1.1f
        var ring = 0
        var ringArea = 0
        for (y in (ccy - outer).toInt()..(ccy + outer).toInt()) {
            for (x in (ccx - outer).toInt()..(ccx + outer).toInt()) {
                val d = kotlin.math.hypot(x - ccx, y - ccy)
                if (d < inner || d > outer) continue
                if (x !in 0 until r.w || y !in 0 until r.h) continue
                ringArea++
                if (r.isFg(x, y)) ring++
            }
        }
        return ringArea > 0 && ring.toFloat() / ringArea <= 0.02f
    }

    private fun evaluate(r: Region, c: Component, screenHeight: Int, minSize: Int): VisualCandidate? {
        val w = c.maxX - c.minX + 1
        val h = c.maxY - c.minY + 1
        if (w < minSize || h < minSize) return null
        val aspect = w.toFloat() / h
        if (aspect < 0.7f || aspect > 1.4f) return null
        val fill = c.pixels.size.toFloat() / (w * h)
        if (fill < 0.08f || fill > 0.55f) return null

        // 1) 거의 모든 픽셀이 두 대각선 중 하나 근처에 있어야 한다.
        var near = 0
        for (p in c.pixels) {
            val u = ((p % r.w) - c.minX + 0.5f) / w
            val v = ((p / r.w) - c.minY + 0.5f) / h
            if (minOf(abs(u - v), abs(u + v - 1f)) < 0.2f) near++
        }
        val nearRatio = near.toFloat() / c.pixels.size
        if (nearRatio < 0.85f) return null

        // 2) 두 대각선이 모두 끝까지 채워져 있어야 한다 (K, V, / 등 제외).
        val steps = (1..9).map { it / 10f }
        val diag1 = steps.count { t -> r.isFgNear(c.minX + t * w, c.minY + t * h) }
        val diag2 = steps.count { t -> r.isFgNear(c.minX + (1 - t) * w, c.minY + t * h) }
        if (diag1 < 8 || diag2 < 8) return null

        // 3) 변의 중앙은 비어 있어야 한다 (네모, 원, + 모양 제외).
        val edges = listOf(0.5f to 0.08f, 0.5f to 0.92f, 0.08f to 0.5f, 0.92f to 0.5f)
        if (edges.any { (u, v) -> r.isFg((c.minX + u * w).toInt(), (c.minY + v * h).toInt()) }) return null

        // 4) 주변에 다른 전경이 거의 없어야 한다 (글자 속 X 제외).
        if (!isIsolated(r, c.minX, c.minY, c.maxX, c.maxY)) return null

        val cx = r.x0 + (c.minX + c.maxX) / 2
        val cy = r.y0 + (c.minY + c.maxY) / 2
        if (cy < screenHeight * STATUS_BAR) return null
        return VisualCandidate(cx, cy, maxOf(w, h), nearRatio)
    }

    private class Component(val pixels: IntArray, val minX: Int, val minY: Int, val maxX: Int, val maxY: Int)

    /** 화면의 한 직사각형 영역을 전경/배경으로 이진화한 것. */
    private class Region(argb: IntArray, stride: Int, val x0: Int, val y0: Int, val w: Int, val h: Int, light: Boolean) {
        private val fg = BooleanArray(w * h)

        init {
            for (y in 0 until h) for (x in 0 until w) {
                val c = argb[(y0 + y) * stride + x0 + x]
                val lum = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                fg[y * w + x] = if (light) lum >= LIGHT_MIN else lum <= DARK_MAX
            }
        }

        fun isFg(x: Int, y: Int) = x in 0 until w && y in 0 until h && fg[y * w + x]

        fun isFgNear(fx: Float, fy: Float): Boolean {
            val x = fx.toInt()
            val y = fy.toInt()
            for (dy in -2..2) for (dx in -2..2) if (isFg(x + dx, y + dy)) return true
            return false
        }

        /** 8방향 연결 요소 중 긴 변이 [minSize, maxSize]인 것만 ('>' 한 획처럼 가는 모양도 포함). */
        fun components(minSize: Int, maxSize: Int): List<Component> {
            val seen = BooleanArray(w * h)
            val out = ArrayList<Component>()
            val stack = IntArray(w * h)
            for (start in fg.indices) {
                if (!fg[start] || seen[start]) continue
                var top = 0
                stack[top++] = start
                seen[start] = true
                val members = ArrayList<Int>()
                var minX = w; var minY = h; var maxX = 0; var maxY = 0
                while (top > 0) {
                    val p = stack[--top]
                    members += p
                    val px = p % w
                    val py = p / w
                    if (px < minX) minX = px
                    if (px > maxX) maxX = px
                    if (py < minY) minY = py
                    if (py > maxY) maxY = py
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = px + dx
                        val ny = py + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val n = ny * w + nx
                        if (fg[n] && !seen[n]) { seen[n] = true; stack[top++] = n }
                    }
                }
                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                if (maxOf(bw, bh) in minSize..maxSize && minOf(bw, bh) >= 3) {
                    out += Component(members.toIntArray(), minX, minY, maxX, maxY)
                }
            }
            return out
        }
    }
}
