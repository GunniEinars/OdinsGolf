package com.odinsgolf.scoring

import com.odinsgolf.data.model.HoleScore
import com.odinsgolf.data.model.Round

/**
 * Stroke-play, net and Stableford scoring.
 *
 * Handicap strokes are allocated by stroke index (SI 1 = hardest hole gets the
 * first stroke). With an 18-hole handicap H: every hole gets floor(H/18)
 * strokes, plus one extra on the (H mod 18) lowest-SI holes. This is the
 * standard WHS/CONGU allocation used by Icelandic clubs.
 */
object Scoring {

    /** Handicap strokes received on a hole with [strokeIndex] given [handicap]. */
    fun strokesReceived(handicap: Int, strokeIndex: Int?): Int {
        if (strokeIndex == null || handicap <= 0) return 0
        val base = handicap / 18
        val remainder = handicap % 18
        return base + if (strokeIndex in 1..remainder) 1 else 0
    }

    /** Net strokes on a hole = gross - handicap strokes received. */
    fun netStrokes(score: HoleScore, handicap: Int): Int =
        score.strokes - strokesReceived(handicap, score.strokeIndex)

    /**
     * Stableford points for a hole.
     * net double bogey or worse = 0, net bogey = 1, net par = 2, net birdie = 3, ...
     */
    fun stablefordPoints(score: HoleScore, handicap: Int): Int {
        if (!score.entered) return 0
        val net = netStrokes(score, handicap)
        val diff = score.par - net // positive = better than net par
        return (2 + diff).coerceAtLeast(0)
    }

    /** Total Stableford points across entered holes. */
    fun totalStableford(round: Round): Int =
        round.enteredHoles.sumOf { stablefordPoints(it, round.playerHandicap) }

    /** Total net strokes across entered holes. */
    fun totalNet(round: Round): Int =
        round.enteredHoles.sumOf { netStrokes(it, round.playerHandicap) }

    /** Net total relative to par across entered holes. */
    fun netToPar(round: Round): Int =
        round.enteredHoles.sumOf { netStrokes(it, round.playerHandicap) - it.par }

    /** Human label for a to-par value: "E", "+3", "-1". */
    fun toParLabel(toPar: Int): String = when {
        toPar == 0 -> "E"
        toPar > 0 -> "+$toPar"
        else -> "$toPar"
    }
}
