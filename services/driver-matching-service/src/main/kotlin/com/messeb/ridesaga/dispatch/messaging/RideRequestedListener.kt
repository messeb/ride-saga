package com.messeb.ridesaga.dispatch.messaging

import com.messeb.ridesaga.common.EventPublisher
import com.messeb.ridesaga.common.Topics
import com.messeb.ridesaga.dispatch.domain.DispatchProperties
import com.messeb.ridesaga.dispatch.domain.DriverPool
import com.messeb.ridesaga.events.DriverAssigned
import com.messeb.ridesaga.events.DriverAssignmentFailed
import com.messeb.ridesaga.events.RideRequested
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.BackOff
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * The failure-handling showcase: non-blocking retries on dedicated retry topics with
 * exponential backoff, and a dead-letter topic once retries are exhausted (or immediately
 * for permanent failures). The main topic keeps flowing while a bad message retries.
 *
 * Demo triggers:
 * - pickupLocation == "POISON"    → fails on every attempt, exhausts the retry topics, lands in the DLT
 * - pickupLocation == "FAIL_FAST" → NonRetryableException, skips retries, straight to the DLT
 */
@Component
class RideRequestedListener(
    private val driverPool: DriverPool,
    private val events: EventPublisher,
    private val properties: DispatchProperties,
    meterRegistry: MeterRegistry,
) {

    // rendered as dlq_messages_total in Prometheus
    private val dlqCounter = meterRegistry.counter("dlq.messages", "topic", Topics.RIDE_REQUESTED)

    @RetryableTopic(
        attempts = "4",
        backOff = BackOff(delay = 1000, multiplier = 2.0, maxDelay = 10_000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        retryTopicSuffix = ".retry",
        dltTopicSuffix = ".dlt",
        exclude = [NonRetryableException::class],
    )
    @KafkaListener(topics = [Topics.RIDE_REQUESTED])
    fun onRideRequested(event: RideRequested) {
        when (event.pickupLocation) {
            "POISON" -> throw PoisonMessageException("simulated transient failure for ride ${event.rideId}")
            "FAIL_FAST" -> throw NonRetryableException("simulated permanent failure for ride ${event.rideId}")
        }

        val driverId = driverPool.assign(event.rideId)
        if (driverId == null) {
            events.publish(
                Topics.DRIVER_ASSIGNMENT_FAILED,
                event.rideId,
                DriverAssignmentFailed.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setOccurredAt(Instant.now())
                    .setRideId(event.rideId)
                    .setReason("no driver available")
                    .build(),
                causationId = event.eventId,
            )
            return
        }

        events.publish(
            Topics.DRIVER_ASSIGNED,
            event.rideId,
            DriverAssigned.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setRideId(event.rideId)
                .setDriverId(driverId)
                .setEtaMinutes(properties.etaMinutes)
                .build(),
            causationId = event.eventId,
        )
    }

    @DltHandler
    fun onDeadLetter(
        record: ConsumerRecord<String, RideRequested>,
        @Header(KafkaHeaders.EXCEPTION_MESSAGE, required = false) exceptionMessage: String?,
    ) {
        dlqCounter.increment()
        log.error(
            "ride request {} moved to DLT after failing permanently: {}",
            record.value().rideId,
            exceptionMessage ?: "unknown error",
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RideRequestedListener::class.java)
    }
}
