# payment-service

Payment microservice — part of the [microservices-workouts](../README.md) e-commerce system. Runs on port 8083.

Before taking a payment, it calls `order-service`'s `GET /api/orders/{id}` to confirm the order actually exists — a payment can't be created for an order that isn't there. See `docs/kafka-notes.md` for the full order → payment → Kafka flow.

## Run locally
```bash
docker compose up -d          # starts Postgres (repo root)
./mvnw spring-boot:run         # from this directory
```

## Endpoints
- `POST /payments` — `{"orderId": 1, "amount": 250.00}`
- `GET /payments/{id}`
