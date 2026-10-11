package com.arjunren.netsurvey.data

import com.arjunren.netsurvey.data.local.SavedWifiSnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedReadingsTest {
    private fun reading(id: Long, name: String, band: String, rssi: Int, time: Long) = SavedWifiSnapshotEntity(
        id = id, name = name, description = "Dock, west\nBehind door", ssid = "OFFICE", bssid = "AA:BB:CC:DD:EE:$id",
        rssi = rssi, band = band, frequencyMhz = 5180, channel = 36, capabilities = "[WPA2]", channelWidthMhz = 80,
        wifiStandard = "Wi-Fi 5", observedAt = time - 1, savedAt = time, connected = false,
    )
    private val readings = listOf(reading(1, "Dock", "5 GHz", -70, 100), reading(2, "Lobby", "2.4 GHz", -40, 200), reading(3, "Office", "5 GHz", -55, 200))

    @Test fun filtersAreCaseInsensitiveAndCombineWithBand() {
        assertEquals(listOf(1L, 3L), filterSavedReadings(readings, " office ", "5 GHz", ReadingSort.NAME).map { it.id })
        assertEquals(listOf(2L), filterSavedReadings(readings, "ee:2", "All", ReadingSort.NEWEST).map { it.id })
        assertEquals(3, filterSavedReadings(readings, "BEHIND", "All", ReadingSort.NEWEST).size)
        assertTrue(filterSavedReadings(readings, "no match", "All", ReadingSort.NEWEST).isEmpty())
    }

    @Test fun sortsHaveDeterministicCaptureOrderAndKeepValuesFrozen() {
        assertEquals(listOf(3L, 2L, 1L), filterSavedReadings(readings, "", "All", ReadingSort.NEWEST).map { it.id })
        assertEquals(listOf(-40, -55, -70), filterSavedReadings(readings, "", "All", ReadingSort.STRONGEST).map { it.rssi })
        assertEquals(listOf(-70, -40, -55), readings.map { it.rssi })
    }

    @Test fun csvEscapesMultilineNotesAndExportsOnlySelectedReadings() {
        val csv = savedReadingsCsv(listOf(readings.first().copy(name = "Dock \"A\"")))
        assertTrue(csv.startsWith("name,description,ssid,bssid,rssi_dbm"))
        assertTrue(csv.contains("\"Dock \"\"A\"\"\",\"Dock, west\nBehind door\""))
        assertTrue(csv.contains(",-70,5 GHz,36,5180,80,Wi-Fi 5,[WPA2],99,100,false"))
        assertTrue(!csv.contains("Lobby"))
    }

    @Test fun csvNeutralizesTextFormulasWithoutChangingNumericRssi() {
        val csv = savedReadingsCsv(listOf(readings.first().copy(name = "=1+1", description = "  @SUM(A1)")))
        assertTrue(csv.contains("'=1+1,'  @SUM(A1),OFFICE"))
        assertTrue(csv.contains(",-70,5 GHz,"))
    }
}
