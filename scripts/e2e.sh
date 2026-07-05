#!/usr/bin/env bash
# End-to-end smoke test: full stack up, happy path confirmed, compensation path
# cancelled, poison message dead-lettered.
set -euo pipefail
cd "$(dirname "$0")/.."

cleanup() { docker compose down -v > /dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> building jars"
./gradlew --console=plain -q bootJar

echo "==> starting the stack"
docker compose up -d --build --wait

echo "==> happy path: ride must reach CONFIRMED"
./scripts/demo.sh | tee /tmp/e2e-happy.log
grep -q "CONFIRMED" /tmp/e2e-happy.log

echo "==> compensation path: fare 500.00 must be CANCELLED"
./scripts/demo.sh 500.00 | tee /tmp/e2e-cancel.log
grep -q "CANCELLED" /tmp/e2e-cancel.log

echo "==> failure handling: poison message must reach the DLT"
./scripts/demo-poison.sh

echo ""
echo "✅ e2e passed: happy path, compensation and dead-lettering all verified"
