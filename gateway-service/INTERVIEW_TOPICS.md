# Interview Topics — gateway-service

> Scope: this service specifically — Spring Cloud Gateway configuration and
> edge concerns. System-wide topics (Saga, service discovery philosophy,
> k8s) live in [../INTERVIEW_TOPICS.md](../INTERVIEW_TOPICS.md).
> Legend: `[ ]` pending · `[x]` completed

## Routing Basics
- [x] Route configuration via `application.yml` (id/uri/predicates) — skeleton routes to product/inventory/order
- [ ] Predicates beyond `Path` (Header, Method, Query, Cookie)
- [ ] Filters — request/response rewriting, `StripPrefix`, `AddRequestHeader`
- [ ] Programmatic routes (`RouteLocatorBuilder`) vs. YAML config — when each is better

## Cross-Cutting Gateway Concerns
- [ ] Global filters — inject a correlation/trace ID on every request before it fans out
- [ ] CORS configuration centralized at the gateway instead of per-service
- [ ] Authentication at the edge — validate JWT once at the gateway vs. per-service (ties to [[spring-security-workouts]])
- [ ] Rate limiting at the gateway (RequestRateLimiter + Redis)
- [ ] Circuit breaker + fallback route when a downstream service is down (Resilience4j integration)
- [ ] Response aggregation / BFF-style composition from multiple services

## Production Concerns
- [ ] Load balancing to multiple instances of the same service (client-side LB vs. k8s Service load balancing)
- [ ] Observability at the edge — per-route metrics, access logging
- [ ] Timeout configuration per route (and how it interacts with downstream Resilience4j timeouts)
- [ ] Blue-green/canary routing at the gateway layer (ties to [[devops-observability-workouts]])

## Team-Lead Scenario Bank
- [ ] "Why validate JWTs at the gateway instead of in every service?"
- [ ] "One route is getting hammered — how do you rate-limit just that route?"
- [ ] "Design a fallback response when order-service is completely down"
