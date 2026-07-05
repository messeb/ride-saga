package com.messeb.ridesaga.dispatch.domain

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Simulated fleet: a fixed set of drivers that get checked out on assignment and
 * returned on release. Releasing is the saga's compensating action — when a ride is
 * cancelled after a driver was assigned, the driver goes back into the pool.
 */
@Component
class DriverPool(properties: DispatchProperties, meterRegistry: MeterRegistry) {

    private val available = ConcurrentLinkedDeque((1..properties.driverPoolSize).map { "driver-$it" })
    private val assignments = ConcurrentHashMap<String, String>()

    init {
        meterRegistry.gauge("drivers.available", available) { it.size.toDouble() }
    }

    /** @return the assigned driver id, or null when no driver is available */
    fun assign(rideId: String): String? {
        assignments[rideId]?.let { alreadyAssigned ->
            log.info("ride {} already has driver {} — treating duplicate assignment as success", rideId, alreadyAssigned)
            return alreadyAssigned
        }
        val driverId = available.pollFirst() ?: return null
        assignments[rideId] = driverId
        return driverId
    }

    fun release(rideId: String) {
        val driverId = assignments.remove(rideId) ?: return
        available.addLast(driverId)
        log.info("released driver {} from cancelled ride {}", driverId, rideId)
    }

    fun availableCount(): Int = available.size

    private companion object {
        private val log = LoggerFactory.getLogger(DriverPool::class.java)
    }
}
