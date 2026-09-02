# API Gateway — Topics

> **Parent Curriculum:** `microservices-workouts/Topics.md`  
> **Area:** 01 — API Gateway  
> **Primary Technology:** Spring Cloud Gateway  
> **Local Runtime:** Kubernetes / Minikube  
> **Production Mapping:** AWS EKS + ALB/WAF/Route 53  
> **Example Domain:** E-Commerce — Gateway, Order, Payment, Inventory  
> **Learning Goal:** Implementation → Kubernetes → Production Scenarios → Architecture Trade-offs → Interview Readiness

---

# Status Legend

| Status | Meaning |
|---|---|
| ⬜ | Not Started |
| 🟡 | Learning |
| 🟢 | Implemented / Practiced |
| ⭐ | Interview Ready |

> A topic becomes **⭐ Interview Ready** only when I can explain the concept, implement it, troubleshoot failures, discuss Kubernetes/EKS behavior, compare alternatives, and defend the architecture decision without depending on notes.

---

# API Gateway Learning Philosophy

The goal is **not** to memorize Spring Cloud Gateway YAML.

The goal is to understand:

```text
WHY do we need a gateway?
        ↓
WHAT responsibilities should it own?
        ↓
HOW does Spring Cloud Gateway implement them?
        ↓
HOW does it work inside Kubernetes?
        ↓
WHAT happens when dependencies fail?
        ↓
HOW do we scale and secure it?
        ↓
HOW would this architecture run on AWS EKS?
        ↓
WHAT alternatives exist?
        ↓
CAN I defend the decision in an architect interview?
```

Our implementation evolves gradually.

We will **not** build all features at once.

---

# Target Architecture

The API Gateway exercises will gradually evolve toward:

```text
                           Internet
                              |
                           Route 53
                              |
                             WAF
                              |
                             ALB
                              |
                  +-----------+-----------+
                  |                       |
             Gateway Pod 1           Gateway Pod 2
                  |                       |
                  +-----------+-----------+
                              |
                    Kubernetes Services
                    /         |          \
                   /          |           \
              Order        Payment      Inventory
              Service      Service       Service
                |             |             |
              Pods          Pods          Pods
```

Supporting components will be introduced only when required:

```text
Keycloak / Identity Provider
Redis
OpenTelemetry
Kafka
Kubernetes ConfigMaps / Secrets
```

Local environment:

```text
Mac
 |
Minikube
 |
Kubernetes
```

Production mapping:

```text
AWS
 |
EKS
```

---

# API Gateway Topic Dashboard

| # | Topic | Status |
|---|---|---|
| 01 | Basic Routing & Gateway Request Flow | ⬜ |
| 02 | Route Predicates & Gateway Filters | ⬜ |
| 03 | Kubernetes Routing & Service Discovery | ⬜ |
| 04 | Load Balancing, Scaling & High Availability | ⬜ |
| 05 | Gateway Security — OAuth2, OIDC & JWT | ⬜ |
| 06 | Token Relay & Service-to-Service Security | ⬜ |
| 07 | Rate Limiting with Redis | ⬜ |
| 08 | Timeout, Retry & Circuit Breaker | ⬜ |
| 09 | Idempotency & Safe Retries | ⬜ |
| 10 | Observability — Tracing, Metrics & Logging | ⬜ |
| 11 | CORS, Headers & Request Policies | ⬜ |
| 12 | Kubernetes Production Deployment | ⬜ |
| 13 | AWS EKS Production Architecture | ⬜ |
| 14 | Gateway vs Ingress vs ALB vs Service Mesh vs BFF | ⬜ |
| 15 | Failure Scenarios & Production Troubleshooting | ⬜ |
| 16 | Architect Interview & System Design Scenarios | ⬜ |
| 17 | Final Revision & Interview Readiness | ⬜ |

---

# 01. Basic Routing & Gateway Request Flow

**Detailed document:** `01-basic-routing.md`

## Goal

Understand the minimum working API Gateway before introducing security, service discovery, resilience, Redis, or other infrastructure.

## Concepts

- What problem does an API Gateway solve?
- API Gateway as an edge entry point
- North-south traffic
- Client → Gateway → Service request flow
- Route
- Route ID
- Destination URI
- Path-based routing
- Static routing
- Gateway request lifecycle
- How Spring Cloud Gateway selects a route
- External API path vs internal service path
- Gateway port vs service port
- Why clients should not know backend topology
- Why backend services should not normally be publicly exposed
- Gateway as reverse proxy
- Basic Spring Cloud Gateway project structure
- Spring Cloud Gateway WebFlux vs Web MVC awareness
- Choosing the gateway programming model

## Initial Architecture

```text
Client
   |
   | GET /api/orders/101
   v
Gateway :8000
   |
   | route match
   v
Order Service
```

## Hands-On

- Create minimal Gateway configuration
- Create one Order endpoint
- Route request through Gateway
- Verify direct service vs Gateway access
- Change destination incorrectly
- Observe failure
- Fix routing
- Understand common 404/5xx routing failures

## Architect Questions

- Why introduce a Gateway?
- What are its disadvantages?
- Does every microservices system need one?
- Is Gateway the same as a load balancer?
- What happens if Gateway becomes unavailable?

---

# 02. Route Predicates & Gateway Filters

**Detailed document:** `02-predicates-and-filters.md`

## Goal

Understand how the Gateway decides **which route matches** and **what processing should happen** before and after forwarding.

## Predicates

- Path predicate
- HTTP Method predicate
- Header predicate
- Query parameter predicate
- Host predicate
- Cookie predicate — awareness
- Remote address predicate
- Time-based predicates — awareness
- Combining predicates
- Route matching order
- Overlapping routes
- Route precedence

Mental model:

```text
Predicate = SHOULD this route handle the request?
```

## Filters

- Pre-filter
- Post-filter
- Route-specific filter
- Global filter
- Built-in filters
- Custom filters
- AddRequestHeader
- RemoveRequestHeader
- AddResponseHeader
- RemoveResponseHeader
- StripPrefix
- PrefixPath
- RewritePath
- SetPath
- Request/response modification
- Filter ordering
- When to create a custom filter
- When NOT to create a custom filter

Mental model:

```text
Filter = WHAT should happen to the request/response?
```

## Hands-On

Implement:

```text
/api/orders/**
/api/payments/**
/api/inventory/**
```

Add useful filters and intentionally create route conflicts.

## Architect Scenarios

- Two routes match the same path.
- Public URL differs from internal URL.
- Client sends an internal-only header.
- A business rule is requested inside a Gateway filter.

---

# 03. Kubernetes Routing & Service Discovery

**Detailed document:** `03-kubernetes-routing-service-discovery.md`

## Goal

Move from beginner-style hard-coded service locations to Kubernetes-native service discovery.

## Concepts

- Why hard-coded ports do not work in production
- Dynamic Pods
- Pod IP lifecycle
- Kubernetes DNS
- Kubernetes Service
- ClusterIP
- Service selectors
- EndpointSlices
- Service DNS names
- Namespace-aware DNS
- Gateway → Kubernetes Service
- Kubernetes Service → Pod
- Internal service discovery
- Why Gateway does not need individual Pod addresses
- Kubernetes-native load balancing
- Spring Cloud LoadBalancer vs Kubernetes Service
- Eureka awareness
- Why Eureka is commonly unnecessary in EKS
- Client-side vs server-side discovery
- Headless Services — awareness
- Service discovery failure scenarios

## Architecture

```text
Gateway Pod
    |
    | http://order-service
    v
Kubernetes Service
    |
    +------> Order Pod 1
    |
    +------> Order Pod 2
```

## Hands-On

- Deploy Order Service to Minikube
- Create ClusterIP Service
- Deploy Gateway
- Route using Kubernetes Service name
- Scale Order Pods
- Delete an Order Pod
- Observe Kubernetes recovery
- Verify Gateway continues routing

## Interview Scenarios

- Why not Eureka in EKS?
- What happens when Pod IP changes?
- How does Gateway locate Order Service?
- What happens if a Kubernetes Service has zero endpoints?

---

# 04. Load Balancing, Scaling & High Availability

**Detailed document:** `04-load-balancing-scaling-ha.md`

## Goal

Understand how the Gateway itself and backend services remain available under load and failures.

## Concepts

- Horizontal scaling
- Stateless Gateway design
- Multiple Gateway replicas
- Kubernetes Service load balancing
- Backend service replicas
- External vs internal load balancing
- Gateway bottleneck risk
- Single point of failure
- Readiness
- Liveness
- Startup probes
- Rolling restarts
- Pod failure
- Node failure concepts
- Multi-AZ production mapping
- Resource requests
- Resource limits
- Horizontal Pod Autoscaler
- CPU-based scaling
- Request/latency/custom metric scaling awareness
- Connection capacity
- Capacity planning
- Gateway saturation
- Noisy route problem
- Blast radius

## Architecture

```text
                    Load Balancer
                         |
              +----------+----------+
              |                     |
         Gateway Pod 1         Gateway Pod 2
              |                     |
              +----------+----------+
                         |
                  Order Service
                   /         \
              Order Pod 1  Order Pod 2
```

## Hands-On

- Run multiple Gateway replicas
- Run multiple Order replicas
- Kill one Gateway Pod
- Kill one Order Pod
- Observe recovery
- Test rolling update
- Inspect readiness behavior

## Architect Scenarios

- Gateway becomes bottleneck.
- One backend route consumes most connections.
- One Gateway Pod crashes during peak traffic.
- Traffic grows 10x.

---

# 05. Gateway Security — OAuth2, OIDC & JWT

**Detailed document:** `05-gateway-security-jwt.md`

## Goal

Secure external API access without making the Gateway the only security boundary.

## Concepts

- Authentication vs authorization
- OAuth2
- OpenID Connect
- Identity Provider
- Authorization Server
- Resource Server
- JWT
- JWT structure
- Claims
- Roles
- Scopes
- Issuer
- Audience
- Signature validation
- Public keys / JWK concepts
- Token expiry
- Gateway as OAuth2 Resource Server
- Spring Security WebFlux concepts
- Public routes
- Protected routes
- Role-based coarse authorization
- Domain authorization
- Defense in depth
- Zero Trust
- Gateway validation + service validation
- Why `permitAll()` services are dangerous
- Network-level restrictions
- Key rotation awareness

## Architecture

```text
                 Identity Provider
                       ^
                       |
                     Login
                       |
Client ---- JWT ----> Gateway
                       |
                 Validate JWT
                       |
                       v
                  Order Service
                       |
                 Validate/Authorize
```

## Hands-On

- Deploy/use Keycloak for local lab
- Protect Gateway routes
- Allow health endpoint
- Protect customer API
- Protect admin API
- Test valid token
- Test expired/invalid token
- Test missing token
- Verify service authorization remains active

## Architect Scenarios

- Gateway validates JWT. Should Order validate again?
- Identity Provider becomes unavailable.
- Signing key rotates.
- User has valid token but attempts another customer's order.

---

# 06. Token Relay & Service-to-Service Security

**Detailed document:** `06-token-relay-service-to-service-security.md`

## Goal

Understand identity propagation across Gateway and downstream services.

## Concepts

- Token relay
- User access token
- Service identity
- Client Credentials
- User token propagation
- Service token
- Delegated identity
- Service-to-service authorization
- OAuth client
- Scopes
- Least privilege
- Token exchange awareness
- Trusted internal headers
- Why identity should not be reconstructed from spoofable headers
- Service account concepts in Kubernetes vs application identity
- mTLS awareness
- Zero Trust between services

## Architecture A — User Token

```text
Client
  |
User JWT
  v
Gateway
  |
User JWT
  v
Order
```

## Architecture B — Service Identity

```text
Order
  |
Client Credentials
  v
Identity Provider
  |
Service Token
  v
Inventory
```

## Architect Scenarios

- When should Order propagate user JWT?
- When should Order use its own service token?
- Should internal services trust `X-User-Id`?
- How do we enforce least privilege?

---

# 07. Rate Limiting with Redis

**Detailed document:** `07-rate-limiting-redis.md`

## Goal

Protect downstream systems and enforce fair API consumption.

## Concepts

- Why rate limiting
- Token bucket
- Replenish rate
- Burst capacity
- Requested tokens
- HTTP 429
- Redis-backed distributed state
- Multi-Gateway replica problem
- Key resolver
- Rate limit by IP
- Rate limit by user
- Rate limit by tenant
- Rate limit by OAuth client
- Per-route limits
- Global limits
- Business-risk-based limits
- WAF vs Gateway vs service rate limiting
- Fail-open
- Fail-closed
- Redis availability
- Redis latency
- Hot keys
- Distributed quota challenges
- Multi-region rate limits

## Architecture

```text
                     Redis
                       ^
                       |
Client -----------> Gateway
                    /   \
             Rate Limit  \
                         Service
```

## Hands-On

- Deploy Redis in Minikube
- Configure Gateway rate limiter
- Create KeyResolver
- Generate burst traffic
- Observe HTTP 429
- Test multiple Gateway replicas
- Simulate Redis failure

## Architect Scenarios

- Redis is down. Allow or reject requests?
- SaaS tenant requires different quota.
- One IP represents thousands of corporate users.
- Global multi-region quota is required.

---

# 08. Timeout, Retry & Circuit Breaker

**Detailed document:** `08-timeout-retry-circuit-breaker.md`

## Goal

Prevent slow or unavailable downstream services from exhausting Gateway resources.

## Concepts

- Network timeout
- Connection timeout
- Response timeout
- End-to-end timeout budget
- Retry
- Bounded retry
- Exponential backoff
- Jitter
- Retry amplification
- Circuit breaker
- Closed
- Open
- Half-open
- Failure threshold
- Slow-call threshold
- Recovery
- Fallback
- Graceful degradation
- Resilience4j
- Spring Cloud CircuitBreaker
- Caller-boundary resilience
- Gateway resilience vs service resilience
- Cascading failures

## Architecture

```text
Gateway
   |
Timeout
   |
Circuit Breaker
   |
Payment
```

## Hands-On

- Make Payment slow
- Configure timeout
- Observe failure
- Add circuit breaker
- Stop Payment
- Observe open circuit
- Restore Payment
- Observe recovery
- Add only a safe retry example

## Architect Scenarios

- Gateway retries three times and Order retries three times.
- Payment takes 20 seconds.
- Payment is down for five minutes.
- Should Gateway provide fallback payment success?

---

# 09. Idempotency & Safe Retries

**Detailed document:** `09-idempotency-safe-retries.md`

## Goal

Understand why resilience mechanisms can create duplicate business actions.

## Concepts

- Idempotent operation
- HTTP method semantics
- Safe vs unsafe retry
- Duplicate POST
- Idempotency-Key
- Deduplication
- Database uniqueness
- Request fingerprint
- Stored result
- Concurrent duplicate requests
- Idempotency expiration
- Payment duplicate prevention
- Retry after lost response
- Gateway retry vs service retry
- Exactly-once misconceptions

## Scenario

```text
Client
  |
POST /payments
  |
Gateway
  |
Payment charges $100
  |
Response lost
  X
Gateway retries
  |
Potential second charge
```

## Hands-On

Implement a basic idempotency approach for Payment and prove that a retry does not create a second transaction.

## Architect Scenarios

- When is POST retry safe?
- Where should idempotency be implemented?
- Can Gateway alone guarantee idempotency?
- How long should an idempotency record live?

---

# 10. Observability — Tracing, Metrics & Logging

**Detailed document:** `10-observability-tracing-logging.md`

## Goal

Trace and diagnose a request across Gateway and services.

## Concepts

- Structured logging
- Correlation ID
- Trace ID
- Span ID
- Distributed tracing
- Micrometer
- OpenTelemetry
- Context propagation
- Gateway span
- Downstream spans
- Metrics
- Request count
- Route latency
- p50/p95/p99
- HTTP status metrics
- Timeout metrics
- Circuit breaker metrics
- Rate-limit metrics
- Active connections
- RED metrics
- Health endpoints
- Actuator
- Sensitive-data logging
- Token masking
- PII
- Production troubleshooting

## Architecture

```text
Client
  |
Gateway span
  |
Order span
  |
Payment span

One Trace ID
```

## Hands-On

- Propagate trace context
- Run OpenTelemetry locally
- Inspect trace in Jaeger/tracing backend
- Compare Gateway vs downstream latency
- Search logs using correlation/trace ID

## Architect Scenarios

- Customer says an order took 8 seconds. Where was the delay?
- Gateway CPU looks normal but p99 latency is high.
- How do you trace a request across asynchronous processing?

---

# 11. CORS, Headers & Request Policies

**Detailed document:** `11-cors-headers-request-policies.md`

## Goal

Handle common HTTP edge policies safely and consistently.

## Concepts

- Same-origin policy
- CORS
- Preflight request
- OPTIONS
- Allowed origins
- Allowed methods
- Allowed headers
- Credentials
- Why wildcard CORS can be dangerous
- Security headers
- Request headers
- Response headers
- Remove sensitive/internal headers
- Trusted headers
- Header spoofing
- Request-size limits
- Response-size considerations
- Path rewriting
- API version paths
- Query/header normalization
- Large file upload architecture
- Pre-signed S3 upload pattern awareness
- Request validation boundaries

## Hands-On

- Configure trusted CORS origin
- Test preflight
- Add/remove headers
- Protect trusted internal header
- Configure request-size behavior
- Test invalid request

---

# 12. Kubernetes Production Deployment

**Detailed document:** `12-kubernetes-production-deployment.md`

## Goal

Run Gateway as a production-style Kubernetes workload in Minikube.

## Concepts

- Deployment
- Service
- ClusterIP
- Ingress
- ConfigMap
- Secret
- Environment configuration
- Replicas
- Resource requests
- Resource limits
- Readiness probe
- Liveness probe
- Startup probe
- Rolling update
- Rollback
- HPA
- Pod disruption awareness
- Graceful shutdown
- Termination grace period
- SIGTERM behavior
- Zero/minimal-downtime deployment
- Kubernetes DNS
- Namespaces
- NetworkPolicy concepts
- Internal-only backend services

## Target Minikube Architecture

```text
Ingress
   |
Gateway Service
   |
Gateway Pods
   |
Backend ClusterIP Services
   |
Backend Pods
```

## Hands-On

- Deploy Gateway with 2 replicas
- Configure probes
- Configure resources
- Perform rolling update
- Kill Pod during traffic
- Verify backend services are internal
- Inspect logs/events during failures

---

# 13. AWS EKS Production Architecture

**Detailed document:** `13-aws-eks-production-architecture.md`

## Goal

Translate the Minikube architecture into an enterprise AWS design.

## Concepts

- Route 53
- CloudFront — when useful
- AWS WAF
- ALB
- AWS Load Balancer Controller
- EKS
- Private subnets
- Public subnets
- Multi-AZ
- Gateway Pods
- Kubernetes Services
- Security Groups
- IAM
- Pod identity / IRSA awareness
- ECR
- Secrets Manager
- ElastiCache Redis
- CloudWatch
- OpenTelemetry
- Autoscaling
- TLS termination
- Certificate management
- Internal vs internet-facing load balancers
- High availability
- Regional failure awareness
- Multi-region architecture awareness
- Cost and operational ownership

## Architecture

```text
Internet
   |
Route 53
   |
WAF
   |
ALB
   |
EKS
   |
Gateway Pods
   |
Kubernetes Services
   |
Microservices
```

## Architect Scenarios

- Why ALB before Spring Cloud Gateway?
- Which layer terminates TLS?
- Where should WAF live?
- Gateway Pods are private; how does internet traffic reach them?
- What changes across multiple Availability Zones?

---

# 14. Gateway vs Ingress vs ALB vs Service Mesh vs BFF

**Detailed document:** `14-gateway-vs-ingress-alb-service-mesh.md`

## Goal

Avoid adding overlapping infrastructure without understanding responsibilities.

## Comparisons

### API Gateway vs Load Balancer

```text
Load Balancer:
traffic distribution

API Gateway:
API-aware routing + policies
```

### API Gateway vs Kubernetes Ingress

- L7 entry routing
- TLS
- host/path routing
- where overlap exists
- when Ingress alone is enough
- when application Gateway adds value

### Spring Cloud Gateway vs AWS ALB

- infrastructure routing
- application-aware policies
- customization
- operational ownership

### Gateway vs Service Mesh

```text
Gateway:
North-South

Service Mesh:
East-West
```

### Gateway vs BFF

```text
Gateway:
shared edge policies

BFF:
client-specific composition
```

### Gateway vs API Management

- Runtime gateway
- API catalog
- developer portal
- subscription plans
- governance
- analytics
- monetization awareness

### Spring Cloud Gateway vs AWS API Gateway

- managed vs self-operated
- portability
- customization
- pricing
- operations
- AWS integration

## Architect Goal

Be able to choose **the minimum architecture that satisfies the requirements**.

---

# 15. Failure Scenarios & Production Troubleshooting

**Detailed document:** `15-failure-scenarios-troubleshooting.md`

## Goal

Learn Gateway by deliberately breaking the system.

## Failure Scenarios

### Gateway failures

- Gateway Pod crashes
- all Gateway Pods unavailable
- readiness incorrectly configured
- bad route configuration
- bad deployment
- memory/CPU pressure
- connection exhaustion

### Backend failures

- Order Pod unavailable
- Payment slow
- Inventory returns 500
- Service has zero endpoints
- DNS resolution failure

### Infrastructure failures

- Redis unavailable
- Redis slow
- Keycloak unavailable
- signing key rotation issue
- Kubernetes node failure
- Ingress failure
- ALB health-check failure

### Policy failures

- retry storm
- incorrect timeout
- overly strict rate limit
- fail-open vs fail-closed mistake
- CORS misconfiguration
- route overlap
- invalid path rewrite

### Observability failures

- missing trace propagation
- missing correlation ID
- sensitive data logged
- misleading readiness status

## Troubleshooting Approach

```text
Symptom
   ↓
Client status/error
   ↓
Gateway route
   ↓
Gateway logs/metrics/trace
   ↓
Kubernetes Service
   ↓
Endpoints
   ↓
Pods
   ↓
Downstream dependencies
```

## Hands-On

Every important failure should be reproduced in Minikube where practical.

---

# 16. Architect Interview & System Design Scenarios

**Detailed document:** `16-architect-interview-scenarios.md`

## Goal

Convert implementation knowledge into architect-level decision making.

## Core Interview Questions

- Why API Gateway?
- When would you avoid one?
- Gateway vs load balancer?
- Gateway vs Ingress?
- Gateway vs service mesh?
- Gateway vs BFF?
- Spring Cloud Gateway vs AWS API Gateway?
- Why Spring Cloud Gateway?
- What belongs in Gateway?
- What should never live in Gateway?
- How do you avoid a God Gateway?
- How do you make Gateway highly available?
- How do you scale it?
- How do you prevent Gateway from becoming a bottleneck?
- How do you secure it?
- Should JWT be validated again by services?
- User token or service token?
- How do you prevent header spoofing?
- How do you rate-limit by tenant?
- What happens if Redis fails?
- Where should circuit breakers live?
- Should Gateway retry POST?
- How do you prevent retry amplification?
- How do you handle Payment outage?
- How do you deploy Gateway safely?
- How do you migrate direct service clients to Gateway?
- How does Gateway work in Kubernetes?
- Why not Eureka in EKS?
- How would you design Gateway for 100k RPS?
- How would you design multi-region Gateway?
- Should internal service-to-service traffic pass through Gateway?
- How do you troubleshoot high p99 latency?

## System Design Scenarios

### Scenario 1 — E-Commerce Platform

Design:

```text
Customer UI
Admin UI
Mobile
   |
Gateway
   |
Order / Payment / Inventory / Customer
```

### Scenario 2 — High-Traffic Sale

Handle:

- sudden traffic spike
- rate limiting
- autoscaling
- downstream capacity
- load shedding

### Scenario 3 — Payment Failure

Design correct behavior when Payment becomes slow/unavailable.

### Scenario 4 — SaaS Multi-Tenant Gateway

Handle:

- tenant identity
- quotas
- tenant isolation
- observability

### Scenario 5 — AWS Migration

Move:

```text
VM + Eureka
```

to:

```text
EKS + Kubernetes Services
```

### Scenario 6 — Multi-Region

Discuss:

- global routing
- regional Gateways
- rate limits
- authentication
- failover
- state

## Answer Framework

```text
Requirements
   ↓
Gateway Responsibilities
   ↓
Traffic Flow
   ↓
Routing / Discovery
   ↓
Security
   ↓
Resilience
   ↓
Rate Limiting
   ↓
Observability
   ↓
Scaling / HA
   ↓
Kubernetes / AWS
   ↓
Alternatives
   ↓
Trade-offs
```

---

# 17. Final Revision & Interview Readiness

**Detailed document:** `17-final-revision.md`

## Goal

Verify that API Gateway knowledge is retained and can be explained without notes.

## Concept Revision

I should be able to explain from memory:

- API Gateway purpose
- Route
- Predicate
- Filter
- Global filter
- Request lifecycle
- Kubernetes service discovery
- Gateway load balancing
- HA
- JWT security
- Token relay
- Service identity
- Rate limiting
- Redis
- Timeout
- Retry
- Circuit breaker
- Idempotency
- CORS
- Observability
- Kubernetes deployment
- EKS architecture
- Gateway vs competing components

## Whiteboard Test

Draw from memory:

```text
Internet
   |
WAF
   |
ALB
   |
EKS
   |
Gateway Pods
   |
Kubernetes Services
   |
Order / Payment / Inventory
```

Then add:

```text
Identity Provider
Redis
OpenTelemetry
```

and explain each connection.

## Failure Test

Explain what happens when:

1. Gateway Pod dies.
2. Order Pod dies.
3. Payment becomes slow.
4. Redis goes down.
5. Identity Provider goes down.
6. JWT signing key changes.
7. Kubernetes Service has zero endpoints.
8. Gateway retries Payment POST.
9. traffic increases 10x.
10. one Availability Zone fails.

## Architecture Decision Test

Defend:

- Spring Cloud Gateway vs AWS API Gateway
- Gateway vs Ingress
- Gateway vs ALB
- Gateway vs service mesh
- Gateway vs BFF
- Kubernetes discovery vs Eureka
- JWT validation at Gateway and service
- Redis-backed rate limiting
- caller-boundary resilience

## Implementation Test

Without copying:

- create a route
- create predicates
- add filter
- route to Kubernetes Service
- secure route
- configure rate limiting
- configure timeout/circuit breaker
- deploy Gateway to Minikube
- scale replicas
- inspect failure

## Interview Ready Criteria

Mark API Gateway **⭐** only when:

- [ ] I can explain the architecture without notes.
- [ ] I can implement core routing myself.
- [ ] I understand Kubernetes-native service discovery.
- [ ] I can deploy Gateway to Minikube.
- [ ] I can scale Gateway and backend Pods.
- [ ] I can explain JWT/OAuth2 security.
- [ ] I can explain user vs service identity.
- [ ] I can implement and explain rate limiting.
- [ ] I understand timeout/retry/circuit breaker trade-offs.
- [ ] I understand idempotency and duplicate payment risks.
- [ ] I can trace a request across Gateway and services.
- [ ] I can troubleshoot common Kubernetes/Gateway failures.
- [ ] I can explain AWS EKS production architecture.
- [ ] I can compare Gateway with ALB, Ingress, service mesh, BFF and AWS API Gateway.
- [ ] I can answer production failure scenarios.
- [ ] I can defend architecture choices and alternatives.

---

# Hands-On Implementation Progression

The same application evolves through all topics.

## Phase A — Minimal Gateway

```text
Client
   |
Gateway
   |
Order
```

Topics:

```text
01
02
```

---

## Phase B — Kubernetes Native

```text
Minikube
   |
Gateway
   |
Kubernetes Services
   |
Order / Payment / Inventory Pods
```

Topics:

```text
03
04
```

---

## Phase C — Secure Gateway

```text
Keycloak
   |
JWT
   |
Gateway
   |
Services
```

Topics:

```text
05
06
```

---

## Phase D — Traffic Protection

```text
            Redis
              |
Client -> Gateway -> Services
```

Topics:

```text
07
```

---

## Phase E — Resilience

```text
Gateway
   |
Order
   |
Payment
```

Add:

```text
timeouts
retry where safe
circuit breaker
idempotency
```

Topics:

```text
08
09
```

---

## Phase F — Observability & Edge Policies

Add:

```text
OpenTelemetry
Tracing
Metrics
Structured logs
CORS
Header policies
```

Topics:

```text
10
11
```

---

## Phase G — Production Deployment

```text
Ingress / ALB
      |
Gateway Replicas
      |
Kubernetes Services
      |
Service Replicas
```

Topics:

```text
12
13
```

---

## Phase H — Architect Mastery

Topics:

```text
14
15
16
17
```

At this stage the focus shifts from:

```text
"How do I configure this?"
```

to:

```text
"Why is this architecture correct,
what can fail,
what alternatives exist,
and how do I defend the decision?"
```

---

# Rules for Detailed Topic Documents

Every detailed `.md` file should use this format where applicable:

```text
1. What Problem Are We Solving?
2. Simple Explanation
3. Why an Architect Needs This
4. Real E-Commerce Scenario
5. Architecture / Request Flow
6. Important Concepts
7. Minimal Spring Implementation
8. Kubernetes / Minikube Implementation
9. Run & Test
10. Break It Intentionally
11. Troubleshoot & Fix
12. Production Considerations
13. AWS/EKS Mapping
14. Alternatives
15. Trade-offs
16. Anti-Patterns
17. Architect Interview Questions
18. Hands-On Challenge
19. Quick Revision
20. Completion Checklist
```

Not every file needs artificial sections when they add no value, but the learning flow should remain consistent.

---

# Important Learning Rules

## 1. Kubernetes First

Our normal path is:

```text
Local code
   ↓
Minikube
   ↓
Kubernetes behavior
   ↓
AWS EKS mapping
```

Eureka is learned for awareness/interviews, not used as the primary service-discovery implementation.

---

## 2. One Evolving POC

Do not create:

```text
gateway-routing-project
gateway-rate-limit-project
gateway-security-project
gateway-circuit-breaker-project
```

Use the same Gateway and microservices.

---

## 3. Do Not Over-Generate Code

Each exercise adds only what is required for the current concept.

Avoid asking coding agents to:

> "Make the Gateway production ready."

That skips the learning progression.

---

## 4. Break the System

For important topics:

```text
Implement
   ↓
Verify
   ↓
Break
   ↓
Observe
   ↓
Diagnose
   ↓
Fix
```

Understanding failure is part of architect preparation.

---

## 5. Separate Gateway Logic from Business Logic

Gateway may own:

```text
routing
edge authentication
coarse authorization
rate limiting
CORS
headers
edge resilience
observability
```

Gateway should not own:

```text
order rules
payment decisions
inventory reservation
pricing
business workflows
database access
```

---

## 6. Always Ask "Which Layer Owns This?"

Possible layers include:

```text
WAF
ALB
Ingress
API Gateway
Service Mesh
Microservice
BFF
```

Avoid implementing the same policy everywhere.

---

# ADRs Generated During API Gateway Study

Create architecture decisions when we reach the relevant topic.

Suggested ADRs:

```text
adr/
├── ADR-001-api-gateway-choice.md
├── ADR-002-kubernetes-service-discovery.md
├── ADR-003-gateway-security-boundary.md
├── ADR-004-user-vs-service-token.md
├── ADR-005-rate-limiting-strategy.md
├── ADR-006-resilience-ownership.md
└── ADR-007-ingress-alb-gateway-responsibilities.md
```

Each ADR should contain:

```text
Context
Options
Decision
Why
Trade-offs
Consequences
```

---

# Portfolio Evidence

When API Gateway is complete, the repository should demonstrate:

- Spring Cloud Gateway implementation
- Kubernetes-native routing
- Minikube deployment
- multiple Gateway replicas
- multiple service replicas
- OAuth2/JWT security
- service identity decisions
- Redis rate limiting
- resilience
- idempotency
- distributed tracing
- production failure handling
- AWS EKS architecture
- ADRs
- architecture comparisons
- interview scenarios

The portfolio goal is not:

> "I created a Spring Cloud Gateway."

It is:

> **"I can design, implement, deploy, secure, scale, observe and troubleshoot an API Gateway as part of a production microservices platform, and I can explain the architectural trade-offs."**

---

# Current Progress

```text
Parent:
Microservices Architect Workouts

Current Major Area:
01 — API Gateway

Current Topic:
01 — Basic Routing & Gateway Request Flow

Environment:
Mac + IntelliJ + Minikube

Production Target:
AWS EKS

Status:
🟡 Learning
```

---

# Next Document

```text
01-basic-routing.md
```

The next step is deliberately small:

```text
Client
   |
Gateway
   |
Order Service
```

We first understand and implement **basic routing correctly**.

No Redis.

No Keycloak.

No circuit breaker.

No unnecessary infrastructure.

Once the basic request flow is understood and working, we move to:

```text
02-predicates-and-filters.md
```
