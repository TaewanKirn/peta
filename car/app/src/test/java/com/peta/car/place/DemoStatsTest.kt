package com.peta.car.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoStatsTest {
    private val place = Place("tour-1", "곳", GeoPoint(37.0, 127.0), "", PlaceType.DOG_REST)

    @Test fun sameIdAlwaysGetsSameValues() {
        assertEquals(DemoStats.fill(place, enabled = true), DemoStats.fill(place.copy(name = "다른 이름"), enabled = true).copy(name = "곳"))
    }

    @Test fun valuesAreInRangeAndOffSwitchLeavesPlaceUntouched() {
        val filled = DemoStats.fill(place, enabled = true)
        assertTrue(filled.petaVisitors in 3..182)
        assertTrue(filled.reviewOneLine.isNotBlank())
        assertEquals(place, DemoStats.fill(place, enabled = false))
    }
}
