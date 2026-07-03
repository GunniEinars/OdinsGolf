package com.odinsgolf.geo

import com.odinsgolf.data.model.GeoPoint
import com.odinsgolf.data.model.Hole
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Suggests the hole you appear to be standing at, from GPS, when it differs from the
 * one currently selected. This is a **hint only** — the player taps to accept — so it
 * never overrides manual hole control.
 *
 * It exists for Setberg's layout: 9 physical greens played as 18 holes, packed into a
 * tight, interleaved plot. There it is easy to leave the watch on the wrong hole and
 * read a real, correct-looking yardage to the *wrong* green. Comparing your position to
 * each hole's play corridor catches that; the same mistake is invisible on a course with
 * 18 spread-out holes, so the hint simply never fires there.
 */
object HoleHint {

    /** Only hint when another hole's corridor is at least this much closer than the selected one. */
    private const val MARGIN_M = 40.0

    /**
     * @return the hole number you seem to be at (to offer as a one-tap switch), or null
     * when the current selection already fits, no other hole is clearly closer, or there
     * is no fix.
     */
    fun suggest(holes: List<Hole>, from: GeoPoint?, currentHole: Int, range: IntRange): Int? {
        if (from == null) return null
        val inRange = holes.filter { it.number in range }
        if (inRange.isEmpty()) return null

        var best: Hole? = null
        var bestD = Double.MAX_VALUE
        var currentD = Double.MAX_VALUE
        for (h in inRange) {
            val d = corridorDistance(h, from)
            if (h.number == currentHole) currentD = d
            if (d < bestD) { bestD = d; best = h }
        }
        var pick = best ?: return null

        // Shared green (hole N and N+9): prefer the sibling in the same nine as the
        // current hole, so at the 3rd/12th green we suggest 3 while you play the front.
        val sibling = inRange.firstOrNull {
            it.greenId != null && it.greenId == pick.greenId && it.number != pick.number
        }
        if (sibling != null &&
            nineOf(sibling.number) == nineOf(currentHole) &&
            nineOf(pick.number) != nineOf(currentHole)
        ) pick = sibling

        if (pick.number == currentHole) return null
        // Don't nag to switch between two holes that share the same physical green.
        val current = holes.firstOrNull { it.number == currentHole }
        if (current?.greenId != null && current.greenId == pick.greenId) return null
        if (currentD - bestD < MARGIN_M) return null
        return pick.number
    }

    private fun nineOf(n: Int) = (n - 1) / 9

    /** Distance from [from] to a hole's play corridor: its centerline, else tee→green. */
    private fun corridorDistance(hole: Hole, from: GeoPoint): Double {
        val tee = hole.tee
        val center = hole.green.center
        val line: List<GeoPoint> = when {
            hole.path.size >= 2 -> hole.path
            tee != null && center != null -> listOf(tee, center)
            else -> return Double.MAX_VALUE
        }
        var min = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val d = pointToSegment(from, line[i], line[i + 1])
            if (d < min) min = d
        }
        return min
    }

    /** Shortest distance (m) from [p] to segment [a]→[b], via a local planar frame. */
    private fun pointToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val mPerLat = 111_320.0
        val mPerLon = 111_320.0 * cos(Math.toRadians(a.lat))
        val px = (p.lon - a.lon) * mPerLon
        val py = (p.lat - a.lat) * mPerLat
        val bx = (b.lon - a.lon) * mPerLon
        val by = (b.lat - a.lat) * mPerLat
        val len2 = bx * bx + by * by
        val t = if (len2 > 0) ((px * bx + py * by) / len2).coerceIn(0.0, 1.0) else 0.0
        return hypot(px - t * bx, py - t * by)
    }
}
