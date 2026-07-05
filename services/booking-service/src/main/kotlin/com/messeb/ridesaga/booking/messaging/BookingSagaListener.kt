package com.messeb.ridesaga.booking.messaging

import com.messeb.ridesaga.booking.application.RideService
import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.DriverAssigned
import com.messeb.ridesaga.events.DriverAssignmentFailed
import com.messeb.ridesaga.events.PaymentCompleted
import com.messeb.ridesaga.events.PaymentFailed
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * The booking side of the choreography: reacts to what the other services publish and
 * advances the ride state machine. Each incoming event's id becomes the causation id of
 * whatever booking publishes in response.
 */
@Component
class BookingSagaListener(private val rides: RideService) {

    @KafkaListener(topics = [Topics.DRIVER_ASSIGNED])
    fun onDriverAssigned(event: DriverAssigned) {
        rides.driverAssigned(event.rideId, event.driverId)
    }

    @KafkaListener(topics = [Topics.PAYMENT_COMPLETED])
    fun onPaymentCompleted(event: PaymentCompleted) {
        rides.paymentCompleted(event.rideId, causationId = event.eventId)
    }

    @KafkaListener(topics = [Topics.PAYMENT_FAILED])
    fun onPaymentFailed(event: PaymentFailed) {
        rides.paymentFailed(event.rideId, event.reason, causationId = event.eventId)
    }

    @KafkaListener(topics = [Topics.DRIVER_ASSIGNMENT_FAILED])
    fun onDriverAssignmentFailed(event: DriverAssignmentFailed) {
        rides.driverAssignmentFailed(event.rideId, event.reason, causationId = event.eventId)
    }
}
