package com.odinsgolf

import com.odinsgolf.data.model.Bag
import com.odinsgolf.geo.Caddie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaddieTest {

    private val bag = Bag.DEFAULT.clubs

    @Test fun takes_enough_club_for_a_between_number() {
        // 152 m: 9i only carries 150, so take the 8-iron (160).
        val p = Caddie.approach(bag, 152.0)!!
        assertEquals("8-iron", p.club.name)
        assertEquals(8, p.marginMeters) // 160 - 152
        assertFalse(p.overClub)
    }

    @Test fun exact_number_picks_that_club() {
        assertEquals("6-iron", Caddie.approach(bag, 190.0)!!.club.name)
    }

    @Test fun beyond_the_bag_is_a_layup_on_the_longest_club() {
        val p = Caddie.approach(bag, 300.0)!!
        assertEquals("Driver", p.club.name)
        assertTrue(p.overClub)
        assertTrue(p.marginMeters < 0)
    }

    @Test fun short_number_takes_the_shortest_club_that_reaches() {
        assertEquals("56°", Caddie.approach(bag, 85.0)!!.club.name)
    }

    @Test fun empty_bag_gives_nothing() {
        assertNull(Caddie.approach(emptyList(), 150.0))
    }

    @Test fun tee_leaves_a_full_scoring_iron() {
        // 350 m hole, leave ~150 → 200 m tee shot = 5-iron, leaves 150.
        val t = Caddie.tee(bag, 350.0, leaveMeters = 150.0)!!
        assertEquals("5-iron", t.club.name)
        assertEquals(150, t.leavesMeters)
    }

    @Test fun tee_is_null_when_the_hole_is_already_short() {
        assertNull(Caddie.tee(bag, 130.0, leaveMeters = 150.0))
    }
}
