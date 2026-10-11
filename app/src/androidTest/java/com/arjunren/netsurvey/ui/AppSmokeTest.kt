package com.arjunren.netsurvey.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.arjunren.netsurvey.NetSurveyApplication
import com.arjunren.netsurvey.data.local.SavedWifiSnapshotEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import com.arjunren.netsurvey.MainActivity
import org.junit.Rule
import org.junit.Test

class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun openTab(label: String) = rule.onNodeWithTag("navigation-${label.lowercase()}").performClick()

    @Test fun dashboardLaunches() {
        rule.onNodeWithText("Know your network.\nPlan with confidence.").assertIsDisplayed()
        rule.onNodeWithTag("dashboard-content").performScrollToNode(hasText("Quick actions"))
        rule.onNodeWithText("Quick actions").assertIsDisplayed()
    }

    @Test fun bottomNavigationReturnsToDashboard() {
        openTab("Projects")
        rule.onNodeWithText("Create project").assertIsDisplayed()
        openTab("Dashboard")
        rule.onNodeWithText("Know your network.\nPlan with confidence.").assertIsDisplayed()
        openTab("Readings")
        rule.onNodeWithText("Your reading library").assertIsDisplayed()
        rule.onNodeWithText("Search name, notes, SSID or BSSID").assertIsDisplayed()
        rule.onNodeWithContentDescription("Open navigation").performClick()
        rule.onNodeWithTag("drawer-dashboard").performClick()
        rule.onNodeWithText("Know your network.\nPlan with confidence.").assertIsDisplayed()
    }

    @Test fun visibleCreateButtonPersistsProjectAndSelectsIt() {
        val name = "Field test ${System.currentTimeMillis()}"
        openTab("Projects")
        rule.onNodeWithText("Create project").performClick()
        rule.onNodeWithText("Project name *").performTextInput(name)
        rule.onNodeWithText("Customer / site").performTextInput("Warehouse")
        rule.onNodeWithText("Create", useUnmergedTree = true).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Active").assertIsDisplayed()
        openTab("Dashboard")
        rule.onAllNodesWithText(name).onFirst().assertIsDisplayed()
    }

    @Test fun readingNotesStayFrozenAndClearAllCanBeCancelled() {
        val dao = ApplicationProvider.getApplicationContext<NetSurveyApplication>().container.database.dao()
        val captured = SavedWifiSnapshotEntity(name = "Library test", ssid = "TEST-SSID", bssid = "AA:BB:CC:DD:EE:FF",
            rssi = -61, frequencyMhz = 5180, channel = 36, band = "5 GHz", capabilities = "[WPA2]",
            channelWidthMhz = 80, wifiStandard = "Wi-Fi 5", observedAt = 100, savedAt = System.currentTimeMillis(), connected = false)
        val id = runBlocking { withContext(Dispatchers.IO) { dao.insertSavedWifiSnapshot(captured) } }
        try {
            openTab("Readings")
            rule.onNodeWithText("Search name, notes, SSID or BSSID").performTextInput("TEST-SSID")
            rule.onNodeWithTag("readings-content").performScrollToNode(hasText("Edit notes"))
            rule.onNodeWithText("Edit notes").performClick()
            rule.onNodeWithText("Name *").performTextClearance()
            rule.onNodeWithText("Name *").performTextInput("Library renamed")
            rule.onNodeWithText("Save", useUnmergedTree = true).performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Library renamed").fetchSemanticsNodes().isNotEmpty() }
            val updated = runBlocking { withContext(Dispatchers.IO) { dao.observeSavedWifiSnapshots().first().first { it.id == id } } }
            assertEquals(captured.copy(id = id, name = "Library renamed"), updated)
            rule.onNodeWithText("Clear all readings").performScrollTo().performClick()
            rule.onNodeWithText("Cancel").performClick()
            assertEquals(updated, runBlocking { withContext(Dispatchers.IO) { dao.observeSavedWifiSnapshots().first().first { it.id == id } } })
        } finally {
            runBlocking { withContext(Dispatchers.IO) { dao.deleteSavedWifiSnapshot(id) } }
        }
    }
}
