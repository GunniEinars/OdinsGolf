package com.odinsgolf.data

import android.content.Context
import com.odinsgolf.data.model.Course
import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.Round
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the active round to a local JSON file in the app's files dir.
 * No cloud, no sync. "Export" simply writes a timestamped copy you can pull
 * off the watch with `adb pull` (see SETUP_WINDOWS_NOADMIN.md).
 */
class ScorecardRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val activeFile: File get() = File(context.filesDir, ACTIVE_ROUND_FILE)
    private val exportsDir: File get() = File(context.filesDir, "rounds").apply { mkdirs() }

    fun loadActiveRound(): Round? = runCatching {
        if (!activeFile.exists()) return null
        json.decodeFromString<Round>(activeFile.readText())
    }.getOrNull()

    fun saveActiveRound(round: Round) {
        runCatching { activeFile.writeText(json.encodeToString(round)) }
    }

    fun clearActiveRound() {
        runCatching { if (activeFile.exists()) activeFile.delete() }
    }

    /** Write a timestamped copy to files/rounds/. Returns the file path or null. */
    fun exportRound(round: Round): String? = runCatching {
        val name = "round_${round.courseId}_${round.startedEpochMillis}.json"
        val out = File(exportsDir, name)
        out.writeText(json.encodeToString(round))
        out.absolutePath
    }.getOrNull()

    /** Build a fresh empty round seeded with par/stroke index from the course. */
    fun newRound(course: Course, handicapIndex: Double): Round = Round(
        courseId = course.id,
        courseName = course.name,
        startedEpochMillis = System.currentTimeMillis(),
        handicapIndex = handicapIndex,
        holes = course.holes.map {
            HoleScore(holeNumber = it.number, par = it.par, strokeIndex = it.strokeIndex)
        },
    )

    companion object {
        const val ACTIVE_ROUND_FILE = "active_round.json"
    }
}
