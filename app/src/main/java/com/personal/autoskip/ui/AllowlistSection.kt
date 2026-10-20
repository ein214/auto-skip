package com.personal.autoskip.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.autoskip.settings.AppAllowlistRepository
import kotlinx.coroutines.launch

private data class LaunchableApp(val packageName: String, val label: String)

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filterNot { AppAllowlistRepository.isExcluded(it.packageName) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun AllowlistSection() {
    val context = LocalContext.current
    val repo = remember { AppAllowlistRepository(context) }
    val scope = rememberCoroutineScope()
    // null = 아직 로드 전. 로드 전에 그리면 순서가 바뀌며 체크 표시가 깜빡인다.
    val allowed by repo.allowed.collectAsState(initial = null)
    val apps = remember { loadLaunchableApps(context) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("허용 앱", style = MaterialTheme.typography.titleMedium)
            Text(
                "체크한 앱에서만 화면을 읽고 종료 버튼을 누릅니다. 스토어·설정·시스템 화면은 선택할 수 없습니다.",
                style = MaterialTheme.typography.bodySmall,
            )
            val current = allowed ?: return@Column
            // 허용된 앱을 먼저 보여 준다.
            apps.sortedByDescending { it.packageName in current }.forEach { app ->
                val checked = app.packageName in current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { scope.launch { repo.setAllowed(app.packageName, it) } },
                    )
                    Column {
                        Text(app.label)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
