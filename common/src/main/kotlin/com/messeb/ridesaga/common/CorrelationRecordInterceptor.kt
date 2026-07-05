package com.messeb.ridesaga.common

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.listener.RecordInterceptor
import java.util.UUID

/**
 * Puts the incoming record's correlation id into the MDC for the duration of the
 * listener invocation, so every log line of the processing carries it. Records without
 * the header (e.g. hand-crafted demo messages) get a fresh id instead of none.
 */
class CorrelationRecordInterceptor<K : Any, V : Any> : RecordInterceptor<K, V> {

    override fun intercept(record: ConsumerRecord<K, V>, consumer: Consumer<K, V>): ConsumerRecord<K, V> {
        val correlationId = record.headers().lastHeader(EventHeaders.CORRELATION_ID)
            ?.value()?.decodeToString()
            ?: UUID.randomUUID().toString()
        MDC.put(EventHeaders.MDC_CORRELATION_ID, correlationId)
        return record
    }

    override fun afterRecord(record: ConsumerRecord<K, V>, consumer: Consumer<K, V>) {
        MDC.remove(EventHeaders.MDC_CORRELATION_ID)
    }
}
