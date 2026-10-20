package com.personal.autoskip

import com.personal.autoskip.detection.VisualCloseDetector
import com.personal.autoskip.detection.VisualKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.InputStream

/** 실기기(Galaxy S23 Ultra, 1080x2316)에서 캡처한 실제 광고 화면으로 검증한다. */
class VisualCloseDetectorTest {

    private companion object {
        const val FULL_HEIGHT = 2316
    }

    /**
     * 테스트 이미지는 판별에 필요한 위쪽 400행만 잘라 저장했다 (광고 소재/화면 내용 최소화).
     * 원래 화면 높이로 복원하고 나머지는 검게 채워 판별기에 넘긴다.
     */
    private fun detect(name: String) = javaClass.getResourceAsStream("/frames/$name")!!.use { input ->
        val (top, w, rows) = readImage(input)
        val argb = IntArray(w * FULL_HEIGHT) { 0xFF000000.toInt() }
        System.arraycopy(top, 0, argb, 0, w * rows)
        VisualCloseDetector.find(argb, w, FULL_HEIGHT)
    }

    /**
     * 단위 테스트는 android.jar로 컴파일되어 javax.imageio를 직접 참조할 수 없다.
     * 실행은 일반 JDK에서 되므로 리플렉션으로 읽는다.
     */
    private fun readImage(input: InputStream): Triple<IntArray, Int, Int> {
        val img = Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream::class.java).invoke(null, input)
        val cls = img.javaClass
        val w = cls.getMethod("getWidth").invoke(img) as Int
        val h = cls.getMethod("getHeight").invoke(img) as Int
        val getRgb = cls.getMethod(
            "getRGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            IntArray::class.java, Int::class.java, Int::class.java,
        )
        val argb = getRgb.invoke(img, 0, 0, w, h, null, 0, w) as IntArray
        return Triple(argb, w, h)
    }

    @Test fun applovinEndCardX() {
        val c = detect("applovin_endcard_with_x.png")
        assertNotNull(c)
        // 화면에서 X는 대략 (998, 164)에 있다.
        assertEquals(998.0, c!!.centerX.toDouble(), 15.0)
        assertEquals(164.0, c.centerY.toDouble(), 15.0)
    }

    @Test fun bidmachineEndCardGrayCircleX() {
        val c = detect("bidmachine_endcard_with_x.png")
        assertNotNull(c)
        // 노드 [997,120][1054,177]의 중심 근처
        assertEquals(1025.0, c!!.centerX.toDouble(), 15.0)
        assertEquals(148.0, c.centerY.toDouble(), 15.0)
    }

    @Test fun bidmachineDoubleChevronSkip() {
        // 영상/이미지 단계의 오른쪽 위 '»' (노드 [997,120][1054,177])
        for (name in listOf("bidmachine_video_no_x.png", "bidmachine_image_no_x.png", "bidmachine_video_next.png")) {
            val c = detect(name)
            assertNotNull(name, c)
            assertEquals(name, VisualKind.NEXT, c!!.kind)
            assertEquals(name, 1025.0, c.centerX.toDouble(), 20.0)
            assertEquals(name, 148.0, c.centerY.toDouble(), 20.0)
        }
    }

    @Test fun xIsReportedAsClose() {
        assertEquals(VisualKind.CLOSE, detect("applovin_endcard_with_x.png")!!.kind)
        assertEquals(VisualKind.CLOSE, detect("bidmachine_endcard_with_x.png")!!.kind)
    }

    @Test fun applovinWithoutX() {
        assertNull(detect("applovin_playing_no_x.png"))
        assertNull(detect("applovin_endcard_no_x.png"))
    }

    @Test fun molocoSkipIconIsNotAnX() {
        assertNull(detect("moloco_skip_icon.png"))
    }

    @Test fun syntheticShapes() {
        val w = 1080
        val h = 2316
        fun frame(draw: (IntArray) -> Unit) = IntArray(w * h) { 0xFF202020.toInt() }.also(draw)
        fun dot(a: IntArray, x: Int, y: Int) { for (dy in -2..2) for (dx in -2..2) a[(y + dy) * w + x + dx] = -1 }
        // X (40px) → 검출
        val x = frame { a -> for (i in 0..40) { dot(a, 980 + i, 150 + i); dot(a, 1020 - i, 150 + i) } }
        assertNotNull(VisualCloseDetector.find(x, w, h))
        // + 모양 → 미검출
        val plus = frame { a -> for (i in 0..40) { dot(a, 1000, 150 + i); dot(a, 980 + i, 170) } }
        assertNull(VisualCloseDetector.find(plus, w, h))
        // 화면 가운데 X → 미검출 (모서리만 본다)
        val center = frame { a -> for (i in 0..40) { dot(a, 520 + i, 1100 + i); dot(a, 560 - i, 1100 + i) } }
        assertNull(VisualCloseDetector.find(center, w, h))
        // '>' 하나(본문 버튼의 화살표 등) → 미검출, '»' → NEXT
        fun chevron(a: IntArray, x0: Int) { for (i in 0..18) { dot(a, x0 + i / 2, 140 + i); dot(a, x0 + i / 2, 176 - i) } }
        val single = frame { a -> chevron(a, 1000) }
        assertNull(VisualCloseDetector.find(single, w, h))
        val double = frame { a -> chevron(a, 995); chevron(a, 1015) }
        assertEquals(VisualKind.NEXT, VisualCloseDetector.find(double, w, h)?.kind)
        // 상태 표시줄 높이의 X → 미검출
        val status = frame { a -> for (i in 0..30) { dot(a, 980 + i, 20 + i); dot(a, 1010 - i, 20 + i) } }
        assertNull(VisualCloseDetector.find(status, w, h))
    }
}
