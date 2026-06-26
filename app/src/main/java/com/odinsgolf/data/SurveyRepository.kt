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
    fun overlay(course: Course, data: SurveyData): Course = overlayOnto(course, data)

    companion object {
        /**
         * Pure overlay (no Context, so it is unit-testable). A captured CENTER is the
         * shared physical green: Setberg plays 9 greens as 18 holes, so hole N and N+9
         * carry the same greenId — a centre capture on one applies to every hole with
         * that greenId (newest capture wins). FRONT/BACK/TEE are per hole; hazards append.
         */
        fun overlayOnto(course: Course, data: SurveyData): Course {
            if (data.points.isEmpty()) return course
            val byHole = data.points.groupBy { it.holeNumber }

            // Latest captured CENTER per shared green, keyed by greenId.
            val holeToGreen: Map<Int, String?> = course.holes.associate { it.number to it.greenId }
            val centerByGreenId: Map<String, GeoPoint> = data.points
                .asSequence()
                .filter { it.kind == SurveyKind.CENTER }
                .sortedBy { it.epochMillis }
                .mapNotNull { p -> holeToGreen[p.holeNumber]?.let { gid -> gid to GeoPoint(p.lat, p.lon) } }
                .toMap() // on duplicate greenId the last (newest) capture wins

            val holes = course.holes.map { hole ->
                val pts = byHole[hole.number].orEmpty()
                fun pt(kind: SurveyKind): GeoPoint? =
                    pts.lastOrNull { it.kind == kind }?.let { GeoPoint(it.lat, it.lon) }

                // This hole's own centre wins; else a sibling on the same shared green; else base.
                val newCenter = pt(SurveyKind.CENTER)
                    ?: hole.greenId?.let { centerByGreenId[it] }
                    ?: hole.green.center
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
}
