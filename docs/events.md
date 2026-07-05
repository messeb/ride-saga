# Event Catalog

Events are the public API of this system. Schemas live in
[`contracts/src/main/avro/`](../contracts/src/main/avro/); this catalog documents the
topics, ownership and conventions around them.

## Topics

| Topic | Event | Key | Producer | Consumers (group) |
|---|---|---|---|---|
| `booking.ride-requested.v1` | `RideRequested` | rideId | booking-service | driver-matching-service, payment-service |
| `dispatch.driver-assigned.v1` | `DriverAssigned` | rideId | driver-matching-service | booking-service, payment-service |
| `dispatch.driver-assignment-failed.v1` | `DriverAssignmentFailed` | rideId | driver-matching-service | booking-service |
| `payment.payment-completed.v1` | `PaymentCompleted` | rideId | payment-service | booking-service |
| `payment.payment-failed.v1` | `PaymentFailed` | rideId | payment-service | booking-service |
| `booking.ride-confirmed.v1` | `RideConfirmed` | rideId | booking-service | notification-service |
| `booking.ride-cancelled.v1` | `RideCancelled` | rideId | booking-service | driver-matching-service, notification-service |

Operational topics (created by spring-kafka's retry machinery):
`booking.ride-requested.v1.retry-0..2` and `booking.ride-requested.v1.dlt`
(driver-matching-service's consumption only — see ADR-004).

## Conventions

- **Naming:** `<context>.<event-kebab>.v<major>`. The major version is part of the name:
  an incompatible schema gets a *new topic* (`…v2`) so old consumers keep working during
  migration. Compatible evolution happens in place (see below).
- **Partitioning:** 6 partitions per topic, key = `rideId`. All events of one ride are
  totally ordered relative to each other; consumer group = service name, listener
  concurrency 3.
- **Envelope fields:** every event carries `eventId` (UUID, the idempotency handle) and
  `occurredAt` (timestamp-millis).
- **Facts, not commands:** topics carry things that *happened* (`DriverAssigned`), never
  instructions (`AssignDriver`). Consumers decide for themselves how to react.

## Headers

| Header | Meaning |
|---|---|
| `x-correlation-id` | Born at HTTP ingress; copied to every event of the same saga instance. Also in each service's log MDC (`corr=` in log lines). |
| `x-causation-id` | The `eventId` of the message being handled when this event was published — reconstructs the causal chain. |
| `traceparent` | W3C trace context, managed by Micrometer Tracing (see ADR-006). |

## Evolution rules

Compatibility mode is **BACKWARD** — a consumer with the new schema can read data
written with the old one:

- ✅ add a field **with a default**
- ✅ remove a field **that had a default**
- ✅ add an enum symbol *if the enum declares a `default`* (see `CancellationReason`)
- ❌ rename a field, change a type, add a mandatory field

Enforced twice: `contracts`' `SchemaCompatibilityTest` in CI (against the frozen copies
in `previous-schemas/`) and by the Schema Registry at runtime. When you evolve a schema,
update the frozen copy and this catalog in the same PR.

Example in history: `RideRequested` gained an optional `paymentMethod` field — see the
schema-evolution commit.

## Future work

- AsyncAPI document generated from the registry (skipped for now: a second source of
  truth is not worth it for 7 events — this file is the catalog).
- Automated DLT re-drive tooling.
- Log aggregation (Loki) alongside metrics and traces.
