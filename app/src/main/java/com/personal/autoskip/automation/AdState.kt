package com.personal.autoskip.automation

/** 기획서 §2 상태 머신의 상태. */
enum class AdState {
    IDLE,
    AD_DETECTED,
    AD_PLAYING,
    END_CARD,
    EXTERNAL_STORE,
    RETURNED_TO_AD,
    WAITING_FOR_CLOSE,
    CLOSE_READY,
    AUTO_CLOSE,
    VERIFY_HOST,
    RETURNED_TO_HOST,
    PAUSED,
    UNKNOWN,
}

/** 한 번의 화면 관찰 결과를 분류한 것. */
enum class ScreenKind {
    HOST,              // 허용 앱의 광고가 아닌 화면
    AD_NO_CLOSE,       // 광고 화면, 종료 버튼 없음
    AD_END_CARD,       // 광고 화면, 설치 유도 엔드카드 (종료 버튼 없음)
    AD_CLOSE_READY,    // 광고 화면, 명확한 종료 버튼 활성화
    EXTERNAL,          // 허용 목록 밖 앱 (Play 스토어 등)
    SENSITIVE,         // 결제/인증/로그인으로 보이는 화면
    UNIDENTIFIED,      // 화면 식별 실패
}

sealed interface AdEvent {
    data class Screen(val kind: ScreenKind) : AdEvent
    /** 클릭 직전 재탐색에서 같은 버튼이 여전히 유효함을 확인했다. */
    data object CloseVerified : AdEvent
    data object ClickSent : AdEvent
    /** [retry]가 false면 더 이상 시도하지 않는다. */
    data class ClickFailed(val retry: Boolean) : AdEvent
    data object Timeout : AdEvent
    /** 클릭 후 호스트로 돌아가지 않고 같은 광고의 다음 단계(예: Skip → 엔드카드)가 나왔다. */
    data object NextAdStep : AdEvent
    data object UserPause : AdEvent
    data object UserResume : AdEvent
}
