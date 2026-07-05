package com.messeb.ridesaga.common

/**
 * Topic naming convention: `<context>.<event-kebab>.v<major>`.
 *
 * The major version is part of the topic name: incompatible schema changes get a new
 * topic instead of breaking consumers in place. Retry topics append `.retry-<n>`,
 * dead-letter topics append `.dlt` (spring-kafka defaults).
 */
object Topics {
    const val RIDE_REQUESTED = "booking.ride-requested.v1"
    const val RIDE_CONFIRMED = "booking.ride-confirmed.v1"
    const val RIDE_CANCELLED = "booking.ride-cancelled.v1"
    const val DRIVER_ASSIGNED = "dispatch.driver-assigned.v1"
    const val DRIVER_ASSIGNMENT_FAILED = "dispatch.driver-assignment-failed.v1"
    const val PAYMENT_COMPLETED = "payment.payment-completed.v1"
    const val PAYMENT_FAILED = "payment.payment-failed.v1"

    /** Partitions per topic; the partition key is always the rideId, preserving per-ride ordering. */
    const val PARTITIONS = 6
}
