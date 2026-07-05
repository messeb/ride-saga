# ADR-001: Choreography over orchestration for the booking saga

## Status

Accepted

## Context

A ride booking spans four services: booking, driver matching, payment, and notification.
The steps must be coordinated, and partial failures (payment declined, no driver) must
undo earlier steps. Two established saga styles exist:

- **Orchestration** — a central coordinator sends commands and tracks a state machine.
- **Choreography** — services react to each other's events; nobody owns the whole flow.

## Decision

Choreography. Each service listens for the events it cares about and publishes facts
about its own domain. booking-service owns the *ride's* state machine (as the ride
aggregate's source of truth) but never commands other services.

Compensation follows the same principle: driver-matching-service releases the driver when
it observes `RideCancelled` — booking does not know that a driver pool exists.

## Consequences

- Services stay decoupled: notification-service was added without touching any producer.
- No single point of coupling/failure for the flow.
- The overall flow is implicit; you reconstruct it from the event catalog
  ([docs/events.md](../events.md)), correlation/causation ids and traces — this is why
  observability is a first-class part of this repo.
- More complex flows (dynamic step ordering, timeouts across steps) would favor an
  orchestrator; at that point introduce one for the *flow*, keeping domain events intact.
