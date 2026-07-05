# ADR-003: At-least-once delivery with idempotent consumers (no EOS)

## Status

Accepted

## Context

Kafka offers a spectrum of delivery guarantees. Exactly-once semantics (EOS,
transactions) look attractive but only cover Kafka-to-Kafka flows — the moment a
consumer touches a database or external API, transactions stop helping. The realistic
baseline for event-driven services is at-least-once: messages can be redelivered after
rebalances, timeouts, or offset resets.

## Decision

At-least-once everywhere, made safe by idempotent consumers:

- Producers: `acks=all` + `enable.idempotence=true` (no duplicates *from retries on the
  producer side*, no reordering).
- Consumers: `AckMode.RECORD` — offsets commit after each successfully processed record,
  minimizing the redelivery window.
- Consumers that cause side effects are idempotent. payment-service records each handled
  `eventId` in a `processed_events` table **in the same database transaction** as the
  business change; redeliveries are skipped and counted (`events_duplicate_total`).
  State-machine consumers (booking) are naturally idempotent: duplicate transitions are
  no-ops.

## Consequences

- Duplicates are a *handled, observable* condition instead of an assumed impossibility.
  `make demo-duplicate` replays events via offset reset to prove it.
- No Kafka transactions: simpler configuration, no throughput penalty, and no false
  sense of safety around external side effects.
- Every new consumer with side effects must choose an idempotency strategy — the pattern
  to copy lives in `PaymentService`.
