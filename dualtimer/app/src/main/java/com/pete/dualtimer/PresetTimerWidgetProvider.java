package com.pete.dualtimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Locale;

public class PresetTimerWidgetProvider extends AppWidgetProvider {
    private static final String WIDGET_PREFS = "dual_timer_widgets";
    private static final String PRESET_PREFS = "dual_timer_presets";

    private static final String ACTION_START =
            "com.pete.dualtimer.action.WIDGET_START";
    private static final String ACTION_FINISH =
            "com.pete.dualtimer.action.WIDGET_FINISH";

    private static final String KEY_TIMER = "timer_";
    private static final String KEY_PRESET = "preset_";
    private static final String KEY_STATE = "state_";
    private static final String KEY_END_EPOCH = "end_epoch_";

    private static final int STATE_IDLE = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_FINISHED = 2;

    private static final long[] DEFAULT_PRESETS = new long[]{
            5 * 60_000L,
            10 * 60_000L,
            15 * 60_000L,
            30 * 60_000L
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        int appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }

        if (ACTION_START.equals(action)) {
            startTimer(context, appWidgetId);
        } else if (ACTION_FINISH.equals(action)) {
            finishTimer(context, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences prefs =
                context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        for (int appWidgetId : appWidgetIds) {
            cancelFinishAlarm(context, appWidgetId);
            editor.remove(KEY_TIMER + appWidgetId);
            editor.remove(KEY_PRESET + appWidgetId);
            editor.remove(KEY_STATE + appWidgetId);
            editor.remove(KEY_END_EPOCH + appWidgetId);
        }
        editor.apply();
    }

    public static void saveConfig(Context context, int appWidgetId,
                                  int timerIndex, int presetIndex) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_TIMER + appWidgetId, timerIndex)
                .putInt(KEY_PRESET + appWidgetId, presetIndex)
                .putInt(KEY_STATE + appWidgetId, STATE_IDLE)
                .putLong(KEY_END_EPOCH + appWidgetId, 0L)
                .apply();
    }

    public static int getConfiguredTimer(Context context, int appWidgetId) {
        return context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TIMER + appWidgetId, 0);
    }

    public static int getConfiguredPreset(Context context, int appWidgetId) {
        return context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_PRESET + appWidgetId, 0);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component =
                new ComponentName(context, PresetTimerWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    public static void updateWidget(Context context,
                                    AppWidgetManager manager,
                                    int appWidgetId) {
        SharedPreferences widgetPrefs =
                context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE);

        int timerIndex = clamp(widgetPrefs.getInt(
                KEY_TIMER + appWidgetId, 0), 0, 1);
        int presetIndex = clamp(widgetPrefs.getInt(
                KEY_PRESET + appWidgetId, 0), 0, 3);
        int state = widgetPrefs.getInt(
                KEY_STATE + appWidgetId, STATE_IDLE);
        long endEpoch = widgetPrefs.getLong(
                KEY_END_EPOCH + appWidgetId, 0L);

        long duration = getPresetDuration(context, timerIndex, presetIndex);
        long now = System.currentTimeMillis();

        if (state == STATE_RUNNING && endEpoch <= now) {
            state = STATE_FINISHED;
            endEpoch = 0L;
            widgetPrefs.edit()
                    .putInt(KEY_STATE + appWidgetId, STATE_FINISHED)
                    .putLong(KEY_END_EPOCH + appWidgetId, 0L)
                    .apply();
        }

        RemoteViews views =
                new RemoteViews(context.getPackageName(), R.layout.widget_preset_timer);

        views.setTextViewText(
                R.id.widget_label,
                "T" + (timerIndex + 1) + "  P" + (presetIndex + 1));

        PendingIntent startIntent = startPendingIntent(context, appWidgetId);
        views.setOnClickPendingIntent(R.id.widget_root, startIntent);

        if (state == STATE_RUNNING && endEpoch > now) {
            long remaining = endEpoch - now;
            long base = SystemClock.elapsedRealtime() + remaining;

            views.setViewVisibility(R.id.widget_time, View.GONE);
            views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE);
            views.setChronometer(R.id.widget_chronometer, base, null, true);
            views.setChronometerCountDown(R.id.widget_chronometer, true);
        } else {
            views.setViewVisibility(R.id.widget_chronometer, View.GONE);
            views.setViewVisibility(R.id.widget_time, View.VISIBLE);
            views.setChronometer(
                    R.id.widget_chronometer,
                    SystemClock.elapsedRealtime(),
                    null,
                    false);

            if (state == STATE_FINISHED) {
                views.setTextViewText(R.id.widget_time, "DONE");
                views.setTextColor(R.id.widget_time, 0xFFC62828);
            } else {
                views.setTextViewText(
                        R.id.widget_time,
                        formatDuration(duration));
                views.setTextColor(R.id.widget_time, 0xFF202020);
            }
        }

        manager.updateAppWidget(appWidgetId, views);
    }

    private static void startTimer(Context context, int appWidgetId) {
        SharedPreferences widgetPrefs =
                context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE);

        int timerIndex = clamp(widgetPrefs.getInt(
                KEY_TIMER + appWidgetId, 0), 0, 1);
        int presetIndex = clamp(widgetPrefs.getInt(
                KEY_PRESET + appWidgetId, 0), 0, 3);

        long duration = getPresetDuration(context, timerIndex, presetIndex);
        long endEpoch = System.currentTimeMillis() + duration;

        widgetPrefs.edit()
                .putInt(KEY_STATE + appWidgetId, STATE_RUNNING)
                .putLong(KEY_END_EPOCH + appWidgetId, endEpoch)
                .apply();

        scheduleFinishAlarm(context, appWidgetId, endEpoch);
        updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                appWidgetId);
    }

    private static void finishTimer(Context context, int appWidgetId) {
        SharedPreferences widgetPrefs =
                context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE);

        int state = widgetPrefs.getInt(
                KEY_STATE + appWidgetId, STATE_IDLE);
        long endEpoch = widgetPrefs.getLong(
                KEY_END_EPOCH + appWidgetId, 0L);

        if (state != STATE_RUNNING || endEpoch <= 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        if (endEpoch > now + 1000L) {
            scheduleFinishAlarm(context, appWidgetId, endEpoch);
            return;
        }

        widgetPrefs.edit()
                .putInt(KEY_STATE + appWidgetId, STATE_FINISHED)
                .putLong(KEY_END_EPOCH + appWidgetId, 0L)
                .apply();

        updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                appWidgetId);
        alertFinished(context);
    }

    private static long getPresetDuration(Context context,
                                          int timerIndex,
                                          int presetIndex) {
        SharedPreferences presetPrefs =
                context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE);
        return presetPrefs.getLong(
                "timer" + timerIndex + "_preset" + presetIndex,
                DEFAULT_PRESETS[presetIndex]);
    }

    private static void scheduleFinishAlarm(Context context,
                                            int appWidgetId,
                                            long endEpoch) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent finishIntent = finishPendingIntent(context, appWidgetId);
        alarmManager.cancel(finishIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endEpoch,
                        finishIntent);
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        endEpoch,
                        finishIntent);
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endEpoch,
                        finishIntent);
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        endEpoch,
                        finishIntent);
            }
        }
    }

    private static void cancelFinishAlarm(Context context, int appWidgetId) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(finishPendingIntent(context, appWidgetId));
        }
    }

    private static PendingIntent startPendingIntent(Context context,
                                                    int appWidgetId) {
        Intent intent = new Intent(
                context,
                PresetTimerWidgetProvider.class);
        intent.setAction(ACTION_START);
        intent.putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId);

        return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent finishPendingIntent(Context context,
                                                     int appWidgetId) {
        Intent intent = new Intent(
                context,
                PresetTimerWidgetProvider.class);
        intent.setAction(ACTION_FINISH);
        intent.putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId);

        return PendingIntent.getBroadcast(
                context,
                100000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE);
    }

    private static void alertFinished(Context context) {
        ToneGenerator tone =
                new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        tone.startTone(
                ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
                1400);
        new Handler(Looper.getMainLooper()).postDelayed(
                tone::release,
                1600);

        Vibrator vibrator =
                (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern =
                    new long[]{0, 300, 150, 300, 150, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(pattern, -1));
            } else {
                //noinspection deprecation
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return String.format(
                    Locale.US,
                    "%d:%02d:%02d",
                    hours,
                    minutes,
                    seconds);
        }
        return String.format(
                Locale.US,
                "%02d:%02d",
                minutes,
                seconds);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
