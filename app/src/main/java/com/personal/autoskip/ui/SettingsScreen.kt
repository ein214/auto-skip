package com.personal.autoskip.ui

import android.content.Intent
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.personal.autoskip.accessibility.AutoSkipAccessibilityService
import com.personal.autoskip.settings.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    val enabled by prefs.enabled.collectAsState(initial = false)

    // 사용자가 시스템 설정에서 돌아올 때마다 서비스 상태를 다시 읽는다.
    var serviceOn by remember { mutableStateOf(AutoSkipAccessibilityService.isEnabledInSettings(context)) }
    val power = remember { context.getSystemService(PowerManager::class.java) }
    val cooldownAlert by prefs.cooldownAlert.collectAsState(initial = true)
    fun notificationsAllowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    var notifyGranted by remember { mutableStateOf(notificationsAllowed()) }
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { notifyGranted = it }
    var batteryUnrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceOn = AutoSkipAccessibilityService.isEnabledInSettings(context)
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        notifyGranted = notificationsAllowed()
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("AutoSkip", style = MaterialTheme.typography.headlineMedium)

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("접근성 서비스", style = MaterialTheme.typography.titleMedium)
                        Text(if (serviceOn) "✅ 켜짐" else "⛔ 꺼짐 — 아래 안내에 따라 직접 켜 주세요")
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("접근성 설정 열기") }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("자동 종료 사용", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (!serviceOn) "접근성 서비스가 꺼져 있어 동작하지 않습니다"
                                else if (enabled) "허용한 앱의 광고 종료 버튼을 누릅니다"
                                else "일시 중지됨",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = { scope.launch { prefs.setEnabled(it) } })
                    }
                }

                SessionLogSection()

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("쿨타임 끝나면 알림", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "허용 앱의 광고 화면에 남은 시간(예: 695초, 04:59)이 보이면, 끝나는 시각에 소리와 짧은 진동으로 알려 줍니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = cooldownAlert, onCheckedChange = { scope.launch { prefs.setCooldownAlert(it) } })
                        }
                        if (!notifyGranted) {
                            Text("⚠️ 알림 권한이 없어 알림을 띄울 수 없습니다")
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }) { Text("알림 허용") }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("배터리 제한", style = MaterialTheme.typography.titleMedium)
                        Text(if (batteryUnrestricted) "✅ 제한 없음" else "⚠️ 제한됨 — 광고 화면이 조용할 때 기기가 AutoSkip을 멈춰 X를 늦게 누를 수 있습니다")
                        if (!batteryUnrestricted) {
                            Button(onClick = {
                                @SuppressLint("BatteryLife")
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }) { Text("배터리 제한 해제") }
                        }
                    }
                }

                AllowlistSection()

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("권한 켜는 방법", style = MaterialTheme.typography.titleMedium)
                        Text("1. [접근성 설정 열기] → 설치된 앱(또는 다운로드한 앱) → AutoSkip 광고 종료 도우미")
                        Text("2. 사용 스위치를 켜고 확인을 누릅니다.")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Text(
                                "※ Android 13 이상에서 스위치가 회색으로 막혀 있으면 '제한된 설정'입니다. " +
                                    "[앱 정보 열기] → 오른쪽 위 ⋮ → '제한된 설정 허용'을 누른 뒤 다시 시도하세요. " +
                                    "(기기 제조사/버전에 따라 메뉴 이름이 다를 수 있습니다.)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                                )
                            }) { Text("앱 정보 열기") }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("이 앱이 하는 일 / 하지 않는 일", style = MaterialTheme.typography.titleMedium)
                        Text("• 허용한 앱에서, 광고 화면에 이미 표시된 Skip/Close/X/Done 버튼만 누릅니다.")
                        Text("• 광고 시작, 설치·스토어 이동 버튼, 결제·인증 화면은 누르지 않습니다.")
                        Text("• 광고에 스토어 이동 버튼이 나타나면 주황색 테두리와 짧은 진동으로 알려 줍니다. 누르는 것은 직접 하셔야 합니다.")
                        Text("• 광고에서 직접 Play 스토어로 이동하면, 잠시 뒤 뒤로가기를 눌러 광고로 돌아옵니다 (광고 1회당 최대 2번, 스토어 화면일 때만).")
                        Text("• 버튼 정보가 없는 광고는 화면 위쪽 모서리를 캡처해 X 모양을 찾아 누릅니다. 캡처 이미지는 저장하지 않습니다.")
                        Text("• 인터넷 권한이 없으며 화면 내용을 기기 밖으로 보내지 않습니다.")
                    }
                }
            }
        }
    }
}
