# microservices-workouts

A small e-commerce system, one service per top-level folder, built to
practice real microservices concerns end-to-end. See `INTERVIEW_TOPICS.md`
for the full topic checklist and `k8s/` for the Kubernetes-native
application deployment setup (no Eureka, no Config Server).

All microservice ports follow one `91xx` sequence, easy to remember —
`9092`/`9094` (Kafka) and `9090` (reserved for Prometheus, not yet added)
are deliberately skipped, see `k8s/README.md`.

| Service | Port | Responsibility |
|---|---|---|
| `gateway-service` | 9100 | API Gateway (Spring Cloud Gateway) — routes to `order-service` |
| `order-service` | 9101 | Orders — Postgres (`order-srv-db`) |
| `payment-service` | 9102 | Payments — calls `order-service` to confirm an order exists before accepting payment; Postgres (`payment-srv-db`); publishes `payment-completed` to Kafka |
| `notification-service` | 9103 | Sends notifications — no DB; consumes `payment-completed` from Kafka |
| `product-service` | 9104 | Product catalog (H2 for now, MongoDB later) |
| `inventory-service` | 9105 | Stock/inventory (H2) |

Each service is an independent Spring Boot Maven project (own `pom.xml`,
own `Application` class) — no multi-module reactor build, so each can be
run, tested, and eventually deployed independently, same as they would be
in production.

## Infrastructure and application deployment

`docker-compose.yml` defines the **shared-infra** project and contains only
Postgres, Kafka, Kafka UI, Redis, and Keycloak. The same infrastructure serves IntelliJ
applications and Minikube applications. No microservice belongs in Compose.

Supporting files are organized by purpose:

- `docker/postgres/init-databases.sql`: Compose-mounted Postgres initialization
  script, executed only when the database volume is first initialized.
- [docs/infra-setup/](docs/infra-setup/README.md): installation, configuration,
  and microservice usage guides for Postgres, Kafka, Kafka UI, Redis, and Keycloak.
- `k8s/`: application manifests for the future Jenkins deployment.

For a fresh Docker environment, create the external volumes once, then start
infrastructure from the repository root:

```bash
docker volume create workouts-postgres-data
docker volume create workouts-kafka-data
docker compose up -d
```

Run microservices from IntelliJ with the `local` Spring profile. Alternatively,
run `mvn spring-boot:run -Dspring-boot.run.profiles=local` in a service folder.
All six services have `local` and `k8s` configuration files, Dockerfiles, and
application manifests. Product and inventory retain embedded H2; their staging
Pod storage is temporary and resets when a Pod is replaced.

Later, **Jenkins will build and deploy microservices to Minikube**, using the
application manifests in `k8s/` and the `k8s` Spring profile. Jenkins is not
implemented yet. Existing Dockerfiles are application image build inputs for
that future pipeline; they do not add microservices to Docker Compose.

| Dependency | IntelliJ (`local`) | Minikube (`k8s`) |
| --- | --- | --- |
| Postgres | `localhost:5432` | `host.minikube.internal:5432` |
| Kafka | `localhost:9092` | `host.minikube.internal:9094` |
| Redis | `localhost:6379` | `host.minikube.internal:6379` |
| Keycloak token endpoint host | `localhost:8180` | `host.minikube.internal:8180` |

Redis application integration is documented but not yet added to service
code. See [Redis setup](docs/infra-setup/redis-setup.md) for its password and profile
examples, [Kubernetes deployment contract](k8s/README.md) for the Jenkins
boundary, and [Kafka notes](docs/kafka-notes.md) for the event flow.


## Authentication and authorization

All application HTTP calls enter the gateway on port 9100. The gateway validates
Keycloak JWTs and forwards trusted identity headers; downstream services enforce
roles, permissions, ownership and tenant. Start all services locally, run the
[React frontend](ecom-ui/README.md) (`npm ci` then `npm run dev` in `ecom-ui`), and open
[the order workspace](http://localhost:5173/) for customer/admin dashboards,
order creation, details and simulated payments, with real Keycloak PKCE login. See [the security guide](docs/security/Authentication%20and%20Authorization%20at%20Microservice.md)
for demo users, policy examples, direct-header POC simulations and automated tests.
