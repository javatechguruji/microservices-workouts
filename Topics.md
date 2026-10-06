# Project learning roadmap

Start with [the documentation index](docs/README.md). Each concept guide explains
a business problem, actual implementation, cross-service flow, verification and
limits. This roadmap describes code status, not a claim of production readiness.

## Implemented concepts

| Concept                                    | Concrete example                                       | Owning services / guide                                                                        |
| ------------------------------------------ | ------------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| Gateway routing and trusted identity       | Browser request becomes authenticated headers          | [Gateway guides](docs/api-gateway/topic-list.md)                                    |
| RBAC, action permissions, ABAC             | Admin shipping; customer/tenant order isolation        | [Security](docs/security/Authentication%20and%20Authorization%20at%20Microservice.md)          |
| Client credentials                         | Order reserves stock as a machine                      | [Machine calls](docs/api-gateway/04-token-relay-service-to-service-security.md)             |
| Reactive aggregation and optional fallback | Personalized cards from independent owners             | [PGS/customer/discount/rating/inventory](docs/project-docs/catalog-aggregation-and-preferences.md) |
| Local transactions and service-owned data  | Orders/items vs stock vs payment                       | [Checkout](docs/project-docs/shopping-and-fulfillment.md)                                          |
| Idempotency and database concurrency       | Duplicate checkout, stock contention, repeated payment | [Checkout](docs/project-docs/shopping-and-fulfillment.md)                                          |
| Durable saga and compensation              | Reserve, pay, commit or release                        | [Order/inventory/payment](docs/project-docs/shopping-and-fulfillment.md)                           |
| Outbox and consumer deduplication          | Persistent notification and preference updates         | [Kafka](docs/kafka/kafka-notes-scenarios.md)                                                                   |
| Role modules and browser state             | Customer cart vs admin management pages                | [Frontend](docs/project-docs/frontend-modules-and-cart.md)                                         |
| Layered verification                       | Unit, MVC slice, repository and running-system checks  | [Tests](docs/testing/Junit_Test_Guide.md)                                                              |

## Defined but not a completed deployment

Dockerfiles, local/k8s profiles, application Deployments/Services, secrets and health
probes exist. Infrastructure runs separately through Compose. Jenkins pipeline,
registry workflow and a verified Minikube rollout remain future work; see
[deployment configuration](docs/infra-setup/minikube-setup.md).

## Future exercises

- Redis caching/rate limiting with explicit keys, invalidation and outage policy.
- Circuit breakers, bounded retry escalation, poison-event/DLQ handling and replay.
- Publisher coordination, schema evolution, outbox cleanup and migration tooling.
- Production observability extensions: durable trace-context links across outboxes, SLO-based paging and HA storage.
  Local logs, metrics, tracing, backlog gauges and alerts are [implemented](docs/infra-setup/observability-implementation-guide.md).
- NetworkPolicy/mTLS or downstream token verification; real secret management.
- Jenkins automation, image tagging, HPA/resource budgets, Ingress/TLS and rollout checks.
- Real payments/refunds, cancellation, carrier integration, search/pagination and review writing.
- Production database/broker redundancy and disaster recovery.

These are not existing features. When adding one, update its owning concept guide
with actual source excerpts and verification rather than adding another duplicate
tutorial or marking an untested architecture diagram as complete.
