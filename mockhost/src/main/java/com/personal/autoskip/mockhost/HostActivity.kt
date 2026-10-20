package com.personal.autoskip.mockhost

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/** 뮤니버스 '충전소'를 흉내 낸 호스트 화면. 광고 시작은 항상 사용자가 직접 누른다. */
class HostActivity : Activity() {

    private var stars = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)
        stars = savedInstanceState?.getInt("stars") ?: 0
        render()

        findViewById<Button>(R.id.watch_a).setOnClickListener { startAd(MockAdActivity.MODE_A) }
        findViewById<Button>(R.id.watch_a2).setOnClickListener { startAd(MockAdActivity.MODE_A_DELAYED) }
        findViewById<Button>(R.id.watch_b).setOnClickListener { startAd(MockAdActivity.MODE_B) }
        findViewById<Button>(R.id.watch_c).setOnClickListener { startAd(MockAdActivity.MODE_C) }
        findViewById<Button>(R.id.watch_d).setOnClickListener { startAd(MockAdActivity.MODE_D) }
        findViewById<Button>(R.id.open_popup).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("공지")
                .setMessage("광고가 아닌 일반 팝업입니다. 자동으로 닫히면 안 됩니다.")
                .setPositiveButton("닫기", null)
                .setNegativeButton("X", null)
                .show()
        }
        findViewById<Button>(R.id.open_notice).setOnClickListener {
            startActivity(Intent(this, NoticeActivity::class.java))
        }
    }

    private fun startAd(mode: String) {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, MockAdActivity::class.java).putExtra(MockAdActivity.EXTRA_MODE, mode), REQ_AD)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_AD) return
        val closedBy = data?.getStringExtra(MockAdActivity.EXTRA_CLOSED_BY)
        if (resultCode == RESULT_OK) stars += 5
        findViewById<TextView>(R.id.last_result).text =
            if (resultCode == RESULT_OK) "마지막 광고: 보상 지급 (닫은 버튼: $closedBy)" else "마지막 광고: 보상 없음 (뒤로가기 등)"
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("stars", stars)
    }

    private fun render() {
        findViewById<TextView>(R.id.stars).text = "받은 별: $stars"
    }

    private companion object {
        const val REQ_AD = 1
    }
}
