package com.messeb.ridesaga.payment.messaging

import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.DriverAssigned
import com.messeb.ridesaga.events.RideRequested
import com.messeb.ridesaga.payment.application.PaymentService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Payment's part of the choreography: remember the fare when the ride is requested,
 * charge once a driver is assigned.
 */
@Component
class PaymentSagaListener(private val paymentService: PaymentService) {

    @KafkaListener(topics = [Topics.RIDE_REQUESTED])
    fun onRideRequested(event: RideRequested) {
        paymentService.registerPendingPayment(event)
    }

    @KafkaListener(topics = [Topics.DRIVER_ASSIGNED])
    fun onDriverAssigned(event: DriverAssigned) {
        paymentService.chargeForRide(event.rideId, triggeringEventId = event.eventId)
    }
}
