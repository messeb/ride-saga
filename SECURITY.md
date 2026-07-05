# Security Policy

## Supported versions

Only the latest release receives security updates.

| Version | Supported |
| ------- | --------- |
| latest  | ✅        |
| older   | ❌        |

## Reporting a vulnerability

Please **do not** open a public issue for security vulnerabilities.

Report vulnerabilities via [GitHub private vulnerability reporting](../../security/advisories/new)
or by email to <sebastian@messeb.com>. You will receive an acknowledgement within 72 hours.

Please include a description of the issue, steps to reproduce, and the affected component
(service, workflow, or container image).

## Scope

This is a reference/demo system. The docker-compose stack is intended for local use only and
deliberately runs without authentication (Kafka PLAINTEXT, Grafana anonymous admin). Do not
deploy it as-is to any network-accessible environment.
