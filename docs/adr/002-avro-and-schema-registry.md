# ADR-002: Avro with Confluent Schema Registry for event contracts

## Status

Accepted

## Context

Events are the public API of this system. The message format must support strong typing,
compact encoding, and above all *governed evolution*: producers must not be able to break
consumers silently.

Options considered: JSON (+JSON Schema), Protobuf (+registry), Avro (+registry).

## Decision

Avro schemas in the `contracts` module, serialized with the Confluent Avro serializer
against a Schema Registry running in `BACKWARD` compatibility mode.

- Every `.avsc` is the single source of truth; Java classes are generated at build time.
- Evolution is enforced twice: at build time by `SchemaCompatibilityTest` (no registry
  needed) and at runtime by the registry when a producer registers a new version.
- BACKWARD compatibility = new consumers read old data: add fields *with defaults*,
  remove fields *that have defaults*, never rename or retype.

## Consequences

- Breaking a contract fails CI before it can reach a broker.
- Avro is the most idiomatic registry pairing on the JVM; Protobuf would work equally
  well but adds a second IDL for no benefit here. JSON Schema tooling for evolution is
  significantly weaker.
- **License note:** the Confluent Schema Registry server and serializer are under the
  Confluent Community License (not OSI-approved). Fine for this demo and most in-house
  use, but check before redistribution. Apache-2.0 alternative: Apicurio Registry, which
  can speak the Confluent API.
