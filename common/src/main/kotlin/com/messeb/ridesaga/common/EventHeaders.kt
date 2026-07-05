package com.messeb.ridesaga.common

/**
 * Kafka header conventions.
 *
 * - correlation id: set once at the HTTP ingress (booking-service) and copied onto every
 *   event of the same saga instance — groups all log lines and messages of one ride.
 * - causation id: the eventId of the message a service was handling when it published —
 *   makes the event chain reconstructable ("what triggered this?").
 *
 * The W3C `traceparent` header is managed separately by Micrometer Tracing.
 */
object EventHeaders {
    const val CORRELATION_ID = "x-correlation-id"
    const val CAUSATION_ID = "x-causation-id"

    /** MDC keys mirroring the headers so every log line carries them. */
    const val MDC_CORRELATION_ID = "correlationId"
}
