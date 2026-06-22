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

    companion object {
        fun fromName(name: String?): GpsUpdateMode =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
