package com.arjunren.netsurvey.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arjunren.netsurvey.NetSurveyApplication
import com.arjunren.netsurvey.data.ReadingSort
import com.arjunren.netsurvey.data.filterSavedReadings
import com.arjunren.netsurvey.data.local.SavedWifiSnapshotEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SavedReadingsScreen(viewModel: MainViewModel) {
    val readings by viewModel.savedWifiSnapshots.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var band by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf(ReadingSort.NEWEST) }
    var deleting by remember { mutableStateOf<SavedWifiSnapshotEntity?>(null) }
    var editing by remember { mutableStateOf<SavedWifiSnapshotEntity?>(null) }
    var saving by remember { mutableStateOf(false) }
    var clearAll by remember { mutableStateOf(false) }
    var shareConfirmation by remember { mutableStateOf<List<SavedWifiSnapshotEntity>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val visible = remember(readings, query, band, sort) { filterSavedReadings(readings, query, band, sort) }
    val context = LocalContext.current
    val app = context.applicationContext as NetSurveyApplication
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize().testTag("readings-content"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeading("Your reading library", "${readings.size} snapshots • original signal values never change")
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search name, notes, SSID or BSSID") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All") + readings.map { it.band }.distinct().sorted()).forEach { value ->
                    FilterChip(band == value, { band = value }, { Text(value) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingSort.entries.forEach { value -> FilterChip(sort == value, { sort = value }, { Text(value.label) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = visible.isNotEmpty() && !exporting, onClick = { shareConfirmation = visible.toList() }) {
                    Icon(Icons.Default.Share, null)
                    Text(if (exporting) "Exporting…" else "Export ${visible.size} as CSV", Modifier.padding(start = 8.dp))
                }
                TextButton(enabled = readings.isNotEmpty(), onClick = { clearAll = true }) { Text("Clear all readings") }
            }
            exportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(visible, key = { it.id }) { reading ->
            SavedWifiReadingRow(reading, onDelete = { deleting = reading }, onEdit = { editing = reading })
        }
        if (visible.isEmpty()) item {
            EmptyState(if (readings.isEmpty()) "No saved readings yet" else "No matching readings",
                if (readings.isEmpty()) "Open Scanner, scan nearby networks, then tap a network to save its current signal and details." else "Try another search or band filter.")
        }
    }
    editing?.let { reading ->
        ReadingNotesDialog(reading, saving, { if (!saving) editing = null }) { name, description ->
            saving = true
            viewModel.editWifiSnapshot(reading.id, name, description) { success ->
                saving = false
                if (success) editing = null
            }
        }
    }
    deleting?.let { reading ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${reading.name}?") },
            text = { Text("This permanently removes this saved snapshot. Other readings and projects are not affected.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteWifiSnapshot(reading.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    if (clearAll) AlertDialog(onDismissRequest = { clearAll = false }, title = { Text("Clear all ${readings.size} readings?") },
        text = { Text("This permanently deletes every saved scanner reading, including readings hidden by your filters. Export first if you need a copy. Projects and surveys are not affected.") },
        confirmButton = { TextButton(onClick = { viewModel.clearWifiSnapshots(); clearAll = false; band = "All" }) { Text("Clear all") } },
        dismissButton = { TextButton(onClick = { clearAll = false }) { Text("Cancel") } })
    shareConfirmation?.let { captured ->
        AlertDialog(onDismissRequest = { shareConfirmation = null }, title = { Text("Export ${captured.size} readings?") },
            text = { Text("CSV includes names, notes, SSIDs, BSSIDs and capture times. Share only with trusted recipients. Only the currently filtered readings are included.") },
            confirmButton = { TextButton(onClick = {
                shareConfirmation = null
                exporting = true
                exportError = null
                scope.launch {
                    try {
                        val file = app.container.exports.exportSavedReadings(captured)
                        context.startActivity(Intent.createChooser(app.container.exports.shareIntent(file, "Saved Wi-Fi readings"), "Share CSV"))
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { exportError = error.message ?: "Could not export readings." }
                    finally { exporting = false }
                }
            }) { Text("Export & share") } },
            dismissButton = { TextButton(onClick = { shareConfirmation = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ReadingNotesDialog(reading: SavedWifiSnapshotEntity, saving: Boolean, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var name by remember(reading.id) { mutableStateOf(reading.name) }
    var description by remember(reading.id) { mutableStateOf(reading.description) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Edit reading notes") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${reading.rssi} dBm • ${reading.ssid} • ${reading.band}")
            OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, maxLines = 4)
            Text("Only the name and notes change. Captured measurements and timestamps remain unchanged.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && !saving, onClick = { save(name, description) }) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") } })
}
