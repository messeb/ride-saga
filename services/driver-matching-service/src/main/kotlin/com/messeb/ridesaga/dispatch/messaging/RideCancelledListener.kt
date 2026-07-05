package com.messeb.ridesaga.dispatch.messaging

import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.dispatch.domain.DriverPool
import com.messeb.ridesaga.events.RideCancelled
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Compensation in a choreographed saga: nobody tells this service to roll back — it
 * observes RideCancelled and undoes its own work by returning the driver to the pool.
 */
@Component
class RideCancelledListener(private val driverPool: DriverPool) {

    @KafkaListener(topics = [Topics.RIDE_CANCELLED])
    fun onRideCancelled(event: RideCancelled) {
        driverPool.release(event.rideId)
    }
}
