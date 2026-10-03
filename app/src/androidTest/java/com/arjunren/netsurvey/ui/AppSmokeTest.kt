package com.arjunren.netsurvey.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.arjunren.netsurvey.MainActivity
import org.junit.Rule
import org.junit.Test

class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun dashboardLaunches() {
        rule.onNodeWithText("Field-ready Wi-Fi intelligence").assertIsDisplayed()
        rule.onNodeWithText("Quick actions").assertIsDisplayed()
    }
}
