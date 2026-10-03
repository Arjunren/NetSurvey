package com.arjunren.netsurvey.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects", indices = [Index("updatedAt")])
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val customer: String = "",
    val address: String = "",
    val engineer: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "buildings",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class BuildingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val notes: String = "",
)

@Entity(
    tableName = "floors",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index(value = ["projectId", "sortOrder"])],
)
data class FloorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val buildingId: Long? = null,
    val name: String,
    val sortOrder: Int = 0,
    val notes: String = "",
    val ceilingHeightMeters: Double? = null,
    val calibrationMeters: Double? = null,
    val calibrationX1: Double? = null,
    val calibrationY1: Double? = null,
    val calibrationX2: Double? = null,
    val calibrationY2: Double? = null,
)

@Entity(
    tableName = "floor_plans",
    foreignKeys = [ForeignKey(FloorEntity::class, ["id"], ["floorId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["floorId"], unique = true)],
)
data class FloorPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val floorId: Long,
    val localPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val importedAt: Long = System.currentTimeMillis(),
    val rotationDegrees: Int = 0,
)

@Entity(
    tableName = "rooms",
    foreignKeys = [ForeignKey(FloorEntity::class, ["id"], ["floorId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("floorId"), Index(value = ["floorId", "name"])],
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val floorId: Long,
    val name: String,
    val notes: String = "",
)

@Entity(
    tableName = "access_points",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("floorId"), Index("bssid")],
)
data class AccessPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val floorId: Long? = null,
    val roomId: Long? = null,
    val name: String,
    val assetId: String = "",
    val ssid: String = "",
    val bssid: String = "",
    val vendor: String = "",
    val model: String = "",
    val serial: String = "",
    val mac: String = "",
    val ipAddress: String = "",
    val xNormalized: Double? = null,
    val yNormalized: Double? = null,
    val mountingHeightMeters: Double? = null,
    val mountingType: String = "",
    val channel24: Int? = null,
    val channel5: Int? = null,
    val channelWidthMhz: Int? = null,
    val transmitPowerDbm: Double? = null,
    val switchName: String = "",
    val switchPort: String = "",
    val poe: String = "",
    val vlan: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "survey_sessions",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("floorId"), Index("startedAt")],
)
data class SurveySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val floorId: Long,
    val name: String,
    val type: String = "Baseline",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val notes: String = "",
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String,
)

@Entity(
    tableName = "survey_points",
    foreignKeys = [ForeignKey(SurveySessionEntity::class, ["id"], ["surveyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("surveyId"), Index("floorId"), Index("timestamp")],
)
data class SurveyPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyId: Long,
    val floorId: Long,
    val roomId: Long? = null,
    val xNormalized: Double,
    val yNormalized: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val confidence: String,
    val deviceModel: String,
    val androidVersion: String,
)

@Entity(
    tableName = "wifi_observations",
    foreignKeys = [ForeignKey(SurveyPointEntity::class, ["id"], ["pointId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("pointId"), Index("ssid"), Index("bssid"), Index("scanTimestamp")],
)
data class WifiObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pointId: Long,
    val ssid: String,
    val bssid: String,
    val rawRssi: Int,
    val displayedRssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: String,
    val capabilities: String,
    val channelWidthMhz: Int? = null,
    val wifiStandard: String? = null,
    val scanTimestamp: Long,
    val scanAgeMillis: Long,
    val connected: Boolean,
)

@Entity(
    tableName = "saved_wifi_snapshots",
    indices = [Index("savedAt"), Index("bssid")],
)
data class SavedWifiSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: String,
    val capabilities: String,
    val channelWidthMhz: Int? = null,
    val wifiStandard: String? = null,
    val observedAt: Long,
    val savedAt: Long = System.currentTimeMillis(),
    val connected: Boolean,
)

@Entity(
    tableName = "walls",
    foreignKeys = [ForeignKey(FloorEntity::class, ["id"], ["floorId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("floorId")],
)
data class WallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val floorId: Long,
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
    val type: String,
    val thicknessMeters: Double? = null,
    val attenuationDb: Double? = null,
)

@Entity(tableName = "coverage_profiles")
data class CoverageProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minimumRssi: Int,
    val overlapTargetDbm: Int? = null,
    val notes: String = "",
)

@Entity(tableName = "device_calibrations", indices = [Index("deviceModel")])
data class DeviceCalibrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceModel: String,
    val offsetDb: Int = 0,
    val referenceAp: String = "",
    val notes: String = "",
    val calibratedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "installation_photos",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("floorId"), Index("accessPointId")],
)
data class InstallationPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val floorId: Long? = null,
    val accessPointId: Long? = null,
    val localPath: String,
    val caption: String = "",
    val capturedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val label: String,
    val completed: Boolean = false,
    val notes: String = "",
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "reports",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("generatedAt")],
)
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val surveyId: Long? = null,
    val format: String,
    val localPath: String,
    val generatedAt: Long = System.currentTimeMillis(),
)

data class ProjectSummary(
    val id: Long,
    val name: String,
    val customer: String,
    val updatedAt: Long,
    val floorCount: Int,
    val surveyCount: Int,
    val pointCount: Int,
    val apCount: Int,
)

data class SurveyObservationRow(
    val pointId: Long,
    val surveyId: Long,
    val floorId: Long,
    val xNormalized: Double,
    val yNormalized: Double,
    val pointTimestamp: Long,
    val confidence: String,
    val ssid: String,
    val bssid: String,
    val rawRssi: Int,
    val displayedRssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: String,
    val scanAgeMillis: Long,
    val connected: Boolean,
)

@Dao
interface NetSurveyDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: Long): ProjectEntity?

    @Query("SELECT * FROM floors WHERE projectId = :projectId ORDER BY sortOrder, name")
    fun observeFloors(projectId: Long): Flow<List<FloorEntity>>

    @Query("SELECT * FROM floors WHERE id = :id")
    suspend fun floor(id: Long): FloorEntity?

    @Query("SELECT * FROM floor_plans WHERE floorId = :floorId LIMIT 1")
    fun observeFloorPlan(floorId: Long): Flow<FloorPlanEntity?>

    @Query("SELECT * FROM access_points WHERE projectId = :projectId ORDER BY name")
    fun observeAccessPoints(projectId: Long): Flow<List<AccessPointEntity>>

    @Query("SELECT * FROM installation_photos WHERE projectId = :projectId ORDER BY capturedAt DESC")
    fun observeInstallationPhotos(projectId: Long): Flow<List<InstallationPhotoEntity>>

    @Query("SELECT * FROM survey_sessions WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeSurveys(projectId: Long): Flow<List<SurveySessionEntity>>

    @Query("SELECT * FROM survey_points WHERE surveyId = :surveyId ORDER BY timestamp")
    fun observePoints(surveyId: Long): Flow<List<SurveyPointEntity>>

    @Query("SELECT * FROM wifi_observations WHERE pointId = :pointId ORDER BY displayedRssi DESC")
    fun observeObservations(pointId: Long): Flow<List<WifiObservationEntity>>

    @Query("SELECT * FROM saved_wifi_snapshots ORDER BY savedAt DESC, id DESC")
    fun observeSavedWifiSnapshots(): Flow<List<SavedWifiSnapshotEntity>>

    @Query("""
        SELECT p.id AS pointId, p.surveyId, p.floorId, p.xNormalized, p.yNormalized,
               p.timestamp AS pointTimestamp, p.confidence, o.ssid, o.bssid, o.rawRssi,
               o.displayedRssi, o.frequencyMhz, o.channel, o.band, o.scanAgeMillis, o.connected
        FROM survey_points p JOIN wifi_observations o ON o.pointId = p.id
        WHERE p.surveyId = :surveyId AND (:ssid = '' OR o.ssid = :ssid)
        ORDER BY p.timestamp, o.displayedRssi DESC
    """)
    fun observeSurveyRows(surveyId: Long, ssid: String = ""): Flow<List<SurveyObservationRow>>

    @Query("""
        SELECT p.id AS pointId, p.surveyId, p.floorId, p.xNormalized, p.yNormalized,
               p.timestamp AS pointTimestamp, p.confidence, o.ssid, o.bssid, o.rawRssi,
               o.displayedRssi, o.frequencyMhz, o.channel, o.band, o.scanAgeMillis, o.connected
        FROM survey_points p
        JOIN survey_sessions s ON s.id = p.surveyId
        JOIN wifi_observations o ON o.pointId = p.id
        WHERE s.projectId = :projectId
        ORDER BY s.startedAt, p.timestamp, o.displayedRssi DESC
    """)
    fun observeProjectSurveyRows(projectId: Long): Flow<List<SurveyObservationRow>>

    @Query("SELECT COUNT(*) FROM projects") suspend fun projectCount(): Int
    @Query("SELECT COUNT(*) FROM floors") suspend fun floorCount(): Int
    @Query("SELECT COUNT(*) FROM survey_points") suspend fun pointCount(): Int
    @Query("SELECT COUNT(*) FROM access_points") suspend fun apCount(): Int
    @Query("SELECT COUNT(*) FROM reports") suspend fun reportCount(): Int

    @Insert suspend fun insertProject(value: ProjectEntity): Long
    @Insert suspend fun insertBuilding(value: BuildingEntity): Long
    @Insert suspend fun insertFloor(value: FloorEntity): Long
    @Insert suspend fun insertFloorPlan(value: FloorPlanEntity): Long
    @Insert suspend fun insertAccessPoint(value: AccessPointEntity): Long
    @Insert suspend fun insertInstallationPhoto(value: InstallationPhotoEntity): Long
    @Insert suspend fun insertSurvey(value: SurveySessionEntity): Long
    @Insert suspend fun insertPoint(value: SurveyPointEntity): Long
    @Insert suspend fun insertObservations(values: List<WifiObservationEntity>)
    @Insert suspend fun insertSavedWifiSnapshot(value: SavedWifiSnapshotEntity): Long
    @Insert suspend fun insertReport(value: ReportEntity): Long
    @Insert suspend fun insertCoverageProfile(value: CoverageProfileEntity): Long

    @Update suspend fun updateProject(value: ProjectEntity)
    @Update suspend fun updateFloor(value: FloorEntity)

    @Query("DELETE FROM floor_plans WHERE floorId = :floorId") suspend fun deleteFloorPlan(floorId: Long)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun deleteProject(id: Long)
    @Query("DELETE FROM floors WHERE id = :id") suspend fun deleteFloor(id: Long)
    @Query("DELETE FROM survey_sessions WHERE id = :id") suspend fun deleteSurvey(id: Long)
    @Query("DELETE FROM survey_points WHERE id = :id") suspend fun deletePoint(id: Long)
    @Query("DELETE FROM access_points WHERE id = :id") suspend fun deleteAccessPoint(id: Long)
    @Query("DELETE FROM saved_wifi_snapshots WHERE id = :id") suspend fun deleteSavedWifiSnapshot(id: Long)
    @Query("DELETE FROM saved_wifi_snapshots") suspend fun clearSavedWifiSnapshots()

    @Query("SELECT * FROM projects WHERE id = :projectId") suspend fun exportProject(projectId: Long): ProjectEntity?
    @Query("SELECT * FROM floors WHERE projectId = :projectId") suspend fun exportFloors(projectId: Long): List<FloorEntity>
    @Query("SELECT * FROM floor_plans WHERE floorId IN (:floorIds)") suspend fun exportFloorPlans(floorIds: List<Long>): List<FloorPlanEntity>
    @Query("SELECT * FROM survey_sessions WHERE projectId = :projectId") suspend fun exportSurveys(projectId: Long): List<SurveySessionEntity>
    @Query("SELECT * FROM survey_points WHERE surveyId IN (:surveyIds)") suspend fun exportPoints(surveyIds: List<Long>): List<SurveyPointEntity>
    @Query("SELECT * FROM wifi_observations WHERE pointId IN (:pointIds)") suspend fun exportObservations(pointIds: List<Long>): List<WifiObservationEntity>
    @Query("SELECT * FROM access_points WHERE projectId = :projectId") suspend fun exportAccessPoints(projectId: Long): List<AccessPointEntity>
}

@Database(
    entities = [
        ProjectEntity::class, BuildingEntity::class, FloorEntity::class, FloorPlanEntity::class,
        RoomEntity::class, AccessPointEntity::class, SurveySessionEntity::class,
        SurveyPointEntity::class, WifiObservationEntity::class, WallEntity::class,
        CoverageProfileEntity::class, DeviceCalibrationEntity::class,
        InstallationPhotoEntity::class, ChecklistItemEntity::class, ReportEntity::class,
        SavedWifiSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class NetSurveyDatabase : RoomDatabase() {
    abstract fun dao(): NetSurveyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE floors ADD COLUMN calibrationMeters REAL")
                db.execSQL("ALTER TABLE floors ADD COLUMN calibrationX1 REAL")
                db.execSQL("ALTER TABLE floors ADD COLUMN calibrationY1 REAL")
                db.execSQL("ALTER TABLE floors ADD COLUMN calibrationX2 REAL")
                db.execSQL("ALTER TABLE floors ADD COLUMN calibrationY2 REAL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_wifi_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `ssid` TEXT NOT NULL,
                        `bssid` TEXT NOT NULL,
                        `rssi` INTEGER NOT NULL,
                        `frequencyMhz` INTEGER NOT NULL,
                        `channel` INTEGER,
                        `band` TEXT NOT NULL,
                        `capabilities` TEXT NOT NULL,
                        `channelWidthMhz` INTEGER,
                        `wifiStandard` TEXT,
                        `observedAt` INTEGER NOT NULL,
                        `savedAt` INTEGER NOT NULL,
                        `connected` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_wifi_snapshots_savedAt` ON `saved_wifi_snapshots` (`savedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_wifi_snapshots_bssid` ON `saved_wifi_snapshots` (`bssid`)")
            }
        }

        fun create(context: Context): NetSurveyDatabase = Room.databaseBuilder(
            context.applicationContext,
            NetSurveyDatabase::class.java,
            "netsurvey.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
