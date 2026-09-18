package com.petern.gtgstrength.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.petern.gtgstrength.GtgApplication
import com.petern.gtgstrength.R
import com.petern.gtgstrength.data.PlateCalculator
import com.petern.gtgstrength.data.TrainingLogEntity
import com.petern.gtgstrength.data.TrainingSettings
import com.petern.gtgstrength.domain.Exercise
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

private const val ACTION_QUICK_DL = "com.petern.gtgstrength.widget.QUICK_DL"
private const val ACTION_QUICK_RDL = "com.petern.gtgstrength.widget.QUICK_RDL"
private const val ACTION_REFRESH = "com.petern.gtgstrength.widget.REFRESH"

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, ACTION_REFRESH -> {
                val result = goAsync()
                widgetScope().launch {
                    try {
                        GtgWidgetController.refreshAll(context)
                    } finally {
                        result.finish()
                    }
                }
            }

            ACTION_QUICK_DL, ACTION_QUICK_RDL -> {
                val result = goAsync()
                widgetScope().launch {
                    try {
                        val exercise = if (intent.action == ACTION_QUICK_DL) Exercise.DEADLIFT else Exercise.RDL
                        GtgWidgetController.quickLog(context, exercise)
                        GtgWidgetController.refreshAll(context)
                    } finally {
                        result.finish()
                    }
                }
            }

            else -> super.onReceive(context, intent)
        }
    }
}

class ProgressWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, ACTION_REFRESH -> {
                val result = goAsync()
                widgetScope().launch {
                    try {
                        GtgWidgetController.refreshAll(context)
                    } finally {
                        result.finish()
                    }
                }
            }

            else -> super.onReceive(context, intent)
        }
    }
}

private fun widgetScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO)

private data class WidgetSnapshot(
    val settings: TrainingSettings,
    val today: LocalDate,
    val logsToday: List<TrainingLogEntity>
) {
    val plan get() = settings.weeklyProgram.forDay(today.dayOfWeek)
    val deadliftSets: Int get() = logsToday.count { it.exercise == Exercise.DEADLIFT.storedName }
    val rdlSets: Int get() = logsToday.count { it.exercise == Exercise.RDL.storedName }
}

private object GtgWidgetController {
    suspend fun quickLog(context: Context, exercise: Exercise) {
        val app = context.applicationContext as GtgApplication
        val snapshot = load(context)
        if (snapshot.plan.isRestDay) return

        val planned = when (exercise) {
            Exercise.DEADLIFT -> snapshot.plan.deadlift
            Exercise.RDL -> snapshot.plan.rdl
        }
        if (planned.sets <= 0 || planned.reps <= 0) return

        val targetWeight = planned.minWeightKg
        val loading = PlateCalculator.calculate(targetWeight, snapshot.settings.barbellEquipment)
        val logWeight = if (loading.configured) loading.actualWeightKg else targetWeight

        app.trainingRepository.logSet(
            exercise = exercise,
            reps = planned.reps,
            weightKg = logWeight.coerceIn(0.0, 2000.0)
        )
    }

    suspend fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = load(context)

        manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            .forEach { renderToday(context, manager, it, snapshot) }

        manager.getAppWidgetIds(ComponentName(context, ProgressWidgetProvider::class.java))
            .forEach { renderProgress(context, manager, it, snapshot) }
    }

    private suspend fun load(context: Context): WidgetSnapshot {
        val app = context.applicationContext as GtgApplication
        val settings = app.settingsRepository.settings.first()
        val logs = app.trainingRepository.logs.first()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return WidgetSnapshot(
            settings = settings,
            today = today,
            logsToday = logs.filter { it.timestamp in start until end }
        )
    }

    private fun renderToday(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: WidgetSnapshot
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        val dayName = snapshot.today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        views.setTextViewText(R.id.widget_title, "GTG Strength · $dayName")
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context, 10_000 + widgetId))

        if (snapshot.plan.isRestDay) {
            views.setViewVisibility(R.id.widget_training_content, View.GONE)
            views.setViewVisibility(R.id.widget_rest_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_rest_text, "Rest day · Saturday")
        } else {
            views.setViewVisibility(R.id.widget_training_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_rest_text, View.GONE)

            val dlWeight = achievableWeight(snapshot.settings, snapshot.plan.deadlift.minWeightKg)
            val rdlWeight = achievableWeight(snapshot.settings, snapshot.plan.rdl.minWeightKg)

            views.setTextViewText(
                R.id.widget_deadlift_text,
                "Deadlift  ${snapshot.deadliftSets}/${snapshot.plan.deadlift.sets} sets"
            )
            views.setTextViewText(
                R.id.widget_deadlift_plan,
                "${snapshot.plan.deadlift.reps} reps @ ${formatKg(dlWeight)} kg"
            )
            views.setTextViewText(
                R.id.widget_rdl_text,
                "RDL  ${snapshot.rdlSets}/${snapshot.plan.rdl.sets} sets"
            )
            views.setTextViewText(
                R.id.widget_rdl_plan,
                "${snapshot.plan.rdl.reps} reps @ ${formatKg(rdlWeight)} kg"
            )

            views.setOnClickPendingIntent(
                R.id.widget_log_deadlift,
                quickLogPendingIntent(context, Exercise.DEADLIFT, 20_000 + widgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_log_rdl,
                quickLogPendingIntent(context, Exercise.RDL, 30_000 + widgetId)
            )
        }

        manager.updateAppWidget(widgetId, views)
    }

    private fun renderProgress(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        snapshot: WidgetSnapshot
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_progress)
        val day = snapshot.today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

        views.setOnClickPendingIntent(R.id.widget_progress_root, openAppPendingIntent(context))
        views.setOnClickPendingIntent(R.id.widget_progress_refresh, refreshPendingIntent(context, 40_000 + widgetId))

        if (snapshot.plan.isRestDay) {
            views.setTextViewText(R.id.widget_progress_title, "GTG · $day · Rest day")
            views.setTextViewText(R.id.widget_progress_sets, "Saturday off")
        } else {
            views.setTextViewText(R.id.widget_progress_title, "GTG · $day")
            views.setTextViewText(
                R.id.widget_progress_sets,
                "DL ${snapshot.deadliftSets}/${snapshot.plan.deadlift.sets}   ·   RDL ${snapshot.rdlSets}/${snapshot.plan.rdl.sets}"
            )
        }

        manager.updateAppWidget(widgetId, views)
    }

    private fun achievableWeight(settings: TrainingSettings, target: Double): Double {
        val loading = PlateCalculator.calculate(target, settings.barbellEquipment)
        return if (loading.configured) loading.actualWeightKg else target
    }
}

private fun openAppPendingIntent(context: Context): PendingIntent {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent().setPackage(context.packageName)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
        context,
        1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun quickLogPendingIntent(
    context: Context,
    exercise: Exercise,
    requestCode: Int
): PendingIntent {
    val action = if (exercise == Exercise.DEADLIFT) ACTION_QUICK_DL else ACTION_QUICK_RDL
    val intent = Intent(context, TodayWidgetProvider::class.java).setAction(action)
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun refreshPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_REFRESH)
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun formatKg(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    val whole = rounded.toLong()
    return if (abs(rounded - whole.toDouble()) < 0.001) {
        whole.toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
