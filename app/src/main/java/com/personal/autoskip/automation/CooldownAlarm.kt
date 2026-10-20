package com.personal.autoskip.automation

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 허용 앱의 광고 쿨타임이 끝나는 시각에 알림(소리 + 짧은 진동)을 띄운다.
 * 광고를 시작하는 것은 사용자다 — 알림을 누르면 해당 앱만 열린다.
 */
object CooldownAlarm {

    private const val CHANNEL_ID = "cooldown_ready"
    private const val EXTRA_PACKAGE = "package"
    private const val EXTRA_LABEL = "label"
    /** 같은 쿨타임을 매초 다시 예약하지 않도록, 예상 종료 시각이 이만큼 이상 바뀔 때만 갱신한다. */
    private const val RESCHEDULE_THRESHOLD_MS = 5_000L
    private val scheduledAt = HashMap<String, Long>()

    fun schedule(context: Context, packageName: String, remainingSeconds: Int) {
        val endAt = System.currentTimeMillis() + remainingSeconds * 1000L
        val previous = scheduledAt[packageName]
        if (previous != null && kotlin.math.abs(previous - endAt) < RESCHEDULE_THRESHOLD_MS) return
        scheduledAt[packageName] = endAt

        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context, packageName, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending)
        }
        SessionLog.add("쿨타임 알림 예약: $label ${remainingSeconds}초 뒤")
    }

    private fun alarmIntent(context: Context, packageName: String, label: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            Intent(context, CooldownAlarmReceiver::class.java).putExtra(EXTRA_PACKAGE, packageName).putExtra(EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "광고 쿨타임 종료", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "허용한 앱에서 광고를 다시 볼 수 있을 때 알려 줍니다."
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150)
        }
        manager.createNotificationChannel(channel)
    }

    internal fun notifyReady(context: Context, packageName: String, label: String) {
        scheduledAt.remove(packageName)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            SessionLog.add("쿨타임 종료 알림을 보낼 수 없음: 알림 권한 없음")
            return
        }
        ensureChannel(context)
        val open = context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(context, packageName.hashCode(), it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("$label 광고 다시 볼 수 있어요")
            .setContentText("쿨타임이 끝났습니다. 눌러서 앱을 엽니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(packageName.hashCode(), notification)
        SessionLog.add("쿨타임 종료 알림: $label")
    }
}

class CooldownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("package") ?: return
        val label = intent.getStringExtra("label") ?: pkg
        CooldownAlarm.notifyReady(context, pkg, label)
    }
}
