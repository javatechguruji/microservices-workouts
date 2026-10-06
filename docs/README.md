# Learn the e-commerce project

There are two kinds of documentation. **Setup guides** explain how to install,
configure and start the system. **Concept guides** explain why the code exists,
which service owns it, and how to observe the behavior. Read the business flow
first, then follow the concept's source links.

## Setup and configuration

Start with [shared infrastructure](infra-setup/README.md), then
[application setup](infra-setup/commerce-setup.md). Credentials, client registrations
and role assignments have one home: [Keycloak setup](infra-setup/keycloak-setup.md).
Do not paste the shortened concept snippets over complete application files.
Developer tooling has a separate [Codex account setup guide](codex-chatgpt-pro-setup.md);
it is unrelated to the application's Keycloak accounts.

## Learning sequence

Use the [API gateway topic list](api-gateway/topic-list.md) to choose additional
interview guides and see their current implementation status.
Topic 06: [Timeouts, safe retries and idempotency](api-gateway/06-timeouts-safe-retries-and-idempotency.md)
traces failure recovery across the actual service clients and checkout participants.

| Order | Concept and project problem                                             | Read                                                                                                                   |
| ----- | ----------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| 1     | Which services participate in each customer/admin action?               | [Business flows and running services](project-docs/business-flows-and-service-dependencies.md)                         |
| 2     | How does one public API reach the correct service?                      | [Routing](api-gateway/01-basic-routing.md), [predicates and filters](api-gateway/02-predicates-and-filters.md)         |
| 3     | How does login become a trusted request?                                | [UI → gateway authentication](api-gateway/03-gateway-security-oauth2-oidc-jwt.md)                                      |
| 4     | Why can't a customer read or ship someone else's order?                 | [Downstream RBAC, permissions and ABAC](security/Authentication%20and%20Authorization%20at%20Microservice.md)          |
| 5     | How does order-service call inventory securely?                         | [Machine authentication](api-gateway/04-token-relay-service-to-service-security.md)                                    |
| 6     | How do profiles, prices, ratings and stock become one catalog response? | [WebFlux aggregation](project-docs/catalog-aggregation-and-preferences.md)                                             |
| 7     | How do we avoid duplicate orders, double payments and overselling?      | [Checkout, idempotency and inventory](project-docs/shopping-and-fulfillment.md)                                        |
| 8     | How do notifications and recommendations survive restarts?              | [Kafka, outbox and consumer deduplication](kafka/kafka-notes-scenarios.md)                                             |
| 9     | Why are customer/admin pages separate while sharing authentication?     | [Frontend modules](project-docs/frontend-modules-and-cart.md)                                                          |
| 10    | How will the same code find services in Minikube?                       | [Kubernetes routing](kubernetes/service-discovery-and-routing.md), [scaling](kubernetes/load-balancing-and-scaling.md) |
| 11    | Which test proves which part?                                           | [Testing through project examples](testing/Junit_Test_Guide.md)                                                        |

[Manual verification](project-docs/manual-verification.md) provides one shared business
walkthrough. Security-specific negative tests remain in the security guide.
[Future topics](../Topics.md) distinguish planned work from code that exists.

## Observability: logs, metrics, traces and alerts

Start with [logs, metrics and tracing](observability/01-logs-metrics-and-tracing.md),
then read the [setup and operations guide](infra-setup/observability-implementation-guide.md).
The shared-infra stack and all nine services are instrumented; the setup guide includes repeatable live checks.

## Reading conventions

- **Implemented** means present in the repository; it does not assert a live deployment.
- Source-linked snippets are excerpts of the named files. Omitted surrounding code
  includes constructors, validation or error paths; use the source for the full contract.
- **Future exercise** means no current implementation. In particular, Redis rate
  limiting, circuit breakers, HPA, Ingress/TLS and Jenkins are not configured.
- Diagrams show logical calls, not separate physical databases/servers for every box.
  PostgreSQL is shared infrastructure with service-owned databases.
- Shopping checkout and the older amount-only payment lab are different flows.
  Only the older lab waits for a Kafka payment event to confirm an order.

Markdown viewers need Mermaid support to render diagrams. On unsupported viewers,
the code block remains readable. These documents describe the checked-in setup,
not a claim that every service or Minikube is currently running.

Browser API access: [09 — CORS, preflight and browser security](api-gateway/09-cors-preflight-and-browser-security.md).

- [Order-service Swagger only](api/order-service-swagger.md): optional read-only API reference.
