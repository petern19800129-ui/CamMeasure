package com.petern.gtgstrength.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petern.gtgstrength.data.TrainingLogEntity
import com.petern.gtgstrength.domain.Exercise
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class AppScreen(val label: String) {
    DASHBOARD("Dashboard"),
    LOG("Training Log"),
    DATA("Backup")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GtgApp(viewModel: GtgViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var quickLogPopupJob by remember { mutableStateOf<Job?>(null) }

    fun showQuickLogPopup(log: TrainingLogEntity) {
        // Only the newest quick-log entry keeps an active Cancel window.
        quickLogPopupJob?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()

        quickLogPopupJob = scope.launch {
            val exerciseName = Exercise.fromStoredName(log.exercise).displayName
            val result = withTimeoutOrNull(3_000L) {
                snackbarHostState.showSnackbar(
                    message = "$exerciseName logged • ${log.reps} reps @ ${formatKg(log.weightKg)} kg",
                    actionLabel = "Cancel",
                    withDismissAction = false,
                    duration = SnackbarDuration.Indefinite
                )
            }

            if (result == SnackbarResult.ActionPerformed) {
                // Delete only the exact row that was created by this tap.
                viewModel.deleteLog(log)
            } else if (result == null) {
                // The 3-second confirmation window expired normally.
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
                    selected = screen == AppScreen.DATA,
                    onClick = { screen = AppScreen.DATA },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(AppScreen.DATA.label) }
                )
            }
        }
    ) { innerPadding ->
        when (screen) {
            AppScreen.DASHBOARD -> DashboardScreen(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                onDeadliftOneRmChange = viewModel::setDeadliftOneRm,
                onRdlOneRmChange = viewModel::setRdlOneRm,
                onIntensityChange = viewModel::setIntensity,
                onDeadliftTargetSetsChange = viewModel::setDeadliftTargetSets,
                onDeadliftRepsChange = viewModel::setDeadliftRepsPerSet,
                onRdlTargetSetsChange = viewModel::setRdlTargetSets,
                onRdlRepsChange = viewModel::setRdlRepsPerSet,
                onQuickLog = { exercise ->
                    viewModel.quickLog(exercise, ::showQuickLogPopup)
                }
            )

            AppScreen.LOG -> TrainingLogScreen(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                onUpdateLog = viewModel::updateLog,
                onDeleteLog = viewModel::deleteLog
            )

            AppScreen.DATA -> BackupScreen(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                createBackupJson = viewModel::createBackupJson,
                onRestoreBackup = viewModel::restoreBackupJson,
                onRebuildProgress = viewModel::rebuildProgress
            )
        }
    }
}
