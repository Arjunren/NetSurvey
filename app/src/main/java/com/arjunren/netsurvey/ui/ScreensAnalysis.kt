package com.arjunren.netsurvey.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arjunren.netsurvey.NetSurveyApplication
import com.arjunren.netsurvey.BuildConfig
import com.arjunren.netsurvey.data.AppSettings
import com.arjunren.netsurvey.data.local.SurveyObservationRow
import com.arjunren.netsurvey.domain.HeatCell
import com.arjunren.netsurvey.domain.IdwInterpolator
import com.arjunren.netsurvey.domain.Measurement
import com.arjunren.netsurvey.domain.Point2d
import com.arjunren.netsurvey.domain.scoreCandidate
import com.arjunren.netsurvey.wifi.ScanStatus
import com.arjunren.netsurvey.wifi.WifiNetwork
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HeatmapScreen(viewModel: MainViewModel) {
    val rows by viewModel.surveyRows.collectAsStateWithLifecycle()
    val surveys by viewModel.surveys.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ssids = rows.map { it.ssid }.filter { it.isNotBlank() }.distinct().sorted()
    var selectedSsid by remember { mutableStateOf("") }
    var showMarkers by remember { mutableStateOf(true) }
    val measurements = remember(rows, selectedSsid) { strongestMeasurements(rows.filter { selectedSsid.isBlank() || it.ssid == selectedSsid }) }
    val grid by produceState<List<HeatCell>>(emptyList(), measurements, settings.heatmapResolution) {
        value = withContext(Dispatchers.Default) {
            if (measurements.isEmpty()) emptyList() else IdwInterpolator.interpolate(measurements, settings.heatmapResolution.coerceIn(10, 100), (settings.heatmapResolution * .72).toInt().coerceAtLeast(8))
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Measured RSSI heatmap", style = MaterialTheme.typography.headlineSmall)
            Text("Black markers are actual measurements. Colors between them are deterministic inverse-distance-weighted interpolation.")
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedSsid.isBlank(), { selectedSsid = "" }, { Text("Best available AP") })
                ssids.forEach { ssid -> FilterChip(selectedSsid == ssid, { selectedSsid = ssid }, { Text(ssid) }) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(showMarkers, { showMarkers = it }); Text("Show measured point markers") }
        }
        item { HeatmapCanvas(grid, measurements, settings.heatmapResolution, settings.heatmapOpacity, showMarkers) }
        item { HeatLegend() }
        if (measurements.isEmpty()) item { EmptyState("No measurements", "Select a survey with saved Wi-Fi observations.") }
        item {
            Text("Survey session", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                surveys.forEach { survey -> FilterChip(selection.surveyId == survey.id, { viewModel.selectSurvey(survey.id) }, { Text(survey.name) }) }
            }
        }
    }
}

private fun strongestMeasurements(rows: List<SurveyObservationRow>): List<Measurement> = rows.groupBy { it.pointId }.values.mapNotNull { pointRows ->
    val strongest = pointRows.maxByOrNull { it.displayedRssi } ?: return@mapNotNull null
    Measurement(strongest.xNormalized, strongest.yNormalized, strongest.displayedRssi.toDouble())
}

@Composable
private fun HeatmapCanvas(grid: List<HeatCell>, points: List<Measurement>, columns: Int, opacity: Float, markers: Boolean) {
    val rows = if (columns > 0) (grid.size / columns).coerceAtLeast(1) else 1
    Box(Modifier.fillMaxWidth().height(480.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)) {
        Canvas(Modifier.fillMaxSize()) {
            grid.forEach { cell ->
                val color = heatColor(cell.rssi).copy(alpha = opacity)
                val cellW = size.width / columns.coerceAtLeast(1)
                val cellH = size.height / rows
                drawRect(color, Offset((cell.x * size.width).toFloat(), (cell.y * size.height).toFloat()), androidx.compose.ui.geometry.Size(cellW + 1, cellH + 1))
            }
            if (markers) points.forEach { point ->
                val center = Offset((point.x * size.width).toFloat(), (point.y * size.height).toFloat())
                drawCircle(Color.Black, 9f, center)
                drawCircle(Color.White, 9f, center, style = Stroke(2f))
            }
        }
    }
}

@Composable
private fun HeatLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Excellent ≥ -55" to Color(0xFF28A745), "Good ≥ -67" to Color(0xFFC5F36B), "Acceptable ≥ -72" to Color(0xFFFFC107), "Weak ≥ -80" to Color(0xFFFF7730), "Poor < -80" to Color(0xFFC9302C)).forEach { (label, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.background(color).height(12.dp).fillMaxWidth(.12f)); Text(label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun heatColor(rssi: Double): Color = when { rssi >= -55 -> Color(0xFF28A745); rssi >= -67 -> Color(0xFFC5F36B); rssi >= -72 -> Color(0xFFFFC107); rssi >= -80 -> Color(0xFFFF7730); else -> Color(0xFFC9302C) }

@Composable
fun ChannelAnalyzerScreen(viewModel: MainViewModel) {
    val status by viewModel.scanStatus.collectAsStateWithLifecycle()
    val all = (status as? ScanStatus.Results)?.networks.orEmpty()
    var band by remember { mutableStateOf("2.4 GHz") }
    val networks = all.filter { it.band == band }
    val channels = networks.groupBy { it.channel }.toSortedMap(compareBy<Int?> { it ?: Int.MAX_VALUE })
    val suggestion = remember(networks, band) { channelSuggestion(networks, band) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Channel analysis", style = MaterialTheme.typography.headlineSmall)
            Text("Network counts and RSSI indicate potential competition, not measured airtime utilization.")
        }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("2.4 GHz", "5 GHz", "6 GHz").forEach { FilterChip(band == it, { band = it }, { Text(it) }) } } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::requestScan) { Text("Refresh scan") }
                Text("${networks.size} BSSIDs", modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Advisory candidate", style = MaterialTheme.typography.titleMedium)
                    Text(suggestion.first, style = MaterialTheme.typography.headlineSmall)
                    Text(suggestion.second)
                }
            }
        }
        items(channels.entries.toList()) { (channel, rows) ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Channel ${channel ?: "?"}", style = MaterialTheme.typography.titleMedium); Text("${rows.size} BSSIDs") }
                    val strongest = rows.maxOfOrNull { it.rssi } ?: -100
                    LinearProgressIndicator(progress = { ((strongest + 100) / 70f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    rows.take(6).forEach { Text("${it.ssid.ifBlank { "Hidden" }} • ${it.rssi} dBm • ${it.bssid}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (networks.isEmpty()) item { EmptyState("No channel data", "Run a Wi-Fi scan, then select a reported band.") }
    }
}

private fun channelSuggestion(networks: List<WifiNetwork>, band: String): Pair<String, String> {
    if (networks.isEmpty()) return "Not available" to "Run a fresh scan before making channel decisions."
    val candidates = if (band == "2.4 GHz") listOf(1, 6, 11) else networks.mapNotNull { it.channel }.distinct().ifEmpty { listOf(36, 44, 149, 157) }
    val scores = candidates.associateWith { candidate ->
        networks.sumOf { network ->
            val distance = kotlin.math.abs((network.channel ?: candidate) - candidate)
            val overlap = if (band == "2.4 GHz") (5 - distance).coerceAtLeast(0) / 5.0 else if (distance == 0) 1.0 else 0.0
            overlap * Math.pow(10.0, (network.rssi + 100) / 20.0)
        }
    }
    val best = scores.minByOrNull { it.value }?.key ?: return "Not available" to "No valid channels were reported."
    return "Channel $best" to "This candidate has the lowest weighted overlap in the latest scan snapshot. Verify with field measurements; it is not guaranteed to be best."
}

@Composable
fun PlannerScreen(viewModel: MainViewModel) {
    val rows by viewModel.surveyRows.collectAsStateWithLifecycle()
    val weak = remember(rows) { strongestMeasurements(rows).filter { it.rssi < -72 }.map { Point2d(it.x, it.y) } }
    val candidates by produceState(initialValue = emptyList<com.arjunren.netsurvey.domain.CandidateScore>(), weak) {
        value = withContext(Dispatchers.Default) {
            if (weak.isEmpty()) emptyList() else buildList {
                for (x in 1..9) for (y in 1..9) add(scoreCandidate(Point2d(x / 10.0, y / 10.0), weak, emptyList()))
            }.sortedByDescending { it.score }.take(5)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("AP placement assistance", style = MaterialTheme.typography.headlineSmall)
            Text("Advisory candidate zones use measured weak points. This is not automatic truth; walls, cabling, mounting, capacity and interference still require engineering review.")
        }
        item { Text("${weak.size} measured weak points (< -72 dBm)", style = MaterialTheme.typography.titleMedium) }
        items(candidates) { candidate ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Candidate ${(candidate.location.x * 100).toInt()}%, ${(candidate.location.y * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                    Text("Attempts to improve ${candidate.weakPointsCovered} nearby weak points; ${candidate.wallIntersections} known wall crossings.")
                    Text("Score ${"%.2f".format(candidate.score)} • bounded 10 × 10 search grid")
                }
            }
        }
        if (candidates.isEmpty()) item { EmptyState("No weak zones to optimize", "Collect a survey first. Candidate placement begins only from actual weak measurements.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp)) { Text("Predicted / simulated coverage", style = MaterialTheme.typography.titleMedium); Text("Planning calculations are isolated from measured heatmaps and must be validated on-site. Generic wall loss values are editable assumptions, not exact building measurements.") }
            }
        }
    }
}

@Composable
fun AssetsScreen(viewModel: MainViewModel) {
    val aps by viewModel.accessPoints.collectAsStateWithLifecycle()
    val photos by viewModel.installationPhotos.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::importInstallationPhoto) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("AP inventory", style = MaterialTheme.typography.headlineSmall); Text("Local installation and asset records") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Photo") }
                    Button(onClick = { add = true }) { androidx.compose.material3.Icon(Icons.Default.Add, null); Text("AP") }
                }
            }
        }
        items(aps, key = { it.id }) { ap ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(ap.name, style = MaterialTheme.typography.titleLarge)
                    Text(listOf(ap.assetId, ap.vendor, ap.model).filter(String::isNotBlank).joinToString(" • ").ifBlank { "Unspecified hardware" })
                    Text("SSID ${ap.ssid.ifBlank { "—" }} • BSSID ${ap.bssid.ifBlank { "—" }}", fontFamily = FontFamily.Monospace)
                    Text("Switch ${ap.switchName.ifBlank { "—" }} / ${ap.switchPort.ifBlank { "—" }} • VLAN ${ap.vlan.ifBlank { "—" }}")
                    ap.xNormalized?.let { Text("Floor-plan marker ${(it * 100).toInt()}%, ${(ap.yNormalized!! * 100).toInt()}%") }
                }
            }
        }
        if (aps.isEmpty()) item { EmptyState("No access points", "Add inventory here or place an AP marker from the Survey floor plan.") }
        if (photos.isNotEmpty()) item { Text("Installation photos (${photos.size})", style = MaterialTheme.typography.titleLarge) }
        items(photos, key = { "photo-${it.id}" }) { photo ->
            Card { Column(Modifier.padding(14.dp)) { Text(photo.caption.ifBlank { "Installation photo" }, style = MaterialTheme.typography.titleMedium); Text("Stored locally • ${DateFormat.getDateTimeInstance().format(Date(photo.capturedAt))}") } }
        }
    }
    if (add) AccessPointDialog({ add = false }) { name, ssid, bssid -> viewModel.addAccessPoint(name, ssid, bssid); add = false }
}

@Composable
fun ReportsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as NetSurveyApplication
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val surveys by viewModel.surveys.collectAsStateWithLifecycle()
    val rows by viewModel.surveyRows.collectAsStateWithLifecycle()
    val projectRows by viewModel.projectSurveyRows.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::restoreBackup) }
    fun runExport(title: String, block: suspend () -> java.io.File) {
        scope.launch {
            busy = true; error = null
            runCatching { block() }.onSuccess { file -> context.startActivity(Intent.createChooser(app.container.exports.shareIntent(file, title), title)) }.onFailure { error = it.message }
            busy = false
        }
    }
    val averages = projectRows.groupBy { it.surveyId }.mapValues { (_, surveyRows) -> surveyRows.map { it.displayedRssi }.average() }
    val baseline = surveys.sortedBy { it.startedAt }.firstNotNullOfOrNull { averages[it.id] }
    val comparisons = surveys.map { survey -> survey to averages[survey.id] }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Reports, exports & backup", style = MaterialTheme.typography.headlineSmall); Text("Reports are human-readable. Backups are restore packages. Neither includes Wi-Fi passwords.") }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(it, Modifier.padding(16.dp)) } } }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = project != null && !busy, onClick = { runExport("Share NetSurvey PDF") { app.container.exports.exportPdf(selection.projectId!!) } }) { androidx.compose.material3.Icon(Icons.Default.PictureAsPdf, null); Text("PDF report") }
                OutlinedButton(enabled = project != null && !busy, onClick = { runExport("Share NetSurvey CSV") { app.container.exports.exportCsv(selection.projectId!!) } }) { androidx.compose.material3.Icon(Icons.Default.FileDownload, null); Text("CSV") }
                OutlinedButton(enabled = selection.surveyId != null && !busy, onClick = { runExport("Share NetSurvey heatmap") { app.container.exports.exportHeatmapPng(selection.projectId!!, selection.surveyId!!) } }) { androidx.compose.material3.Icon(Icons.Default.Share, null); Text("Heatmap PNG") }
                OutlinedButton(enabled = project != null, onClick = {
                    val values = projectRows.map { it.displayedRssi }
                    val intent = app.container.exports.textSummary(project!!, projectRows.map { it.pointId }.distinct().size, values.takeIf { it.isNotEmpty() }?.average(), projectRows.groupBy { it.pointId }.count { (_, pointRows) -> pointRows.maxOfOrNull { it.displayedRssi }?.let { it < -72 } == true })
                    context.startActivity(Intent.createChooser(intent, "Share NetSurvey summary"))
                }) { androidx.compose.material3.Icon(Icons.Default.Share, null); Text("Text summary") }
                OutlinedButton(enabled = project != null && !busy, onClick = { runExport("Share NetSurvey backup") { app.container.exports.exportBackup(selection.projectId!!) } }) { androidx.compose.material3.Icon(Icons.Default.Backup, null); Text("Project backup") }
                OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { androidx.compose.material3.Icon(Icons.Default.Restore, null); Text("Restore backup") }
            }
        }
        item { HorizontalDivider(); Text("Before / after summary", style = MaterialTheme.typography.titleLarge); Text("Select each survey in Heatmaps to calculate its measurements. Historical observations are stored immutably by point.") }
        items(comparisons) { (survey, average) ->
            Card {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(survey.name, style = MaterialTheme.typography.titleMedium); Text("${survey.type} • ${DateFormat.getDateInstance().format(Date(survey.startedAt))}") }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(average?.let { "%.1f dBm".format(it) } ?: "No data")
                        if (average != null && baseline != null) Text("${if (average - baseline >= 0) "+" else ""}${"%.1f".format(average - baseline)} dB vs first", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sharing privacy", style = MaterialTheme.typography.titleMedium)
                    Text("Heatmap PNGs exclude IP addresses, serials, MAC/BSSID labels and customer notes. CSV and restore backups are detailed engineering exports and may contain sensitive SSID/BSSID infrastructure data.")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val current by viewModel.settings.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var draft by remember(current) { mutableStateOf(current) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Measurement settings", style = MaterialTheme.typography.headlineSmall); Text("Thresholds are site profiles, not universal wireless laws.") }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThresholdField("Excellent minimum", draft.excellentMin) { draft = draft.copy(excellentMin = it) }
                    ThresholdField("Good minimum", draft.goodMin) { draft = draft.copy(goodMin = it) }
                    ThresholdField("Acceptable minimum", draft.acceptableMin) { draft = draft.copy(acceptableMin = it) }
                    ThresholdField("Weak minimum", draft.weakMin) { draft = draft.copy(weakMin = it) }
                    ThresholdField("POCO F5 calibration offset", draft.calibrationOffsetDb) { draft = draft.copy(calibrationOffsetDb = it) }
                    Text("Raw RSSI remains stored separately from the calibrated display value.", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Heatmap resolution: ${draft.heatmapResolution}", style = MaterialTheme.typography.titleMedium)
                    Slider(draft.heatmapResolution.toFloat(), { draft = draft.copy(heatmapResolution = it.toInt()) }, valueRange = 10f..100f, steps = 8)
                    Text("Overlay opacity: ${(draft.heatmapOpacity * 100).toInt()}%")
                    Slider(draft.heatmapOpacity, { draft = draft.copy(heatmapOpacity = it) }, valueRange = 0.1f..0.9f)
                }
            }
        }
        item { ToggleRow("Keep screen awake during active measurement", draft.keepAwake) { draft = draft.copy(keepAwake = it) } }
        item { ToggleRow("Vibration indication", draft.vibrationEnabled) { draft = draft.copy(vibrationEnabled = it) } }
        item { ToggleRow("Audio indication", draft.audioEnabled) { draft = draft.copy(audioEnabled = it) } }
        item {
            Button(onClick = { viewModel.updateSettings(draft) }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NetSurvey ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text("Created by Arjunren")
                    TextButton(onClick = { uriHandler.openUri("https://github.com/Arjunren/NetSurvey") }) {
                        Text("View NetSurvey on GitHub")
                    }
                }
            }
        }
        item { Text("All primary data is stored on-device. NetSurvey has no account, analytics, advertising, backend, telemetry, or silent upload.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun ThresholdField(label: String, value: Int, onChange: (Int) -> Unit) { OutlinedTextField(value.toString(), { it.toIntOrNull()?.let(onChange) }, label = { Text("$label (dBm)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
@Composable private fun ToggleRow(label: String, checked: Boolean, change: (Boolean) -> Unit) { Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f)); Switch(checked, change) } } }
