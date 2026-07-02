package com.odinsgolf.data.model

/**
 * GPS update cadence. The interval (not the priority) is the main battery lever:
 * golf needs accurate fixes, so we always request high accuracy but space the
 * updates out. See BATTERY_STRATEGY.md.
 */
enum class GpsUpdateMode(
    val label: String,
    val intervalMillis: Long,
    val minUpdateMillis: Long,
    val warnsBattery: Boolean,
) {
    BATTERY_SAVER("Battery saver", 25_000L, 15_000L, false),
    NORMAL("Normal", 12_000L, 6_000L, false),
    PRECISE("Precise", 5_000L, 3_000L, true);

    /**
     * A fix older than this counts as **stale**: the update interval plus slack. A live fix
     * arriving on schedule never false-dims (the threshold is above the interval), but a fix
     * that aged — e.g. while you walked to the ball with the wrist down — flags sooner than a
     * flat 30 s (Normal → 20 s), so the number never masquerades as live after you've moved.
     */
    val staleAfterMillis: Long get() = intervalMillis + 8_000L

    companion object {
        fun fromName(name: String?): GpsUpdateMode =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
