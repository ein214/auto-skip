package com.personal.autoskip

import com.personal.autoskip.detection.CooldownParser
import com.personal.autoskip.detection.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CooldownParserTest {
    private fun n(text: String, top: Int) = ScreenNode(text = text, top = top, bottom = top + 50, right = 500)

    @Test fun parseFormats() {
        assertEquals(695, CooldownParser.parseSeconds("6 팬 포인트 받기 | 695초"))
        assertEquals(750, CooldownParser.parseSeconds("12분 30초"))
        assertEquals(299, CooldownParser.parseSeconds("오늘 17회 남음 04:59"))
        assertEquals(3723, CooldownParser.parseSeconds("01:02:03"))
        assertEquals(3600, CooldownParser.parseSeconds("1시간"))
        assertNull(CooldownParser.parseSeconds("광고 3/3"))
        assertNull(CooldownParser.parseSeconds("6 팬 포인트 받기"))
    }

    @Test fun tinFanPointRow() {
        // 실기기 TIN '팬 포인트' 화면: 제목과 시간이 같은 줄의 다른 노드
        val nodes = listOf(n("일일 포스트 미션", 1500), n("동영상 광고 시청하기", 1750), n("6 팬 포인트 받기 | 695초", 1810), n("1/20", 1760))
        assertEquals(695, CooldownParser.findRemainingSeconds(nodes))
    }

    @Test fun muniverseButton() {
        val nodes = listOf(
            n("출석하고 100 받기 1일 1회 13:40:18", 571),
            n("영상보고 5 받기 오늘 17회 남음 04:59", 1217),
        )
        assertEquals(299, CooldownParser.findRemainingSeconds(nodes))
    }

    @Test fun readyOrExhaustedOrUnrelated() {
        // 00:00 = 이미 볼 수 있음
        assertNull(CooldownParser.findRemainingSeconds(listOf(n("영상보고 5 받기 오늘 17회 남음 00:00", 1217))))
        // 하루 소진
        assertNull(CooldownParser.findRemainingSeconds(listOf(n("영상보고 5 받기 오늘 0회 남음 10:20:00", 1217))))
        // 광고와 무관한 출석 타이머 (멀리 떨어져 있음)
        assertNull(CooldownParser.findRemainingSeconds(listOf(n("출석하고 100 받기 1일 1회 13:40:18", 571), n("애드 보기", 1400))))
        // 3시간 초과
        assertNull(CooldownParser.findRemainingSeconds(listOf(n("광고 보기 05:00:00", 100))))
    }
}
