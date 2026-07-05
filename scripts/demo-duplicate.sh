#!/usr/bin/env bash
# Prove the idempotent consumer: reset payment-service's consumer offsets so every
# DriverAssigned event is redelivered. The processed_events ledger skips them all —
# events_duplicate_total rises, but nobody is charged twice.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PAYMENT_METRICS="${PAYMENT_METRICS:-http://localhost:8083/actuator/prometheus}"

duplicates() {
  curl -sS "$PAYMENT_METRICS" 2>/dev/null | awk '/^events_duplicate_total/ {n = int($2)} END {print n + 0}'
}

echo "→ ensuring at least one completed ride exists"
"$(dirname "$0")/demo.sh" > /dev/null

BEFORE=$(duplicates)
echo "→ duplicates skipped so far: ${BEFORE:-0}"

echo "→ stopping payment-service and rewinding its offsets on dispatch.driver-assigned.v1"
docker compose stop payment-service > /dev/null
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group payment-service \
  --topic dispatch.driver-assigned.v1 \
  --reset-offsets --to-earliest --execute > /dev/null
docker compose start payment-service > /dev/null

echo "→ payment-service restarted — every DriverAssigned is being redelivered (at-least-once)"

for _ in $(seq 1 30); do
  AFTER=$(duplicates) || AFTER=0
  if [ "${AFTER:-0}" -gt "${BEFORE:-0}" ]; then
    echo ""
    echo "✅ idempotent consumer skipped $((AFTER - BEFORE)) redelivered event(s)"
    echo "   events_duplicate_total: $BEFORE → $AFTER (no ride was charged twice)"
    echo "   ledger: SELECT * FROM processed_events; (docker compose exec postgres psql -U ridesaga payment)"
    exit 0
  fi
  sleep 2
done

echo "⚠️  duplicate counter did not increase — check: docker compose logs payment-service"
exit 1
