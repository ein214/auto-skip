package com.personal.autoskip.automation

import android.os.Handler
import android.os.SystemClock
import kotlin.math.abs
import com.personal.autoskip.accessibility.ClickExecutor
import com.personal.autoskip.accessibility.ScreenSnapshot
import com.personal.autoskip.automation.AdState.*
import com.personal.autoskip.detection.AdContextDetector
import com.personal.autoskip.detection.CloseButtonMatcher
import com.personal.autoskip.detection.CloseCandidate
import com.personal.autoskip.detection.HostScreenDetector
import com.personal.autoskip.detection.ScreenFrame
import com.personal.autoskip.detection.VisualCandidate
import com.personal.autoskip.detection.VisualCloseDetector
import com.personal.autoskip.detection.VisualKind

/**
 * 상태 머신을 구동하고 부수효과(화면 탐색 예약, 클릭, 타이머)를 실행한다.
 * 모든 메서드는 메인 스레드에서 호출된다.
 */
class AdSessionController(
    private val handler: Handler,
    private val isAllowed: (String) -> Boolean,
    /** 허용 앱 자체 코드의 패키지 경로들 (예: com.tnk.muniverse). 이 밖의 라이브러리 화면을 광고 후보로 본다. */
    private val hostNamespaces: (String) -> List<String> = { listOf(it) },
    /** 현재 활성 창을 탐색한다. 허용 목록 밖 앱이면 노드 없이 패키지명만 담아 돌려준다. */
    private val scanActiveWindow: () -> ScreenSnapshot?,
    private val onAdClosed: () -> Unit,
    private val log: (String) -> Unit,
    /** 디버그 빌드 전용: 광고 화면의 노드 목록을 logcat에 남긴다 (실제 광고의 노드 노출 여부 확인용). */
    private val dumpAdNodes: Boolean = false,
    /** 화면 이미지를 캡처해 메인 스레드에서 돌려준다. 실패/미지원이면 null. */
    private val captureScreen: ((ScreenFrame?) -> Unit) -> Unit = { it(null) },
    /** 화면 좌표 탭. 이미지로 찾은 X에만 쓴다. */
    private val tapAt: (Int, Int) -> Boolean = { _, _ -> false },
    /** 시스템 뒤로가기. 광고에서 넘어간 Play 스토어에서 돌아올 때만 쓴다. */
    private val goBack: () -> Boolean = { false },
    /** 스토어 이동 버튼 위치 강조 (left, top, right, bottom). 빈 목록이면 숨김. 누르는 것은 사용자다. */
    private val highlightStoreButtons: (List<IntArray>) -> Unit = {},
    /** 허용 앱의 호스트(광고 아닌) 화면을 탐색했다. 쿨타임 읽기에 쓴다. */
    private val onHostScreen: (String, List<com.personal.autoskip.detection.ScreenNode>) -> Unit = { _, _ -> },
) {
    var state: AdState = PAUSED
        private set

    private var foregroundPackage: String? = null
    private var foregroundActivity: String? = null
    private var foregroundWindowId: Int? = null
    /** 허용 목록 밖에서 마지막으로 전경에 올라온 앱 (Activity 전환이 아닌 창 변화도 포함). */
    private var externalPackage: String? = null
    private var pendingCandidate: CloseCandidate? = null
    /** 노드가 없는 광고에서 이미지로 찾은 X. [pendingCandidate]가 있으면 노드 쪽을 우선한다. */
    private var pendingVisual: VisualCandidate? = null
    private var captureInFlight = false
    private var clicksThisSession = 0
    private var storeReturnsThisSession = 0
    /** '»'(다음 단계) 탭 횟수. 닫기 클릭과 따로 센다 — 아직 넘길 수 없는 시점의 헛누름이 닫기 횟수를 소진하지 않도록. */
    private var nextTapsThisSession = 0
    private var lastTapWasNext = false
    private var lastClickAt = 0L
    private var lastDumped: List<String>? = null
    /** 직전에 노드 클릭한 버튼 (label + 위치). 같은 버튼이 그대로 남아 있으면 다음에는 좌표 탭으로 누른다. */
    private var lastNodeClick: String? = null

    private val scanRunnable = Runnable { observe() }
    private val confirmRunnable = Runnable { confirmAndClick() }
    private val timeoutRunnable = Runnable { dispatch(AdEvent.Timeout) }
    private val verifyTimeoutRunnable = Runnable { onVerifyTimeout() }
    private val visualRunnable = Runnable { visualTick() }
    private val storeReturnRunnable = Runnable { returnFromStore() }

    fun setActive(active: Boolean) {
        dispatch(if (active) AdEvent.UserResume else AdEvent.UserPause)
    }

    /** 전경 Activity가 바뀌었다 (대화상자/팝업 창은 해당 없음). */
    fun onActivityChanged(packageName: String, className: String, windowId: Int) {
        foregroundPackage = packageName
        foregroundActivity = className
        foregroundWindowId = windowId
        if (isAllowed(packageName)) {
            requestScan()
        } else {
            handler.removeCallbacks(scanRunnable)
            onExternalWindowChanged(packageName)
            dispatch(AdEvent.Screen(ScreenKind.EXTERNAL))
        }
    }

    /**
     * 허용 목록 밖 앱의 창이 바뀌었다 (Activity가 아닌 창 포함). 패키지명만 기록하며 화면 내용은 읽지 않는다.
     * 스토어가 https 링크로 열리는 등 Activity 전환으로 잡히지 않는 경우가 있어 따로 받는다.
     */
    fun onExternalWindowChanged(packageName: String) {
        if (packageName in IGNORED_OVERLAY_PACKAGES || packageName.contains("inputmethod") || packageName.contains("honeyboard")) return
        externalPackage = packageName
        // 첫 뒤로가기 뒤에도 스토어 화면(예: 하단 시트 아래의 상세 화면)이 남아 있으면 이 이벤트를 신호로 한 번 더 누른다.
        // 타이머만 쓰면 기기가 백그라운드 프로세스를 동결했을 때 늦어진다 (실기기 삼성 Freecess에서 확인).
        if (state == EXTERNAL_STORE && packageName == PLAY_STORE_PACKAGE && storeReturnsThisSession in 1 until MAX_STORE_RETURNS_PER_SESSION) {
            handler.removeCallbacks(storeReturnRunnable)
            handler.postDelayed(storeReturnRunnable, STORE_RETURN_RECHECK_MS)
        }
    }

    /** 허용 앱의 화면 내용이 바뀌었다. */
    fun onAllowedWindowChanged() = requestScan()

    // 이벤트 폭주 방지: 마지막 이벤트 후 SCAN_DEBOUNCE_MS 동안 조용하면 한 번만 탐색한다.
    private fun requestScan() {
        if (state == PAUSED) return
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, SCAN_DEBOUNCE_MS)
    }

    private fun observe() {
        val snapshot = scanActiveWindow() ?: return
        val (kind, candidate) = classify(snapshot) ?: return
        if (dumpAdNodes && kind.isAd()) dumpNodes(snapshot)
        if (kind == ScreenKind.EXTERNAL) externalPackage = snapshot.packageName
        // 이미지로 찾은 X를 재확인하는 동안에는 노드 기준 'X 없음' 관찰로 상태를 되돌리지 않는다.
        if (state == CLOSE_READY && pendingVisual != null && candidate == null && kind.isAd()) return
        pendingCandidate = candidate
        if (candidate != null) pendingVisual = null
        dispatch(AdEvent.Screen(kind))
        updateStoreHighlight(if (kind.isAd()) snapshot else null)
        if (kind == ScreenKind.HOST) onHostScreen(snapshot.packageName, snapshot.nodes)
    }

    /** null = 판단 보류 (화면 전환 직후처럼 이벤트와 활성 창이 아직 맞지 않는 경우). */
    private fun classify(snapshot: ScreenSnapshot): Pair<ScreenKind, CloseCandidate?>? {
        val pkg = snapshot.packageName
        if (!isAllowed(pkg)) return ScreenKind.EXTERNAL to null
        if (foregroundPackage != pkg) return null
        // 광고 Activity가 닫히는 순간 '창 전환' 이벤트보다 호스트 화면 내용이 먼저 올 수 있다.
        // 마지막으로 확인된 Activity의 창이 아니면 이전 Activity 이름으로 판정하지 않고 보류한다.
        if (foregroundWindowId != snapshot.windowId) {
            if (dumpAdNodes) android.util.Log.d("AutoSkipNodes", "보류: 창 불일치 fg=$foregroundWindowId root=${snapshot.windowId} ($foregroundActivity)")
            return null
        }
        val activity = foregroundActivity ?: return ScreenKind.UNIDENTIFIED to null
        val nodes = snapshot.nodes
        if (AdContextDetector.isSensitive(nodes)) return ScreenKind.SENSITIVE to null
        if (HostScreenDetector.isHostScreen(packageAllowed = true, activityClass = activity, hostNamespaces = hostNamespaces(pkg))) {
            return ScreenKind.HOST to null
        }
        CloseButtonMatcher.find(nodes)?.let { return ScreenKind.AD_CLOSE_READY to it }
        if (AdContextDetector.looksLikeEndCard(nodes)) return ScreenKind.AD_END_CARD to null
        return ScreenKind.AD_NO_CLOSE to null
    }

    private fun dispatch(event: AdEvent) {
        val prev = state
        var next = AdStateMachine.next(prev, event)
        // 일시 상태는 같은 관찰로 한 번 더 전이시킨다.
        if (next == AD_DETECTED && event is AdEvent.Screen) {
            log("광고 감지 (${foregroundActivity?.substringAfterLast('.')})")
            clicksThisSession = 0
            storeReturnsThisSession = 0
            nextTapsThisSession = 0
            lastNodeClick = null
            next = AdStateMachine.next(next, event)
        }
        // 스토어에서 돌아온 순간 이미 종료 버튼이 보이면 다음 이벤트를 기다리지 않고 바로 판정한다.
        if (next == RETURNED_TO_AD && event is AdEvent.Screen) {
            log("상태 $prev → RETURNED_TO_AD (${event.kind})")
            next = AdStateMachine.next(next, event)
        }
        if (next == RETURNED_TO_HOST) {
            log(if (prev == VERIFY_HOST) "호스트 화면 복귀 확인 → 자동 종료 성공" else "호스트 화면 복귀 (사용자/광고가 직접 닫음)")
            if (prev == VERIFY_HOST) onAdClosed()
            next = AdStateMachine.next(next, AdEvent.Screen(ScreenKind.HOST))
        }
        if (next == prev) return
        state = next
        SessionLog.setState(next)
        log("상태 $prev → $next (${event.describe()})")
        onEnter(next)
        if (next == EXTERNAL_STORE && prev in AD_STATES) handler.postDelayed(storeReturnRunnable, STORE_RETURN_DELAY_MS)
    }

    /** 아직 스토어에 다녀오지 않은 광고 화면에서만 스토어 이동 버튼을 강조한다. */
    private fun updateStoreHighlight(snapshot: ScreenSnapshot?) {
        val show = snapshot != null && state in HIGHLIGHT_STATES && storeReturnsThisSession == 0
        val buttons = if (show) AdContextDetector.findStoreButtons(snapshot!!.nodes) else emptyList()
        highlightStoreButtons(buttons.map { intArrayOf(it.left, it.top, it.right, it.bottom) })
    }

    private fun onEnter(state: AdState) {
        if (state !in HIGHLIGHT_STATES) highlightStoreButtons(emptyList())
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(verifyTimeoutRunnable)
        handler.removeCallbacks(confirmRunnable)
        handler.removeCallbacks(visualRunnable)
        handler.removeCallbacks(storeReturnRunnable)
        if (state != CLOSE_READY && state != AUTO_CLOSE) pendingVisual = null
        // 노드로 종료 버튼이 안 보이는 광고 화면에서는 주기적으로 화면 이미지를 확인한다. 외부 앱에서는 하지 않는다.
        if (state in VISUAL_STATES) handler.postDelayed(visualRunnable, VISUAL_INTERVAL_MS)
        when (state) {
            IDLE, UNKNOWN, PAUSED, EXTERNAL_STORE -> Unit // 외부 앱에 있는 동안은 제한시간을 두지 않는다.
            // '»'를 누른 뒤에는 광고가 이어지는 게 정상이므로 호스트 복귀를 오래 기다리지 않는다.
            VERIFY_HOST -> handler.postDelayed(verifyTimeoutRunnable, if (lastTapWasNext) NEXT_VERIFY_MS else VERIFY_TIMEOUT_MS)
            CLOSE_READY -> {
                handler.postDelayed(confirmRunnable, CONFIRM_DELAY_MS)
                handler.postDelayed(timeoutRunnable, STATE_TIMEOUT_MS)
            }
            else -> handler.postDelayed(timeoutRunnable, STATE_TIMEOUT_MS)
        }
        if (state == PAUSED) handler.removeCallbacks(scanRunnable)
    }

    /**
     * 클릭 후 호스트 화면이 확인되지 않았다. 여전히 광고 화면이고 클릭 상한이 남았으면
     * 같은 광고의 다음 단계로 보고 다시 탐색한다. 그 외에는 안전하게 UNKNOWN.
     */
    private fun onVerifyTimeout() {
        if (state != VERIFY_HOST) return
        val observed = scanActiveWindow()?.let { classify(it) }
        val kind = observed?.first
        if (kind?.isAd() == true && clicksThisSession < MAX_CLICKS_PER_SESSION) {
            log("같은 광고의 다음 단계 화면 → 종료 버튼 다시 탐색")
            pendingCandidate = observed!!.second
            dispatch(AdEvent.NextAdStep)
            dispatch(AdEvent.Screen(kind!!))
        } else {
            log("호스트 복귀 확인 실패 ($kind, 클릭 ${clicksThisSession}회)")
            dispatch(AdEvent.Timeout)
        }
    }

    /**
     * 기획서 v0.2 이후 결정: 사용자가 광고에서 Play 스토어로 이동하면 잠시 뒤 뒤로가기를 한 번 눌러 광고로 돌아간다.
     * 스토어 안의 버튼은 절대 누르지 않는다. 누르기 직전마다 현재 화면이 Play 스토어인지 확인하고,
     * 광고 1회당 최대 [MAX_STORE_RETURNS_PER_SESSION]번만 누른다 (실기기에서 두 번이 필요한 경우가 있었다).
     */
    private fun returnFromStore() {
        if (state != EXTERNAL_STORE || storeReturnsThisSession >= MAX_STORE_RETURNS_PER_SESSION) return
        // 활성 창 조회(rootInActiveWindow)는 화면 전환 중 수 초간 막힐 수 있어, 창 전환 이벤트로 추적한 값을 쓴다.
        if (externalPackage != PLAY_STORE_PACKAGE) {
            log("스토어 자동 복귀 생략: 현재 화면이 Play 스토어가 아님 ($externalPackage)")
            return
        }
        storeReturnsThisSession++
        val ok = goBack()
        log("Play 스토어에서 뒤로가기 ($storeReturnsThisSession/$MAX_STORE_RETURNS_PER_SESSION) → ${if (ok) "전송" else "실패"}")
        // 스토어 상세 화면 위에 시트가 겹쳐 뜨는 경우 등 한 번으로 안 돌아오면 다음 확인 때 한 번 더 누른다.
        // (보통은 그 전에 스토어 창 전환 이벤트가 와서 onActivityChanged에서 다시 예약된다.)
        if (ok) handler.postDelayed(storeReturnRunnable, STORE_RETURN_RECHECK_MS * 2)
    }

    /** 광고 화면에서 노드 종료 버튼이 없을 때 화면 이미지로 X를 찾는다. */
    private fun visualTick() {
        if (state !in VISUAL_STATES) return
        val snapshot = scanActiveWindow()
        val observed = snapshot?.let { classify(it) }
        if (observed == null || captureInFlight) {
            handler.postDelayed(visualRunnable, VISUAL_INTERVAL_MS)
            return
        }
        val (kind, node) = observed
        if (!kind.isAd() || node != null) {
            // 광고 화면이 아니게 되었거나 노드 버튼이 생겼다 → 일반 경로로 처리.
            pendingCandidate = node
            dispatch(AdEvent.Screen(kind))
            return
        }
        captureInFlight = true
        captureScreen { frame ->
            captureInFlight = false
            if (state !in VISUAL_STATES) return@captureScreen
            val found = frame?.let { VisualCloseDetector.find(it) }
                ?.takeUnless { it.kind == VisualKind.NEXT && nextTapsThisSession >= MAX_NEXT_TAPS_PER_SESSION }
            if (found == null) {
                handler.postDelayed(visualRunnable, VISUAL_INTERVAL_MS)
                return@captureScreen
            }
            log("이미지에서 ${found.label()} 발견 (${found.centerX}, ${found.centerY}) 크기 ${found.size}px")
            pendingCandidate = null
            pendingVisual = found
            dispatch(AdEvent.Screen(ScreenKind.AD_CLOSE_READY))
        }
    }

    /** 이미지 X: 광고 화면인지 다시 확인하고, 새로 캡처한 이미지에서 같은 자리에 X가 있을 때만 탭한다. */
    private fun confirmVisualAndTap(first: VisualCandidate) {
        val observed = scanActiveWindow()?.let { classify(it) }
        if (observed == null) {
            handler.postDelayed(confirmRunnable, CONFIRM_DELAY_MS)
            return
        }
        val (kind, node) = observed
        if (node != null) {
            pendingVisual = null
            pendingCandidate = node
            handler.postDelayed(confirmRunnable, CONFIRM_DELAY_MS)
            return
        }
        if (!kind.isAd()) {
            pendingVisual = null
            dispatch(AdEvent.Screen(kind))
            return
        }
        val wait = lastClickAt + MIN_CLICK_INTERVAL_MS - SystemClock.uptimeMillis()
        if (wait > 0) {
            handler.postDelayed(confirmRunnable, wait)
            return
        }
        captureScreen { frame ->
            if (state != CLOSE_READY) return@captureScreen
            val again = frame?.let { VisualCloseDetector.find(it) }
            if (again == null || again.kind != first.kind ||
                abs(again.centerX - first.centerX) > VISUAL_TOLERANCE_PX || abs(again.centerY - first.centerY) > VISUAL_TOLERANCE_PX
            ) {
                log("이미지 재검증 실패: ${first.label()}가 사라졌거나 위치/모양이 바뀜")
                pendingVisual = null
                dispatch(AdEvent.Screen(kind))
                return@captureScreen
            }
            // 캡처와 탭 사이에 다른 앱/창으로 바뀌었으면 누르지 않는다.
            if (!foregroundIsAdWindow()) {
                log("이미지 ${first.label()} 탭 취소: 광고 화면이 아님")
                pendingVisual = null
                dispatch(AdEvent.Screen(ScreenKind.UNIDENTIFIED))
                return@captureScreen
            }
            dispatch(AdEvent.CloseVerified)
            lastTapWasNext = again.kind == VisualKind.NEXT
            if (lastTapWasNext) nextTapsThisSession++ else clicksThisSession++
            lastClickAt = SystemClock.uptimeMillis()
            val ok = tapAt(again.centerX, again.centerY)
            log("이미지 ${again.label()} 탭 (${again.centerX}, ${again.centerY}) → ${if (ok) "전송" else "실패"}")
            dispatch(if (ok) AdEvent.ClickSent else AdEvent.ClickFailed(retry = clicksThisSession < MAX_CLICKS_PER_SESSION))
        }
    }

    /** 종료 버튼이 처음 보인 뒤 잠시 기다렸다가 다시 탐색해 같은 버튼이 여전히 유효할 때만 클릭한다. */
    private fun confirmAndClick() {
        if (state != CLOSE_READY) return
        pendingVisual?.let { visual ->
            if (pendingCandidate == null) {
                confirmVisualAndTap(visual)
                return
            }
        }
        val first = pendingCandidate ?: return
        val snapshot = scanActiveWindow() ?: return
        val (kind, candidate) = classify(snapshot) ?: return
        if (kind != ScreenKind.AD_CLOSE_READY || candidate == null || candidate.signature != first.signature) {
            log("재검증 실패: $kind / ${candidate?.label}")
            pendingCandidate = candidate
            dispatch(AdEvent.Screen(kind))
            if (state == CLOSE_READY) handler.postDelayed(confirmRunnable, CONFIRM_DELAY_MS)
            return
        }
        val wait = lastClickAt + MIN_CLICK_INTERVAL_MS - SystemClock.uptimeMillis()
        if (wait > 0) {
            handler.postDelayed(confirmRunnable, wait)
            return
        }
        dispatch(AdEvent.CloseVerified)
        lastTapWasNext = false
        clicksThisSession++
        lastClickAt = SystemClock.uptimeMillis()
        val target = snapshot.nodes[candidate.clickIndex]
        val key = "${candidate.label}@${target.left},${target.top},${target.right},${target.bottom}"
        // 웹 화면 안의 버튼은 노드 클릭(ACTION_CLICK)이 먹지 않는 경우가 있다 (실기기 TIN Google 광고).
        // 같은 버튼이 같은 자리에 그대로 남아 있으면 이번에는 그 버튼 중심을 실제 탭으로 누른다.
        val ok = if (key == lastNodeClick) {
            val cx = (target.left + target.right) / 2
            val cy = (target.top + target.bottom) / 2
            tapAt(cx, cy).also { log("클릭 '${candidate.label}' 좌표 탭 ($cx, $cy) → ${if (it) "전송" else "실패"}") }
        } else {
            ClickExecutor.click(snapshot, candidate).also { log("클릭 '${candidate.label}' (score=${candidate.score}) → ${if (it) "전송" else "실패"}") }
        }
        lastNodeClick = key
        dispatch(if (ok) AdEvent.ClickSent else AdEvent.ClickFailed(retry = clicksThisSession < MAX_CLICKS_PER_SESSION))
    }

    private fun dumpNodes(snapshot: ScreenSnapshot) {
        val lines = snapshot.nodes.mapIndexed { i, n ->
            "#$i ${n.className?.substringAfterLast('.')} text=${n.text} desc=${n.desc} id=${n.viewId} " +
                "clk=${n.clickable} en=${n.enabled} vis=${n.visible} [${n.left},${n.top}][${n.right},${n.bottom}]"
        }
        if (lines == lastDumped) return
        lastDumped = lines
        android.util.Log.d("AutoSkipNodes", "---- ${foregroundActivity?.substringAfterLast('.')} (${lines.size} nodes)")
        lines.forEach { android.util.Log.d("AutoSkipNodes", it) }
    }

    /** 창 전환 이벤트로 추적한 현재 화면이 허용 앱의 광고 화면이고, 그 위에 외부 창이 없는지. */
    private fun foregroundIsAdWindow(): Boolean {
        val pkg = foregroundPackage ?: return false
        if (!isAllowed(pkg) || state != CLOSE_READY) return false
        val active = scanActiveWindow() ?: return false
        if (active.packageName != pkg || active.windowId != foregroundWindowId) return false
        return !HostScreenDetector.isHostScreen(packageAllowed = true, activityClass = foregroundActivity, hostNamespaces = hostNamespaces(pkg))
    }

    private fun VisualCandidate.label() = if (kind == VisualKind.NEXT) "»" else "X"

    private fun ScreenKind.isAd() =
        this == ScreenKind.AD_NO_CLOSE || this == ScreenKind.AD_END_CARD || this == ScreenKind.AD_CLOSE_READY

    private fun AdEvent.describe(): String = when (this) {
        is AdEvent.Screen -> kind.name
        else -> this::class.simpleName.orEmpty()
    }

    companion object {
        const val SCAN_DEBOUNCE_MS = 300L
        const val CONFIRM_DELAY_MS = 500L
        const val MIN_CLICK_INTERVAL_MS = 1_500L
        // 'Google 광고 3/3'처럼 광고가 연달아 나오는 경우가 있어 넉넉히 둔다 (실기기 TIN).
        const val MAX_CLICKS_PER_SESSION = 4
        const val VERIFY_TIMEOUT_MS = 5_000L
        // 엔드카드에서 사용자가 스토어에 다녀오는 등 사람이 행동하는 단계가 있어 넉넉히 둔다.
        // (실기기에서 2분으로 두었을 때 엔드카드 대기 중 UNKNOWN으로 정지한 사례가 있었다.)
        const val STATE_TIMEOUT_MS = 10 * 60_000L
        const val VISUAL_INTERVAL_MS = 1_000L
        const val NEXT_VERIFY_MS = 1_500L
        const val MAX_NEXT_TAPS_PER_SESSION = 4
        const val VISUAL_TOLERANCE_PX = 12
        private val VISUAL_STATES = setOf(AD_PLAYING, END_CARD, RETURNED_TO_AD, WAITING_FOR_CLOSE)
        private val AD_STATES = VISUAL_STATES + CLOSE_READY
        private val HIGHLIGHT_STATES = setOf(AD_PLAYING, END_CARD)
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        /** 다른 앱 위에 잠깐 겹쳐 뜨는 시스템 창. 전경 앱이 바뀐 것으로 보지 않는다. */
        private val IGNORED_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.samsung.android.app.smartcapture", // 삼성 스크린샷 도구 (실기기에서 외부 앱 이동으로 오인한 사례)
        )
        const val STORE_RETURN_DELAY_MS = 1_500L
        const val STORE_RETURN_RECHECK_MS = 800L
        const val MAX_STORE_RETURNS_PER_SESSION = 2
    }
}
