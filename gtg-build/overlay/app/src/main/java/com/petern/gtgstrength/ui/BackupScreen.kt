package com.petern.gtgstrength.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen(
    uiState: GtgUiState,
    modifier: Modifier = Modifier,
    createBackupJson: () -> String,
    onRestoreBackup: (String, (String) -> Unit) -> Unit,
    onRebuildProgress: ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    val backupNameFormatter = remember { DateTimeFormatter.ofPattern("'gtg-Backup-'yyMMdd-HHmm'.json'") }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val json = createBackupJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                            writer.write(json)
                        } ?: error("Could not open backup destination")
                    }
                }
                status = result.fold(
                    onSuccess = { "Backup saved successfully." },
                    onFailure = { "Backup failed: ${it.message ?: "unknown error"}" }
                )
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not open backup file")
                    }
                }
                result.onSuccess { pendingRestoreJson = it }
                    .onFailure { status = "Could not read backup: ${it.message ?: "unknown error"}" }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Backup & restore",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Backup includes your training settings, weekly program, bar & plate stock, and all ${uiState.logs.size} logged sets. The file is normal JSON and can be kept in Drive, local storage, or another backup location.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        createBackupLauncher.launch(LocalDateTime.now().format(backupNameFormatter))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create backup")
                }
                OutlinedButton(
                    onClick = { openBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore backup")
                }
                Text(
                    text = "Restoring replaces the current settings and training history with the selected backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Rebuild progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Recalculate today's and weekly progress directly from the saved training history. No logged sets are deleted or changed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { onRebuildProgress { status = it } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rebuild progress now")
                }
            }
        }

        status?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    pendingRestoreJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("Restore this backup?") },
            text = {
                Text("Your current settings, bar & plate stock, and training history will be replaced. Create a backup first if you want to keep the current data.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreJson = null
                        onRestoreBackup(json) { status = it }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
