# microservices-workouts

A learning e-commerce system with a React customer/admin portal and nine independent
Spring Boot services. Customers shop, save preferences and pay through a simulated
checkout; administrators create customer orders, ship and mark delivered. The
PostgreSQL records and Kafka events are real; payments and tracking are simulations.

## Start and learn

- [Documentation index and learning sequence](docs/README.md)
- [Infrastructure setup](docs/infra-setup/README.md) and [application startup](docs/infra-setup/commerce-setup.md)
- [Business flows and required services](docs/project-docs/business-flows-and-service-dependencies.md)
- [Concept-to-code guides](docs/README.md#learning-sequence) and [manual verification](docs/project-docs/manual-verification.md)
- [Keycloak credentials and configuration](docs/infra-setup/keycloak-setup.md)
- [Implemented vs future topics](Topics.md)

## Service ownership

| Service                    | Port | Owns                                                                |
| -------------------------- | ---- | ------------------------------------------------------------------- |
| gateway-service            | 9100 | JWT validation, trusted identity headers, HTTP routing              |
| order-service              | 9101 | Orders/items, durable checkout coordination, shipping, order outbox |
| payment-service            | 9102 | Idempotent simulated payments and payment outbox                    |
| notification-service       | 9103 | Customer event inbox and payment notification logging               |
| product-aggregator-service | 9104 | Product data/images, WebFlux aggregation, authoritative quotes      |
| inventory-service          | 9105 | Stock and atomic reservations                                       |
| customer-service           | 9106 | Profiles and preference history                                     |
| product-discount-service   | 9107 | Product discount lookup                                             |
| rating-service             | 9108 | Rating averages/counts                                              |

Each Java service has its own Maven project, Dockerfile and local/k8s profiles.
The root [pom.xml](pom.xml) aggregates all nine services for a single Maven/IntelliJ
import; each service remains independently buildable and deployable.
React lives in `ecom-ui` and runs locally on 5173; it uses npm, not Maven.
See [IntelliJ and Maven setup](docs/infra-setup/intellij-maven-setup.md).

## Deployment boundary

Root Compose runs **shared-infra only**: PostgreSQL, Kafka, Kafka UI, Redis and
Keycloak, OpenTelemetry Collector, Prometheus, Loki, Tempo, Grafana, Alertmanager
and PostgreSQL/Redis/Kafka metric exporters. Applications run in IntelliJ now. Future Jenkins deployments use
[application manifests](k8s/README.md) in Minikube with the same infrastructure.
No Jenkins pipeline is implemented. Redis is installed but not used by application
code. [docker/](docker/README.md) contains support files and local product images.

All application HTTP goes through gateway. Downstream services trust its headers
and apply permission/owner/tenant checks; direct local ports remain a deliberate
POC bypass. Kafka processing is independent of HTTP authentication.

## Observability

[Setup, dashboards and verification](docs/infra-setup/observability-implementation-guide.md)
cover all nine services. Build with `mvn test package`, then select the shared
`SERVICE (observable)` IntelliJ run configurations. Grafana is at
http://localhost:3000/d/commerce-overview. Learn the design and interview examples
in [logs, metrics and traces](docs/observability/01-logs-metrics-and-tracing.md).
