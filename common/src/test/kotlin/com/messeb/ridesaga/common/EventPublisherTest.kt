package com.messeb.ridesaga.common

import com.messeb.ridesaga.events.PaymentFailed
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.concurrent.CompletableFuture

class EventPublisherTest {

    private val kafkaTemplate = mockk<KafkaTemplate<Any, Any>>()
    private val publisher = EventPublisher(kafkaTemplate)
    private val sent = slot<ProducerRecord<Any, Any>>()

    private val event = PaymentFailed.newBuilder()
        .setEventId("event-1")
        .setOccurredAt(Instant.now())
        .setRideId("ride-1")
        .setReason("card declined")
        .build()

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `keys the record with the ride id`() {
        every { kafkaTemplate.send(capture(sent)) } returns CompletableFuture()

        publisher.publish(Topics.PAYMENT_FAILED, "ride-1", event)

        assertEquals("ride-1", sent.captured.key())
        assertEquals(Topics.PAYMENT_FAILED, sent.captured.topic())
    }

    @Test
    fun `propagates the correlation id from the MDC`() {
        every { kafkaTemplate.send(capture(sent)) } returns CompletableFuture()
        MDC.put(EventHeaders.MDC_CORRELATION_ID, "corr-42")

        publisher.publish(Topics.PAYMENT_FAILED, "ride-1", event)

        val header = sent.captured.headers().lastHeader(EventHeaders.CORRELATION_ID)
        assertEquals("corr-42", header.value().decodeToString())
    }

    @Test
    fun `generates a correlation id when none is in scope`() {
        every { kafkaTemplate.send(capture(sent)) } returns CompletableFuture()

        publisher.publish(Topics.PAYMENT_FAILED, "ride-1", event)

        assertNotNull(sent.captured.headers().lastHeader(EventHeaders.CORRELATION_ID))
    }

    @Test
    fun `adds the causation id only when the event was caused by another one`() {
        every { kafkaTemplate.send(capture(sent)) } returns CompletableFuture()

        publisher.publish(Topics.PAYMENT_FAILED, "ride-1", event, causationId = "cause-7")
        assertEquals("cause-7", sent.captured.headers().lastHeader(EventHeaders.CAUSATION_ID).value().decodeToString())

        publisher.publish(Topics.PAYMENT_FAILED, "ride-1", event)
        assertNull(sent.captured.headers().lastHeader(EventHeaders.CAUSATION_ID))
    }
}
