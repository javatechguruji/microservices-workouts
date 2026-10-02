# API gateway interview topic list

This is the topic selection and reading-order index. The foundational guides use consecutive
prefixes **01–06**, and the selected CORS guide retains its reserved prefix **09**. Topics 06 and 09 are now available; the remaining proposed interview
guides reserve **07–08 and 10–15** and will be written after selection. A proposed topic is not an implemented feature.

## Existing guides

| Prefix | Topic                                                          | Guide and project status                                                                                                                                          |
| ------ | -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 01     | Basic routing and gateway request flow                         | [Basic routing](01-basic-routing.md): path routes and local/k8s destinations are implemented.                                                                     |
| 02     | Predicates and filters                                         | [Predicates and filters](02-predicates-and-filters.md): Path predicates and trusted identity-header replacement are implemented.                                  |
| 03     | Gateway security: OAuth2, OIDC and JWT                         | [Gateway security](03-gateway-security-oauth2-oidc-jwt.md): browser PKCE, JWT validation and downstream identity are implemented.                                 |
| 04     | Service-to-service security: client credentials vs token relay | [Service security](04-token-relay-service-to-service-security.md): client credentials through gateway is implemented; user-token relay is not.                    |
| 05     | Redis rate limiting and traffic protection                     | [Rate limiting](05-rate-limiting-redis.md): a future exercise; shared Redis exists, but gateway rate limiting is not implemented.                                 |
| 06     | Timeouts, Safe Retries and Idempotency                         | [Timeouts and retry safety](06-timeouts-safe-retries-and-idempotency.md): service timeouts and checkout/payment idempotency exist; gateway policies are proposed. |

## Additional interview topics

The previous selection list's topics 1–10 correspond to prefixes 06–15 here.
Start with **06, 07, 08 and 10** for resilience, observability and reactive execution.

| Prefix | Topic name                                                                                 | Purpose or interview scenario                                                                   | Current project connection                                                                                                                                |
| ------ | ------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 07     | Circuit Breakers and Fallback Strategies                                                   | Stop repeatedly calling a failing dependency and choose which degraded responses remain valid.  | PGS has optional ratings/stock-preview fallbacks; no circuit breaker is configured.                                                                       |
| 08     | Gateway Observability: Logs, Metrics, Correlation IDs and Distributed Tracing              | Locate failures and latency across browser, gateway and downstream calls.                       | Actuator exists; end-to-end tracing and custom correlation propagation are not implemented.                                                               |
| 09     | [CORS, Preflight Requests and Browser Security](09-cors-preflight-and-browser-security.md) | Explain browser access when the frontend and API use different origins.                         | Implemented: React calls gateway directly; gateway validates origins and handles preflight before JWT authentication.                                     |
| 10     | Reactive Gateway: WebFlux, Event Loops and Blocking Calls                                  | Explain concurrent I/O and why blocking an event loop harms throughput.                         | Gateway is reactive; PGS uses WebClient and schedules blocking JDBC work on boundedElastic.                                                               |
| 11     | API Gateway vs Ingress vs Load Balancer vs BFF                                             | Explain which layer owns routing, exposure, load distribution and response composition.         | Gateway, Kubernetes Service manifests and PGS exist; Ingress is not configured. PGS is a domain aggregator, not automatically a separate UI-specific BFF. |
| 12     | Gateway Error Handling and HTTP Status Codes                                               | Distinguish invalid authentication, denied access, business conflicts and dependency failures.  | Gateway/security checks and service-client error mappings exist; no uniform gateway-wide custom error contract is implemented.                            |
| 13     | Gateway Trust Boundaries and Defense in Depth                                              | Explain how header spoofing is prevented through gateway and what happens if callers bypass it. | Gateway replaces identity headers; direct downstream ports remain a POC bypass. NetworkPolicy/mTLS and downstream JWT validation are absent.              |
| 14     | API Versioning and Backward-Compatible Routing                                             | Evolve APIs without unexpectedly breaking existing consumers.                                   | Current routes are unversioned; no version-routing policy is configured.                                                                                  |
| 15     | Request Size Limits, Connection Pools and Traffic Protection                               | Bound resource use so large requests or too many concurrent calls do not exhaust services.      | PGS bounds its connection pool and buffered responses; gateway-specific request-size policies are not configured.                                         |

Numbers identify guide filenames as well as the reading order. No empty
placeholder guides are created for unselected topics.

## Structure for each selected guide

1. **Topic name** and implementation status.
2. **Purpose / problem it solves**, using a business scenario from this application.
3. **Concept details** and relevant tradeoffs.
4. **Where it is implemented**: microservices, source/configuration links and excerpts
   from both sides of a cross-service interaction.
5. **How it works**: a diagram and explanation following the actual request/event flow.
6. **How to test it**: required running services, tools, requests, expected outcomes
   and failure cases. Link to setup instructions instead of duplicating them.
7. **Summary**: key lessons and implementation limitations.
8. **Interview explanation**: a simple-English answer suitable for 3–4 minutes.

For unimplemented features, the guide will distinguish existing code from a proposed
example. Selecting a documentation topic does not itself add that feature to the codebase.

## Related guides

- [Documentation index](../README.md)
- [Setup and configuration](../infra-setup/README.md)
- [Kubernetes service discovery](../kubernetes/service-discovery-and-routing.md)
- [Kubernetes load balancing and scaling](../kubernetes/load-balancing-and-scaling.md)
- [Checkout and idempotency](../project-docs/shopping-and-fulfillment.md)
- [PGS aggregation and fallbacks](../project-docs/catalog-aggregation-and-preferences.md)
