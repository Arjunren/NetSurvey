package com.arjunren.netsurvey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arjunren.netsurvey.ui.MainViewModel
import com.arjunren.netsurvey.ui.NetSurveyApp
import com.arjunren.netsurvey.ui.theme.NetSurveyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            NetSurveyTheme(theme = settings.theme) {
                NetSurveyApp(viewModel)
            }
        }
    }
}
