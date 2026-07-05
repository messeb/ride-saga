.PHONY: build up down logs demo demo-payment-failure demo-poison demo-duplicate e2e

build:
	./gradlew build

up: ## build jars + start the full stack (Kafka, registry, services, observability)
	./gradlew bootJar
	docker compose up -d --build --wait
	@echo ""
	@echo "  booking API   http://localhost:8080"
	@echo "  kafka-ui      http://localhost:8085"
	@echo "  grafana       http://localhost:3000  (dashboard: Ride Saga Overview)"
	@echo "  prometheus    http://localhost:9090"

down:
	docker compose down -v

logs:
	docker compose logs -f booking-service driver-matching-service payment-service notification-service

demo: ## request a ride and follow the saga to CONFIRMED
	./scripts/demo.sh

demo-payment-failure: ## fare >= 500 is declined -> ride CANCELLED, driver released
	./scripts/demo.sh 500.00

demo-poison: ## poison message retries, then lands in the DLT (watch in kafka-ui)
	./scripts/demo-poison.sh

demo-duplicate: ## replay delivered events via offset reset -> idempotent consumer skips them
	./scripts/demo-duplicate.sh

e2e: ## full end-to-end smoke test against the compose stack
	./scripts/e2e.sh
