# ADR-006: Micrometer Tracing (OTel bridge) instead of the OpenTelemetry Java agent

## Status

Accepted

## Context

"Monitoring message flows" needs distributed traces that follow a ride across four
services and every Kafka hop. Two mainstream ways to get them on the JVM:

1. **OpenTelemetry Java agent** — attached via `-javaagent`, instruments bytecode,
   zero code changes.
2. **Micrometer Tracing with the OTel bridge** — Spring's native observability API;
   spring-kafka and Spring MVC emit observations that become spans.

## Decision

Micrometer Tracing with `micrometer-tracing-bridge-otel`, OTLP-exported to an
otel-collector and stored in Tempo. Kafka observations are enabled via configuration
(`spring.kafka.listener.observation-enabled`, `spring.kafka.template.observation-enabled`).

Rationale:

- It is the idiomatic Spring Boot path; traces, metrics and log correlation share one
  observation model.
- Works identically in integration tests — no special JVM flags in Docker, CI or IDE.
- Keeps the Dockerfiles plain (no agent jar to download and mount), which matters in a
  repo whose images are meant to be read.

## Consequences

- Only instrumented libraries produce spans (Spring MVC, spring-kafka, JDBC via further
  modules). The agent would trace more third-party code for free.
- W3C `traceparent` propagates in Kafka headers automatically; our own
  `x-correlation-id` (ADR-001 observability) stays independent of the tracing vendor.
- Boot 4 note: the autoconfiguration lives in
  `spring-boot-micrometer-tracing-opentelemetry`, and the bridge library must be added
  explicitly.
