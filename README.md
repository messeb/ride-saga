# ride-saga — Event-Driven Microservices Reference

[![CI](https://github.com/messeb/ride-saga/actions/workflows/ci.yml/badge.svg)](https://github.com/messeb/ride-saga/actions/workflows/ci.yml)
[![Release](https://github.com/messeb/ride-saga/actions/workflows/release.yml/badge.svg)](https://github.com/messeb/ride-saga/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

A production-grade reference implementation of **event-driven microservices** on Apache Kafka:
four Kotlin/Spring Boot services coordinate a ride booking through a **choreographed saga** —
no central orchestrator, just events.

The repository demonstrates, each in exactly one clearly documented place:

- choreography-based sagas with compensation
- Avro event contracts with an enforced schema-evolution guardrail
- at-least-once delivery and idempotent consumers
- non-blocking retries and dead-letter topics
- correlation/causation IDs and end-to-end tracing across Kafka hops
- a full release pipeline publishing multi-arch images to GitHub Container Registry

> 🚧 Skeleton README — architecture diagrams, pattern map, and demo walkthroughs land in the
> docs phase.

## Quickstart

```bash
make up     # Kafka (KRaft), Schema Registry, 4 services, Grafana/Tempo/Prometheus, kafka-ui
make demo   # request a ride, watch the saga run to CONFIRMED
make down
```

## Documentation

- [Event catalog](docs/events.md)
- [Architecture Decision Records](docs/adr/)
- [Contributing](CONTRIBUTING.md)

## License

[Apache-2.0](LICENSE)
