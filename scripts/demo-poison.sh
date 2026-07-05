#!/usr/bin/env bash
# Send a poison ride request: driver-matching fails on every attempt, the message walks
# through the retry topics and ends in the dead-letter topic — without blocking other traffic.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DLT_TOPIC="booking.ride-requested.v1.dlt"

echo "→ requesting ride with pickupLocation=POISON"
RIDE_ID=$(curl -fsS -X POST "$BASE_URL/api/rides" \
  -H "Content-Type: application/json" \
  -d '{
        "riderId": "rider-42",
        "pickupLocation": "POISON",
        "dropoffLocation": "Kreuzberg",
        "fareAmount": 23.50,
        "currency": "EUR"
      }' | jq -r .rideId)

echo "→ ride $RIDE_ID published — driver-matching will fail 4 times (backoff 1s/2s/4s)"
echo "→ waiting for the message to reach $DLT_TOPIC ..."

for _ in $(seq 1 24); do
  COUNT=$(docker compose exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh \
    --bootstrap-server localhost:9092 --topic "$DLT_TOPIC" 2>/dev/null \
    | awk -F: '{sum += $3} END {print sum+0}') || COUNT=0
  if [ "${COUNT:-0}" -gt 0 ]; then
    echo ""
    echo "☠️  $COUNT message(s) in $DLT_TOPIC"
    echo "   inspect payload + error headers: http://localhost:8085 (topic $DLT_TOPIC)"
    echo "   dlq_messages_total metric:       http://localhost:3000 (Ride Saga Overview)"
    exit 0
  fi
  sleep 5
done

echo "⚠️  no message arrived in the DLT within 2 minutes — check: docker compose logs driver-matching-service"
exit 1
