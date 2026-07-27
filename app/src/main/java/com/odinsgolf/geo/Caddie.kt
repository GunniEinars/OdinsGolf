package com.odinsgolf.geo

import com.odinsgolf.data.model.ClubDistance

/**
 * Deterministic, offline club advice from the player's bag. No AI, no network: club selection is
 * just the target distance against the clubs you can count on. The target handed in should already
 * be **plays-like and wind-adjusted** — this only picks the club for it.
 */
object Caddie {

    /**
     * Below this (m) you're chipping/putting, not clubbing to a number — the UI suppresses club
     * advice so it never suggests a wedge when you're 6 m off the green.
     */
    const val MIN_APPROACH_METERS = 35.0

    /** How the pick relates to the target, so the UI can nuance it ("smooth", "stretch", "layup"). */
    data class Pick(
        val club: ClubDistance,
        /** club carry − target (m): >0 = you have this much extra club (ease off); <0 = a stretch. */
        val marginMeters: Int,
        /** True when the target is beyond your longest club — it's a layup / two-shotter. */
        val overClub: Boolean,
    )

    /**
     * "Take enough club": the smallest club that carries [targetMeters]. Falls back to the longest
     * club (flagged [Pick.overClub]) when nothing reaches — that's a layup, not a swing-harder.
     * Null only when the bag is empty or the target is non-positive.
     */
    fun approach(bag: List<ClubDistance>, targetMeters: Double): Pick? {
        if (bag.isEmpty() || targetMeters <= 0.0) return null
        val sorted = bag.sortedBy { it.carryMeters }
        val target = Math.round(targetMeters).toInt()
        val enough = sorted.firstOrNull { it.carryMeters >= target }
        return if (enough != null) {
            Pick(enough, enough.carryMeters - target, overClub = false)
        } else {
            val longest = sorted.last()
            Pick(longest, longest.carryMeters - target, overClub = true)
        }
    }

    /**
     * A tee/position suggestion for a two-shot (or three-shot) hole of [holeMeters]: the club that
     * leaves a comfortable **full approach** rather than the longest one. We aim to leave roughly a
     * scoring-iron distance ([leaveMeters], default ~150 m) and pick the tee club that gets there,
     * capped so it never suggests more than needed. Returns the tee pick and what it leaves in.
     */
    fun tee(bag: List<ClubDistance>, holeMeters: Double, leaveMeters: Double = 150.0): TeePlan? {
        if (bag.isEmpty() || holeMeters <= 0.0) return null
        val wanted = holeMeters - leaveMeters
        // If the hole is short enough that even a stock scoring iron is plenty, just leave it long.
        if (wanted <= 0.0) return null
        val pick = approach(bag, wanted) ?: return null
        val leaves = Math.round(holeMeters - pick.club.carryMeters).toInt()
        return TeePlan(pick.club, leaves.coerceAtLeast(0))
    }

    data class TeePlan(val club: ClubDistance, val leavesMeters: Int)

    /**
     * The club whose carry is closest to [meters] — for the shot tracker's "you hit that ≈ 7-iron".
     * Null on an empty bag or a non-positive distance. Only matches within [tolMeters] so a chip or a
     * monster drive past the bag doesn't get labelled with the nearest club.
     */
    fun nearestClub(bag: List<ClubDistance>, meters: Double, tolMeters: Int = 12): ClubDistance? {
        if (bag.isEmpty() || meters <= 0.0) return null
        val target = Math.round(meters).toInt()
        return bag.minByOrNull { kotlin.math.abs(it.carryMeters - target) }
            ?.takeIf { kotlin.math.abs(it.carryMeters - target) <= tolMeters }
    }
}
