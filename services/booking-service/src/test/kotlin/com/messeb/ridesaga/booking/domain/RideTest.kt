package com.messeb.ridesaga.booking.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class RideTest {

    private fun ride() = Ride(
        id = "ride-1",
        riderId = "rider-1",
        pickupLocation = "Alexanderplatz",
        dropoffLocation = "Kreuzberg",
        fareAmount = BigDecimal("23.50"),
        currency = "EUR",
        requestedAt = Instant.now(),
    )

    @Test
    fun `walks the happy path REQUESTED to CONFIRMED`() {
        val ride = ride()
        assertEquals(RideStatus.REQUESTED, ride.status)

        assertTrue(ride.assignDriver("driver-7"))
        assertEquals(RideStatus.DRIVER_ASSIGNED, ride.status)
        assertEquals("driver-7", ride.driverId)

        assertTrue(ride.recordPayment())
        assertTrue(ride.confirm())
        assertEquals(RideStatus.CONFIRMED, ride.status)
    }

    @Test
    fun `tolerates the payment arriving before the driver (cross-topic ordering)`() {
        val ride = ride()

        assertTrue(ride.recordPayment())
        assertFalse(ride.confirm(), "cannot confirm without a driver")

        assertTrue(ride.assignDriver("driver-7"))
        assertTrue(ride.confirm())
        assertEquals(RideStatus.CONFIRMED, ride.status)
    }

    @Test
    fun `can be cancelled before and after driver assignment but not after confirmation`() {
        val beforeAssignment = ride()
        assertTrue(beforeAssignment.cancel("no drivers"))
        assertEquals(RideStatus.CANCELLED, beforeAssignment.status)

        val afterAssignment = ride().apply { assignDriver("driver-1") }
        assertTrue(afterAssignment.cancel("payment declined"))
        assertEquals(RideStatus.CANCELLED, afterAssignment.status)

        val confirmed = ride().apply {
            assignDriver("driver-1")
            recordPayment()
            confirm()
        }
        assertFalse(confirmed.cancel("too late"))
        assertEquals(RideStatus.CONFIRMED, confirmed.status)
    }

    @Test
    fun `ignores duplicate transitions instead of failing`() {
        val ride = ride()
        assertTrue(ride.assignDriver("driver-7"))

        assertFalse(ride.assignDriver("driver-8"))
        assertEquals("driver-7", ride.driverId)

        assertTrue(ride.recordPayment())
        assertTrue(ride.confirm())
        assertFalse(ride.confirm())
        assertEquals(RideStatus.CONFIRMED, ride.status)
    }

    @Test
    fun `cannot confirm without an assigned driver or without payment`() {
        val unpaid = ride().apply { assignDriver("driver-7") }
        assertFalse(unpaid.confirm())
        assertEquals(RideStatus.DRIVER_ASSIGNED, unpaid.status)

        val driverless = ride().apply { recordPayment() }
        assertFalse(driverless.confirm())
        assertEquals(RideStatus.REQUESTED, driverless.status)
        assertNull(driverless.driverId)
    }
}
