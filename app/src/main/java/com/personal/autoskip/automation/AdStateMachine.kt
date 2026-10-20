package com.personal.autoskip.automation

import com.personal.autoskip.automation.AdState.*
import com.personal.autoskip.automation.ScreenKind.*

/**
 * 순수 상태 전이 함수. 부수효과(클릭, 타이머)는 [AdSessionController]가 담당한다.
 * 기획서 §2와 다른 점은 주석으로 표시한다.
 */
object AdStateMachine {

    fun next(state: AdState, event: AdEvent): AdState {
        when (event) {
            AdEvent.UserPause -> return PAUSED
            AdEvent.UserResume -> return if (state == PAUSED) IDLE else state
            else -> Unit
        }
        if (state == PAUSED) return PAUSED

        if (event == AdEvent.Timeout) return if (state == IDLE) IDLE else UNKNOWN

        return when (event) {
            AdEvent.CloseVerified -> if (state == CLOSE_READY) AUTO_CLOSE else state
            AdEvent.ClickSent -> if (state == AUTO_CLOSE) VERIFY_HOST else UNKNOWN
            // 기획서 v0.2 이후 결정: 다단계 광고(Skip → 엔드카드 → X)는 클릭 상한 안에서 한 단계 더 진행한다.
            AdEvent.NextAdStep -> if (state == VERIFY_HOST) AD_PLAYING else state
            is AdEvent.ClickFailed -> if (event.retry) AD_PLAYING else UNKNOWN
            is AdEvent.Screen -> onScreen(state, event.kind)
            else -> state
        }
    }

    private fun onScreen(state: AdState, kind: ScreenKind): AdState {
        // 결제/인증 화면, 식별 실패: 광고 세션 중이면 즉시 안전 정지.
        if (kind == SENSITIVE || kind == UNIDENTIFIED) {
            return if (state == IDLE) IDLE else UNKNOWN
        }
        return when (state) {
            IDLE -> if (kind.isAd()) AD_DETECTED else IDLE

            AD_DETECTED, AD_PLAYING -> when (kind) {
                AD_CLOSE_READY -> CLOSE_READY
                AD_END_CARD -> END_CARD
                AD_NO_CLOSE -> AD_PLAYING
                HOST -> RETURNED_TO_HOST
                // 기획서는 END_CARD에서만 스토어 이동을 정의하지만, 재생 중 광고를 눌러 나가는 경우도 같은 방식으로 추적한다.
                EXTERNAL -> EXTERNAL_STORE
                else -> UNKNOWN
            }

            END_CARD -> when (kind) {
                AD_CLOSE_READY -> CLOSE_READY
                AD_END_CARD, AD_NO_CLOSE -> END_CARD
                EXTERNAL -> EXTERNAL_STORE
                HOST -> RETURNED_TO_HOST
                else -> UNKNOWN
            }

            // 외부 앱에 있는 동안은 아무것도 하지 않는다. 광고로 돌아오면 다시 검사.
            EXTERNAL_STORE -> when (kind) {
                AD_CLOSE_READY, AD_END_CARD, AD_NO_CLOSE -> RETURNED_TO_AD
                HOST -> RETURNED_TO_HOST
                else -> EXTERNAL_STORE
            }

            RETURNED_TO_AD, WAITING_FOR_CLOSE -> when (kind) {
                AD_CLOSE_READY -> CLOSE_READY
                AD_END_CARD, AD_NO_CLOSE -> WAITING_FOR_CLOSE
                EXTERNAL -> EXTERNAL_STORE
                HOST -> RETURNED_TO_HOST
                else -> UNKNOWN
            }

            CLOSE_READY -> when (kind) {
                AD_CLOSE_READY -> CLOSE_READY
                AD_END_CARD, AD_NO_CLOSE -> AD_PLAYING // 버튼이 사라짐 = 검증 실패
                EXTERNAL -> EXTERNAL_STORE
                HOST -> RETURNED_TO_HOST
                else -> UNKNOWN
            }

            // 클릭 중에는 화면 변화를 무시하고 클릭 결과 이벤트를 기다린다.
            AUTO_CLOSE -> AUTO_CLOSE

            VERIFY_HOST -> when (kind) {
                HOST -> RETURNED_TO_HOST
                // 클릭 후 외부 앱이 열렸다면 잘못 누른 것일 수 있으므로 정지.
                EXTERNAL -> UNKNOWN
                // 광고 화면이 아직 남아 있으면 닫히는 중일 수 있으므로 타임아웃까지 기다린다.
                else -> VERIFY_HOST
            }

            RETURNED_TO_HOST -> if (kind.isAd()) AD_DETECTED else IDLE

            // 기획서에 UNKNOWN의 출구가 없어, 호스트 화면이 확인되면 IDLE로 돌아가도록 했다 (클릭 없음).
            UNKNOWN -> if (kind == HOST) IDLE else UNKNOWN

            PAUSED -> PAUSED
        }
    }

    private fun ScreenKind.isAd() = this == AD_NO_CLOSE || this == AD_END_CARD || this == AD_CLOSE_READY
}
