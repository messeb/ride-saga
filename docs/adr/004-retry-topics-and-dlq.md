# ADR-004: Non-blocking retry topics with a dead-letter topic

## Status

Accepted

## Context

A Kafka partition is an ordered log: if a consumer keeps failing on one message and
retries in place, every message behind it stalls (head-of-line blocking). Failures come
in two flavors — transient (dependency briefly down) and permanent (poison message that
can never succeed).

## Decision

spring-kafka's `@RetryableTopic` on the `RideRequested` listener in
driver-matching-service:

- 4 attempts with exponential backoff (1s → 2s → 4s), each retry on its own topic
  (`booking.ride-requested.v1.retry-0..2`) so the main topic keeps flowing.
- Exhausted or permanently failing messages land on `booking.ride-requested.v1.dlt`.
- `NonRetryableException` bypasses retries and goes straight to the DLT.
- The `@DltHandler` logs payload + error and increments `dlq_messages_total`, which is
  visible on the Grafana dashboard — a DLQ nobody watches is a black hole.

## Consequences

- One poison message cannot stall ride processing (`make demo-poison` proves it).
- Retries lose per-key ordering relative to the main topic. Acceptable here: a retried
  `RideRequested` is the first event of its ride, so nothing can overtake it. For
  order-sensitive flows, reconsider per-partition blocking retries.
- DLT messages need an operational answer (inspect via kafka-ui, fix, re-publish).
  Automated re-drive is out of scope and listed as future work.
- The pattern lives in ONE place by design; other listeners rely on the default
  in-memory error handler until they need more.
