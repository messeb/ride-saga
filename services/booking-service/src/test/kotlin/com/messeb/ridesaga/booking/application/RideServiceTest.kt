package com.messeb.ridesaga.booking.application

import com.messeb.ridesaga.booking.domain.Ride
import com.messeb.ridesaga.booking.domain.RideRepository
import com.messeb.ridesaga.booking.domain.RideStatus
import com.messeb.ridesaga.common.EventPublisher
import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.RideCancelled
import com.messeb.ridesaga.events.RideConfirmed
import com.messeb.ridesaga.events.RideRequested
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.avro.specific.SpecificRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

class RideServiceTest {

    private val repository = mockk<RideRepository>(relaxed = true)
    private val publisher = mockk<EventPublisher>(relaxed = true)
    private val service = RideService(repository, publisher)

    @Test
    fun `requesting a ride persists it and publishes RideRequested with normalized fare`() {
        val published = slot<SpecificRecord>()
        every { repository.save(any<Ride>()) } returnsArgument 0
        every { publisher.publish(eq(Topics.RIDE_REQUESTED), any(), capture(published), any()) } returns mockk()

        val ride = service.requestRide("rider-1", "A", "B", BigDecimal("19.9"), "EUR")

        verify { repository.save(ride) }
        val event = published.captured as RideRequested
        assertEquals(ride.id, event.rideId)
        assertEquals(BigDecimal("19.90"), event.fareAmount)
        assertEquals(RideStatus.REQUESTED, ride.status)
    }

    @Test
    fun `payment completion confirms the ride and publishes RideConfirmed with causation`() {
        val ride = rideWithDriver()
        every { repository.findById(ride.id) } returns Optional.of(ride)
        val published = slot<SpecificRecord>()
        every { publisher.publish(eq(Topics.RIDE_CONFIRMED), any(), capture(published), eq("payment-event-1")) } returns mockk()

        service.paymentCompleted(ride.id, causationId = "payment-event-1")

        assertEquals(RideStatus.CONFIRMED, ride.status)
        assertEquals("driver-7", (published.captured as RideConfirmed).driverId)
    }

    @Test
    fun `a duplicate payment completion does not publish a second RideConfirmed`() {
        val ride = rideWithDriver().apply { confirm() }
        every { repository.findById(ride.id) } returns Optional.of(ride)

        service.paymentCompleted(ride.id, causationId = "payment-event-1")

        verify(exactly = 0) { publisher.publish(eq(Topics.RIDE_CONFIRMED), any(), any(), any()) }
    }

    @Test
    fun `payment failure cancels the ride and publishes RideCancelled`() {
        val ride = rideWithDriver()
        every { repository.findById(ride.id) } returns Optional.of(ride)
        val published = slot<SpecificRecord>()
        every { publisher.publish(eq(Topics.RIDE_CANCELLED), any(), capture(published), any()) } returns mockk()

        service.paymentFailed(ride.id, "card declined", causationId = "payment-event-2")

        assertEquals(RideStatus.CANCELLED, ride.status)
        assertEquals("PAYMENT_FAILED", (published.captured as RideCancelled).reason.name)
    }

    @Test
    fun `events for unknown rides are ignored`() {
        every { repository.findById("nope") } returns Optional.empty()

        service.paymentCompleted("nope", causationId = "x")
        service.paymentFailed("nope", "reason", causationId = "x")
        service.driverAssignmentFailed("nope", "reason", causationId = "x")

        verify(exactly = 0) { publisher.publish(any(), any(), any(), any()) }
    }

    private fun rideWithDriver() = Ride(
        id = "ride-1",
        riderId = "rider-1",
        pickupLocation = "A",
        dropoffLocation = "B",
        fareAmount = BigDecimal("10.00"),
        currency = "EUR",
        requestedAt = Instant.now(),
    ).apply { assignDriver("driver-7") }
}
