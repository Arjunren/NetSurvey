package com.arjunren.netsurvey.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.arjunren.netsurvey.data.local.NetSurveyDatabase
import com.arjunren.netsurvey.data.local.SavedWifiSnapshotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryPersistenceTest {
    private lateinit var database: NetSurveyDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetSurveyDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() = database.close()

    @Test
    fun projectCreationAndSavedReadingLifecyclePersist() = runBlocking {
        val projectId = NetSurveyRepository(database).createProject("Warehouse", "ACME", "Arjun")
        assertEquals("Warehouse", database.dao().project(projectId)?.name)

        val firstId = database.dao().insertSavedWifiSnapshot(snapshot("Dock AP", -67, 100))
        database.dao().insertSavedWifiSnapshot(snapshot("Office AP", -48, 200))
        assertEquals(listOf("Office AP", "Dock AP"), database.dao().observeSavedWifiSnapshots().first().map { it.name })

        database.dao().deleteSavedWifiSnapshot(firstId)
        assertEquals(listOf("Office AP"), database.dao().observeSavedWifiSnapshots().first().map { it.name })

        database.dao().clearSavedWifiSnapshots()
        assertEquals(emptyList<SavedWifiSnapshotEntity>(), database.dao().observeSavedWifiSnapshots().first())
    }

    private fun snapshot(name: String, rssi: Int, savedAt: Long) = SavedWifiSnapshotEntity(
        name = name,
        ssid = "FIELD",
        bssid = "AA:BB:CC:DD:EE:${savedAt / 100}",
        rssi = rssi,
        frequencyMhz = 5180,
        channel = 36,
        band = "5 GHz",
        capabilities = "[WPA2]",
        channelWidthMhz = 80,
        wifiStandard = "Wi-Fi 5",
        observedAt = savedAt - 1,
        savedAt = savedAt,
        connected = false,
    )
}
