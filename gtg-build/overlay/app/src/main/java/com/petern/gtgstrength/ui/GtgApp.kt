package com.petern.gtgstrength.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petern.gtgstrength.data.TrainingLogEntity
import com.petern.gtgstrength.domain.Exercise
import com.petern.gtgstrength.util.performStrongLogHaptic
import com.petern.gtgstrength.widget.requestWidgetRefresh
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class AppScreen(val label: String) {
    DASHBOARD("Dashboard"),
    LOG("Log"),
    SETTINGS("Settings"),
    DATA("Backup")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GtgApp(viewModel: GtgViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setCooldownAlarmEnabled(true) { requestWidgetRefresh(context) }
        } else {
            Toast.makeText(context, "Allow GTG notifications to hear timer alarms.", Toast.LENGTH_LONG).show()
        }
    }
    var quickLogPopupJob by remember { mutableStateOf<Job?>(null) }

    // Apply KEEP_SCREEN_ON to the actual Activity window. This is more reliable
    // than setting keepScreenOn on the ComposeView and requires no permission.
    DisposableEffect(activity, uiState.settings.keepScreenOn) {
        val window = activity?.window
        if (uiState.settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            if (uiState.settings.keepScreenOn) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun showQuickLogPopup(log: TrainingLogEntity) {
        performStrongLogHaptic(context)
        requestWidgetRefresh(context)

        quickLogPopupJob?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        quickLogPopupJob = scope.launch {
            val exerciseName = Exercise.fromStoredName(log.exercise).displayName
            val result = withTimeoutOrNull(3_000L) {
                snackbarHostState.showSnackbar(
                    message = "$exerciseName logged · ${log.reps} reps @ ${formatKg(log.weightKg)} kg",
                    actionLabel = "Cancel",
                    withDismissAction = false,
                    duration = SnackbarDuration.Indefinite
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoQuickLog(log)
                requestWidgetRefresh(context)
            } else if (result == null) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (screen) {
                            AppScreen.DASHBOARD -> "GTG Strength"
                            AppScreen.LOG -> "Training Log"
                            AppScreen.SETTINGS -> "Training Settings"
                            AppScreen.DATA -> "Backup & Data"
                        }
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == AppScreen.DASHBOARD,
                    onClick = { screen = AppScreen.DASHBOARD },
                    icon = { Icon(Icons.Outlined.FitnessCenter, contentDescription = null) },
                    label = { Text(AppScreen.DASHBOARD.label) }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.LOG,
                    onClick = { screen = AppScreen.LOG },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text(AppScreen.LOG.label) }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.SETTINGS,
                    onClick = { screen = AppScreen.SETTINGS },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(AppScreen.SETTINGS.label) }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.DATA,
                    onClick = { screen = AppScreen.DATA },
                    icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    label = { Text(AppScreen.DATA.label) }
                )
            }
        }
    ) { innerPadding ->
        when (screen) {
            AppScreen.DASHBOARD -> DashboardScreen(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                onQuickLog = { exercise, weight ->
                    viewModel.quickLog(
                        exercise = exercise,
                        weightKg = weight,
                        onLogged = ::showQuickLogPopup,
                        onBlocked = { remainingMillis ->
                            Toast.makeText(
                                context,
                                "${exercise.displayName} available in ${formatCooldown(remainingMillis)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            )

            AppScreen.LOG -> TrainingLogScreen(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                onUpdateLog = viewModel::updateLog,
                onDeleteLog = viewModel::deleteLog
            )

            AppScreen.SETTINGS -> SettingsScreen(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                onDeadliftOneRmChange = viewModel::setDeadliftOneRm,
                onRdlOneRmChange = viewModel::setRdlOneRm,
                onIntensityChange = viewModel::setIntensity,
                onDayPlanChange = viewModel::updateDayPlan,
                onDeadliftIncreaseFive = viewModel::increaseDeadliftProgramByFivePercent,
                onRdlIncreaseFive = viewModel::increaseRdlProgramByFivePercent,
                onEquipmentChange = viewModel::setBarbellEquipment,
                onKeepScreenOnChange = viewModel::setKeepScreenOn,
                onCooldownAlarmEnabledChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setCooldownAlarmEnabled(enabled) { requestWidgetRefresh(context) }
                    }
                },
                onDeadliftCooldownMinutesChange = { minutes ->
                    viewModel.setDeadliftCooldownMinutes(minutes) {
                        requestWidgetRefresh(context)
                    }
                },
                onRdlCooldownMinutesChange = { minutes ->
                    viewModel.setRdlCooldownMinutes(minutes) {
                        requestWidgetRefresh(context)
                    }
                },
                onCrossExerciseCooldownMinutesChange = { minutes ->
                    viewModel.setCrossExerciseCooldownMinutes(minutes) {
                        requestWidgetRefresh(context)
                    }
                }
            )

            AppScreen.DATA -> BackupScreen(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                createBackupJson = viewModel::createBackupJson,
                onRestoreBackup = { json, onResult ->
                    viewModel.restoreBackupJson(json) { result ->
                        requestWidgetRefresh(context)
                        onResult(result)
                    }
                },
                onRebuildProgress = viewModel::rebuildProgress
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
