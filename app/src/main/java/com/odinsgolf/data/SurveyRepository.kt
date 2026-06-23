package com.odinsgolf.data

import android.content.Context
import com.odinsgolf.data.model.Course
import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Green
import com.odinsgolf.data.model.Hazard
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class SurveyKind { TEE, FRONT, CENTER, BACK, HAZARD }

/** One field-captured point. Center applies to the hole's shared green. */
@Serializable
data class SurveyPoint(
    val holeNumber: Int,
    val kind: SurveyKind,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float? = null,
    val label: String = "",
    val epochMillis: Long = 0L,
)

@Serializable
data class SurveyData(val courseId: String, val points: List<SurveyPoint> = emptyList())

/**
 * Stores points captured in the app's Survey mode to files/survey_<courseId>.json,
 * and overlays them onto a loaded [Course] so captured front/back/hazards show up
 * immediately. The file can be pulled with `adb pull` and folded into the asset
 * JSON permanently.
 */
class SurveyRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun file(courseId: String) = File(context.filesDir, "survey_$courseId.json")

    fun load(courseId: String): SurveyData = runCatching {
        val f = file(courseId)
        if (!f.exists()) SurveyData(courseId) else json.decodeFromString<SurveyData>(f.readText())
    }.getOrDefault(SurveyData(courseId))

    fun add(courseId: String, point: SurveyPoint): SurveyData {
        val current = load(courseId)
        // Replace any existing TEE/FRONT/CENTER/BACK for the same hole; append hazards.
        val filtered = if (point.kind == SurveyKind.HAZARD) current.points
        else current.points.filterNot { it.holeNumber == point.holeNumber && it.kind == point.kind }
        val updated = current.copy(points = filtered + point)
        runCatching { file(courseId).writeText(json.encodeToString(updated)) }
        return updated
    }

    fun clear(courseId: String) {
        runCatching { if (file(courseId).exists()) file(courseId).delete() }
    }

    fun exportPath(courseId: String): String = file(courseId).absolutePath

    /** Apply captured points onto a freshly-loaded course. */
    fun overlay(course: Course, data: SurveyData): Course {
        if (data.points.isEmpty()) return course
        val byHole = data.points.groupBy { it.holeNumber }
        // A captured CENTER updates the shared green for every hole using it.
        val holes = course.holes.map { hole ->
            val pts = byHole[hole.number].orEmpty()
            fun pt(kind: SurveyKind): GeoPoint? =
                pts.lastOrNull { it.kind == kind }?.let { GeoPoint(it.lat, it.lon) }

            val newCenter = pt(SurveyKind.CENTER) ?: hole.green.center
            val newFront = pt(SurveyKind.FRONT) ?: hole.green.front
            val newBack = pt(SurveyKind.BACK) ?: hole.green.back
            val newTee = pt(SurveyKind.TEE) ?: hole.tee
            val baseHazardCount = hole.hazards.size
            val extraHazards = pts.filter { it.kind == SurveyKind.HAZARD }.mapIndexed { i, p ->
                Hazard(
                    id = "survey_${hole.number}_$i",
                    name = p.label.ifBlank { "Hazard ${baseHazardCount + i + 1}" },
                    type = "hazard",
                    point = GeoPoint(p.lat, p.lon),
                )
            }
            hole.copy(
                tee = newTee,
                green = Green(center = newCenter, front = newFront, back = newBack),
                hazards = hole.hazards + extraHazards,
            )
        }
        return course.copy(holes = holes)
    }
}
