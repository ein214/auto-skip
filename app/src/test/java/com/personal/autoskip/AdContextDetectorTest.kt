package com.personal.autoskip

import com.personal.autoskip.detection.AdContextDetector
import com.personal.autoskip.detection.HostScreenDetector
import com.personal.autoskip.detection.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdContextDetectorTest {

    @Test fun knownSdkActivities() {
        assertTrue(AdContextDetector.isAdActivity("com.google.android.gms.ads.AdActivity"))
        assertTrue(AdContextDetector.isAdActivity("com.unity3d.services.ads.adunit.AdUnitActivity"))
        assertTrue(AdContextDetector.isAdActivity("com.applovin.adview.AppLovinFullscreenActivity"))
        // 실기기 뮤니버스에서 관찰된 광고 화면들
        assertTrue(AdContextDetector.isAdActivity("io.bidmachine.rendering.ad.fullscreen.FullScreenActivity"))
        assertTrue(AdContextDetector.isAdActivity("com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.mraid.MraidActivity"))
        // 'ad' 구간 규칙
        assertTrue(AdContextDetector.isAdActivity("com.unknown.sdk.ads.player.PlayerActivity"))
        assertFalse(AdContextDetector.isAdActivity("com.tnk.muniverse.MainActivity"))
        assertFalse(AdContextDetector.isAdActivity("com.host.address.AddressActivity"))
        assertFalse(AdContextDetector.isAdActivity("com.host.ad.AdHelper"))
    }

    @Test fun case9_samePackageAdActivityByName() {
        assertTrue(AdContextDetector.isAdActivity("com.personal.autoskip.mockhost.MockAdActivity"))
        assertTrue(AdContextDetector.isAdActivity("com.host.RewardedActivity"))
        assertFalse(AdContextDetector.isAdActivity("com.personal.autoskip.mockhost.HostActivity"))
        assertFalse(AdContextDetector.isAdActivity("com.host.ReadActivity"))
        assertFalse(AdContextDetector.isAdActivity("com.host.LoadActivity"))
        assertFalse(AdContextDetector.isAdActivity(null))
    }

    @Test fun anyThirdPartyScreenInHostIsAd() {
        val host = listOf("com.tnk.muniverse")
        // 목록에 없는 새 광고 SDK도 호스트 밖 화면이면 광고
        assertTrue(AdContextDetector.isAdScreen("com.newadnetwork.render.PlayerActivity", host))
        assertTrue(AdContextDetector.isAdScreen("io.bidmachine.rendering.ad.fullscreen.FullScreenActivity", host))
        // 호스트 자체 화면은 광고 아님
        assertFalse(AdContextDetector.isAdScreen("com.tnk.muniverse.MainActivity", host))
        assertFalse(AdContextDetector.isAdScreen("com.tnk.muniverse.ui.charge.ChargeActivity", host))
        // 로그인/결제/인앱 브라우저 라이브러리는 광고 아님
        assertFalse(AdContextDetector.isAdScreen("com.google.android.gms.auth.api.signin.internal.SignInHubActivity", host))
        assertFalse(AdContextDetector.isAdScreen("com.android.billingclient.api.ProxyBillingActivity", host))
        assertFalse(AdContextDetector.isAdScreen("com.kakao.sdk.auth.AuthCodeHandlerActivity", host))
        assertFalse(AdContextDetector.isAdScreen("androidx.browser.customtabs.CustomTabActivity", host))
        // 호스트 네임스페이스를 모르면 알려진 광고 규칙만 적용
        assertFalse(AdContextDetector.isAdScreen("com.newadnetwork.render.PlayerActivity", emptyList()))
    }

    @Test fun hostScreen() {
        assertTrue(HostScreenDetector.isHostScreen(true, "com.host.MainActivity"))
        assertFalse(HostScreenDetector.isHostScreen(true, "com.google.android.gms.ads.AdActivity"))
        assertFalse(HostScreenDetector.isHostScreen(false, "com.host.MainActivity"))
        assertFalse(HostScreenDetector.isHostScreen(true, "com.other.sdk.PlayerActivity", listOf("com.host")))
        assertTrue(HostScreenDetector.isHostScreen(true, "com.host.MainActivity", listOf("com.host")))
    }

    @Test fun sensitiveScreens() {
        assertTrue(AdContextDetector.isSensitive(listOf(ScreenNode(text = "결제하기"))))
        assertTrue(AdContextDetector.isSensitive(listOf(ScreenNode(text = "Enter password"))))
        assertFalse(AdContextDetector.isSensitive(listOf(ScreenNode(text = "결제하기", visible = false))))
        assertFalse(AdContextDetector.isSensitive(listOf(ScreenNode(text = "영상보고 ⭐ 5 받기"))))
    }

    @Test fun storeButtonsForHighlight() {
        fun n(text: String, visible: Boolean = true) = ScreenNode(text = text, visible = visible, right = 100, bottom = 50)
        val found = AdContextDetector.findStoreButtons(
            listOf(n("Google Play"), n("Open Store"), n("받기"), n("설치"), n("Dragon Village 3"), n("Install", visible = false),
                n("Collect & breed pixel dragons! Real-time PvP, build your village. Download now"), n("⭐ 모의 RPG — 지금 설치하고 보상 받기")),
        ).map { it.text }
        assertEquals(listOf("Google Play", "Open Store", "받기", "설치"), found)
    }

    @Test fun endCard() {
        assertTrue(AdContextDetector.looksLikeEndCard(listOf(ScreenNode(text = "설치하기", clickable = true, clickTargetIndex = 0))))
        assertFalse(AdContextDetector.looksLikeEndCard(listOf(ScreenNode(text = "설치하기"))))
    }
}
