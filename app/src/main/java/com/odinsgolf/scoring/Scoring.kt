package com.odinsgolf.scoring

import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.Round
import kotlin.math.roundToInt

/**
 * Stroke-play, net and Stableford scoring.
 *
 * A player has a decimal handicap *index* (e.g. 15.7). Strokes are allocated
 * using the rounded *playing* handicap (16), by stroke index: SI 1 (hardest)
 * gets the first stroke. With playing handicap H: every hole gets floor(H/18)
 * strokes, plus one extra on the (H mod 18) lowest-SI holes — the standard
 * WHS/CONGU allocation used by Icelandic clubs.
 */
object Scoring {

    /**
     * WHS Course Handicap = Index × (Slope / 113) + (Course Rating − Par), rounded.
     * e.g. Setberg off 15.7 with Slope 130, CR 70.8, Par 72 → 17.
     */
    fun courseHandicap(index: Double, slopeRating: Int, courseRating: Double, par: Int): Int =
        (index * slopeRating / 113.0 + (courseRating - par)).roundToInt()

    /** Playing handicap = course handicap × allowance%, rounded (≥ 0). */
    fun playingHandicap(courseHandicap: Int, allowancePercent: Int): Int =
        (courseHandicap * allowancePercent / 100.0).roundToInt().coerceAtLeast(0)

    /**
     * Playing handicap for a round: from the course rating (WHS) when the course has
     * ratings, otherwise the rounded index; then the handicap allowance is applied.
     */
    fun playingHandicap(round: Round): Int {
        val ch = if (round.slopeRating != null && round.courseRating != null && round.coursePar != null) {
            courseHandicap(round.handicapIndex, round.slopeRating, round.courseRating, round.coursePar)
        } else {
            round.handicapIndex.roundToInt()
        }
        return playingHandicap(ch, round.handicapAllowancePercent)
    }

    /** Handicap strokes received on a hole with [strokeIndex] given a playing [handicap]. */
    fun strokesReceived(handicap: Int, strokeIndex: Int?): Int {
        if (strokeIndex == null || handicap <= 0) return 0
        val base = handicap / 18
        val remainder = handicap % 18
        return base + if (strokeIndex in 1..remainder) 1 else 0
    }

    /** Net strokes on a hole = gross - handicap strokes received (playing handicap). */
    fun netStrokes(score: HoleScore, handicap: Int): Int =
        score.strokes - strokesReceived(handicap, score.strokeIndex)

    /**
     * Stableford points for a hole using a playing [handicap].
     * net double bogey or worse = 0, net bogey = 1, net par = 2, net birdie = 3, ...
     */
    fun stablefordPoints(score: HoleScore, handicap: Int): Int {
        if (score.pickedUp || !score.entered) return 0
        val net = netStrokes(score, handicap)
        val diff = score.par - net // positive = better than net par
        return (2 + diff).coerceAtLeast(0)
    }

    /** Total Stableford points across entered holes. */
    fun totalStableford(round: Round): Int {
        val ph = playingHandicap(round)
        return round.enteredHoles.sumOf { stablefordPoints(it, ph) }
    }

    /** Total net strokes across entered holes. */
    fun totalNet(round: Round): Int {
        val ph = playingHandicap(round)
        return round.enteredHoles.sumOf { netStrokes(it, ph) }
    }

    /** Net total relative to par across entered holes. */
    fun netToPar(round: Round): Int {
        val ph = playingHandicap(round)
        return round.enteredHoles.sumOf { netStrokes(it, ph) - it.par }
    }

    /** Human label for a to-par value: "E", "+3", "-1". */
    fun toParLabel(toPar: Int): String = when {
        toPar == 0 -> "E"
        toPar > 0 -> "+$toPar"
        else -> "$toPar"
    }
}
