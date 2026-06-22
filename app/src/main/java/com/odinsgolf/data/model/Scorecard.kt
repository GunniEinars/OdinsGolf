package com.odinsgolf.data.model

import kotlinx.serialization.Serializable

/** Optional fairway result for par 4/5 holes. */
@Serializable
enum class FairwayResult { NONE, HIT, MISS }

/** Per-hole score entry. Strokes of 0 means "not entered yet". */
@Serializable
data class HoleScore(
    val holeNumber: Int,
    val par: Int,
    val strokeIndex: Int? = null,
    val strokes: Int = 0,
    val putts: Int = 0,
    val fairway: FairwayResult = FairwayResult.NONE,
    val gir: Boolean = false,
) {
    val entered: Boolean get() = strokes > 0
    val toPar: Int get() = if (entered) strokes - par else 0
}

/**
 * A persisted round. [playerHandicap] is the course/playing handicap used for
 * net and Stableford. Stored locally only — never synced anywhere.
 */
@Serializable
data class Round(
    val courseId: String,
    val courseName: String,
    val startedEpochMillis: Long,
    val playerHandicap: Int = 0,
    val holes: List<HoleScore>,
) {
    val enteredHoles: List<HoleScore> get() = holes.filter { it.entered }

    val totalStrokes: Int get() = enteredHoles.sumOf { it.strokes }

    /** Total to par over the holes actually entered. */
    val toPar: Int get() = enteredHoles.sumOf { it.toPar }

    fun strokesForRange(range: IntRange): Int =
        enteredHoles.filter { it.holeNumber in range }.sumOf { it.strokes }

    fun parForRange(range: IntRange): Int =
        enteredHoles.filter { it.holeNumber in range }.sumOf { it.par }
}
