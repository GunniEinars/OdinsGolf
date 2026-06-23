package com.odinsgolf

import com.odinsgolf.data.model.RoundMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundModeTest {

    @Test
    fun ranges_for_18_hole_course() {
        assertEquals(1..18, RoundMode.FULL_18.range(18))
        assertEquals(1..9, RoundMode.FRONT_9.range(18))
        assertEquals(10..18, RoundMode.BACK_9.range(18))
    }

    @Test
    fun start_hole_matches_mode() {
        assertEquals(1, RoundMode.FULL_18.startHole)
        assertEquals(1, RoundMode.FRONT_9.startHole)
        assertEquals(10, RoundMode.BACK_9.startHole)
    }

    @Test
    fun unknown_name_defaults_to_full() {
        assertEquals(RoundMode.FULL_18, RoundMode.fromName(null))
        assertEquals(RoundMode.BACK_9, RoundMode.fromName("BACK_9"))
    }
}
