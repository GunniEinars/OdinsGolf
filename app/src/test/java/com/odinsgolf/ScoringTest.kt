package com.odinsgolf

import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.Round
import com.odinsgolf.scoring.Scoring
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    @Test
    fun strokes_received_allocates_by_stroke_index() {
        // Handicap 10: hardest 10 holes (SI 1..10) get a stroke.
        assertEquals(1, Scoring.strokesReceived(10, 1))
        assertEquals(1, Scoring.strokesReceived(10, 10))
        assertEquals(0, Scoring.strokesReceived(10, 11))
        assertEquals(0, Scoring.strokesReceived(0, 1))
    }

    @Test
    fun strokes_received_wraps_past_18() {
        // Handicap 20: every hole gets 1, plus SI 1 and 2 get a second.
        assertEquals(2, Scoring.strokesReceived(20, 1))
        assertEquals(2, Scoring.strokesReceived(20, 2))
        assertEquals(1, Scoring.strokesReceived(20, 3))
    }

    @Test
    fun stableford_par_is_two_points_off_scratch() {
        val parHole = HoleScore(holeNumber = 1, par = 4, strokeIndex = 1, strokes = 4)
        assertEquals(2, Scoring.stablefordPoints(parHole, handicap = 0))
    }

    @Test
    fun stableford_uses_net_for_handicap_player() {
        // Par 4, SI 1, handicap 18 => 1 stroke; gross 5 => net 4 => net par => 2 points.
        val hole = HoleScore(holeNumber = 1, par = 4, strokeIndex = 1, strokes = 5)
        assertEquals(2, Scoring.stablefordPoints(hole, handicap = 18))
    }

    @Test
    fun stableford_never_negative() {
        val blowup = HoleScore(holeNumber = 1, par = 3, strokeIndex = 18, strokes = 9)
        assertEquals(0, Scoring.stablefordPoints(blowup, handicap = 0))
    }

    @Test
    fun course_handicap_uses_slope_and_rating() {
        // Setberg tee 56: Slope 130, CR 70.8, Par 72. Off 15.7 -> 17.
        assertEquals(17, Scoring.courseHandicap(15.7, slopeRating = 130, courseRating = 70.8, par = 72))
        // A scratch index still shifts by (CR - Par).
        assertEquals(-1, Scoring.courseHandicap(0.0, 130, 70.8, 72))
    }

    @Test
    fun playing_handicap_applies_allowance() {
        // Course handicap 17: 95% -> 16, 100% -> 17.
        assertEquals(16, Scoring.playingHandicap(17, allowancePercent = 95))
        assertEquals(17, Scoring.playingHandicap(17, allowancePercent = 100))
    }

    @Test
    fun round_playing_handicap_from_ratings_and_allowance() {
        val round = Round(
            courseId = "setbergsvollur", courseName = "Setberg", startedEpochMillis = 0,
            handicapIndex = 15.7, courseRating = 70.8, slopeRating = 130, coursePar = 72,
            handicapAllowancePercent = 95, holes = emptyList(),
        )
        assertEquals(16, Scoring.playingHandicap(round))
        assertEquals(17, Scoring.playingHandicap(round.copy(handicapAllowancePercent = 100)))
        // No ratings -> falls back to the rounded index.
        val noRatings = round.copy(
            courseRating = null, slopeRating = null, coursePar = null, handicapAllowancePercent = 100,
        )
        assertEquals(16, Scoring.playingHandicap(noRatings)) // round(15.7) = 16
    }

    @Test
    fun round_totals() {
        val round = Round(
            courseId = "x",
            courseName = "X",
            startedEpochMillis = 0,
            handicapIndex = 0.0,
            holes = listOf(
                HoleScore(1, par = 4, strokeIndex = 1, strokes = 5), // +1
                HoleScore(2, par = 3, strokeIndex = 2, strokes = 3), // E
                HoleScore(3, par = 5, strokeIndex = 3, strokes = 0), // not entered
            ),
        )
        assertEquals(8, round.totalStrokes)
        assertEquals(1, round.toPar)
        assertEquals("+1", Scoring.toParLabel(round.toPar))
        // hole1 bogey (5 on par 4) = 1 pt, hole2 par = 2 pts, hole3 not entered = 0.
        assertEquals(3, Scoring.totalStableford(round))
    }
}
