package com.arjunren.netsurvey.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arjunren.netsurvey.data.local.FloorEntity
import com.arjunren.netsurvey.data.local.NetSurveyDatabase
import com.arjunren.netsurvey.data.local.ProjectEntity
import com.arjunren.netsurvey.data.local.SurveyPointEntity
import com.arjunren.netsurvey.data.local.SurveySessionEntity
import com.arjunren.netsurvey.data.local.WifiObservationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var database: NetSurveyDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NetSurveyDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test fun pointAndObservationsSaveAtomicallyAndCascade() = runBlocking {
        val repository = NetSurveyRepository(database)
        val project = database.dao().insertProject(ProjectEntity(name = "Office"))
        val floor = database.dao().insertFloor(FloorEntity(projectId = project, name = "Ground"))
        val survey = database.dao().insertSurvey(SurveySessionEntity(projectId = project, floorId = floor, name = "Baseline", deviceModel = "Test", androidVersion = "17", appVersion = "1"))
        val point = SurveyPointEntity(surveyId = survey, floorId = floor, xNormalized = .2, yNormalized = .3, confidence = "Fresh Scan", deviceModel = "Test", androidVersion = "17")
        val observation = WifiObservationEntity(pointId = 0, ssid = "OFFICE", bssid = "00:11:22:33:44:55", rawRssi = -55, displayedRssi = -53, frequencyMhz = 5180, channel = 36, band = "5 GHz", capabilities = "[WPA2]", scanTimestamp = 1, scanAgeMillis = 2, connected = true)
        val id = repository.saveMeasurement(point, listOf(observation))
        assertEquals(1, database.dao().observeObservations(id).first().size)
        database.dao().deleteProject(project)
        assertEquals(0, database.dao().observeProjects().first().size)
        assertEquals(0, database.dao().observePoints(survey).first().size)
    }
}
