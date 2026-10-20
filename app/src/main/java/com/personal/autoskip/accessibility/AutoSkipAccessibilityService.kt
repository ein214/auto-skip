package com.personal.autoskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.personal.autoskip.BuildConfig
import com.personal.autoskip.automation.AdSessionController
import com.personal.autoskip.automation.SessionLog
import com.personal.autoskip.detection.ScreenFrame
import com.personal.autoskip.detection.VisualCloseDetector
import com.personal.autoskip.settings.AppAllowlistRepository
import com.personal.autoskip.settings.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 이벤트를 받아 [AdSessionController]로 넘기기만 한다. 판정/클릭 로직은 다른 클래스에 둔다. */
class AutoSkipAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var enabled = false
    private var allowed: Set<String> = emptySet()
    private var cooldownAlert = true

    private val prefs by lazy { PreferencesRepository(this) }
    private val storeOverlay by lazy { StoreButtonOverlay(this) }

    private val controller = AdSessionController(
        handler = handler,
        isAllowed = { it in allowed },
        hostNamespaces = ::hostNamespaces,
        scanActiveWindow = ::scanActiveWindow,
        onAdClosed = { scope.launch { prefs.incrementClosedCount() } },
        log = SessionLog::add,
        // 광고 화면 노드를 logcat에 남기는 진단 로그. 기본 꺼짐 (./gradlew -PautoskipDiag=true 로 빌드할 때만 켜짐).
        dumpAdNodes = BuildConfig.DIAG_LOG,
        captureScreen = ::captureScreen,
        tapAt = { x, y -> ClickExecutor.tap(this, x, y) },
        goBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
        onHostScreen = ::onHostScreen,
        highlightStoreButtons = { boxes ->
            storeOverlay.show(boxes.map { android.graphics.RectF(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) })
        },
    )

    override fun onServiceConnected() {
        SessionLog.add("접근성 서비스 연결됨")
        SessionLog.setState(controller.state)
        prefs.enabled
            .onEach { enabled = it; controller.setActive(it) }
            .launchIn(scope)
        prefs.cooldownAlert
            .onEach { cooldownAlert = it }
            .launchIn(scope)
        AppAllowlistRepository(this).allowed
            .onEach { allowed = it }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled) return
        val pkg = event.packageName?.toString() ?: return
        val isAllowedPkg = pkg in allowed
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString()
            // 허용 목록 밖 앱은 패키지/클래스명만 본다 (외부 스토어 이동 감지용). 화면 내용은 읽지 않는다.
            if (cls != null && isActivity(pkg, cls)) {
                controller.onActivityChanged(pkg, cls, event.windowId)
                return
            }
            if (!isAllowedPkg) {
                controller.onExternalWindowChanged(pkg)
                return
            }
        }
        if (isAllowedPkg) controller.onAllowedWindowChanged()
    }

    private fun scanActiveWindow(): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (pkg !in allowed) return ScreenSnapshot(pkg, root.windowId, emptyList(), emptyList())
        return NodeScanner.scan(root)
    }

    /**
     * 접근성 서비스 스크린샷(Android 11+). 화면 위쪽 판정 영역만 메모리로 복사하며,
     * 저장하거나 외부로 보내지 않는다. 보안 화면(FLAG_SECURE)은 시스템이 검게 돌려준다.
     */
    private fun captureScreen(callback: (ScreenFrame?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val frame = result.hardwareBuffer.use { buffer ->
                    val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return@use null
                    val bitmap = hw.copy(Bitmap.Config.ARGB_8888, false)
                    hw.recycle()
                    val rows = VisualCloseDetector.requiredRows(bitmap.height)
                    val pixels = IntArray(bitmap.width * rows)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, rows)
                    ScreenFrame(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
                }
                callback(frame)
            }

            override fun onFailure(errorCode: Int) {
                SessionLog.add("화면 캡처 실패 (code=$errorCode)")
                callback(null)
            }
        })
    }

    private fun onHostScreen(pkg: String, nodes: List<com.personal.autoskip.detection.ScreenNode>) {
        if (!cooldownAlert) return
        val seconds = com.personal.autoskip.detection.CooldownParser.findRemainingSeconds(nodes) ?: return
        com.personal.autoskip.automation.CooldownAlarm.schedule(this, pkg, seconds)
    }

    private val namespaceCache = HashMap<String, List<String>>()

    /** 앱 id와 런처 Activity의 패키지 경로. 둘 다 앱 자체 코드로 본다. */
    private fun hostNamespaces(pkg: String): List<String> = namespaceCache.getOrPut(pkg) {
        val launcher = packageManager.getLaunchIntentForPackage(pkg)?.component?.className
        listOfNotNull(pkg, launcher?.substringBeforeLast('.', "")?.takeIf { it.isNotEmpty() }).distinct()
    }

    private fun isActivity(pkg: String, cls: String): Boolean = try {
        packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        // 사용자가 접근성 설정에서 끄거나 시스템이 서비스를 해제하면 즉시 중단한다.
        controller.setActive(false)
        storeOverlay.hide()
        SessionLog.add("접근성 서비스 해제됨")
        SessionLog.setState(null)
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {

        /** 시스템 설정에서 이 서비스가 켜져 있는지 확인한다. */
        fun isEnabledInSettings(context: Context): Boolean {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            return isServiceListed(setting, context.packageName, AutoSkipAccessibilityService::class.java.name)
        }

        /**
         * ENABLED_ACCESSIBILITY_SERVICES는 "pkg/cls:pkg2/.Cls2" 형식이다.
         * 클래스명은 전체 이름 또는 "."으로 시작하는 축약형일 수 있다.
         */
        fun isServiceListed(setting: String?, packageName: String, className: String): Boolean {
            if (setting.isNullOrBlank()) return false
            return setting.split(':').any { entry ->
                val (pkg, cls) = entry.split('/', limit = 2).takeIf { it.size == 2 } ?: return@any false
                val fullCls = if (cls.startsWith(".")) pkg + cls else cls
                pkg.equals(packageName, ignoreCase = true) && fullCls.equals(className, ignoreCase = true)
            }
        }
    }
}
