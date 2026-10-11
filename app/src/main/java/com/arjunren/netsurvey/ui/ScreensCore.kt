package com.arjunren.netsurvey.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.testTag
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
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val plan by viewModel.floorPlan.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val weakCount = rows.groupBy { it.pointId }.count { (_, values) -> values.maxOfOrNull { it.displayedRssi }?.let { it < settings.acceptableMin } == true }
    val selectedFloor = floors.firstOrNull { it.id == selection.floorId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard-content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(color = androidx.compose.ui.graphics.Color(0xFF132F43), contentColor = androidx.compose.ui.graphics.Color.White, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("NETSURVEY / FIELD WORKSPACE", style = MaterialTheme.typography.labelMedium)
                    Text("Know your network.\nPlan with confidence.", style = MaterialTheme.typography.headlineMedium)
                    Text("Scan, capture and map your Wi-Fi. Your measurements stay on this device.")
                    FilledTonalButton(onClick = { navigate(AppScreen.Scanner) }) { Icon(Icons.Default.NetworkWifi, null); Text("Scan nearby networks", Modifier.padding(start = 8.dp)) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Projects", projects.size.toString(), Icons.Default.Apartment, Modifier.weight(1f))
                    MetricCard("Saved readings", savedReadings.size.toString(), Icons.Default.NetworkWifi, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Measured points", points.size.toString(), Icons.Default.Speed, Modifier.weight(1f))
                    MetricCard("Weak zones", weakCount.toString(), Icons.Default.Map, Modifier.weight(1f))
                }
                Text("Points and weak zones refer to the active survey.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(activeProject?.name ?: "Set up your first site", style = MaterialTheme.typography.titleLarge)
                    Text(selectedFloor?.let { "${it.name} • ${surveys.size} sessions • ${aps.size} access points" } ?: "Create a project, add a floor, then import your floor plan.")
                    Text("${if (activeProject != null) "✓" else "1"} Project   /   ${if (selectedFloor != null) "✓" else "2"} Floor   /   ${if (plan != null) "✓" else "3"} Plan", style = MaterialTheme.typography.labelLarge)
                    if (plan != null) Text(if (selectedFloor?.calibrationMeters != null) "Scale calibrated • ready for a site survey" else "Next: calibrate scale in Survey using a known distance.", style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = { navigate(if (plan == null) AppScreen.Projects else AppScreen.Survey) }) {
                        Text(if (activeProject == null) "Create your first project" else if (plan == null) "Continue site setup" else "Open site survey")
                    }
                }
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
                    Column(Modifier.weight(1f)) {
                        Text("Saved Wi-Fi readings", style = MaterialTheme.typography.titleLarge)
                        Text("Recent snapshots • frozen at capture", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { navigate(AppScreen.Readings) }) {
                        Text("View all")
                    }
                }
            }
            items(savedReadings.take(3), key = { "saved-${it.id}" }) { reading ->
                SavedWifiReadingRow(reading)
            }
        }
    }

}

@Composable
fun SavedWifiReadingRow(reading: SavedWifiSnapshotEntity, onDelete: (() -> Unit)? = null, onEdit: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reading.name, style = MaterialTheme.typography.titleMedium)
                    if (reading.description.isNotBlank()) Text(reading.description)
                }
                Text("${reading.rssi} dBm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (onEdit != null || onDelete != null) Row {
                onEdit?.let { TextButton(onClick = it) { Icon(Icons.Default.Edit, null); Text("Edit notes", Modifier.padding(start = 6.dp)) } }
                onDelete?.let { TextButton(onClick = it) { Icon(Icons.Default.Delete, null); Text("Delete", Modifier.padding(start = 6.dp)) } }
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
private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
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
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleting by remember { mutableStateOf<ProjectEntity?>(null) }
    var updating by remember { mutableStateOf(false) }
    val visibleProjects = projects.filter { listOf(it.name, it.customer, it.address, it.engineer).any { text -> text.contains(query.trim(), ignoreCase = true) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::importFloorPlan) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Site projects", "${projects.size} local projects • tap a site to make it active")
                Button(
                    onClick = { createProject = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Create project", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search projects") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            }
        }
        items(visibleProjects, key = { it.id }) { project ->
            ProjectRow(project, selection.projectId == project.id, { editing = project }, { deleting = project }) { viewModel.selectProject(project.id) }
        }
        if (projects.isNotEmpty() && visibleProjects.isEmpty()) item { EmptyState("No matching projects", "Try another project name, customer, address or engineer.") }
        if (projects.isEmpty()) {
            item { EmptyState("No projects yet", "Create a project to start documenting a site.") }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Active site's floors", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(enabled = selection.projectId != null, onClick = { addFloor = true }) { Icon(Icons.Default.Add, null); Text("Floor") }
                }
            }
            if (floors.isEmpty()) item { EmptyState("Add your first floor", "Use + Floor above, then import its PNG or JPEG floor plan.") }
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
    editing?.let { project ->
        EditProjectDialog(project, updating, { if (!updating) editing = null }) { changed ->
            updating = true
            viewModel.editProject(changed) { success -> updating = false; if (success) editing = null }
        }
    }
    deleting?.let { project ->
        AlertDialog(onDismissRequest = { if (!updating) deleting = null }, title = { Text("Delete ${project.name}?") },
            text = { Text("This permanently deletes the project and its floors, surveys, measurements, access points and report records. Export a project backup in Reports first if needed. Standalone scanner readings are kept.") },
            confirmButton = { TextButton(enabled = !updating, onClick = { updating = true; viewModel.deleteProject(project.id) { success -> updating = false; if (success) deleting = null } }) { Text(if (updating) "Deleting…" else "Delete project") } },
            dismissButton = { TextButton(enabled = !updating, onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ProjectRow(project: ProjectEntity, selected: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick, colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apartment, contentDescription = null, modifier = Modifier.size(36.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                    Text(project.customer.ifBlank { "No customer/site name" })
                    Text("Updated ${DateFormat.getDateInstance().format(Date(project.updatedAt))}", style = MaterialTheme.typography.labelSmall)
                }
                if (selected) AssistChip(onClick = onClick, label = { Text("Active") })
            }
            Row {
                TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Text("Edit", Modifier.padding(start = 6.dp)) }
                TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("Delete", Modifier.padding(start = 6.dp)) }
            }
        }
    }
}

@Composable
private fun EditProjectDialog(project: ProjectEntity, saving: Boolean, dismiss: () -> Unit, save: (ProjectEntity) -> Unit) {
    var draft by remember(project.id) { mutableStateOf(project) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Edit project") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("Project name *") }, singleLine = true)
            OutlinedTextField(draft.customer, { draft = draft.copy(customer = it) }, label = { Text("Customer / site") }, singleLine = true)
            OutlinedTextField(draft.engineer, { draft = draft.copy(engineer = it) }, label = { Text("Engineer") }, singleLine = true)
            OutlinedTextField(draft.address, { draft = draft.copy(address = it) }, label = { Text("Site address") })
            OutlinedTextField(draft.notes, { draft = draft.copy(notes = it) }, label = { Text("Site notes") }, minLines = 2, maxLines = 4)
        }
    }, confirmButton = { TextButton(enabled = draft.name.isNotBlank() && !saving, onClick = { save(draft) }) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") } })
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
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val lifecycleOwner = LocalLifecycleOwner.current
    fun accessReady(): Boolean {
        val location = context.getSystemService<LocationManager>()
        val locationEnabled = if (Build.VERSION.SDK_INT >= 28) location?.isLocationEnabled == true
        else location?.let { it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } == true
        return locationEnabled && permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
    var ready by remember { mutableStateOf(accessReady()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ -> ready = accessReady() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (ready) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Wi-Fi access", style = MaterialTheme.typography.titleMedium)
            Text("Android requires precise location for active Wi-Fi scan results, plus Nearby Wi-Fi on Android 13+. NetSurvey does not record GPS location.")
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(permissions) }) { Text("Grant permissions") }
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Location settings") }
            }
        }
    }
}
