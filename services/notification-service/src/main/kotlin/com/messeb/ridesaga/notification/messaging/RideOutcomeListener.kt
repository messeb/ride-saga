package com.messeb.ridesaga.notification.messaging

import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.RideCancelled
import com.messeb.ridesaga.events.RideConfirmed
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Terminal fan-out consumer: reacts to the saga's outcome events without participating
 * in the saga itself. New consumers like this one can be added without touching any
 * producer — the loose-coupling payoff of event-driven communication.
 */
@Component
class RideOutcomeListener(meterRegistry: MeterRegistry) {

    // rendered as notifications_sent_total in Prometheus
    private val sentCounter = meterRegistry.counter("notifications.sent")

    @KafkaListener(topics = [Topics.RIDE_CONFIRMED])
    fun onRideConfirmed(event: RideConfirmed) {
        sentCounter.increment()
        log.info(
            "📱 push notification → rider: your ride {} is confirmed, driver {} is on the way",
            event.rideId,
            event.driverId,
        )
    }

    @KafkaListener(topics = [Topics.RIDE_CANCELLED])
    fun onRideCancelled(event: RideCancelled) {
        sentCounter.increment()
        log.info(
            "📱 push notification → rider: your ride {} was cancelled ({})",
            event.rideId,
            event.reason,
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RideOutcomeListener::class.java)
    }
}
