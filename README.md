# microservices-workouts

A small e-commerce system, one service per top-level folder, built to
practice real microservices concerns end-to-end. See `INTERVIEW_TOPICS.md`
for the full topic checklist and `k8s/` for the Kubernetes-native
infrastructure story (no Eureka, no Config Server).

| Service | Port | Responsibility |
|---|---|---|
| `gateway-service` | 8000 | API Gateway (Spring Cloud Gateway) — routes to `order-service` |
| `product-service` | 8081 | Product catalog (H2 for now, MongoDB later) |
| `inventory-service` | 8082 | Stock/inventory (H2) |
| `order-service` | 9091 | Orders — Postgres (`order-srv-db`) |
| `payment-service` | 8083 | Payments — calls `order-service` to confirm an order exists before accepting payment; Postgres (`payment-srv-db`); publishes `payment-completed` to Kafka |
| `notification-service` | 8084 | Sends notifications — no DB; consumes `payment-completed` from Kafka |

Each service is an independent Spring Boot Maven project (own `pom.xml`,
own `Application` class) — no multi-module reactor build, so each can be
run, tested, and eventually deployed independently, same as they would be
in production.

`order-service` and `payment-service` need Postgres. It runs in Docker via
`docker-compose.yml` at the repo root — start it once with
`docker compose up -d`. Kubernetes deployment for the microservices
themselves goes through plain `kubectl apply -f k8s/...`, not
docker-compose — see `k8s/README.md` for why Postgres deliberately lives
outside the cluster. See `docs/kafka-notes.md` for the full
order → payment → Kafka → (order, notification) event flow.

Run any service locally with `./mvnw spring-boot:run` from its folder, or
see `k8s/` for manifests.
