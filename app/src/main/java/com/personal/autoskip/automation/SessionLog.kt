package com.personal.autoskip.automation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 서비스와 설정 화면이 같은 프로세스에서 공유하는 메모리 로그. 디스크/네트워크에 저장하지 않는다. */
object SessionLog {
    private const val MAX_LINES = 100
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    private val _state = MutableStateFlow<AdState?>(null)
    /** null = 서비스가 연결되어 있지 않음. */
    val state: StateFlow<AdState?> = _state

    fun add(message: String) {
        Log.i("AutoSkip", message)
        val line = "${time.format(Date())}  $message"
        _lines.update { (it + line).takeLast(MAX_LINES) }
    }

    fun setState(state: AdState?) {
        _state.value = state
    }
}
