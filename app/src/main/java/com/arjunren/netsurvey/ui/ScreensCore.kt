package com.arjunren.netsurvey.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arjunren.netsurvey.data.local.ProjectEntity
import com.arjunren.netsurvey.data.local.SavedWifiSnapshotEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navigate: (AppScreen) -> Unit) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val floors by viewModel.floors.collectAsStateWithLifecycle()
    val points by viewModel.surveyPoints.collectAsStateWithLifecycle()
    val aps by viewModel.accessPoints.collectAsStateWithLifecycle()
    val surveys by viewModel.surveys.collectAsStateWithLifecycle()
    val rows by viewModel.surveyRows.collectAsStateWithLifecycle()
    val savedReadings by viewModel.savedWifiSnapshots.collectAsStateWithLifecycle()
    var confirmClearReadings by remember { mutableStateOf(false) }
    val weakCount = rows.groupBy { it.pointId }.count { (_, values) -> values.maxOfOrNull { it.displayedRssi }?.let { it < -72 } == true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(22.dp)) {
                    Text("Field-ready Wi-Fi intelligence", style = MaterialTheme.typography.headlineMedium)
                    Text("Measurements stay on this device. Predicted and interpolated values are always identified.", modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Projects", projects.size.toString(), Icons.Default.Apartment)
                MetricCard("Floors", floors.size.toString(), Icons.Default.Layers)
                MetricCard("Surveys", surveys.size.toString(), Icons.Default.Map)
                MetricCard("Points", points.size.toString(), Icons.Default.Speed)
                MetricCard("Access points", aps.size.toString(), Icons.Default.NetworkWifi)
                MetricCard("Weak zones", weakCount.toString(), Icons.Default.Map)
                MetricCard("Saved readings", savedReadings.size.toString(), Icons.Default.NetworkWifi)
            }
        }
        item { Text("Quick actions", style = MaterialTheme.typography.titleLarge) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("New / open project", Icons.Default.Apartment) { navigate(AppScreen.Projects) }
                QuickAction("Start Wi-Fi scan", Icons.Default.NetworkWifi) { navigate(AppScreen.Scanner) }
                QuickAction("Signal meter", Icons.Default.Speed) { navigate(AppScreen.Signal) }
                QuickAction("Start survey", Icons.Default.Map) { navigate(AppScreen.Survey) }
                QuickAction("Generate report", Icons.Default.PictureAsPdf) { navigate(AppScreen.Reports) }
            }
        }
        if (surveys.isNotEmpty()) {
            item { Text("Latest survey", style = MaterialTheme.typography.titleLarge) }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(surveys.first().name, style = MaterialTheme.typography.titleMedium)
                        Text("${surveys.first().type} • ${DateFormat.getDateTimeInstance().format(Date(surveys.first().startedAt))}")
                    }
                }
            }
        }
        if (savedReadings.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Saved Wi-Fi readings", style = MaterialTheme.typography.titleLarge)
                        Text("Frozen scanner values stored in the local database")
                    }
                    TextButton(onClick = { confirmClearReadings = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Text("Clear all")
                    }
                }
            }
            items(savedReadings, key = { "saved-${it.id}" }) { reading ->
                SavedWifiReadingRow(reading, onDelete = { viewModel.deleteWifiSnapshot(reading.id) })
            }
        }
    }

    if (confirmClearReadings) {
        AlertDialog(
            onDismissRequest = { confirmClearReadings = false },
            title = { Text("Clear saved readings?") },
            text = { Text("This permanently deletes all saved Wi-Fi scanner snapshots from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearWifiSnapshots()
                    confirmClearReadings = false
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClearReadings = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SavedWifiReadingRow(reading: SavedWifiSnapshotEntity, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reading.name, style = MaterialTheme.typography.titleMedium)
                    if (reading.description.isNotBlank()) Text(reading.description)
                }
                Text("${reading.rssi} dBm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${reading.name}") }
            }
            Text(reading.ssid.ifBlank { "Hidden SSID" }, style = MaterialTheme.typography.titleSmall)
            Text(reading.bssid, style = MaterialTheme.typography.bodySmall)
            Text("${reading.band} • channel ${reading.channel ?: "?"} • ${reading.frequencyMhz} MHz${reading.channelWidthMhz?.let { " • $it MHz" }.orEmpty()}")
            reading.wifiStandard?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            if (reading.capabilities.isNotBlank()) Text(reading.capabilities, style = MaterialTheme.typography.labelSmall)
            Text(
                "Observed ${DateFormat.getDateTimeInstance().format(Date(reading.observedAt))} • saved ${DateFormat.getDateTimeInstance().format(Date(reading.savedAt))}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun ProjectsScreen(viewModel: MainViewModel) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val floors by viewModel.floors.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val floorPlan by viewModel.floorPlan.collectAsStateWithLifecycle()
    var createProject by remember { mutableStateOf(false) }
    var creatingProject by remember { mutableStateOf(false) }
    var addFloor by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::importFloorPlan) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Local projects", style = MaterialTheme.typography.headlineSmall)
                Text("No account, backend, analytics, or cloud sync")
                Button(
                    onClick = { createProject = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Create project", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        items(projects, key = { it.id }) { project ->
            ProjectRow(project, selection.projectId == project.id) { viewModel.selectProject(project.id) }
        }
        if (projects.isEmpty()) {
            item { EmptyState("No projects yet", "Create a project to start documenting a site.") }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Floors", style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(onClick = { addFloor = true }) { Icon(Icons.Default.Add, null); Text("Floor") }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    floors.forEach { floor ->
                        FilterChip(
                            selected = floor.id == selection.floorId,
                            onClick = { viewModel.selectFloor(floor.id) },
                            label = { Text(floor.name) },
                        )
                    }
                }
            }
            if (selection.floorId != null) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("Floor plan", style = MaterialTheme.typography.titleMedium)
                            Text(floorPlan?.let { "${it.width} × ${it.height} • ${it.mimeType}" } ?: "No floor plan imported")
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Default.Image, null)
                                Text(if (floorPlan == null) "Import PNG/JPEG" else "Replace floor plan", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (createProject) {
        ProjectDialog(
            saving = creatingProject,
            onDismiss = { if (!creatingProject) createProject = false },
        ) { name, customer, engineer ->
            creatingProject = true
            viewModel.createProject(name, customer, engineer) { success ->
                creatingProject = false
                if (success) createProject = false
            }
        }
    }
    if (addFloor) {
        TextEntryDialog("Add floor", "Floor name", onDismiss = { addFloor = false }) { name ->
            viewModel.addFloor(name)
            addFloor = false
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectEntity, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Apartment, contentDescription = null, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(project.customer.ifBlank { "No customer/site name" })
                Text("Updated ${DateFormat.getDateInstance().format(Date(project.updatedAt))}", style = MaterialTheme.typography.labelSmall)
            }
            if (selected) AssistChip(onClick = onClick, label = { Text("Active") })
        }
    }
}

@Composable
private fun ProjectDialog(saving: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var engineer by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name *") }, singleLine = true)
                OutlinedTextField(customer, { customer = it }, label = { Text("Customer / site") }, singleLine = true)
                OutlinedTextField(engineer, { engineer = it }, label = { Text("Engineer") }, singleLine = true)
                if (name.isBlank()) Text("Enter a project name to enable Create.", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !saving, onClick = { onSave(name, customer, engineer) }) { Text(if (saving) "Creating…" else "Create") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextEntryDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(title: String, body: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun WifiPermissionCard(onGranted: () -> Unit = {}) {
    val context = LocalContext.current
    val permissions = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) onGranted()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Wi-Fi access", style = MaterialTheme.typography.titleMedium)
            Text("Android requires precise location for active Wi-Fi scan results, plus Nearby Wi-Fi on Android 13+. NetSurvey does not record GPS location.")
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(permissions) }) { Text("Grant permissions") }
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Location settings") }
            }
        }
    }
}
