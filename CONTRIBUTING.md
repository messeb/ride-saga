# Contributing

Thanks for your interest in contributing! This repository is a reference implementation of
event-driven microservices patterns, so contributions should keep the codebase small, focused,
and well documented.

## Development setup

Prerequisites:

- JDK 21 (the Gradle toolchain targets Java 21)
- Docker with Compose v2 (for integration tests and the local stack)
- `make`, `curl`, `jq` (for the demo scripts)

```bash
./gradlew build          # compiles, lints (detekt + ktlint) and runs all tests
make up                  # starts Kafka, Schema Registry, services and observability stack
make demo                # requests a ride and follows the saga to CONFIRMED
```

> **Note:** the Gradle wrapper is intentionally pinned to the 8.x line because the Avro
> code-generation plugin does not support Gradle 9 yet. Do not bump the wrapper major version.

## Commit messages

This project uses [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
The release changelog is generated from commit history with git-cliff, so the format is
mandatory:

```
feat(booking): add ride cancellation endpoint
fix(payment): skip already processed events
docs: extend event catalog with retention settings
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`.
Use the service or module name as scope where it applies.

## Event schema changes

Event schemas in `contracts/src/main/avro/` are the public API of this system:

- Schema changes must stay **BACKWARD compatible**: you may add fields *with defaults* or
  remove fields *that have defaults*. Renaming or changing the type of a field is a breaking
  change and will fail CI.
- The compatibility gate is `contracts`' schema-compatibility test, which validates every
  schema against the frozen copies in `contracts/src/test/resources/previous-schemas/`.
- When you intentionally evolve a schema, update `docs/events.md` in the same PR.

Try it: change a field type in `RideRequested.avsc` and run
`./gradlew :contracts:test` — the build goes red. That guardrail is the point.

## Pull requests

1. Fork and create a feature branch from `main`.
2. Make your change; add or adjust tests (every pattern implementation has a test proving it).
3. Ensure `./gradlew build` is green locally.
4. Open a PR using the template. CI must pass before review.

## Architecture decisions

Non-trivial design choices are documented as ADRs in `docs/adr/`. If your change alters an
accepted decision, add a new ADR that supersedes the old one rather than editing history.
