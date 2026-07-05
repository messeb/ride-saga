package com.messeb.ridesaga.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * The idempotent-consumer pattern's ledger: the eventId of every handled message,
 * written in the same database transaction as the business change it caused. A redelivery
 * finds its eventId here and is skipped — at-least-once delivery, exactly-once *effect*.
 */
@Entity
@Table(name = "processed_events")
class ProcessedEvent(
    @Id
    val eventId: String,
    @Column(nullable = false)
    val processedAt: Instant,
)
