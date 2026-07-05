package com.messeb.ridesaga.payment.application

import com.messeb.ridesaga.common.EventPublisher
import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.events.PaymentCompleted
import com.messeb.ridesaga.events.PaymentFailed
import com.messeb.ridesaga.events.RideRequested
import com.messeb.ridesaga.payment.domain.Payment
import com.messeb.ridesaga.payment.domain.PaymentRepository
import com.messeb.ridesaga.payment.domain.ProcessedEvent
import com.messeb.ridesaga.payment.domain.ProcessedEventRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The idempotent-consumer showcase. Charging is guarded by the processed_events ledger:
 * the eventId is recorded in the SAME transaction as the payment state change, so a
 * redelivered DriverAssigned cannot charge twice. Demo rule: fares >= 500.00 are declined.
 */
@Service
class PaymentService(
    private val payments: PaymentRepository,
    private val processedEvents: ProcessedEventRepository,
    private val events: EventPublisher,
    meterRegistry: MeterRegistry,
) {

    // rendered as events_duplicate_total in Prometheus
    private val duplicateCounter = meterRegistry.counter("events.duplicate", "service", "payment-service")

    @Transactional
    fun registerPendingPayment(event: RideRequested) {
        if (payments.findByRideId(event.rideId) != null) {
            return
        }
        payments.save(
            Payment(
                id = UUID.randomUUID().toString(),
                rideId = event.rideId,
                amount = event.fareAmount,
                currency = event.currency,
                createdAt = Instant.now(),
            ),
        )
    }

    @Transactional
    fun chargeForRide(rideId: String, triggeringEventId: String) {
        if (processedEvents.existsById(triggeringEventId)) {
            duplicateCounter.increment()
            log.info("event {} already processed — skipping duplicate charge for ride {}", triggeringEventId, rideId)
            return
        }
        processedEvents.save(ProcessedEvent(triggeringEventId, Instant.now()))

        val payment = payments.findByRideId(rideId)
        if (payment == null) {
            log.warn("no pending payment for ride {} — RideRequested not seen yet", rideId)
            error("pending payment for ride $rideId not found — retriggering redelivery")
        }

        if (payment.amount >= DECLINE_THRESHOLD) {
            payment.fail()
            events.publish(
                Topics.PAYMENT_FAILED,
                rideId,
                PaymentFailed.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setOccurredAt(Instant.now())
                    .setRideId(rideId)
                    .setReason("card declined (demo rule: fare >= $DECLINE_THRESHOLD)")
                    .build(),
                causationId = triggeringEventId,
            )
            return
        }

        payment.complete()
        events.publish(
            Topics.PAYMENT_COMPLETED,
            rideId,
            PaymentCompleted.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setRideId(rideId)
                .setPaymentId(payment.id)
                .setAmount(payment.amount)
                .setCurrency(payment.currency)
                .build(),
            causationId = triggeringEventId,
        )
    }

    companion object {
        val DECLINE_THRESHOLD: BigDecimal = BigDecimal("500.00")
        private val log = LoggerFactory.getLogger(PaymentService::class.java)
    }
}
