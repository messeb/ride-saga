package com.messeb.ridesaga.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class PaymentStatus { PENDING, COMPLETED, FAILED }

/**
 * Payment-service's own view of a ride's payment. The fare is learned from
 * RideRequested — services in a choreography build local state from events instead of
 * querying each other.
 */
@Entity
@Table(name = "payments")
class Payment(
    @Id
    val id: String,
    @Column(nullable = false, unique = true)
    val rideId: String,
    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    @Column(nullable = false, length = 3)
    val currency: String,
    @Column(nullable = false)
    val createdAt: Instant,
) {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    fun complete() {
        status = PaymentStatus.COMPLETED
    }

    fun fail() {
        status = PaymentStatus.FAILED
    }
}
