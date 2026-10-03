package com.arjunren.netsurvey

import android.app.Application
import com.arjunren.netsurvey.data.NetSurveyRepository
import com.arjunren.netsurvey.data.SettingsRepository
import com.arjunren.netsurvey.data.local.NetSurveyDatabase
import com.arjunren.netsurvey.report.ExportService
import com.arjunren.netsurvey.wifi.AndroidWifiDataSource

class NetSurveyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = NetSurveyDatabase.create(this)
        container = AppContainer(
            database = database,
            repository = NetSurveyRepository(database),
            settings = SettingsRepository(this),
            wifi = AndroidWifiDataSource(this),
            exports = ExportService(this, database),
        )
    }
}

data class AppContainer(
    val database: NetSurveyDatabase,
    val repository: NetSurveyRepository,
    val settings: SettingsRepository,
    val wifi: AndroidWifiDataSource,
    val exports: ExportService,
)
