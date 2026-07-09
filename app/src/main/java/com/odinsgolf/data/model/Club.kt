package com.odinsgolf.data.model

import kotlinx.serialization.Serializable

/**
 * One club and the **conservative planning carry** you can count on (metres). A single number,
 * not a range: the caddie "takes enough club", so the number it plans with should be the one you
 * reliably reach, not your best.
 */
@Serializable
data class ClubDistance(val name: String, val carryMeters: Int)

/** The player's bag — clubs with planning carries, longest first is not assumed (engine sorts). */
@Serializable
data class Bag(val clubs: List<ClubDistance> = emptyList()) {
    val isEmpty: Boolean get() = clubs.isEmpty()

    companion object {
        /** Seed bag from the player's stated numbers (low end of ranges = the count-on-it carry). */
        val DEFAULT = Bag(
            listOf(
                ClubDistance("Driver", 240),
                ClubDistance("3-wood", 220),
                ClubDistance("5-iron", 200),
                ClubDistance("6-iron", 190),
                ClubDistance("7-iron", 175),
                ClubDistance("8-iron", 160),
                ClubDistance("9-iron", 150),
                ClubDistance("PW", 135),
                ClubDistance("GW", 115),
                ClubDistance("56°", 90),
            ),
        )
    }
}
