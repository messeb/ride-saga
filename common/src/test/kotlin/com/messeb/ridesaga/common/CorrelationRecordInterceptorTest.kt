package com.messeb.ridesaga.common

import io.mockk.mockk
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class CorrelationRecordInterceptorTest {

    private val interceptor = CorrelationRecordInterceptor<String, String>()
    private val consumer = mockk<Consumer<String, String>>()

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `puts the correlation header into the MDC and clears it afterwards`() {
        val record = record().apply {
            headers().add(RecordHeader(EventHeaders.CORRELATION_ID, "corr-9".toByteArray()))
        }

        interceptor.intercept(record, consumer)
        assertEquals("corr-9", MDC.get(EventHeaders.MDC_CORRELATION_ID))

        interceptor.afterRecord(record, consumer)
        assertNull(MDC.get(EventHeaders.MDC_CORRELATION_ID))
    }

    @Test
    fun `generates a correlation id for records without the header`() {
        interceptor.intercept(record(), consumer)

        assertNotNull(MDC.get(EventHeaders.MDC_CORRELATION_ID))
    }

    private fun record() = ConsumerRecord("topic", 0, 0L, "key", "value")
}
