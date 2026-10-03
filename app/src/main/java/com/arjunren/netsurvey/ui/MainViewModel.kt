package com.arjunren.netsurvey.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arjunren.netsurvey.BuildConfig
import com.arjunren.netsurvey.NetSurveyApplication
import com.arjunren.netsurvey.data.AppSettings
import com.arjunren.netsurvey.data.local.AccessPointEntity
import com.arjunren.netsurvey.data.local.FloorEntity
import com.arjunren.netsurvey.data.local.FloorPlanEntity
import com.arjunren.netsurvey.data.local.ProjectEntity
import com.arjunren.netsurvey.data.local.SurveyObservationRow
import com.arjunren.netsurvey.data.local.SurveyPointEntity
import com.arjunren.netsurvey.data.local.SurveySessionEntity
import com.arjunren.netsurvey.data.local.WifiObservationEntity
import com.arjunren.netsurvey.domain.applyCalibration
import com.arjunren.netsurvey.storage.FloorPlanImporter
import com.arjunren.netsurvey.storage.InstallationPhotoImporter
import com.arjunren.netsurvey.wifi.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppSelection(val projectId: Long? = null, val floorId: Long? = null, val surveyId: Long? = null)

sealed interface UiMessage {
    data class Success(val text: String) : UiMessage
    data class Error(val text: String) : UiMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NetSurveyApplication).container
    private val repository = container.repository
    private val dao = repository.dao
    private val importer = FloorPlanImporter(application)
    private val photoImporter = InstallationPhotoImporter(application)

    val projects = dao.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val scanStatus = container.wifi.scanStatus

    private val _selection = MutableStateFlow(AppSelection())
    val selection: StateFlow<AppSelection> = _selection
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val floors = _selection.flatMapLatest { selected ->
        selected.projectId?.let(dao::observeFloors) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val surveys = _selection.flatMapLatest { selected ->
        selected.projectId?.let(dao::observeSurveys) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accessPoints = _selection.flatMapLatest { selected ->
        selected.projectId?.let(dao::observeAccessPoints) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installationPhotos = _selection.flatMapLatest { selected ->
        selected.projectId?.let(dao::observeInstallationPhotos) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val floorPlan = _selection.flatMapLatest { selected ->
        selected.floorId?.let(dao::observeFloorPlan) ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val surveyPoints = _selection.flatMapLatest { selected ->
        selected.surveyId?.let(dao::observePoints) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val surveyRows = _selection.flatMapLatest { selected ->
        selected.surveyId?.let { dao.observeSurveyRows(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projectSurveyRows = _selection.flatMapLatest { selected ->
        selected.projectId?.let(dao::observeProjectSurveyRows) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProject = combine(projects, _selection) { list, selected -> list.firstOrNull { it.id == selected.projectId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            projects.collect { list ->
                val current = _selection.value
                if (current.projectId == null && list.isNotEmpty()) selectProject(list.first().id)
            }
        }
        viewModelScope.launch {
            floors.collect { list ->
                val current = _selection.value
                if (current.projectId != null && current.floorId !in list.map { it.id }) {
                    _selection.value = current.copy(floorId = list.firstOrNull()?.id, surveyId = null)
                }
            }
        }
        viewModelScope.launch {
            surveys.collect { list ->
                val current = _selection.value
                val onFloor = list.filter { it.floorId == current.floorId }
                if (current.floorId != null && current.surveyId !in onFloor.map { it.id }) {
                    _selection.value = current.copy(surveyId = onFloor.firstOrNull()?.id)
                }
            }
        }
    }

    fun selectProject(id: Long) {
        _selection.value = AppSelection(projectId = id)
    }

    fun selectFloor(id: Long) {
        _selection.value = _selection.value.copy(floorId = id, surveyId = null)
    }

    fun selectSurvey(id: Long) {
        _selection.value = _selection.value.copy(surveyId = id)
    }

    fun createProject(name: String, customer: String, engineer: String) = launchAction("Project created") {
        val id = repository.createProject(name, customer, engineer)
        selectProject(id)
    }

    fun addFloor(name: String) = launchAction("Floor added") {
        val projectId = requireNotNull(_selection.value.projectId) { "Create or select a project first." }
        val id = repository.addFloor(projectId, name)
        selectFloor(id)
    }

    fun importFloorPlan(uri: Uri) = launchAction("Floor plan imported") {
        val floorId = requireNotNull(_selection.value.floorId) { "Select a floor first." }
        val plan = importer.import(floorId, uri)
        repository.replaceFloorPlan(plan)
    }

    fun importInstallationPhoto(uri: Uri) = launchAction("Installation photo added") {
        val selected = _selection.value
        val projectId = requireNotNull(selected.projectId) { "Select a project first." }
        dao.insertInstallationPhoto(photoImporter.import(projectId, selected.floorId, uri))
    }

    fun calibrateFloor(x1: Double, y1: Double, x2: Double, y2: Double, meters: Double) = launchAction("Floor scale calibrated") {
        require(meters > 0) { "Distance must be greater than zero." }
        val floorId = requireNotNull(_selection.value.floorId)
        val floor = requireNotNull(dao.floor(floorId))
        dao.updateFloor(floor.copy(calibrationMeters = meters, calibrationX1 = x1, calibrationY1 = y1, calibrationX2 = x2, calibrationY2 = y2))
    }

    fun startSurvey(name: String, type: String) = launchAction("Survey session started") {
        val selected = _selection.value
        val projectId = requireNotNull(selected.projectId) { "Select a project first." }
        val floorId = requireNotNull(selected.floorId) { "Add or select a floor first." }
        val id = repository.startSurvey(
            SurveySessionEntity(
                projectId = projectId,
                floorId = floorId,
                name = name.ifBlank { "Survey ${System.currentTimeMillis()}" },
                type = type,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = Build.VERSION.RELEASE,
                appVersion = BuildConfig.VERSION_NAME,
            ),
        )
        selectSurvey(id)
    }

    fun measureAt(x: Double, y: Double, notes: String = "") = launchAction("Measurement point saved") {
        val selected = _selection.value
        val surveyId = requireNotNull(selected.surveyId) { "Start or select a survey first." }
        val floorId = requireNotNull(selected.floorId)
        val networks = container.wifi.cachedResults()
        require(networks.isNotEmpty()) { "No Wi-Fi observations are available. Run a scan first." }
        val now = System.currentTimeMillis()
        val newestAge = networks.minOf { (now - it.timestampMillis).coerceAtLeast(0) }
        val confidence = when {
            networks.any { it.connected } && newestAge < 5_000 -> "Connected AP Live Sample"
            newestAge < 15_000 -> "Fresh Scan"
            newestAge < 120_000 -> "Cached Scan"
            else -> "Old Scan Warning"
        }
        val point = SurveyPointEntity(
            surveyId = surveyId,
            floorId = floorId,
            xNormalized = x.coerceIn(0.0, 1.0),
            yNormalized = y.coerceIn(0.0, 1.0),
            notes = notes.trim(),
            confidence = confidence,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
        )
        val offset = settings.value.calibrationOffsetDb
        val observations = networks.map { network ->
            WifiObservationEntity(
                pointId = 0,
                ssid = network.ssid,
                bssid = network.bssid,
                rawRssi = network.rssi,
                displayedRssi = applyCalibration(network.rssi, offset),
                frequencyMhz = network.frequencyMhz,
                channel = network.channel,
                band = network.band,
                capabilities = network.capabilities,
                channelWidthMhz = network.channelWidthMhz,
                wifiStandard = network.wifiStandard,
                scanTimestamp = network.timestampMillis,
                scanAgeMillis = (now - network.timestampMillis).coerceAtLeast(0),
                connected = network.connected,
            )
        }
        repository.saveMeasurement(point, observations)
    }

    fun addAccessPoint(name: String, ssid: String, bssid: String, x: Double? = null, y: Double? = null) = launchAction("Access point added") {
        val projectId = requireNotNull(_selection.value.projectId) { "Select a project first." }
        repository.addAccessPoint(
            AccessPointEntity(
                projectId = projectId,
                floorId = _selection.value.floorId,
                name = name.ifBlank { "Access Point" },
                ssid = ssid.trim(),
                bssid = bssid.trim(),
                xNormalized = x,
                yNormalized = y,
            ),
        )
    }

    fun requestScan() = container.wifi.requestScan()

    fun updateSettings(value: AppSettings) = launchAction("Settings saved") { container.settings.update(value) }

    fun restoreBackup(uri: Uri) = launchAction("Project backup restored") {
        val id = container.exports.restoreBackup(uri)
        selectProject(id)
    }

    private fun launchAction(success: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { _messages.emit(UiMessage.Success(success)) }
                .onFailure { error -> _messages.emit(UiMessage.Error(error.message ?: "The operation could not be completed.")) }
        }
    }
}
