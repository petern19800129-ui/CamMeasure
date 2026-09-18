package com.petern.gtgstrength.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Strong double-pulse confirmation for a successfully saved set.
 *
 * Home-screen widget actions execute while the app is backgrounded, so they
 * need vibration attributes that Android permits for background vibration.
 */
fun performStrongLogHaptic(context: Context, background: Boolean = false) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return

    // Two unmistakable pulses at full amplitude.
    val effect = VibrationEffect.createWaveform(
        longArrayOf(0L, 80L, 45L, 120L),
        intArrayOf(0, 255, 0, 255),
        -1
    )

    if (background) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            @Suppress("DEPRECATION")
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attributes)
        }
    } else {
        vibrator.vibrate(effect)
    }
}
