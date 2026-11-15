package com.peta.car.stay

import com.peta.car.vehicle.Gear
import com.peta.car.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StayTrackerTest {

    private var now = 0L
    private val minute = 60_000L
    private val tracker = StayTracker(thresholdMillis = 30 * minute, repeatMillis = 15 * minute, clock = { now })

    private fun park(doorOpen: Boolean = false) = VehicleState(gear = Gear.PARK, driverDoorOpen = doorOpen)
    private val drive = VehicleState(gear = Gear.DRIVE)

    @Test
    fun unknownGearIsIgnored() {
        assertTrue(tracker.onVehicleState(VehicleState()).isEmpty())
        assertNull(tracker.parkedAt)
    }

    @Test
    fun parkingStartsTracking() {
        assertEquals(listOf(StayEvent.Parked(0)), tracker.onVehicleState(park()))
    }

    @Test
    fun noAlertBeforeThreshold() {
        tracker.onVehicleState(park())
        now = 29 * minute
        assertNull(tracker.tick())
    }

    @Test
    fun alertsAtThresholdThenRepeats() {
        tracker.onVehicleState(park())
        now = 30 * minute
        assertEquals(StayEvent.LongStay(30), tracker.tick())
        now = 40 * minute
        assertNull(tracker.tick())
        now = 45 * minute
        assertEquals(StayEvent.LongStay(45), tracker.tick())
    }

    @Test
    fun doorOpenWhileParkedMeansLeftVehicleOnce() {
        tracker.onVehicleState(park())
        now = 5 * minute
        assertEquals(listOf(StayEvent.LeftVehicle(0)), tracker.onVehicleState(park(doorOpen = true)))
        tracker.onVehicleState(park(doorOpen = false))
        assertTrue(tracker.onVehicleState(park(doorOpen = true)).isEmpty())
    }

    @Test
    fun doorOpenWhileDrivingIsIgnored() {
        assertTrue(tracker.onVehicleState(drive.copy(driverDoorOpen = true)).isEmpty())
    }

    @Test
    fun departingReportsStayAndResets() {
        tracker.onVehicleState(park())
        now = 12 * minute
        assertEquals(listOf(StayEvent.Departed(12)), tracker.onVehicleState(drive))
        assertNull(tracker.parkedAt)
        assertTrue(tracker.onVehicleState(drive).isEmpty())
    }
}
