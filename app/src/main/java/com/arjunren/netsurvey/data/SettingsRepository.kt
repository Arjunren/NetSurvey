package com.arjunren.netsurvey.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

data class AppSettings(
    val excellentMin: Int = -55,
    val goodMin: Int = -67,
    val acceptableMin: Int = -72,
    val weakMin: Int = -80,
    val heatmapResolution: Int = 40,
    val heatmapOpacity: Float = 0.58f,
    val keepAwake: Boolean = false,
    val audioEnabled: Boolean = false,
    val vibrationEnabled: Boolean = false,
    val units: String = "meters",
    val theme: String = "system",
    val calibrationOffsetDb: Int = 0,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val excellent = intPreferencesKey("excellent")
        val good = intPreferencesKey("good")
        val acceptable = intPreferencesKey("acceptable")
        val weak = intPreferencesKey("weak")
        val resolution = intPreferencesKey("resolution")
        val opacity = floatPreferencesKey("opacity")
        val keepAwake = booleanPreferencesKey("keep_awake")
        val audio = booleanPreferencesKey("audio")
        val vibration = booleanPreferencesKey("vibration")
        val units = stringPreferencesKey("units")
        val theme = stringPreferencesKey("theme")
        val offset = intPreferencesKey("offset")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { values ->
        AppSettings(
            excellentMin = values[Keys.excellent] ?: -55,
            goodMin = values[Keys.good] ?: -67,
            acceptableMin = values[Keys.acceptable] ?: -72,
            weakMin = values[Keys.weak] ?: -80,
            heatmapResolution = values[Keys.resolution] ?: 40,
            heatmapOpacity = values[Keys.opacity] ?: 0.58f,
            keepAwake = values[Keys.keepAwake] ?: false,
            audioEnabled = values[Keys.audio] ?: false,
            vibrationEnabled = values[Keys.vibration] ?: false,
            units = values[Keys.units] ?: "meters",
            theme = values[Keys.theme] ?: "system",
            calibrationOffsetDb = values[Keys.offset] ?: 0,
        )
    }

    suspend fun update(value: AppSettings) {
        context.settingsStore.edit {
            it[Keys.excellent] = value.excellentMin
            it[Keys.good] = value.goodMin
            it[Keys.acceptable] = value.acceptableMin
            it[Keys.weak] = value.weakMin
            it[Keys.resolution] = value.heatmapResolution.coerceIn(10, 100)
            it[Keys.opacity] = value.heatmapOpacity.coerceIn(0.1f, 0.9f)
            it[Keys.keepAwake] = value.keepAwake
            it[Keys.audio] = value.audioEnabled
            it[Keys.vibration] = value.vibrationEnabled
            it[Keys.units] = value.units
            it[Keys.theme] = value.theme
            it[Keys.offset] = value.calibrationOffsetDb.coerceIn(-20, 20)
        }
    }
}
