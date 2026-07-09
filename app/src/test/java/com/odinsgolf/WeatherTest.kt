package com.odinsgolf

import com.odinsgolf.data.model.Weather
import com.odinsgolf.data.model.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    private fun w(speed: Double, fromDeg: Double, precip: Double = 0.0) =
        Weather(windSpeedMps = speed, windFromDeg = fromDeg, precipMm = precip, tempC = 10.0)

    @Test fun headwind_plays_longer() {
        // Hitting due north, wind from the north (in your face) → club up.
        val d = Wind.playingDelta(w(10.0, 0.0), shotBearingDeg = 0.0)
        assertEquals(15.0, d, 0.001) // 10 m/s * 1.5
    }

    @Test fun tailwind_plays_shorter() {
        // Wind from the south, hitting north → at your back.
        val d = Wind.playingDelta(w(10.0, 180.0), shotBearingDeg = 0.0)
        assertEquals(-15.0, d, 0.001)
    }

    @Test fun crosswind_barely_changes_distance() {
        // Wind from the east, hitting north → pure crosswind, ~no distance change.
        val d = Wind.playingDelta(w(10.0, 90.0), shotBearingDeg = 0.0)
        assertEquals(0.0, d, 0.001)
    }

    @Test fun rain_adds_a_little_length() {
        val d = Wind.playingDelta(w(0.0, 0.0, precip = 2.0), shotBearingDeg = 0.0)
        assertEquals(6.0, d, 0.001)
        assertTrue(Wind.isRaining(w(0.0, 0.0, precip = 2.0)))
    }
}
