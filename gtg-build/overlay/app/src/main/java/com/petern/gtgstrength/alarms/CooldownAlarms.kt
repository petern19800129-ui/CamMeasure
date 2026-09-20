package com.petern.gtgstrength.alarms

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.petern.gtgstrength.GtgApplication
import com.petern.gtgstrength.MainActivity
import com.petern.gtgstrength.data.TrainingSettings
import com.petern.gtgstrength.domain.Exercise
import com.petern.gtgstrength.widget.requestWidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ACTION_TIMER_DONE = "com.petern.gtgstrength.alarms.TIMER_DONE"
private const val EXTRA_EXERCISE = "exercise"
private const val EXTRA_DUE_AT = "dueAt"
private const val CHANNEL_ID = "gtg_cooldown_finished_v1"

/**
 * AlarmManager continues to deliver scheduled checks when the app is closed.
 * setAndAllowWhileIdle is battery-friendly, but Android may defer delivery
 * during Doze: notifications are not promised to fire at the exact second.
 */
object CooldownAlarms {
    fun reconcile(context: Context, settings: TrainingSettings) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        for (exercise in Exercise.entries) {
            val intent = timerPendingIntent(context, exercise)
            manager.cancel(intent)
            val dueAt = settings.nextLogAllowedAtMillis(exercise)
            if (!settings.cooldownAlarmEnabled || dueAt <= now) continue
            val scheduled = timerPendingIntent(context, exercise, dueAt)
            manager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + (dueAt - now) + 1_000L,
                scheduled
            )
        }
    }

    private fun timerPendingIntent(
        context: Context,
        exercise: Exercise,
        dueAt: Long = 0L
    ): PendingIntent {
        val request = if (exercise == Exercise.DEADLIFT) 91_001 else 91_002
        val broadcast = Intent(context, CooldownAlarmReceiver::class.java)
            .setAction(ACTION_TIMER_DONE)
            .putExtra(EXTRA_EXERCISE, exercise.storedName)
            .putExtra(EXTRA_DUE_AT, dueAt)
        return PendingIntent.getBroadcast(
            context,
            request,
            broadcast,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyReady(context: Context, exercise: Exercise) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "GTG timer finished",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Sound and vibration when Deadlift or RDL becomes available."
                    enableVibration(true)
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            91_003,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val name = exercise.displayName
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$name ready")
            .setContentText("Your GTG cooldown is finished. You can log the next set.")
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        manager.notify(if (exercise == Exercise.DEADLIFT) 91_011 else 91_012, notification)
    }
}

class CooldownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            requestWidgetRefresh(context)
            return
        }
        if (intent.action != ACTION_TIMER_DONE) return
        val exercise = when (intent.getStringExtra(EXTRA_EXERCISE)) {
            Exercise.DEADLIFT.storedName -> Exercise.DEADLIFT
            Exercise.RDL.storedName -> Exercise.RDL
            else -> return
        }
        val expectedDue = intent.getLongExtra(EXTRA_DUE_AT, 0L)
        if (expectedDue <= 0L) return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = (context.applicationContext as GtgApplication)
                    .settingsRepository.settings.first()
                val actualDue = settings.nextLogAllowedAtMillis(exercise)
                if (settings.cooldownAlarmEnabled && actualDue == expectedDue &&
                    actualDue <= System.currentTimeMillis()
                ) {
                    CooldownAlarms.notifyReady(context, exercise)
                }
                // If a timer was extended or cancelled, silently reschedule.
                CooldownAlarms.reconcile(context, settings)
                requestWidgetRefresh(context)
            } finally {
                result.finish()
            }
        }
    }
}
