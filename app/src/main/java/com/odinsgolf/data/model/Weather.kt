package com.odinsgolf.data.model

import kotlinx.serialization.Serializable
import kotlin.math.cos

/**
 * Current conditions at the course (from Open-Meteo — free, no key). Cached so the round keeps
 * working if the signal drops. [windFromDeg] is the direction the wind blows *from* (0 = N).
 */
@Serializable
data class Weather(
    val windSpeedMps: Double,
    val windFromDeg: Double,
    val precipMm: Double,
    val tempC: Double,
    val fetchedEpochMillis: Long = 0L,
)

/**
 * Deterministic "plays like" adjustment from wind + rain — no AI. Golf rule-of-thumb: a headwind
 * makes the shot play longer (club up), a tailwind shorter; rain flies/rolls shorter so it also
 * plays a touch longer. Applied on top of the elevation plays-like.
 */
object Wind {
    /** Playing metres added per 1 m/s of *headwind* on a mid-iron (conservative). */
    private const val M_PER_MPS = 1.5
    private const val RAIN_MM_THRESHOLD = 0.1
    private const val RAIN_PLAYS_LONGER_M = 6.0

    /** Headwind component (m/s): + into your face, − at your back, for a shot on [shotBearingDeg]. */
    fun headwindMps(weather: Weather, shotBearingDeg: Double): Double =
        weather.windSpeedMps * cos(Math.toRadians(weather.windFromDeg - shotBearingDeg))

    /** Extra playing metres from wind + rain (+ = club up) for a shot on [shotBearingDeg] (0 = N). */
    fun playingDelta(weather: Weather, shotBearingDeg: Double): Double {
        val windDelta = headwindMps(weather, shotBearingDeg) * M_PER_MPS
        val rainDelta = if (weather.precipMm > RAIN_MM_THRESHOLD) RAIN_PLAYS_LONGER_M else 0.0
        return windDelta + rainDelta
    }

    fun isRaining(weather: Weather): Boolean = weather.precipMm > RAIN_MM_THRESHOLD
}
