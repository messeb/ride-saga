package com.messeb.ridesaga.dispatch.messaging

/** Simulated transient processing failure — retried on the retry topics until exhausted. */
class PoisonMessageException(message: String) : RuntimeException(message)

/** Simulated permanent failure — excluded from retries, routed straight to the DLT. */
class NonRetryableException(message: String) : RuntimeException(message)
