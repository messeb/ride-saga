package com.messeb.ridesaga.booking.application

import com.messeb.ridesaga.booking.domain.Ride
import com.messeb.ridesaga.booking.domain.RideRepository
import com.messeb.ridesaga.common.EventPublisher
import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.CancellationReason
import com.messeb.ridesaga.events.RideCancelled
import com.messeb.ridesaga.events.RideConfirmed
import com.messeb.ridesaga.events.RideRequested
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Saga initiator and terminator. State changes are committed before the corresponding
 * event is published (no transactional outbox — see ADR-005 for the accepted risk).
 */
@Service
class RideService(private val rides: RideRepository, private val events: EventPublisher) {

    @Transactional
    fun requestRide(riderId: String, pickup: String, dropoff: String, fare: BigDecimal, currency: String): Ride {
        val ride = Ride(
            id = UUID.randomUUID().toString(),
            riderId = riderId,
            pickupLocation = pickup,
            dropoffLocation = dropoff,
            fareAmount = fare.setScale(2, RoundingMode.HALF_UP),
            currency = currency,
            requestedAt = Instant.now(),
        )
        rides.save(ride)

        val event = RideRequested.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOccurredAt(ride.requestedAt)
            .setRideId(ride.id)
            .setRiderId(ride.riderId)
            .setPickupLocation(ride.pickupLocation)
            .setDropoffLocation(ride.dropoffLocation)
            .setFareAmount(ride.fareAmount)
            .setCurrency(ride.currency)
            .build()
        events.publish(Topics.RIDE_REQUESTED, ride.id, event)
        return ride
    }

    fun findRide(rideId: String): Ride? = rides.findById(rideId).orElse(null)

    @Transactional
    fun driverAssigned(rideId: String, driverId: String, causationId: String) {
        val ride = rides.findById(rideId).orElse(null) ?: return
        ride.assignDriver(driverId)
        // PaymentCompleted may have arrived first (cross-topic ordering is not guaranteed)
        confirmIfComplete(ride, causationId)
    }

    @Transactional
    fun paymentCompleted(rideId: String, causationId: String) {
        val ride = rides.findById(rideId).orElse(null) ?: return
        if (!ride.recordPayment()) return
        confirmIfComplete(ride, causationId)
    }

    private fun confirmIfComplete(ride: Ride, causationId: String) {
        if (!ride.confirm()) return

        val event = RideConfirmed.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOccurredAt(Instant.now())
            .setRideId(ride.id)
            .setDriverId(requireNotNull(ride.driverId) { "confirmed ride must have a driver" })
            .setConfirmedAt(Instant.now())
            .build()
        events.publish(Topics.RIDE_CONFIRMED, ride.id, event, causationId)
    }

    @Transactional
    fun paymentFailed(rideId: String, reason: String, causationId: String) {
        cancelRide(rideId, CancellationReason.PAYMENT_FAILED, reason, causationId)
    }

    @Transactional
    fun driverAssignmentFailed(rideId: String, reason: String, causationId: String) {
        cancelRide(rideId, CancellationReason.NO_DRIVER_AVAILABLE, reason, causationId)
    }

    private fun cancelRide(rideId: String, reason: CancellationReason, detail: String, causationId: String) {
        val ride = rides.findById(rideId).orElse(null) ?: return
        if (!ride.cancel("$reason: $detail")) return

        val event = RideCancelled.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOccurredAt(Instant.now())
            .setRideId(ride.id)
            .setReason(reason)
            .build()
        events.publish(Topics.RIDE_CANCELLED, ride.id, event, causationId)
    }
}
