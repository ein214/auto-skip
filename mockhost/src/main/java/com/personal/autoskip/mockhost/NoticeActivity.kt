package com.personal.autoskip.mockhost

import android.app.Activity
import android.os.Bundle
import android.widget.Button

/** 광고가 아닌 화면에 X 버튼이 있는 경우 (테스트 케이스 4, 9). */
class NoticeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notice)
        findViewById<Button>(R.id.notice_close).setOnClickListener { finish() }
    }
}
