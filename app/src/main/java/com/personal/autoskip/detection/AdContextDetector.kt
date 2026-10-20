package com.personal.autoskip.detection

/**
 * 지금 화면이 광고 화면인지 판단한다.
 *
 * 광고 SDK는 호스트 앱과 같은 패키지 안에서 Activity를 띄우므로 패키지명이 아니라
 * 전경 Activity 클래스명으로 판별한다. 호스트 Activity 안에 그려지는 광고(오버레이/뷰)는
 * 판별할 수 없으므로 의도적으로 광고로 보지 않는다(= 클릭하지 않는다).
 */
object AdContextDetector {

    // 주요 광고 SDK의 전체 화면 Activity 패키지 접두사.
    private val AD_SDK_PREFIXES = listOf(
        "com.google.android.gms.ads.",
        "com.unity3d.",
        "com.applovin.",
        "com.vungle.",
        "com.ironsource.",
        "com.facebook.ads.",
        "com.mbridge.",
        "com.bytedance.sdk.openadsdk.",
        "com.inmobi.",
        "com.fyber.",
        "com.chartboost.",
        "com.adcolony.",
        "com.tapjoy.",
        "com.kakao.adfit.",
        "com.naver.gfpsdk.",
        "com.moloco.",
        "com.pangle.",
        "io.bidmachine.",       // 실기기 뮤니버스에서 확인 (io.bidmachine.rendering.ad.fullscreen.FullScreenActivity)
        "com.my.target.",
        "sg.bigo.ads.",
        "com.yandex.mobile.ads.",
        "com.smaato.",
        "com.ogury.",
        "net.pubnative.",
        "com.criteo.",
        "com.amazon.device.ads.",
    )
    // 패키지 경로에 'ad'/'ads' 구간이 있는 Activity (예: x.y.ad.fullscreen.FullScreenActivity).
    private val AD_PACKAGE_SEGMENT = Regex("\\.(ad|ads)\\.")
    // 접두사 목록에 없는 SDK 대비: 클래스 단순 이름이 ...AdActivity 등으로 끝나는 경우 (대소문자 구분: ReadActivity 제외).
    private val AD_ACTIVITY_NAME = Regex("(Ad|Ads|Interstitial|Rewarded|RewardedVideo)Activity$")

    // 이런 문구가 보이면 결제/인증/로그인 화면일 수 있으므로 아무것도 누르지 않는다.
    private val SENSITIVE = Regex(
        "(?i)(결제|비밀번호|인증번호|카드\\s?번호|로그인|본인\\s?인증|password|passcode|payment|checkout|card number|\\bOTP\\b|sign in|log in)",
    )
    // 설치 유도 엔드카드 문구.
    private val INSTALL_CTA = Regex("(?i)(설치|install|다운로드|download|google play)")

    // 호스트 앱 밖의 라이브러리 화면이지만 광고가 아닌 것 (로그인, 결제, 인앱 브라우저, 인앱 업데이트/리뷰 등).
    private val NON_AD_SDK_PREFIXES = listOf(
        "com.google.android.gms.auth.",
        "com.google.android.gms.signin.",
        "com.google.android.gms.common.",
        "com.google.android.play.core.",
        "com.google.firebase.",
        "com.android.billingclient.",
        "androidx.browser.",
        "com.kakao.sdk.",
        "com.navercorp.nid.",
        "com.nhn.android.naverlogin.",
        "com.facebook.CustomTab",
        "com.facebook.FacebookActivity",
        "com.facebook.login.",
    )

    /**
     * 광고 화면인지 판정한다.
     * 1) 알려진 광고 SDK/이름 규칙에 맞으면 광고.
     * 2) 그 외에도 호스트 앱 자체 코드가 아닌(= [hostNamespaces] 밖의) 라이브러리 화면이면 광고로 본다.
     *    광고 SDK마다 목록을 늘리지 않기 위한 규칙이며, 로그인/결제 등 [NON_AD_SDK_PREFIXES]는 제외한다.
     */
    fun isAdScreen(className: String?, hostNamespaces: List<String>): Boolean {
        if (className.isNullOrEmpty()) return false
        if (NON_AD_SDK_PREFIXES.any { className.startsWith(it) }) return false
        if (isAdActivity(className)) return true
        if (hostNamespaces.isEmpty()) return false
        return hostNamespaces.none { className.startsWith("$it.") }
    }

    fun isAdActivity(className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        if (AD_SDK_PREFIXES.any { className.startsWith(it) }) return true
        if (AD_PACKAGE_SEGMENT.containsMatchIn(className.substringBeforeLast('.') + ".") && className.endsWith("Activity")) return true
        return AD_ACTIVITY_NAME.containsMatchIn(className.substringAfterLast('.'))
    }

    fun isSensitive(nodes: List<ScreenNode>): Boolean =
        nodes.any { n -> n.visible && listOfNotNull(n.text, n.desc).any { SENSITIVE.containsMatchIn(it) } }

    // 사용자가 직접 눌러 스토어로 이동하는 버튼 문구 (화면 표시용. AutoSkip이 누르지는 않는다).
    private val STORE_BUTTON = Regex("(?i)(google ?play|open ?store|install|download|설치|다운로드|받기|스토어)")

    /** 화면에 보이는 스토어 이동 버튼들. 강조 표시에만 쓴다. */
    fun findStoreButtons(nodes: List<ScreenNode>): List<ScreenNode> =
        nodes.filter { n ->
            n.visible && n.hasArea && listOfNotNull(n.text, n.desc).any { it.trim().length <= 15 && STORE_BUTTON.containsMatchIn(it) }
        }

    fun looksLikeEndCard(nodes: List<ScreenNode>): Boolean =
        nodes.any { n -> n.visible && n.enabled && n.clickTargetIndex != null && listOfNotNull(n.text, n.desc).any { INSTALL_CTA.containsMatchIn(it) } }
}
