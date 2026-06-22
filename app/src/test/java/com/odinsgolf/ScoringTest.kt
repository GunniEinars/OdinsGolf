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
    fun round_totals() {
        val round = Round(
            courseId = "x",
            courseName = "X",
            startedEpochMillis = 0,
            playerHandicap = 0,
            holes = listOf(
                HoleScore(1, par = 4, strokeIndex = 1, strokes = 5), // +1
                HoleScore(2, par = 3, strokeIndex = 2, strokes = 3), // E
                HoleScore(3, par = 5, strokeIndex = 3, strokes = 0), // not entered
            ),
        )
        assertEquals(8, round.totalStrokes)
        assertEquals(1, round.toPar)
        assertEquals("+1", Scoring.toParLabel(round.toPar))
        assertEquals(4, Scoring.totalStableford(round)) // 2 + 2 + 0
    }
}
