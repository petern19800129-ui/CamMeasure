package com.petern.gtgstrength.alarms

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.petern.gtgstrength.domain.Exercise

private const val ACTION_START_RINGING = "com.petern.gtgstrength.alarm.START_RINGING"
private const val ACTION_STOP_RINGING = "com.petern.gtgstrength.alarm.STOP_RINGING"
private const val EXTRA_RING_NAME = "ring_name"
private const val RINGING_CHANNEL = "gtg_ringing_alarm_v1"
private const val RINGING_NOTIFICATION_ID = 91_050

/**
 * A foreground service keeps the alarm sound and vibration going until the
 * user presses STOP ALARM (or Android forcibly ends the service).
 * Both exercises share one active alarm; Stop silences both.
 */
class CooldownRingingService : Service() {
    private val pendingNames = linkedSetOf<String>()
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RINGING) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START_RINGING) {
            stopSelf()
            return START_NOT_STICKY
        }

        val name = intent.getStringExtra(EXTRA_RING_NAME)
            ?.takeIf { it == Exercise.DEADLIFT.displayName || it == Exercise.RDL.displayName || it == "Test alarm" }
            ?: return START_NOT_STICKY
        pendingNames.add(name)
        ensureChannel()
        val notification = activeNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(RINGING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(RINGING_NOTIFICATION_ID, notification)
        }
        if (player == null) startSound()
        if (vibrator == null) startVibration()
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                RINGING_CHANNEL,
                "GTG ringing alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "An ongoing alarm that you stop manually after a GTG timer finishes."
                setSound(null, null) // Sound is played continuously by the foreground service.
                enableVibration(false) // The service repeats a separate vibration pattern.
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun activeNotification(): Notification {
        val title = if (pendingNames.size == 1) "${pendingNames.first()} ready" else "GTG exercises ready"
        val openAlarm = PendingIntent.getActivity(
            this,
            91_051,
            Intent(this, CooldownAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAlarm = PendingIntent.getService(
            this,
            91_052,
            Intent(this, CooldownRingingService::class.java).setAction(ACTION_STOP_RINGING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, RINGING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("GTG timer finished. Tap STOP ALARM to silence.")
            .setContentIntent(openAlarm)
            .setFullScreenIntent(openAlarm, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ALARM", stopAlarm)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startSound() {
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(audio)
                setDataSource(this@CooldownRingingService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val device = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!device.hasVibrator()) return
        vibrator = device
        val pattern = longArrayOf(0L, 550L, 250L, 550L, 950L)
        if (Build.VERSION.SDK_INT >= 26) {
            device.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            device.vibrate(pattern, 0)
        }
    }

    override fun onDestroy() {
        try {
            player?.stop()
        } catch (_: IllegalStateException) { }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        pendingNames.clear()
        super.onDestroy()
    }

    companion object {
        fun ring(context: Context, exercise: Exercise) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CooldownRingingService::class.java)
                    .setAction(ACTION_START_RINGING)
                    .putExtra(EXTRA_RING_NAME, exercise.displayName)
            )
        }

        fun test(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CooldownRingingService::class.java)
                    .setAction(ACTION_START_RINGING)
                    .putExtra(EXTRA_RING_NAME, "Test alarm")
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CooldownRingingService::class.java))
        }
    }
}

/** Simple large STOP button when the ongoing notification is tapped. */
class CooldownAlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(20, 23, 29))
        }
        val title = TextView(this).apply {
            text = "GTG TIMER FINISHED"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val message = TextView(this).apply {
            text = "Deadlift / RDL ready"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val stopButton = Button(this).apply {
            text = "STOP ALARM"
            textSize = 23f
            setOnClickListener {
                CooldownRingingService.stop(this@CooldownAlarmActivity)
                finish()
            }
        }
        content.addView(title, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(message, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 26 })
        content.addView(stopButton, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 48 })
        setContentView(content)
    }
}
