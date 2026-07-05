# ADR-005: No transactional outbox (accepted dual-write risk)

## Status

Accepted

## Context

booking-service and payment-service write to Postgres *and* publish to Kafka. These are
two resources without a shared transaction: a crash between the database commit and the
Kafka send loses the event (or, in the reverse order, publishes an event for a state
that was rolled back). The textbook remedy is the transactional outbox: write the event
into an `outbox` table in the same transaction, and let a relay (Debezium, or a polling
publisher) move it to Kafka.

## Decision

No outbox in this repository. State is committed first, then the event is published on
the same thread.

Rationale:

- The failure window is a process crash in the milliseconds between commit and send —
  real, but rare.
- An outbox adds a relay component, ordering considerations, and cleanup — significant
  moving parts that would blur every other pattern this repo demonstrates.
- The demo's operational fallback is honest and simple: a ride stuck in `REQUESTED` /
  `DRIVER_ASSIGNED` is visible via the API and metrics, and can be re-driven manually.

## Consequences

- **Do not copy this decision blindly into systems where a lost event means lost money.**
  There, use an outbox (Debezium CDC being the common production choice) or a
  listen-to-yourself pattern.
- The publishing seam is one class (`EventPublisher`), so retrofitting an outbox later
  touches one place per service.
