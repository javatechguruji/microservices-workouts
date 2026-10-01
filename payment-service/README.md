# payment-service

Payment microservice — part of the [microservices-workouts](../README.md) e-commerce system. Runs on port 9102.

Before taking a payment, it calls `order-service`'s `GET /api/orders/{id}` to confirm the order actually exists — a payment can't be created for an order that isn't there. See `docs/kafka-notes.md` for the full order → payment → Kafka flow.

## Run locally
```bash
docker compose up -d          # starts Postgres (repo root)
./mvnw spring-boot:run         # from this directory
```

## Endpoints
- `POST /payments` — `{"orderId": 1, "amount": 250.00}`
- `GET /payments/{id}`


## Security

Call application APIs through the gateway on port 9100 with a Keycloak access
token. The gateway validates JWTs and derives trusted `X-Auth-*` headers;
downstream controllers enforce roles, permissions and resource policies.
See [authentication and authorization](../docs/security/Authentication%20and%20Authorization%20at%20Microservice.md)
for browser login, machine calls and direct-header POC testing.
