package com.messeb.ridesaga.common

import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Single publishing path for all services: keys every record with the ride id and
 * propagates the correlation id (from MDC, i.e. from the message or HTTP request being
 * handled) plus the causation id of the triggering event.
 */
class EventPublisher(private val kafkaTemplate: KafkaTemplate<String, SpecificRecord>) {

    fun publish(
        topic: String,
        rideId: String,
        event: SpecificRecord,
        causationId: String? = null,
    ): CompletableFuture<SendResult<String, SpecificRecord>> {
        val record = ProducerRecord<String, SpecificRecord>(topic, rideId, event)
        val correlationId = MDC.get(EventHeaders.MDC_CORRELATION_ID) ?: UUID.randomUUID().toString()
        record.headers().add(RecordHeader(EventHeaders.CORRELATION_ID, correlationId.toByteArray()))
        causationId?.let { record.headers().add(RecordHeader(EventHeaders.CAUSATION_ID, it.toByteArray())) }
        log.info("publishing {} to {} for ride {}", event.schema.name, topic, rideId)
        return kafkaTemplate.send(record)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(EventPublisher::class.java)
    }
}
