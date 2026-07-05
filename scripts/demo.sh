#!/usr/bin/env bash
# Request a ride and follow the choreographed saga to its outcome.
# Usage: demo.sh [fare]   — fares >= 500.00 are declined by payment-service.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
FARE="${1:-23.50}"
CORRELATION_ID="demo-$(date +%s)"

echo "→ requesting ride (fare $FARE EUR, correlation id $CORRELATION_ID)"
RESPONSE=$(curl -fsS -X POST "$BASE_URL/api/rides" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -d "{
        \"riderId\": \"rider-42\",
        \"pickupLocation\": \"Alexanderplatz\",
        \"dropoffLocation\": \"Kreuzberg\",
        \"fareAmount\": $FARE,
        \"currency\": \"EUR\"
      }")

RIDE_ID=$(echo "$RESPONSE" | jq -r .rideId)
echo "→ ride $RIDE_ID requested, waiting for the saga..."

for _ in $(seq 1 30); do
  STATUS=$(curl -fsS "$BASE_URL/api/rides/$RIDE_ID" | jq -r .status)
  case "$STATUS" in
    CONFIRMED)
      curl -fsS "$BASE_URL/api/rides/$RIDE_ID" | jq .
      echo ""
      echo "✅ ride CONFIRMED — RideRequested → DriverAssigned → PaymentCompleted → RideConfirmed"
      echo "   trace:  http://localhost:3000/explore (Tempo, search service.name=booking-service)"
      echo "   topics: http://localhost:8085"
      echo "   logs:   docker compose logs | grep $CORRELATION_ID"
      exit 0
      ;;
    CANCELLED)
      curl -fsS "$BASE_URL/api/rides/$RIDE_ID" | jq .
      echo ""
      echo "🛑 ride CANCELLED — compensation ran (driver released, rider notified)"
      exit 0
      ;;
  esac
  sleep 1
done

echo "⚠️  saga did not finish within 30s — check: docker compose logs"
exit 1
