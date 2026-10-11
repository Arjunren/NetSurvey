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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.arjunren.netsurvey.NetSurveyApplication
import com.arjunren.netsurvey.data.local.FloorPlanEntity
import com.arjunren.netsurvey.data.local.SurveyPointEntity
import com.arjunren.netsurvey.domain.RssiThresholds
import com.arjunren.netsurvey.domain.rollingStatistics
import com.arjunren.netsurvey.wifi.ConnectedWifi
import com.arjunren.netsurvey.wifi.ScanStatus
import com.arjunren.netsurvey.wifi.WifiNetwork
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(viewModel: MainViewModel) {
    val status by viewModel.scanStatus.collectAsStateWithLifecycle()
    var band by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("RSSI") }
    var query by rememberSaveable { mutableStateOf("") }
    var connectedOnly by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }
    var savingSnapshot by remember { mutableStateOf(false) }
    var latestNetworks by remember { mutableStateOf(emptyList<WifiNetwork>()) }
    var autoScan by remember { mutableStateOf(false) }
    val resultNetworks = (status as? ScanStatus.Results)?.networks
    LaunchedEffect(resultNetworks) {
        resultNetworks?.let { latestNetworks = it }
    }
    LaunchedEffect(autoScan, lifecycleOwner) {
        if (autoScan) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(30_000L)
                viewModel.requestScan()
            }
        }
    }
    val networks = latestNetworks.let { list ->
        val filtered = list.filter {
            (band == "All" || it.band == band) && (!connectedOnly || it.connected) &&
                (it.ssid.contains(query.trim(), ignoreCase = true) || it.bssid.contains(query.trim(), ignoreCase = true))
        }
        when (sort) { "SSID" -> filtered.sortedBy { it.ssid }; "Channel" -> filtered.sortedBy { it.channel }; else -> filtered.sortedByDescending { it.rssi } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("Nearby networks", "Discover access points and save a fixed reading with one tap.")
                Button(
                    onClick = {
                        viewModel.requestScan()
                        autoScan = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(if (autoScan) "Scan now" else "Scan & start live updates", modifier = Modifier.padding(start = 8.dp))
                }
                if (autoScan) {
                    OutlinedButton(onClick = { autoScan = false }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text("Stop live updates", modifier = Modifier.padding(start = 8.dp))
                    }
                    Text("Live updates active • next scan within 30 seconds • Android may return cached results when scans are throttled", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            WifiPermissionCard { viewModel.requestScan(); autoScan = true }
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
        if (latestNetworks.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${latestNetworks.size} access points • ${latestNetworks.map { it.ssid }.distinct().size} SSIDs", style = MaterialTheme.typography.titleMedium)
                    Text(latestNetworks.groupingBy { it.band }.eachCount().entries.joinToString("   /   ") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.bodySmall)
                    Text("${networks.size} matching your filters", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search SSID or BSSID") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "2.4 GHz", "5 GHz", "6 GHz").forEach { item -> FilterChip(selected = band == item, onClick = { band = item }, label = { Text(item) }) }
                FilterChip(connectedOnly, { connectedOnly = !connectedOnly }, { Text("Connected only") })
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("RSSI", "SSID", "Channel").forEach { item -> FilterChip(selected = sort == item, onClick = { sort = item }, label = { Text("Sort $item") }) }
            }
        }
        items(networks, key = { "${it.bssid}-${it.frequencyMhz}" }) { network ->
            NetworkRow(network, onClick = { selectedNetwork = network })
        }
        if (networks.isEmpty() && status !is ScanStatus.Scanning) item { EmptyState(if (latestNetworks.isEmpty()) "Ready to discover" else "No matching networks", if (latestNetworks.isEmpty()) "Grant Wi-Fi access below if needed, enable Wi-Fi and Location, then tap Scan." else "Change your search or filters to see more access points.") }
    }

    selectedNetwork?.let { network ->
        WifiSnapshotDialog(
            network = network,
            saving = savingSnapshot,
            onDismiss = { if (!savingSnapshot) selectedNetwork = null },
            onSave = { name, description ->
                savingSnapshot = true
                viewModel.saveWifiSnapshot(network, name, description) { success ->
                    savingSnapshot = false
                    if (success) selectedNetwork = null
                }
            },
        )
    }
}

@Composable
private fun NetworkRow(network: WifiNetwork, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = if (network.connected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${network.rssi}", fontSize = 25.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(" dBm", style = MaterialTheme.typography.labelSmall)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(network.ssid.ifBlank { "Hidden SSID" }, style = MaterialTheme.typography.titleMedium)
                Text(network.bssid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("${network.band} • ch ${network.channel ?: "?"} • ${network.frequencyMhz} MHz${network.channelWidthMhz?.let { " • $it MHz" }.orEmpty()}")
                Text(network.capabilities, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text("Tap to save this reading", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (network.connected) StatusLabel("Connected")
            network.wifiStandard?.let { StatusLabel(it) }
            StatusLabel("Observed ${DateFormat.getTimeInstance().format(Date(network.timestampMillis))}")
        }
        LinearProgressIndicator(progress = { ((network.rssi + 100) / 70f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WifiSnapshotDialog(
    network: WifiNetwork,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(network.bssid, network.timestampMillis) {
        mutableStateOf(network.ssid.ifBlank { "Hidden network" })
    }
    var description by remember(network.bssid, network.timestampMillis) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Wi-Fi reading") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${network.rssi} dBm • ${network.band} • channel ${network.channel ?: "?"}", style = MaterialTheme.typography.titleMedium)
                Text(network.bssid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text("The current RSSI and network details will be saved as a fixed snapshot and will not change with later scans.", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !saving, onClick = { onSave(name, description) }) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SignalMeterScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as NetSurveyApplication
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var running by remember { mutableStateOf(false) }
    val samples = remember { mutableStateListOf<Int>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var sampleBssid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.calibrationOffsetDb) { samples.clear() }
    val connected by produceState<ConnectedWifi?>(initialValue = null, running, settings.calibrationOffsetDb, lifecycleOwner) {
        if (!running) return@produceState
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            app.container.wifi.connectedSamples().collect { info ->
                value = info
                info?.let {
                    if (sampleBssid != it.bssid) { samples.clear(); sampleBssid = it.bssid }
                    samples += it.rssi + settings.calibrationOffsetDb
                    while (samples.size > 120) samples.removeAt(0)
                }
            }
        }
    }
    val stats = rollingStatistics(samples)
    val thresholds = runCatching { RssiThresholds(settings.excellentMin, settings.goodMin, settings.acceptableMin, settings.weakMin) }.getOrDefault(RssiThresholds())
    val quality = stats?.current?.toInt()?.let(thresholds::classify)
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(running, settings.keepAwake, lifecycleOwner, window) {
        val updateWindow = {
            if (running && settings.keepAwake && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val observer = LifecycleEventObserver { _, _ -> updateWindow() }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateWindow()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Button(onClick = { running = !running }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Text(if (running) "Stop measurement" else "Start live measurement", Modifier.padding(start = 8.dp))
            }
        }
        item {
            WifiPermissionCard {
                samples.clear()
                running = true
            }
        }
        item {
            Surface(color = Color.Black, contentColor = Color.White, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (running) "LIVE / CONNECTED AP" else "MEASUREMENT PAUSED", style = MaterialTheme.typography.labelLarge)
                    Text(if (running && connected == null) "—" else stats?.current?.toInt()?.toString() ?: "—", fontSize = 72.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("dBm", style = MaterialTheme.typography.titleLarge)
                    Text(if (running && connected == null) "Waiting for Wi-Fi" else quality?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Ready to measure", style = MaterialTheme.typography.headlineSmall)
                    Text("Device-specific thresholds • calibrated RSSI", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Average", stats?.average?.let { "%.1f".format(it) } ?: "—")
                Stat("Minimum", stats?.minimum?.toString() ?: "—")
                Stat("Maximum", stats?.maximum?.toString() ?: "—")
                Stat("Samples", stats?.count?.toString() ?: "0")
            }
        }
        item { SignalChart(samples) }
        item {
            Text("Variation (σ): ${stats?.standardDeviation?.let { "%.1f dB".format(it) } ?: "—"} • last ${samples.size} samples • 1-second sampling", style = MaterialTheme.typography.bodySmall)
            Text("History resets when the connected BSSID or calibration offset changes.", style = MaterialTheme.typography.bodySmall)
        }
        if (running && connected == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "No readable Wi-Fi connection yet. Connect this phone to Wi-Fi, grant precise location and Nearby Wi-Fi access, and keep Location services enabled.",
                        Modifier.padding(16.dp),
                    )
                }
            }
        }
        connected?.let { info ->
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(info.ssid.ifBlank { "Connected Wi-Fi" }, style = MaterialTheme.typography.titleLarge)
                        Text(info.bssid, fontFamily = FontFamily.Monospace)
                        Text("${info.band} • channel ${info.channel ?: "?"} • ${info.frequencyMhz} MHz")
                        Text("Link ${info.linkSpeedMbps ?: "—"} Mbps • Tx ${info.txLinkSpeedMbps ?: "—"} • Rx ${info.rxLinkSpeedMbps ?: "—"}")
                        Text("Last sample ${DateFormat.getTimeInstance().format(Date(info.timestampMillis))}${if (running) " • monitoring" else " • paused"}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item { OutlinedButton(enabled = samples.isNotEmpty(), onClick = { samples.clear() }, modifier = Modifier.fillMaxWidth()) { Text("Reset signal history") } }
    }
}

@Composable private fun Stat(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun SignalChart(samples: List<Int>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Card {
        Column(Modifier.padding(14.dp)) {
            Text("Signal history", style = MaterialTheme.typography.titleMedium)
            Text("−30 dBm (strong) to −100 dBm (weak) • chronological", style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp)) {
                listOf(0f, .5f, 1f).forEach { fraction -> drawLine(gridColor, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))) }
                if (samples.size > 1) {
                    val path = androidx.compose.ui.graphics.Path()
                    samples.forEachIndexed { index, value ->
                        val x = index.toFloat() / (samples.size - 1) * size.width
                        val y = ((-30 - value).coerceIn(0, 70) / 70f) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 5f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Older", style = MaterialTheme.typography.labelSmall); Text("Latest", style = MaterialTheme.typography.labelSmall) }
        }
    }
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
                Text("2. Select survey", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(enabled = selection.floorId != null, onClick = { startDialog = true }) { Icon(Icons.Default.PlayArrow, null); Text("New survey") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                floorSurveys.forEach { survey -> FilterChip(survey.id == selection.surveyId, { viewModel.selectSurvey(survey.id) }, { Text(survey.name) }) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = selection.surveyId != null, onClick = { viewModel.measureAt(point.first, point.second); pending = null }) { Icon(Icons.Default.Save, null); Text("Measure & save") }
                            OutlinedButton(onClick = { apDialog = true }) { Icon(Icons.Default.AddLocationAlt, null); Text("Place AP") }
                        }
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::requestScan) { Icon(Icons.Default.Refresh, null); Text("Refresh scan snapshot") }
                Text("${points.size} measured points", modifier = Modifier.padding(vertical = 12.dp))
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
    val currentOnTap by rememberUpdatedState(onTap)
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, plan?.localPath) {
        value = withContext(Dispatchers.IO) { plan?.localPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    val ratio = plan?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }?.coerceIn(.5f, 2f) ?: 1.4f
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).heightIn(min = 280.dp, max = 600.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .pointerInput(Unit) { detectTapGestures { offset -> currentOnTap((offset.x / size.width).toDouble(), (offset.y / size.height).toDouble()) } },
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
