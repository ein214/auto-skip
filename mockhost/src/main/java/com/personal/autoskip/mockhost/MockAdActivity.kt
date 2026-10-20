package com.personal.autoskip.mockhost

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * 모의 전체 화면 광고.
 * - MODE_A: 5초 동안 "N초 후 건너뛰기"(비활성) → "건너뛰기"(활성)
 * - MODE_A_DELAYED: 5초 영상 후 7초 동안 버튼 없음 → "Skip" 등장 (테스트 케이스 2)
 * - MODE_C: 5초 영상 후 "Skip" → 같은 Activity에서 엔드카드 → 2초 뒤 X (실기기 Moloco 광고에서 관찰된 다단계 흐름)
 * - MODE_D: 5초 영상 → 엔드카드 → 2초 뒤 화면에 '그림으로만' X 표시 (접근성 노드 없음, 실기기 AppLovin 재현)
 * - MODE_B: 5초 영상 → 설치 엔드카드(X 없음) → 사용자가 '설치하기'로 스토어 이동 후 복귀 → 4초 뒤 X 등장
 */
class MockAdActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mode: String
    private var wentToStore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mock_ad)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_A

        findViewById<Button>(R.id.skip_button).setOnClickListener {
            if (mode == MODE_C) showEndCardThenClose() else close("skip_button")
        }
        findViewById<View>(R.id.close_button).setOnClickListener { close("close_button") }
        findViewById<Button>(R.id.cta_install).setOnClickListener { openStore() }

        when (mode) {
            MODE_A -> startModeA()
            MODE_A_DELAYED -> playVideo { handler.postDelayed({ showSkip("Skip") }, 7_000) }
            MODE_B -> playVideo { showEndCard() }
            MODE_C -> playVideo { showSkip("Skip") }
            MODE_D -> playVideo {
                showEndCard()
                val drawn = DrawnCloseView(this) { close("drawn_x") }
                (findViewById<View>(android.R.id.content) as android.view.ViewGroup).addView(drawn)
                handler.postDelayed({ drawn.showX = true }, 2_000)
            }
        }
    }

    private fun startModeA() {
        val skip = findViewById<Button>(R.id.skip_button)
        skip.visibility = View.VISIBLE
        skip.isEnabled = false
        playVideo(onTick = { left -> skip.text = "${left}초 후 건너뛰기" }) { showSkip("건너뛰기") }
    }

    private fun playVideo(onTick: (Int) -> Unit = {}, onEnd: () -> Unit) {
        val text = findViewById<TextView>(R.id.video_text)
        fun tick(left: Int) {
            if (left == 0) {
                text.text = "▶ 영상 종료"
                onEnd()
                return
            }
            text.text = "▶ 광고 영상 재생 중… $left"
            onTick(left)
            handler.postDelayed({ tick(left - 1) }, 1_000)
        }
        tick(VIDEO_SECONDS)
    }

    private fun showSkip(label: String) {
        findViewById<Button>(R.id.skip_button).apply {
            text = label
            isEnabled = true
            visibility = View.VISIBLE
        }
    }

    private fun showEndCard() {
        findViewById<View>(R.id.video_container).visibility = View.GONE
        findViewById<View>(R.id.end_card).visibility = View.VISIBLE
    }

    private fun showEndCardThenClose() {
        findViewById<View>(R.id.skip_button).visibility = View.GONE
        showEndCard()
        handler.postDelayed({ findViewById<View>(R.id.close_button).visibility = View.VISIBLE }, 2_000)
    }

    private fun openStore() {
        wentToStore = true
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.example.mockgame"))
        try {
            startActivity(market)
        } catch (e: ActivityNotFoundException) {
            // Play 스토어가 없는 에뮬레이터: 외부 앱 이동만 재현하기 위해 시스템 설정 화면을 연다.
            Toast.makeText(this, "Play 스토어 없음 → 설정 화면으로 대체", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (mode == MODE_B && wentToStore) {
            findViewById<TextView>(R.id.end_card_hint).text = "잠시 후 닫기 버튼이 나타납니다"
            handler.removeCallbacksAndMessages(CLOSE_TOKEN)
            handler.postDelayed({ findViewById<View>(R.id.close_button).visibility = View.VISIBLE }, CLOSE_TOKEN, 4_000)
        }
    }

    private fun close(by: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CLOSED_BY, by))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_CLOSED_BY = "closed_by"
        const val MODE_A = "A"
        const val MODE_A_DELAYED = "A2"
        const val MODE_B = "B"
        const val MODE_C = "C"
        const val MODE_D = "D"
        private const val VIDEO_SECONDS = 5
        private val CLOSE_TOKEN = Any()
    }
}
