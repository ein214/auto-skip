package com.personal.autoskip

import com.personal.autoskip.detection.CloseButtonMatcher
import com.personal.autoskip.detection.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloseButtonMatcherTest {

    private fun button(text: String? = null, desc: String? = null, id: String? = null, enabled: Boolean = true, visible: Boolean = true, top: Int = 0) =
        ScreenNode(text = text, desc = desc, viewId = id, clickable = true, enabled = enabled, visible = visible,
            left = 0, top = top, right = 100, bottom = top + 100, clickTargetIndex = null)

    /** 각 노드를 자기 자신이 클릭 대상이 되도록 인덱스를 채운다. */
    private fun screen(vararg nodes: ScreenNode) = nodes.mapIndexed { i, n -> n.copy(clickTargetIndex = if (n.clickable) i else null) }

    @Test fun skipKoreanAndEnglish() {
        assertEquals("건너뛰기", CloseButtonMatcher.find(screen(button("건너뛰기")))?.label)
        assertEquals("SKIP", CloseButtonMatcher.find(screen(button("SKIP")))?.label)
        assertEquals("Skip ▶", CloseButtonMatcher.find(screen(button("Skip ▶")))?.label)
        assertEquals("Done", CloseButtonMatcher.find(screen(button("Done")))?.label)
    }

    @Test fun imageButtonByDescriptionOrViewId() {
        assertEquals("Close", CloseButtonMatcher.find(screen(button(desc = "Close")))?.label)
        assertEquals("com.ad:id/btnClose", CloseButtonMatcher.find(screen(button(id = "com.ad:id/btnClose")))?.label)
        assertEquals("X", CloseButtonMatcher.find(screen(button("X")))?.label)
    }

    @Test fun case5_disabledOrInvisibleIsIgnored() {
        assertNull(CloseButtonMatcher.find(screen(button("건너뛰기", enabled = false))))
        assertNull(CloseButtonMatcher.find(screen(button("Skip", visible = false))))
        assertNull(CloseButtonMatcher.find(screen(button("Skip").copy(right = 0)))) // 크기 없음
    }

    @Test fun countdownIsNotReady() {
        assertNull(CloseButtonMatcher.find(screen(button("5초 후 건너뛰기"))))
        assertNull(CloseButtonMatcher.find(screen(button("Skip in 3"))))
    }

    @Test fun installCtaIsNeverMatched() {
        assertNull(CloseButtonMatcher.find(screen(button("설치하기", id = "com.ad:id/cta_install"))))
        assertNull(CloseButtonMatcher.find(screen(button("Install"))))
        assertNull(CloseButtonMatcher.find(screen(button("Close and open Google Play"))))
        assertNull(CloseButtonMatcher.find(screen(button(desc = "close", id = "com.ad:id/install_close"))))
    }

    @Test fun nonClickableTextWithoutClickableAncestorIsIgnored() {
        assertNull(CloseButtonMatcher.find(listOf(ScreenNode(text = "Skip", right = 10, bottom = 10))))
    }

    @Test fun textInsideClickableParentClicksParent() {
        val parent = ScreenNode(clickable = true, right = 100, bottom = 100, clickTargetIndex = 0)
        val label = ScreenNode(text = "닫기", right = 50, bottom = 50, clickTargetIndex = 0)
        val c = CloseButtonMatcher.find(listOf(parent, label))!!
        assertEquals(1, c.nodeIndex)
        assertEquals(0, c.clickIndex)
    }

    @Test fun strongerMatchWins() {
        val c = CloseButtonMatcher.find(screen(button("X", top = 0), button("Skip", id = "com.ad:id/skip_button", top = 500)))!!
        assertEquals("Skip", c.label)
    }

    @Test fun longSentencesAreNotButtons() {
        assertNull(CloseButtonMatcher.find(screen(button("Close friends only"))))
    }

    @Test fun viewIdTokens() {
        assertEquals(listOf("btn", "close", "ad"), CloseButtonMatcher.viewIdTokens("com.x:id/btnCloseAd"))
        assertEquals(listOf("closed", "caption"), CloseButtonMatcher.viewIdTokens("com.x:id/closed_caption"))
        assertNull(CloseButtonMatcher.find(screen(button(id = "com.x:id/closed_caption"))))
    }
}
