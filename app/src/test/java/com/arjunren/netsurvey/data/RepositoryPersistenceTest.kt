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

    @Test
    fun editingReadingNotesPreservesEveryCapturedField() = runBlocking {
        val captured = snapshot("Dock AP", -67, 100)
        val id = database.dao().insertSavedWifiSnapshot(captured)
        database.dao().updateSavedWifiSnapshotNotes(id, "Loading dock", "Behind the steel door")
        assertEquals(captured.copy(id = id, name = "Loading dock", description = "Behind the steel door"),
            database.dao().observeSavedWifiSnapshots().first().single())
    }

    @Test
    fun projectEditAndDeleteKeepStandaloneReadings() = runBlocking {
        val repository = NetSurveyRepository(database)
        val id = repository.createProject("Old name", "ACME", "Arjun")
        repository.addFloor(id, "Ground")
        val project = requireNotNull(database.dao().project(id))
        database.dao().updateProject(project.copy(name = "Warehouse", address = "Dock 1", notes = "Survey at closing"))
        assertEquals("Dock 1", database.dao().project(id)?.address)
        database.dao().insertSavedWifiSnapshot(snapshot("AP", -67, 100))
        database.dao().deleteProject(id)
        assertEquals(0, database.dao().floorCount())
        assertEquals(1, database.dao().observeSavedWifiSnapshots().first().size)
    }
}
