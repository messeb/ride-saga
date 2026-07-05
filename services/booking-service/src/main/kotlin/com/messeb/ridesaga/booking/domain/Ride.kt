package com.messeb.ridesaga.booking.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

enum class RideStatus { REQUESTED, DRIVER_ASSIGNED, CONFIRMED, CANCELLED }

/**
 * The ride aggregate — booking-service is the source of truth for ride state.
 *
 * The choreographed saga is driven by this explicit state machine:
 * REQUESTED → DRIVER_ASSIGNED → CONFIRMED, with CANCELLED reachable from the two
 * non-terminal states. Transition methods return whether they were applied, so
 * duplicate or late events degrade to logged no-ops instead of corrupting state.
 */
@Entity
@Table(name = "rides")
class Ride(
    @Id
    val id: String,
    @Column(nullable = false)
    val riderId: String,
    @Column(nullable = false)
    val pickupLocation: String,
    @Column(nullable = false)
    val dropoffLocation: String,
    @Column(nullable = false, precision = 10, scale = 2)
    val fareAmount: BigDecimal,
    @Column(nullable = false, length = 3)
    val currency: String,
    @Column(nullable = false)
    val requestedAt: Instant,
) {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RideStatus = RideStatus.REQUESTED
        protected set

    var driverId: String? = null
        protected set

    var cancellationReason: String? = null
        protected set

    fun assignDriver(driverId: String): Boolean = transition(RideStatus.DRIVER_ASSIGNED, from = setOf(RideStatus.REQUESTED)) {
        this.driverId = driverId
    }

    fun confirm(): Boolean = transition(RideStatus.CONFIRMED, from = setOf(RideStatus.DRIVER_ASSIGNED))

    fun cancel(reason: String): Boolean = transition(RideStatus.CANCELLED, from = setOf(RideStatus.REQUESTED, RideStatus.DRIVER_ASSIGNED)) {
        this.cancellationReason = reason
    }

    private fun transition(to: RideStatus, from: Set<RideStatus>, apply: () -> Unit = {}): Boolean {
        if (status !in from) {
            log.warn("ignoring transition of ride {} from {} to {} — not a valid source state", id, status, to)
            return false
        }
        apply()
        status = to
        return true
    }

    private companion object {
        private val log = LoggerFactory.getLogger(Ride::class.java)
    }
}
