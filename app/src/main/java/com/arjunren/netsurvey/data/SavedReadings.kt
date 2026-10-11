package com.arjunren.netsurvey.data

import com.arjunren.netsurvey.data.local.SavedWifiSnapshotEntity
import com.arjunren.netsurvey.domain.csvEscape

enum class ReadingSort(val label: String) { NEWEST("Newest"), STRONGEST("Strongest"), NAME("Name") }

fun filterSavedReadings(
    readings: List<SavedWifiSnapshotEntity>, query: String, band: String, sort: ReadingSort,
): List<SavedWifiSnapshotEntity> {
    val search = query.trim()
    val filtered = readings.filter { reading ->
        (band == "All" || reading.band == band) &&
            listOf(reading.name, reading.description, reading.ssid, reading.bssid).any { it.contains(search, ignoreCase = true) }
    }
    return when (sort) {
        ReadingSort.NEWEST -> filtered.sortedWith(compareByDescending<SavedWifiSnapshotEntity> { it.savedAt }.thenByDescending { it.id })
        ReadingSort.STRONGEST -> filtered.sortedWith(compareByDescending<SavedWifiSnapshotEntity> { it.rssi }.thenByDescending { it.savedAt })
        ReadingSort.NAME -> filtered.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }
}

fun savedReadingsCsv(readings: List<SavedWifiSnapshotEntity>): String = buildString {
    appendLine("name,description,ssid,bssid,rssi_dbm,band,channel,frequency_mhz,channel_width_mhz,wifi_standard,capabilities,observed_at_epoch_ms,saved_at_epoch_ms,connected")
    readings.forEach { reading ->
        appendLine(listOf(
            csvText(reading.name), csvText(reading.description), csvText(reading.ssid), csvText(reading.bssid), reading.rssi.toString(), reading.band,
            reading.channel?.toString().orEmpty(), reading.frequencyMhz.toString(), reading.channelWidthMhz?.toString().orEmpty(),
            csvText(reading.wifiStandard.orEmpty()), csvText(reading.capabilities), reading.observedAt.toString(), reading.savedAt.toString(), reading.connected.toString(),
        ).joinToString(",", transform = ::csvEscape))
    }
}

// Quoting alone does not prevent spreadsheet formula execution in untrusted names/notes.
private fun csvText(value: String): String = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
