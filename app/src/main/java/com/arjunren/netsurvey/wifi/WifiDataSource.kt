package com.arjunren.netsurvey.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.arjunren.netsurvey.domain.WifiChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: String,
    val capabilities: String,
    val channelWidthMhz: Int?,
    val wifiStandard: String?,
    val timestampMillis: Long,
    val connected: Boolean,
)

data class ConnectedWifi(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: String,
    val linkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val timestampMillis: Long = System.currentTimeMillis(),
)

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Scanning : ScanStatus
    data class Results(val networks: List<WifiNetwork>, val updatedAt: Long, val fresh: Boolean) : ScanStatus
    data class Unavailable(val reason: String) : ScanStatus
}

interface WifiDataSource {
    val scanStatus: StateFlow<ScanStatus>
    fun requestScan()
    fun cachedResults(): List<WifiNetwork>
    fun connectedSamples(intervalMillis: Long = 1_000L): kotlinx.coroutines.flow.Flow<ConnectedWifi?>
}

class AndroidWifiDataSource(private val context: Context) : WifiDataSource {
    private val wifiManager = context.applicationContext.getSystemService<WifiManager>()
    private val connectivityManager = context.getSystemService<ConnectivityManager>()
    private val locationManager = context.getSystemService<LocationManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    override val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            val fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            publishResults(fresh)
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun requestScan() {
        val manager = wifiManager ?: run {
            _scanStatus.value = ScanStatus.Unavailable("Wi-Fi hardware is unavailable on this device.")
            return
        }
        if (!manager.isWifiEnabled) {
            _scanStatus.value = ScanStatus.Unavailable("Wi-Fi is turned off.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && locationManager?.isLocationEnabled == false) {
            _scanStatus.value = ScanStatus.Unavailable("Location services are required for Wi-Fi scan results on this Android version.")
            return
        }
        if (!hasScanPermission()) {
            _scanStatus.value = ScanStatus.Unavailable("Location and nearby Wi-Fi permission are required for scanning.")
            return
        }
        _scanStatus.value = ScanStatus.Scanning
        try {
            @Suppress("DEPRECATION")
            if (!manager.startScan()) {
                publishResults(fresh = false, fallbackMessage = true)
            }
        } catch (_: SecurityException) {
            _scanStatus.value = ScanStatus.Unavailable("Android denied Wi-Fi scan access. Check app permissions and Location services.")
        } catch (_: RuntimeException) {
            _scanStatus.value = ScanStatus.Unavailable("The Wi-Fi scan could not be started.")
        }
    }

    override fun cachedResults(): List<WifiNetwork> = readResults()

    override fun connectedSamples(intervalMillis: Long) = flow {
        while (true) {
            emit(readConnectedWifi())
            delay(intervalMillis.coerceIn(500L, 10_000L))
        }
    }

    private fun publishResults(fresh: Boolean, fallbackMessage: Boolean = false) {
        val results = readResults()
        if (results.isEmpty() && fallbackMessage) {
            _scanStatus.value = ScanStatus.Unavailable("Android throttled or rejected the active scan. No cached results are available yet.")
        } else {
            _scanStatus.value = ScanStatus.Results(results, System.currentTimeMillis(), fresh)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readResults(): List<WifiNetwork> {
        if (!hasScanPermission()) return emptyList()
        val connectedBssid = readConnectedWifi()?.bssid
        return try {
            wifiManager?.scanResults.orEmpty().map { result ->
                WifiNetwork(
                    ssid = if (Build.VERSION.SDK_INT >= 33) result.wifiSsid?.toString().orEmpty().trim('"') else @Suppress("DEPRECATION") result.SSID.orEmpty(),
                    bssid = result.BSSID.orEmpty(),
                    rssi = result.level,
                    frequencyMhz = result.frequency,
                    channel = WifiChannels.channel(result.frequency),
                    band = WifiChannels.band(result.frequency).label,
                    capabilities = result.capabilities.orEmpty(),
                    channelWidthMhz = channelWidth(result),
                    wifiStandard = wifiStandard(result),
                    timestampMillis = scanWallClock(result.timestamp),
                    connected = result.BSSID.equals(connectedBssid, ignoreCase = true),
                )
            }.sortedByDescending { it.rssi }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun readConnectedWifi(): ConnectedWifi? {
        if (!hasScanPermission()) return null
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                connectivityManager?.activeNetwork?.let { network ->
                    connectivityManager.getNetworkCapabilities(network)?.transportInfo as? WifiInfo
                }
            } else {
                @Suppress("DEPRECATION") wifiManager?.connectionInfo
            } ?: return null
            if (info.networkId == -1 || info.rssi <= -127) return null
            ConnectedWifi(
                ssid = info.ssid.orEmpty().trim('"').takeUnless { it == "<unknown ssid>" }.orEmpty(),
                bssid = info.bssid.orEmpty(),
                rssi = info.rssi,
                frequencyMhz = info.frequency,
                channel = WifiChannels.channel(info.frequency),
                band = WifiChannels.band(info.frequency).label,
                linkSpeedMbps = info.linkSpeed.takeIf { it >= 0 },
                txLinkSpeedMbps = if (Build.VERSION.SDK_INT >= 29) info.txLinkSpeedMbps.takeIf { it >= 0 } else null,
                rxLinkSpeedMbps = if (Build.VERSION.SDK_INT >= 29) info.rxLinkSpeedMbps.takeIf { it >= 0 } else null,
            )
        } catch (_: SecurityException) {
            null
        }
    }

    private fun hasScanPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val nearby = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine && nearby
    }

    private fun scanWallClock(timestampMicros: Long): Long {
        val age = (SystemClock.elapsedRealtimeNanos() / 1_000L - timestampMicros).coerceAtLeast(0L) / 1_000L
        return System.currentTimeMillis() - age
    }

    private fun channelWidth(result: ScanResult): Int? = when (result.channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ -> 160
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        else -> null
    }

    private fun wifiStandard(result: ScanResult): String? = if (Build.VERSION.SDK_INT >= 30) when (result.wifiStandard) {
        ScanResult.WIFI_STANDARD_LEGACY -> "Legacy"
        ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
        ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
        ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
        ScanResult.WIFI_STANDARD_11AD -> "WiGig"
        else -> null
    } else null
}
