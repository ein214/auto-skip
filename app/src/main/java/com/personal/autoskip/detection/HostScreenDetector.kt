package com.personal.autoskip.detection

/** 광고가 닫히고 허용 앱의 일반(호스트) 화면으로 돌아왔는지 판단한다. */
object HostScreenDetector {
    fun isHostScreen(packageAllowed: Boolean, activityClass: String?, hostNamespaces: List<String> = emptyList()): Boolean =
        packageAllowed && activityClass != null && !AdContextDetector.isAdScreen(activityClass, hostNamespaces)
}
