#!/usr/bin/env bash
# End-to-end smoke test: full stack up, happy path confirmed, compensation path
# cancelled, poison message dead-lettered.
set -euo pipefail
cd "$(dirname "$0")/.."

cleanup() {
  if [ "${1:-}" != "ok" ]; then
    echo "==> e2e failed — last service logs:"
    docker compose logs --tail 60 booking-service driver-matching-service payment-service notification-service || true
  fi
  docker compose down -v > /dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> building jars"
./gradlew --console=plain -q bootJar

echo "==> starting the stack"
docker compose up -d --build --wait

echo "==> waiting for all consumer groups to be stable"
for group in booking-service driver-matching-service payment-service notification-service; do
  for _ in $(seq 1 60); do
    state=$(docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 --describe --group "$group" --state 2>/dev/null \
      | awk 'NR==2 {print $5}') || state=""
    [ "$state" = "Stable" ] && break
    sleep 2
  done
  echo "    $group: ${state:-unknown}"
done

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
trap 'cleanup ok' EXIT
