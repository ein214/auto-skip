package com.personal.autoskip

import com.personal.autoskip.automation.AdEvent
import com.personal.autoskip.automation.AdState
import com.personal.autoskip.automation.AdState.*
import com.personal.autoskip.automation.AdStateMachine
import com.personal.autoskip.automation.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AdStateMachineTest {

    private fun run(start: AdState, vararg events: AdEvent): List<AdState> {
        var s = start
        return events.map { s = AdStateMachine.next(s, it); s }
    }

    private fun screen(k: ScreenKind) = AdEvent.Screen(k)

    @Test fun case1_directClose() {
        assertEquals(
            listOf(AD_DETECTED, AD_PLAYING, CLOSE_READY, AUTO_CLOSE, VERIFY_HOST, RETURNED_TO_HOST, IDLE),
            run(IDLE, screen(ScreenKind.AD_NO_CLOSE), screen(ScreenKind.AD_NO_CLOSE), screen(ScreenKind.AD_CLOSE_READY),
                AdEvent.CloseVerified, AdEvent.ClickSent, screen(ScreenKind.HOST), screen(ScreenKind.HOST)),
        )
    }

    @Test fun case2_delayedButtonDoesNotClickOnTimeAlone() {
        // 버튼이 없는 동안은 AD_PLAYING에 머문다. 시간이 지나도 클릭 이벤트는 생기지 않는다.
        assertEquals(listOf(AD_PLAYING, AD_PLAYING, CLOSE_READY),
            run(AD_PLAYING, screen(ScreenKind.AD_NO_CLOSE), screen(ScreenKind.AD_NO_CLOSE), screen(ScreenKind.AD_CLOSE_READY)))
    }

    @Test fun case3_storeRoundTrip() {
        assertEquals(
            listOf(END_CARD, EXTERNAL_STORE, EXTERNAL_STORE, RETURNED_TO_AD, WAITING_FOR_CLOSE, CLOSE_READY),
            run(AD_PLAYING, screen(ScreenKind.AD_END_CARD), screen(ScreenKind.EXTERNAL), screen(ScreenKind.EXTERNAL),
                screen(ScreenKind.AD_END_CARD), screen(ScreenKind.AD_END_CARD), screen(ScreenKind.AD_CLOSE_READY)),
        )
    }

    @Test fun externalStoreNeverLeadsToClick() {
        // 외부 앱에서는 CLOSE_READY로 갈 수 없다.
        assertEquals(EXTERNAL_STORE, AdStateMachine.next(EXTERNAL_STORE, AdEvent.CloseVerified))
        assertEquals(EXTERNAL_STORE, AdStateMachine.next(EXTERNAL_STORE, screen(ScreenKind.EXTERNAL)))
    }

    @Test fun case4_hostScreenNeverStartsSession() {
        assertEquals(IDLE, AdStateMachine.next(IDLE, screen(ScreenKind.HOST)))
        assertEquals(IDLE, AdStateMachine.next(IDLE, AdEvent.CloseVerified))
    }

    @Test fun case6_screenEventsDuringVerifyDoNotTriggerAnotherClick() {
        assertEquals(VERIFY_HOST, AdStateMachine.next(VERIFY_HOST, screen(ScreenKind.AD_CLOSE_READY)))
        assertEquals(VERIFY_HOST, AdStateMachine.next(VERIFY_HOST, AdEvent.CloseVerified))
        assertEquals(AUTO_CLOSE, AdStateMachine.next(AUTO_CLOSE, screen(ScreenKind.AD_CLOSE_READY)))
    }

    @Test fun case7_unexpectedScreensStopSafely() {
        assertEquals(UNKNOWN, AdStateMachine.next(AD_PLAYING, screen(ScreenKind.SENSITIVE)))
        assertEquals(UNKNOWN, AdStateMachine.next(CLOSE_READY, screen(ScreenKind.UNIDENTIFIED)))
        assertEquals(UNKNOWN, AdStateMachine.next(VERIFY_HOST, AdEvent.Timeout))
        assertEquals(UNKNOWN, AdStateMachine.next(VERIFY_HOST, screen(ScreenKind.EXTERNAL)))
        assertEquals(UNKNOWN, AdStateMachine.next(AUTO_CLOSE, AdEvent.ClickFailed(retry = false)))
        assertEquals(AD_PLAYING, AdStateMachine.next(AUTO_CLOSE, AdEvent.ClickFailed(retry = true)))
        // UNKNOWN에서는 광고 화면이 보여도 다시 시작하지 않는다. 호스트 화면을 확인해야 IDLE.
        assertEquals(UNKNOWN, AdStateMachine.next(UNKNOWN, screen(ScreenKind.AD_CLOSE_READY)))
        assertEquals(IDLE, AdStateMachine.next(UNKNOWN, screen(ScreenKind.HOST)))
    }

    @Test fun case8_userPauseFromAnyState() {
        AdState.entries.forEach { assertEquals(PAUSED, AdStateMachine.next(it, AdEvent.UserPause)) }
        assertEquals(PAUSED, AdStateMachine.next(PAUSED, screen(ScreenKind.AD_CLOSE_READY)))
        assertEquals(IDLE, AdStateMachine.next(PAUSED, AdEvent.UserResume))
    }

    @Test fun multiStepAdContinuesOnlyFromVerifyHost() {
        assertEquals(AD_PLAYING, AdStateMachine.next(VERIFY_HOST, AdEvent.NextAdStep))
        assertEquals(CLOSE_READY, AdStateMachine.next(AD_PLAYING, screen(ScreenKind.AD_CLOSE_READY)))
        assertEquals(IDLE, AdStateMachine.next(IDLE, AdEvent.NextAdStep))
        assertEquals(UNKNOWN, AdStateMachine.next(UNKNOWN, AdEvent.NextAdStep))
        assertEquals(PAUSED, AdStateMachine.next(PAUSED, AdEvent.NextAdStep))
    }

    @Test fun closeReadyButtonVanished() {
        assertEquals(AD_PLAYING, AdStateMachine.next(CLOSE_READY, screen(ScreenKind.AD_NO_CLOSE)))
    }
}
