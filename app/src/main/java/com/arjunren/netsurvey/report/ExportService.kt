package com.arjunren.netsurvey.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.room.withTransaction
import com.arjunren.netsurvey.data.local.AccessPointEntity
import com.arjunren.netsurvey.data.local.FloorEntity
import com.arjunren.netsurvey.data.local.FloorPlanEntity
import com.arjunren.netsurvey.data.local.NetSurveyDatabase
import com.arjunren.netsurvey.data.local.ProjectEntity
import com.arjunren.netsurvey.data.local.ReportEntity
import com.arjunren.netsurvey.data.local.SurveyPointEntity
import com.arjunren.netsurvey.data.local.SurveySessionEntity
import com.arjunren.netsurvey.data.local.WifiObservationEntity
import com.arjunren.netsurvey.domain.IdwInterpolator
import com.arjunren.netsurvey.domain.Measurement
import com.arjunren.netsurvey.domain.csvEscape
import com.arjunren.netsurvey.domain.isSafeZipEntry
import com.arjunren.netsurvey.domain.shareMime
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProjectBackup(
    val schemaVersion: Int = 2,
    val exportedAt: Long,
    val project: ProjectDto,
    val floors: List<FloorDto>,
    val floorPlans: List<FloorPlanDto>,
    val surveys: List<SurveyDto>,
    val points: List<PointDto>,
    val observations: List<ObservationDto>,
    val accessPoints: List<AccessPointDto>,
)

@Serializable data class ProjectDto(val id: Long, val name: String, val customer: String, val address: String, val engineer: String, val notes: String, val createdAt: Long, val updatedAt: Long)
@Serializable data class FloorDto(val id: Long, val projectId: Long, val name: String, val sortOrder: Int, val notes: String, val ceilingHeightMeters: Double?, val calibrationMeters: Double?, val calibrationX1: Double?, val calibrationY1: Double?, val calibrationX2: Double?, val calibrationY2: Double?)
@Serializable data class FloorPlanDto(val id: Long, val floorId: Long, val archiveName: String, val mimeType: String, val width: Int, val height: Int, val rotationDegrees: Int)
@Serializable data class SurveyDto(val id: Long, val projectId: Long, val floorId: Long, val name: String, val type: String, val startedAt: Long, val endedAt: Long?, val notes: String, val deviceModel: String, val androidVersion: String, val appVersion: String)
@Serializable data class PointDto(val id: Long, val surveyId: Long, val floorId: Long, val roomId: Long?, val x: Double, val y: Double, val timestamp: Long, val notes: String, val confidence: String, val deviceModel: String, val androidVersion: String)
@Serializable data class ObservationDto(val pointId: Long, val ssid: String, val bssid: String, val rawRssi: Int, val displayedRssi: Int, val frequencyMhz: Int, val channel: Int?, val band: String, val capabilities: String, val channelWidthMhz: Int?, val wifiStandard: String?, val scanTimestamp: Long, val scanAgeMillis: Long, val connected: Boolean)
@Serializable data class AccessPointDto(val floorId: Long?, val name: String, val assetId: String, val ssid: String, val bssid: String, val vendor: String, val model: String, val serial: String, val mac: String, val ipAddress: String, val x: Double?, val y: Double?, val mountingHeightMeters: Double?, val mountingType: String, val channel24: Int?, val channel5: Int?, val channelWidthMhz: Int?, val switchName: String, val switchPort: String, val poe: String, val vlan: String, val notes: String)

class ExportService(
    private val context: Context,
    private val database: NetSurveyDatabase,
) {
    private val dao = database.dao()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false; explicitNulls = false }
    private val shareDir get() = File(context.cacheDir, "share").apply { mkdirs() }

    suspend fun exportCsv(projectId: Long): File = withContext(Dispatchers.IO) {
        cleanupOldExports()
        val project = requireNotNull(dao.exportProject(projectId))
        val surveys = dao.exportSurveys(projectId)
        val points = dao.exportPoints(surveys.map { it.id })
        val observations = dao.exportObservations(points.map { it.id })
        val pointMap = points.associateBy { it.id }
        val surveyMap = surveys.associateBy { it.id }
        val file = File(shareDir, safeName(project.name) + "-measurements.csv")
        file.bufferedWriter().use { writer ->
            writer.appendLine("project,survey,survey_type,floor_id,point_time,x_normalized,y_normalized,confidence,ssid,bssid,rssi_raw,rssi_displayed,frequency_mhz,channel,band,scan_age_ms,connected")
            observations.forEach { observation ->
                val point = pointMap[observation.pointId] ?: return@forEach
                val survey = surveyMap[point.surveyId] ?: return@forEach
                val fields = listOf(
                    project.name, survey.name, survey.type, point.floorId.toString(), point.timestamp.toString(),
                    point.xNormalized.toString(), point.yNormalized.toString(), point.confidence,
                    observation.ssid, observation.bssid, observation.rawRssi.toString(), observation.displayedRssi.toString(),
                    observation.frequencyMhz.toString(), observation.channel?.toString().orEmpty(), observation.band,
                    observation.scanAgeMillis.toString(), observation.connected.toString(),
                )
                writer.appendLine(fields.joinToString(",", transform = ::csvEscape))
            }
        }
        dao.insertReport(ReportEntity(projectId = projectId, format = "CSV", localPath = file.absolutePath))
        file
    }

    suspend fun exportPdf(projectId: Long): File = withContext(Dispatchers.IO) {
        cleanupOldExports()
        val project = requireNotNull(dao.exportProject(projectId))
        val floors = dao.exportFloors(projectId)
        val surveys = dao.exportSurveys(projectId)
        val points = dao.exportPoints(surveys.map { it.id })
        val observations = dao.exportObservations(points.map { it.id })
        val values = observations.map { it.displayedRssi }
        val weak = values.count { it < -72 }
        val poor = values.count { it < -80 }
        val average = values.takeIf { it.isNotEmpty() }?.average()
        val median = values.sorted().let { sorted ->
            if (sorted.isEmpty()) null else if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 61, 71); textSize = 28f; isFakeBoldText = true }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 88, 102); textSize = 16f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 12f }
        canvas.drawText("NetSurvey Wi-Fi Site Survey", 42f, 58f, title)
        canvas.drawText(project.name, 42f, 90f, heading)
        var y = 122f
        val rows = listOf(
            "Customer / site" to project.customer.ifBlank { "Not specified" },
            "Engineer" to project.engineer.ifBlank { "Not specified" },
            "Floors" to floors.size.toString(),
            "Survey sessions" to surveys.size.toString(),
            "Measurement points" to points.size.toString(),
            "Wi-Fi observations" to observations.size.toString(),
            "Average RSSI" to (average?.let { "%.1f dBm".format(it) } ?: "Not available"),
            "Median RSSI" to (median?.let { "%.1f dBm".format(it) } ?: "Not available"),
            "Minimum / maximum" to (if (values.isEmpty()) "Not available" else "${values.min()} / ${values.max()} dBm"),
            "Weak observations (< -72 dBm)" to weak.toString(),
            "Poor observations (< -80 dBm)" to poor.toString(),
        )
        rows.forEach { (label, value) ->
            canvas.drawText(label, 42f, y, heading)
            canvas.drawText(value, 255f, y, body)
            y += 26f
        }
        y += 22f
        canvas.drawText("Engineering note", 42f, y, heading)
        y += 22f
        wrapText(
            canvas,
            "Wi-Fi RSSI varies with client hardware, antenna orientation, people, doors, furniture, interference, transmit power, building materials, AP configuration, and environmental change. Interpolated or predicted coverage is an engineering aid, not a substitute for actual measurements.",
            body,
            42f,
            y,
            510f,
        )
        document.finishPage(page)
        val file = File(shareDir, safeName(project.name) + "-report.pdf")
        FileOutputStream(file).use(document::writeTo)
        document.close()
        dao.insertReport(ReportEntity(projectId = projectId, format = "PDF", localPath = file.absolutePath))
        file
    }

    suspend fun exportHeatmapPng(projectId: Long, surveyId: Long, ssid: String = ""): File = withContext(Dispatchers.IO) {
        cleanupOldExports()
        val project = requireNotNull(dao.exportProject(projectId))
        val surveys = dao.exportSurveys(projectId)
        val survey = requireNotNull(surveys.firstOrNull { it.id == surveyId })
        val points = dao.exportPoints(listOf(surveyId))
        val observations = dao.exportObservations(points.map { it.id })
            .filter { ssid.isBlank() || it.ssid == ssid }
        val strongest = observations.groupBy { it.pointId }.mapNotNull { (pointId, rows) ->
            val point = points.firstOrNull { it.id == pointId } ?: return@mapNotNull null
            Measurement(point.xNormalized, point.yNormalized, rows.maxOf { it.displayedRssi }.toDouble())
        }
        require(strongest.isNotEmpty()) { "No survey measurements are available for this heatmap." }
        val width = 1400
        val height = 1000
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(245, 248, 248))
        val grid = IdwInterpolator.interpolate(strongest, 70, 50)
        val cellW = width / 70f
        val cellH = 820f / 50f
        val paint = Paint()
        grid.forEach { cell ->
            paint.color = heatColor(cell.rssi)
            paint.alpha = 155
            canvas.drawRect((cell.x * width).toFloat(), 90 + (cell.y * 820).toFloat(), (cell.x * width + cellW + 1).toFloat(), 90 + (cell.y * 820 + cellH + 1).toFloat(), paint)
        }
        paint.alpha = 255
        paint.color = Color.BLACK
        strongest.forEach { point ->
            canvas.drawCircle((point.x * width).toFloat(), 90 + (point.y * 820).toFloat(), 10f, paint)
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 61, 71); textSize = 34f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 22f }
        canvas.drawText("${project.name} — ${survey.name}", 28f, 48f, title)
        canvas.drawText("Measured points are black markers; colors between points are IDW interpolation.", 28f, 965f, body)
        val file = File(shareDir, safeName(project.name) + "-heatmap.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        dao.insertReport(ReportEntity(projectId = projectId, surveyId = surveyId, format = "PNG", localPath = file.absolutePath))
        file
    }

    suspend fun exportBackup(projectId: Long): File = withContext(Dispatchers.IO) {
        cleanupOldExports()
        val project = requireNotNull(dao.exportProject(projectId))
        val floors = dao.exportFloors(projectId)
        val floorPlans = dao.exportFloorPlans(floors.map { it.id })
        val surveys = dao.exportSurveys(projectId)
        val points = dao.exportPoints(surveys.map { it.id })
        val observations = dao.exportObservations(points.map { it.id })
        val accessPoints = dao.exportAccessPoints(projectId)
        val backup = ProjectBackup(
            exportedAt = System.currentTimeMillis(),
            project = project.toDto(),
            floors = floors.map { it.toDto() },
            floorPlans = floorPlans.map { it.toDto() },
            surveys = surveys.map { it.toDto() },
            points = points.map { it.toDto() },
            observations = observations.map { it.toDto() },
            accessPoints = accessPoints.map { it.toDto() },
        )
        val file = File(shareDir, safeName(project.name) + ".netsurvey.zip")
        ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(json.encodeToString(backup).encodeToByteArray())
            zip.closeEntry()
            floorPlans.forEach { plan ->
                val source = File(plan.localPath)
                if (source.isFile && source.length() <= MAX_IMAGE_BYTES) {
                    zip.putNextEntry(ZipEntry("floorplans/plan-${plan.id}.bin"))
                    source.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        file
    }

    suspend fun restoreBackup(uri: Uri): Long = withContext(Dispatchers.IO) {
        val importRoot = File(context.filesDir, "imports/${System.currentTimeMillis()}").apply { mkdirs() }
        var backupJson: String? = null
        val extracted = mutableMapOf<String, File>()
        var totalBytes = 0L
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(isSafeZipEntry(entry.name)) { "Backup contains an unsafe path." }
                    require(!entry.isDirectory) { "Backup contains unsupported directories." }
                    val bytes = readBounded(zip, MAX_ENTRY_BYTES)
                    require(bytes.size <= MAX_ENTRY_BYTES) { "Backup entry is too large." }
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_BACKUP_BYTES) { "Backup is too large." }
                    if (entry.name == "project.json") backupJson = bytes.decodeToString()
                    else if (entry.name.startsWith("floorplans/")) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        require(options.outWidth in 1..12_000 && options.outHeight in 1..12_000) { "Backup contains an invalid floor-plan image." }
                        require(options.outMimeType in setOf("image/png", "image/jpeg")) { "Backup floor plans must be PNG or JPEG." }
                        val target = File(importRoot, File(entry.name).name)
                        target.writeBytes(bytes)
                        extracted[entry.name] = target
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Unable to read backup file.")
        val backup = json.decodeFromString<ProjectBackup>(requireNotNull(backupJson) { "Backup has no project manifest." })
        require(backup.schemaVersion in 1..2) { "Unsupported backup schema ${backup.schemaVersion}." }
        database.withTransaction {
            val newProjectId = dao.insertProject(backup.project.toEntity().copy(id = 0, name = backup.project.name + " (Imported)", updatedAt = System.currentTimeMillis()))
            val floorIds = mutableMapOf<Long, Long>()
            backup.floors.forEach { floor -> floorIds[floor.id] = dao.insertFloor(floor.toEntity(newProjectId)) }
            backup.floorPlans.forEach { plan ->
                val newFloorId = floorIds[plan.floorId] ?: return@forEach
                val file = extracted[plan.archiveName] ?: return@forEach
                dao.insertFloorPlan(plan.toEntity(newFloorId, file.absolutePath))
            }
            val surveyIds = mutableMapOf<Long, Long>()
            backup.surveys.forEach { survey ->
                val newFloorId = floorIds[survey.floorId] ?: return@forEach
                surveyIds[survey.id] = dao.insertSurvey(survey.toEntity(newProjectId, newFloorId))
            }
            val pointIds = mutableMapOf<Long, Long>()
            backup.points.forEach { point ->
                val newSurveyId = surveyIds[point.surveyId] ?: return@forEach
                val newFloorId = floorIds[point.floorId] ?: return@forEach
                pointIds[point.id] = dao.insertPoint(point.toEntity(newSurveyId, newFloorId))
            }
            val newObservations = backup.observations.mapNotNull { observation ->
                val newPointId = pointIds[observation.pointId] ?: return@mapNotNull null
                observation.toEntity(newPointId)
            }
            if (newObservations.isNotEmpty()) dao.insertObservations(newObservations)
            backup.accessPoints.forEach { ap -> dao.insertAccessPoint(ap.toEntity(newProjectId, ap.floorId?.let(floorIds::get))) }
            newProjectId
        }
    }

    fun shareIntent(file: File, title: String): Intent {
        require(file.isFile && file.canonicalPath.startsWith(shareDir.canonicalPath))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = shareMime(file.extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(title, uri)
        }
    }

    fun textSummary(project: ProjectEntity, measurementCount: Int, averageRssi: Double?, weakCount: Int): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, """NetSurvey Wi-Fi Site Survey

Project: ${project.name}
Site: ${project.customer.ifBlank { "Not specified" }}
Measurement Points: $measurementCount
Average RSSI: ${averageRssi?.let { "%.1f dBm".format(it) } ?: "Not available"}
Weak Points: $weakCount

Report generated locally with NetSurvey.""".trimIndent())
        }

    private fun cleanupOldExports() {
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        shareDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun readBounded(input: ZipInputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Backup entry is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(60).ifBlank { "netsurvey" }

    private fun heatColor(rssi: Double): Int = when {
        rssi >= -55 -> Color.rgb(40, 167, 69)
        rssi >= -67 -> Color.rgb(197, 243, 107)
        rssi >= -72 -> Color.rgb(255, 193, 7)
        rssi >= -80 -> Color.rgb(255, 119, 48)
        else -> Color.rgb(201, 48, 44)
    }

    private fun wrapText(canvas: Canvas, text: String, paint: Paint, x: Float, y: Float, width: Float) {
        var line = ""
        var baseline = y
        text.split(' ').forEach { word ->
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) > width) {
                canvas.drawText(line, x, baseline, paint)
                baseline += 18f
                line = word
            } else line = candidate
        }
        if (line.isNotBlank()) canvas.drawText(line, x, baseline, paint)
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 30L * 1024 * 1024
        private const val MAX_BACKUP_BYTES = 250L * 1024 * 1024
    }
}

private fun ProjectEntity.toDto() = ProjectDto(id, name, customer, address, engineer, notes, createdAt, updatedAt)
private fun ProjectDto.toEntity() = ProjectEntity(id, name, customer, address, engineer, notes, createdAt, updatedAt)
private fun FloorEntity.toDto() = FloorDto(id, projectId, name, sortOrder, notes, ceilingHeightMeters, calibrationMeters, calibrationX1, calibrationY1, calibrationX2, calibrationY2)
private fun FloorDto.toEntity(project: Long) = FloorEntity(0, project, null, name, sortOrder, notes, ceilingHeightMeters, calibrationMeters, calibrationX1, calibrationY1, calibrationX2, calibrationY2)
private fun FloorPlanEntity.toDto() = FloorPlanDto(id, floorId, "floorplans/plan-${id}.bin", mimeType, width, height, rotationDegrees)
private fun FloorPlanDto.toEntity(floor: Long, path: String) = FloorPlanEntity(0, floor, path, mimeType, width, height, System.currentTimeMillis(), rotationDegrees)
private fun SurveySessionEntity.toDto() = SurveyDto(id, projectId, floorId, name, type, startedAt, endedAt, notes, deviceModel, androidVersion, appVersion)
private fun SurveyDto.toEntity(project: Long, floor: Long) = SurveySessionEntity(0, project, floor, name, type, startedAt, endedAt, notes, deviceModel, androidVersion, appVersion)
private fun SurveyPointEntity.toDto() = PointDto(id, surveyId, floorId, roomId, xNormalized, yNormalized, timestamp, notes, confidence, deviceModel, androidVersion)
private fun PointDto.toEntity(survey: Long, floor: Long) = SurveyPointEntity(0, survey, floor, null, x, y, timestamp, notes, confidence, deviceModel, androidVersion)
private fun WifiObservationEntity.toDto() = ObservationDto(pointId, ssid, bssid, rawRssi, displayedRssi, frequencyMhz, channel, band, capabilities, channelWidthMhz, wifiStandard, scanTimestamp, scanAgeMillis, connected)
private fun ObservationDto.toEntity(point: Long) = WifiObservationEntity(0, point, ssid, bssid, rawRssi, displayedRssi, frequencyMhz, channel, band, capabilities, channelWidthMhz, wifiStandard, scanTimestamp, scanAgeMillis, connected)
private fun AccessPointEntity.toDto() = AccessPointDto(floorId, name, assetId, ssid, bssid, vendor, model, serial, mac, ipAddress, xNormalized, yNormalized, mountingHeightMeters, mountingType, channel24, channel5, channelWidthMhz, switchName, switchPort, poe, vlan, notes)
private fun AccessPointDto.toEntity(project: Long, floor: Long?) = AccessPointEntity(projectId = project, floorId = floor, name = name, assetId = assetId, ssid = ssid, bssid = bssid, vendor = vendor, model = model, serial = serial, mac = mac, ipAddress = ipAddress, xNormalized = x, yNormalized = y, mountingHeightMeters = mountingHeightMeters, mountingType = mountingType, channel24 = channel24, channel5 = channel5, channelWidthMhz = channelWidthMhz, switchName = switchName, switchPort = switchPort, poe = poe, vlan = vlan, notes = notes)
