package com.arjunren.netsurvey.data

import androidx.room.withTransaction
import com.arjunren.netsurvey.data.local.AccessPointEntity
import com.arjunren.netsurvey.data.local.FloorEntity
import com.arjunren.netsurvey.data.local.FloorPlanEntity
import com.arjunren.netsurvey.data.local.NetSurveyDatabase
import com.arjunren.netsurvey.data.local.ProjectEntity
import com.arjunren.netsurvey.data.local.SurveyPointEntity
import com.arjunren.netsurvey.data.local.SurveySessionEntity
import com.arjunren.netsurvey.data.local.WifiObservationEntity

class NetSurveyRepository(private val database: NetSurveyDatabase) {
    val dao = database.dao()

    suspend fun createProject(name: String, customer: String, engineer: String): Long {
        require(name.isNotBlank()) { "Project name is required" }
        return dao.insertProject(
            ProjectEntity(name = name.trim(), customer = customer.trim(), engineer = engineer.trim()),
        )
    }

    suspend fun addFloor(projectId: Long, name: String): Long {
        require(name.isNotBlank()) { "Floor name is required" }
        return dao.insertFloor(FloorEntity(projectId = projectId, name = name.trim()))
    }

    suspend fun replaceFloorPlan(plan: FloorPlanEntity): Long = database.withTransaction {
        dao.deleteFloorPlan(plan.floorId)
        dao.insertFloorPlan(plan)
    }

    suspend fun startSurvey(session: SurveySessionEntity): Long = dao.insertSurvey(session)

    suspend fun saveMeasurement(
        point: SurveyPointEntity,
        observations: List<WifiObservationEntity>,
    ): Long = database.withTransaction {
        require(point.xNormalized in 0.0..1.0 && point.yNormalized in 0.0..1.0) {
            "Measurement coordinates must be normalized"
        }
        val pointId = dao.insertPoint(point)
        dao.insertObservations(observations.map { it.copy(pointId = pointId) })
        pointId
    }

    suspend fun addAccessPoint(ap: AccessPointEntity): Long = dao.insertAccessPoint(ap)
}
