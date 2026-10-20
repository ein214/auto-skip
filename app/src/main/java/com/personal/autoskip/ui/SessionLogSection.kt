package com.personal.autoskip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.personal.autoskip.automation.SessionLog
import com.personal.autoskip.settings.PreferencesRepository

@Composable
fun SessionLogSection() {
    val context = LocalContext.current
    val prefs = remember { PreferencesRepository(context) }
    val closedCount by prefs.closedCount.collectAsState(initial = 0)
    val state by SessionLog.state.collectAsState()
    val lines by SessionLog.lines.collectAsState()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("세션 로그", style = MaterialTheme.typography.titleMedium)
            Text("현재 상태: ${state?.name ?: "서비스 연결 안 됨"}  ·  자동 종료 누적: ${closedCount}회")
            Text(
                "알림창 빠른 설정에 'AutoSkip' 타일을 추가하면 어느 화면에서든 즉시 끌 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
            )
            lines.takeLast(30).asReversed().forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
