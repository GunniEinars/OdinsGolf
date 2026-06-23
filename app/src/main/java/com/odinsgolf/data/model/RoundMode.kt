package com.odinsgolf.data.model

/** Which holes a round covers. Setberg is 9 greens played as 18 from different tees. */
enum class RoundMode(val label: String) {
    FULL_18("18 holes"),
    FRONT_9("Front 9"),
    BACK_9("Back 9");

    /** Inclusive hole-number range for this mode given the course's hole count. */
    fun range(totalHoles: Int): IntRange = when (this) {
        FULL_18 -> 1..totalHoles
        FRONT_9 -> 1..minOf(9, totalHoles)
        BACK_9 -> (minOf(10, totalHoles))..totalHoles
    }

    /** Hole to start on when this mode is selected. */
    val startHole: Int get() = if (this == BACK_9) 10 else 1

    companion object {
        fun fromName(name: String?): RoundMode = entries.firstOrNull { it.name == name } ?: FULL_18
    }
}
