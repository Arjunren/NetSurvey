package com.arjunren.netsurvey.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arjunren.netsurvey.NetSurveyApplication
import com.arjunren.netsurvey.data.local.FloorPlanEntity
import com.arjunren.netsurvey.data.local.SurveyPointEntity
import com.arjunren.netsurvey.domain.RssiThresholds
import com.arjunren.netsurvey.domain.SignalQuality
import com.arjunren.netsurvey.domain.rollingStatistics
import com.arjunren.netsurvey.wifi.ConnectedWifi
import com.arjunren.netsurvey.wifi.ScanStatus
import com.arjunren.netsurvey.wifi.WifiNetwork
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(viewModel: MainViewModel) {
    val status by viewModel.scanStatus.collectAsStateWithLifecycle()
    var band by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("RSSI") }
    val networks = (status as? ScanStatus.Results)?.networks.orEmpty().let { list ->
        val filtered = if (band == "All") list else list.filter { it.band == band }
        when (sort) { "SSID" -> filtered.sortedBy { it.ssid }; "Channel" -> filtered.sortedBy { it.channel }; else -> filtered.sortedByDescending { it.rssi } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { WifiPermissionCard { viewModel.requestScan() } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Nearby network snapshot", style = MaterialTheme.typography.headlineSmall)
                    Text("Nearby BSSID values only change when Android provides scan results.")
                }
                Button(onClick = viewModel::requestScan) { Icon(Icons.Default.Refresh, null); Text("Scan") }
            }
        }
        if (status is ScanStatus.Scanning) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (status is ScanStatus.Unavailable) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text((status as ScanStatus.Unavailable).reason, Modifier.padding(16.dp))
            }
        }
        if (status is ScanStatus.Results) item {
            val result = status as ScanStatus.Results
            Text(if (result.fresh) "Fresh scan • ${networks.size} BSSIDs" else "Cached scan • ${networks.size} BSSIDs — Android may have throttled the request")
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "2.4 GHz", "5 GHz", "6 GHz").forEach { item -> FilterChip(selected = band == item, onClick = { band = item }, label = { Text(item) }) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("RSSI", "SSID", "Channel").forEach { item -> FilterChip(selected = sort == item, onClick = { sort = item }, label = { Text("Sort $item") }) }
            }
        }
        items(networks, key = { "${it.bssid}-${it.frequencyMhz}" }) { network -> NetworkRow(network) }
        if (networks.isEmpty() && status !is ScanStatus.Scanning) item { EmptyState("No scan results", "Grant permissions, enable Wi-Fi and Location services, then request a scan.") }
    }
}

@Composable
private fun NetworkRow(network: WifiNetwork) {
    Card(colors = if (network.connected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${network.rssi}", fontSize = 25.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(" dBm", style = MaterialTheme.typography.labelSmall)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(network.ssid.ifBlank { "Hidden SSID" }, style = MaterialTheme.typography.titleMedium)
                Text(network.bssid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("${network.band} • ch ${network.channel ?: "?"} • ${network.frequencyMhz} MHz${network.channelWidthMhz?.let { " • $it MHz" }.orEmpty()}")
                Text(network.capabilities, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (network.connected) Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SignalMeterScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as NetSurveyApplication
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var running by remember { mutableStateOf(false) }
    val samples = remember { mutableStateListOf<Int>() }
    val connected by produceState<ConnectedWifi?>(initialValue = null, running) {
        if (!running) return@produceState
        app.container.wifi.connectedSamples().collect { info ->
            value = info
            info?.let {
                samples += it.rssi + settings.calibrationOffsetDb
                while (samples.size > 120) samples.removeAt(0)
            }
        }
    }
    val stats = rollingStatistics(samples)
    val thresholds = runCatching { RssiThresholds(settings.excellentMin, settings.goodMin, settings.acceptableMin, settings.weakMin) }.getOrDefault(RssiThresholds())
    val quality = stats?.current?.toInt()?.let(thresholds::classify)
    val window = (context as? android.app.Activity)?.window
    LaunchedEffect(running, settings.keepAwake) {
        if (running && settings.keepAwake) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { WifiPermissionCard() }
        item {
            Surface(color = qualityColor(quality), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stats?.current?.toInt()?.toString() ?: "—", fontSize = 72.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("dBm", style = MaterialTheme.typography.titleLarge)
                    Text(quality?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Not measuring", style = MaterialTheme.typography.headlineMedium)
                    Text("Device-specific thresholds; not a universal RF guarantee.")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Average", stats?.average?.let { "%.1f".format(it) } ?: "—")
                Stat("Minimum", stats?.minimum?.toString() ?: "—")
                Stat("Maximum", stats?.maximum?.toString() ?: "—")
                Stat("Samples", stats?.count?.toString() ?: "0")
            }
        }
        item { SignalChart(samples) }
        connected?.let { info ->
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(info.ssid.ifBlank { "Connected Wi-Fi" }, style = MaterialTheme.typography.titleLarge)
                        Text(info.bssid, fontFamily = FontFamily.Monospace)
                        Text("${info.band} • channel ${info.channel ?: "?"} • ${info.frequencyMhz} MHz")
                        Text("Link ${info.linkSpeedMbps ?: "—"} Mbps • Tx ${info.txLinkSpeedMbps ?: "—"} • Rx ${info.rxLinkSpeedMbps ?: "—"}")
                        Text("Connected AP live sample • age < 1 second", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Button(
                onClick = { running = !running; if (running) samples.clear() },
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Text(if (running) "Stop measurement" else "Start live measurement", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable private fun Stat(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun SignalChart(samples: List<Int>) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Text("Signal history", style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp)) {
                drawLine(Color.Gray, Offset(0f, size.height * .5f), Offset(size.width, size.height * .5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                if (samples.size > 1) {
                    val path = androidx.compose.ui.graphics.Path()
                    samples.forEachIndexed { index, value ->
                        val x = index.toFloat() / (samples.size - 1) * size.width
                        val y = ((-30 - value).coerceIn(0, 70) / 70f) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, Color(0xFF0C8B9D), style = Stroke(width = 5f))
                }
            }
        }
    }
}

private fun qualityColor(quality: SignalQuality?) = when (quality) {
    SignalQuality.EXCELLENT -> Color(0xFFCDEFC9)
    SignalQuality.GOOD -> Color(0xFFDFF3B6)
    SignalQuality.ACCEPTABLE -> Color(0xFFFFE9A8)
    SignalQuality.WEAK -> Color(0xFFFFD0A8)
    SignalQuality.POOR -> Color(0xFFFFC6C2)
    null -> Color(0xFFE2E8E9)
}

@Composable
fun SurveyScreen(viewModel: MainViewModel) {
    val floors by viewModel.floors.collectAsStateWithLifecycle()
    val surveys by viewModel.surveys.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val plan by viewModel.floorPlan.collectAsStateWithLifecycle()
    val points by viewModel.surveyPoints.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var startDialog by remember { mutableStateOf(false) }
    var apDialog by remember { mutableStateOf(false) }
    var calibrationMode by remember { mutableStateOf(false) }
    var calibrationPoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var distanceDialog by remember { mutableStateOf(false) }
    val floorSurveys = surveys.filter { it.floorId == selection.floorId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WifiPermissionCard { viewModel.requestScan() } }
        item {
            Text("1. Select floor", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                floors.forEach { floor -> FilterChip(floor.id == selection.floorId, { viewModel.selectFloor(floor.id) }, { Text(floor.name) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("2. Select survey", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(enabled = selection.floorId != null, onClick = { startDialog = true }) { Icon(Icons.Default.PlayArrow, null); Text("New survey") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                floorSurveys.forEach { survey -> FilterChip(survey.id == selection.surveyId, { viewModel.selectSurvey(survey.id) }, { Text(survey.name) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("3. Tap your position", style = MaterialTheme.typography.titleMedium); Text("Coordinates are normalized; indoor position is manually anchored.") }
                OutlinedButton(enabled = plan != null, onClick = { calibrationMode = true; calibrationPoints = emptyList(); pending = null }) { Text(if (calibrationMode) "Tap 2 scale points" else "Calibrate scale") }
            }
        }
        item {
            FloorPlanCanvas(plan, points, pending, calibrationPoints) { x, y ->
                if (calibrationMode) {
                    val updated = calibrationPoints + (x to y)
                    calibrationPoints = updated
                    if (updated.size == 2) { calibrationMode = false; distanceDialog = true }
                } else pending = x to y
            }
        }
        pending?.let { point ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Pending position: ${(point.first * 100).toInt()}%, ${(point.second * 100).toInt()}%")
                        Text("Measure saves the latest Android scan snapshot with scan age and confidence metadata.")
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = selection.surveyId != null, onClick = { viewModel.measureAt(point.first, point.second); pending = null }) { Icon(Icons.Default.Save, null); Text("Measure & save") }
                            OutlinedButton(onClick = { apDialog = true }) { Icon(Icons.Default.AddLocationAlt, null); Text("Place AP") }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::requestScan) { Icon(Icons.Default.Refresh, null); Text("Refresh scan snapshot") }
                Text("${points.size} measured points", modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }

    if (startDialog) SurveyStartDialog({ startDialog = false }) { name, type -> viewModel.startSurvey(name, type); startDialog = false }
    if (apDialog) AccessPointDialog({ apDialog = false }) { name, ssid, bssid ->
        pending?.let { viewModel.addAccessPoint(name, ssid, bssid, it.first, it.second) }
        apDialog = false
    }
    if (distanceDialog) DistanceDialog({ distanceDialog = false }) { meters ->
        if (calibrationPoints.size == 2) viewModel.calibrateFloor(calibrationPoints[0].first, calibrationPoints[0].second, calibrationPoints[1].first, calibrationPoints[1].second, meters)
        distanceDialog = false
    }
}

@Composable
private fun FloorPlanCanvas(
    plan: FloorPlanEntity?,
    points: List<SurveyPointEntity>,
    pending: Pair<Double, Double>?,
    calibration: List<Pair<Double, Double>>,
    onTap: (Double, Double) -> Unit,
) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, plan?.localPath) {
        value = withContext(Dispatchers.IO) { plan?.localPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    val ratio = plan?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }?.coerceIn(.5f, 2f) ?: 1.4f
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).heightIn(min = 280.dp, max = 600.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .pointerInput(Unit) { detectTapGestures { offset -> onTap((offset.x / size.width).toDouble(), (offset.y / size.height).toDouble()) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            image?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            if (image == null) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 34f; textAlign = android.graphics.Paint.Align.CENTER }
                    canvas.nativeCanvas.drawText("Import a floor plan in Projects", size.width / 2, size.height / 2, paint)
                }
            }
            points.forEachIndexed { index, point ->
                val center = Offset((point.xNormalized * size.width).toFloat(), (point.yNormalized * size.height).toFloat())
                drawCircle(Color(0xFF0C5866), 13f, center)
                drawCircle(Color.White, 13f, center, style = Stroke(3f))
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 15f; textAlign = android.graphics.Paint.Align.CENTER }
                    canvas.nativeCanvas.drawText((index + 1).toString(), center.x, center.y + 5f, paint)
                }
            }
            calibration.forEach { point -> drawCircle(Color(0xFFFFB300), 15f, Offset((point.first * size.width).toFloat(), (point.second * size.height).toFloat())) }
            if (calibration.size == 2) drawLine(Color(0xFFFFB300), Offset((calibration[0].first * size.width).toFloat(), (calibration[0].second * size.height).toFloat()), Offset((calibration[1].first * size.width).toFloat(), (calibration[1].second * size.height).toFloat()), 6f)
            pending?.let { drawCircle(Color(0xFFE53935), 18f, Offset((it.first * size.width).toFloat(), (it.second * size.height).toFloat()), style = Stroke(6f)) }
        }
    }
}

@Composable
private fun SurveyStartDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Baseline") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New survey session") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Survey name") })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Baseline", "Pre-installation", "Post-installation", "Validation", "Troubleshooting").forEach { FilterChip(type == it, { type = it }, { Text(it) }) } }
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, type) }) { Text("Start") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun AccessPointDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var ssid by remember { mutableStateOf("") }; var bssid by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add access point") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Friendly name *") })
            OutlinedTextField(ssid, { ssid = it }, label = { Text("SSID") })
            OutlinedTextField(bssid, { bssid = it }, label = { Text("BSSID") })
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, ssid, bssid) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun DistanceDialog(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Known distance") }, text = { OutlinedTextField(value, { value = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text("Distance in meters") }) }, confirmButton = { TextButton(enabled = (value.toDoubleOrNull() ?: 0.0) > 0, onClick = { onSave(value.toDouble()) }) { Text("Calibrate") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
