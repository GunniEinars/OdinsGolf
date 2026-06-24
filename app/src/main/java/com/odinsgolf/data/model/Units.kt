package com.odinsgolf.data.model

/** Display units. Internally distances are always meters. */
enum class Units(val suffix: String) {
    METERS("m"),
    YARDS("yd");

    /** Convert a distance in meters to this unit. */
    fun fromMeters(meters: Double): Double = when (this) {
        METERS -> meters
        YARDS -> meters * 1.0936132983
    }

    /** Convert a value in this unit back to meters. */
    fun toMeters(value: Double): Double = when (this) {
        METERS -> value
        YARDS -> value / 1.0936132983
    }

    companion object {
        fun parse(value: String?): Units =
            if (value?.lowercase()?.startsWith("yard") == true || value?.lowercase() == "yd") YARDS else METERS
    }
}
