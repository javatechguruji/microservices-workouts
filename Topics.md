# Microservices Architect Workouts — Topics

> **Purpose:** Master microservices from implementation to production architecture and architect interviews.  
> **Primary stack:** Java, Spring Boot, Spring Cloud, Kafka, Redis, OAuth2/OIDC, Kubernetes/Minikube, AWS EKS, OpenTelemetry.  
> **Learning approach:** Learn → Implement → Break/Test → Production Scenario → Trade-offs → Interview → Revision.  
> **Deployment focus:** Kubernetes-first. Minikube is the local production-like lab; AWS EKS is the target cloud architecture.

---

# Status Legend

| Status | Meaning |
|---|---|
| ⬜ | Not Started |
| 🟡 | Learning |
| 🟢 | Implemented / Practiced |
| ⭐ | Interview Ready |

> A topic should be marked **⭐ Interview Ready** only when I can explain the architecture, implementation, failure scenarios, alternatives, and trade-offs without depending on notes.

---

# Master Topic Dashboard

| # | Major Topic | Status |
|---|---|---|
| 01 | API Gateway | 🟡 |
| 02 | Service-to-Service Communication | ⬜ |
| 03 | Service Discovery & Load Balancing | ⬜ |
| 04 | Resilience & Fault Tolerance | ⬜ |
| 05 | Data Ownership & Database per Service | ⬜ |
| 06 | Distributed Transactions & Data Consistency | ⬜ |
| 07 | Event-Driven Architecture | ⬜ |
| 08 | Kafka & Messaging | ⬜ |
| 09 | Idempotency & Duplicate Handling | ⬜ |
| 10 | Security & Zero Trust | ⬜ |
| 11 | Observability | ⬜ |
| 12 | Configuration & Secrets Management | ⬜ |
| 13 | Kubernetes for Microservices | ⬜ |
| 14 | AWS EKS Production Architecture | ⬜ |
| 15 | Scalability & Performance | ⬜ |
| 16 | Caching | ⬜ |
| 17 | API Design & Versioning | ⬜ |
| 18 | Microservice Decomposition | ⬜ |
| 19 | Domain-Driven Design for Microservices | ⬜ |
| 20 | Integration Patterns | ⬜ |
| 21 | Saga / Transactional Outbox / CDC | ⬜ |
| 22 | Reliability & Production Failure Handling | ⬜ |
| 23 | Deployment & Release Strategies | ⬜ |
| 24 | Testing Microservices | ⬜ |
| 25 | Service Mesh | ⬜ |
| 26 | Distributed Systems Fundamentals | ⬜ |
| 27 | Multi-Tenancy | ⬜ |
| 28 | Architecture Governance & ADRs | ⬜ |
| 29 | Monolith-to-Microservices Migration | ⬜ |
| 30 | Production Architecture Scenarios | ⬜ |
| 31 | Architect Interview Scenarios | ⬜ |

---

# 01. API Gateway

**Goal:** Design and implement a production-oriented entry point for microservices.

Coverage includes:

- Spring Cloud Gateway architecture
- Routes, predicates and filters
- Kubernetes Service-based routing and discovery
- Load balancing, scaling and gateway HA
- OAuth2/OIDC, JWT and gateway security
- Token relay and service-to-service identity
- Redis-backed rate limiting
- Timeouts, retries and circuit breakers
- Idempotency and safe retry behavior
- CORS, headers and request policies
- Distributed tracing, metrics and logging
- Kubernetes deployment
- AWS ALB + EKS architecture
- Gateway vs Ingress vs ALB vs service mesh vs BFF
- Failure scenarios and troubleshooting
- Production design and architect interview scenarios

Detailed curriculum:

`docs/01-api-gateway/Topics.md`

---

# 02. Service-to-Service Communication

**Goal:** Choose the correct communication mechanism between distributed services.

Coverage includes:

- Synchronous vs asynchronous communication
- REST communication
- HTTP clients in modern Spring applications
- gRPC and when it is appropriate
- Request/response vs event-driven communication
- API composition
- Fan-out/fan-in
- Service aggregation
- Long-running operations
- Webhooks and callbacks
- Backpressure considerations
- Coupling and temporal coupling
- Choosing communication based on business requirements

---

# 03. Service Discovery & Load Balancing

**Goal:** Understand how services locate and distribute traffic to other services in modern platforms.

Coverage includes:

- Client-side vs server-side discovery
- Spring Cloud LoadBalancer
- Kubernetes DNS
- Kubernetes Services
- ClusterIP
- Endpoints / EndpointSlices
- Pod discovery
- Internal load balancing
- External load balancing
- AWS ALB/NLB
- Eureka — interview/legacy awareness
- Why Kubernetes often removes the need for Eureka
- Health-aware routing
- Failure and scaling scenarios

---

# 04. Resilience & Fault Tolerance

**Goal:** Prevent one service failure from cascading through the platform.

Coverage includes:

- Timeouts
- Retry
- Circuit breaker
- Bulkhead
- Rate limiting
- Fallback
- Graceful degradation
- Retry amplification
- Exponential backoff
- Jitter
- Timeout budgets
- Cascading failures
- Load shedding
- Resilience4j
- Caller-boundary resilience
- Failure recovery
- Partial availability

---

# 05. Data Ownership & Database per Service

**Goal:** Design independent service data ownership without creating hidden coupling.

Coverage includes:

- Database per service
- Schema per service
- Shared database anti-pattern
- Data ownership boundaries
- Polyglot persistence
- Cross-service queries
- Data duplication
- Read models
- Reporting requirements
- Referential integrity across services
- Data contracts
- Database migration
- Service autonomy vs operational complexity

---

# 06. Distributed Transactions & Data Consistency

**Goal:** Maintain business consistency without relying on traditional distributed ACID transactions.

Coverage includes:

- Local transactions
- Distributed transaction problem
- Eventual consistency
- Strong vs eventual consistency
- Saga pattern
- Choreography
- Orchestration
- Compensating transactions
- Transactional Outbox
- CDC
- Idempotent consumers
- Failure recovery
- Duplicate events
- Ordering
- Reconciliation
- Business invariants

---

# 07. Event-Driven Architecture

**Goal:** Design loosely coupled systems around business events.

Coverage includes:

- Events vs commands
- Domain events
- Integration events
- Event notification
- Event-carried state transfer
- Event-driven workflows
- Producers and consumers
- Event contracts
- Event schema evolution
- Loose coupling
- Temporal decoupling
- Eventual consistency
- Event choreography
- Event storm / event explosion risks
- When NOT to use events

---

# 08. Kafka & Messaging

**Goal:** Apply Kafka correctly inside real microservice architectures.

Coverage includes:

- Topics and partitions
- Producers
- Consumers
- Consumer groups
- Offsets
- Partition keys
- Ordering
- Delivery semantics
- At-least-once processing
- Duplicate handling
- Consumer retries
- Dead-letter topics
- Poison messages
- Schema evolution
- Rebalancing
- Kafka availability
- Kafka scaling
- Kafka vs queues
- Kafka in Kubernetes/AWS architecture

> Deep Kafka mechanics may also be practiced in the dedicated Kafka workout project; this section focuses on integration into the end-to-end microservices system.

---

# 09. Idempotency & Duplicate Handling

**Goal:** Make distributed operations safe when messages or requests are repeated.

Coverage includes:

- Idempotent APIs
- Idempotency keys
- Duplicate HTTP requests
- Duplicate Kafka events
- Deduplication stores
- Idempotent consumers
- Payment duplicate prevention
- Retry safety
- Exactly-once myths and practical guarantees
- Database constraints
- Race conditions
- Idempotency expiration
- Replay scenarios

---

# 10. Security & Zero Trust

**Goal:** Secure users, services and APIs using modern identity-based security.

Coverage includes:

- Authentication vs authorization
- OAuth2
- OpenID Connect
- JWT
- Access and refresh tokens
- Resource server
- API Gateway security
- Service-level authorization
- Client Credentials
- Service-to-service identity
- Token relay
- User token vs service token
- Scopes and roles
- Keycloak / managed identity provider concepts
- Zero Trust
- Defense in depth
- Secrets and credential handling
- Kubernetes network/security boundaries

> Deep Spring Security mechanics may also be practiced in the dedicated security workout project; this section applies them to distributed architecture.

---

# 11. Observability

**Goal:** Understand what is happening across a distributed request and diagnose production failures quickly.

Coverage includes:

- Logs
- Structured logging
- Metrics
- Distributed tracing
- Correlation IDs
- Trace IDs and span IDs
- Micrometer
- OpenTelemetry
- Collector architecture
- Jaeger/tracing backend concepts
- RED metrics
- Golden signals
- p50/p95/p99 latency
- Service-level dashboards
- Alerting
- SLI/SLO concepts
- Production debugging
- Sensitive-data logging risks

---

# 12. Configuration & Secrets Management

**Goal:** Externalize configuration safely across environments.

Coverage includes:

- Spring profiles
- Environment-specific configuration
- ConfigMaps
- Kubernetes Secrets
- AWS Secrets Manager
- Parameter Store concepts
- Secret rotation
- Credentials
- Configuration refresh
- Immutable configuration
- Feature configuration
- Avoiding secrets in Git/images
- Dev/Test/Stage/Prod configuration strategy

---

# 13. Kubernetes for Microservices

**Goal:** Run the complete platform locally in Minikube using production-style Kubernetes concepts.

Coverage includes:

- Pods
- Deployments
- ReplicaSets
- Services
- ClusterIP
- Ingress
- ConfigMaps
- Secrets
- Namespaces
- Resource requests and limits
- Liveness probes
- Readiness probes
- Startup probes
- Rolling updates
- Rollbacks
- Horizontal Pod Autoscaler
- Pod disruption concepts
- Persistent storage where appropriate
- NetworkPolicy concepts
- Service DNS
- Failure/restart behavior
- Scaling services
- Minikube workflow

> Kubernetes is the primary local deployment environment for this workout rather than treating Docker Compose as the main runtime.

---

# 14. AWS EKS Production Architecture

**Goal:** Translate the Minikube implementation into a realistic AWS production design.

Coverage includes:

- VPC
- Public/private subnets
- Multi-AZ
- EKS
- Managed node groups / compute choices
- ALB
- AWS Load Balancer Controller
- Route 53
- WAF
- ECR
- IAM
- IRSA / pod identity concepts
- Security groups
- Secrets Manager
- ElastiCache
- RDS
- MSK concepts
- CloudWatch
- OpenTelemetry architecture
- Autoscaling
- High availability
- Disaster recovery concepts
- Cost/operational trade-offs

---

# 15. Scalability & Performance

**Goal:** Design services that scale predictably under real traffic.

Coverage includes:

- Horizontal vs vertical scaling
- Stateless services
- Autoscaling
- HPA
- CPU vs custom scaling metrics
- Connection pools
- Thread pools
- Reactive vs blocking trade-offs
- Database bottlenecks
- Hot partitions
- Backpressure
- Load shedding
- Capacity planning
- Performance testing
- Latency budgets
- Throughput
- Tail latency
- Traffic spikes
- Hot-sale scenarios

---

# 16. Caching

**Goal:** Improve performance without introducing incorrect or stale behavior.

Coverage includes:

- Local cache
- Distributed cache
- Redis
- Cache-aside
- Read-through/write-through concepts
- TTL
- Eviction
- Cache invalidation
- Cache stampede
- Hot keys
- Distributed caching
- User-specific caching
- API caching
- CDN caching concepts
- Consistency trade-offs
- When not to cache

---

# 17. API Design & Versioning

**Goal:** Build stable APIs that can evolve without breaking consumers.

Coverage includes:

- REST resource design
- HTTP methods
- Status codes
- Error contracts
- Pagination
- Filtering
- Sorting
- API versioning
- Backward compatibility
- Consumer-driven evolution
- API deprecation
- Idempotent API design
- API contracts
- OpenAPI concepts
- Public vs internal APIs
- API lifecycle

---

# 18. Microservice Decomposition

**Goal:** Decide where service boundaries should actually exist.

Coverage includes:

- Why microservices
- Modular monolith vs microservices
- Business capability decomposition
- Bounded contexts
- Service boundaries
- Data ownership
- Coupling and cohesion
- Independent deployment
- Team ownership
- Service granularity
- Nano-service anti-pattern
- Distributed monolith
- Shared libraries
- Shared database problems
- When to merge services
- When not to use microservices

---

# 19. Domain-Driven Design for Microservices

**Goal:** Use DDD concepts where they help define maintainable business boundaries.

Coverage includes:

- Domain
- Subdomain
- Core/supporting/generic domains
- Bounded context
- Ubiquitous language
- Entities
- Value objects
- Aggregates
- Aggregate roots
- Domain services
- Domain events
- Context maps
- Anti-corruption layer
- DDD and microservice boundaries
- Practical DDD vs overengineering

---

# 20. Integration Patterns

**Goal:** Choose appropriate patterns for connecting services and external systems.

Coverage includes:

- API Gateway
- Aggregator
- BFF
- Adapter
- Anti-corruption layer
- Strangler Fig
- Event notification
- Event-carried state transfer
- Request/reply
- Publish/subscribe
- Competing consumers
- Dead-letter channel
- Retry
- Circuit breaker
- Webhook
- Polling
- Batch integration
- File integration where still relevant

---

# 21. Saga / Transactional Outbox / CDC

**Goal:** Deep-dive the patterns most often used to coordinate distributed data changes.

Coverage includes:

- Saga choreography
- Saga orchestration
- Compensation
- Saga state
- Failure handling
- Transactional Outbox
- Outbox publisher
- Polling publisher
- CDC
- Debezium concepts
- Duplicate delivery
- Ordering
- Idempotent consumers
- Recovery and reconciliation
- Saga observability
- When to use each pattern

---

# 22. Reliability & Production Failure Handling

**Goal:** Design for failure rather than assuming every dependency works.

Coverage includes:

- Partial failure
- Cascading failure
- Dependency outage
- Slow dependency
- Network partition
- Pod crash
- Node failure
- Zone failure
- Database failure
- Kafka failure
- Redis failure
- Identity provider failure
- Graceful degradation
- Fail-open vs fail-closed
- Recovery
- Reconciliation
- Runbooks
- SLOs/error budgets concepts
- Chaos/failure testing concepts

---

# 23. Deployment & Release Strategies

**Goal:** Release microservices safely without unnecessary downtime.

Coverage includes:

- CI/CD pipeline
- Immutable artifacts
- Rolling deployment
- Recreate strategy
- Blue/green
- Canary
- Progressive delivery
- Feature flags
- Database migration compatibility
- Backward-compatible API changes
- Rollback
- Kubernetes rollout
- GitOps concepts
- Environment promotion
- Deployment failure recovery

---

# 24. Testing Microservices

**Goal:** Test service behavior without creating an impossibly slow or fragile test suite.

Coverage includes:

- Unit tests
- Component tests
- Integration tests
- Repository tests
- API tests
- Contract testing
- Consumer-driven contracts
- Testcontainers concepts
- Kafka integration tests
- Security tests
- End-to-end tests
- Kubernetes smoke tests
- Resilience/failure tests
- Performance tests
- Test pyramid/trophy trade-offs
- Avoiding excessive mocks

---

# 25. Service Mesh

**Goal:** Understand when platform-level east-west traffic management is useful.

Coverage includes:

- North-south vs east-west traffic
- Sidecar / ambient concepts
- Service identity
- mTLS
- Traffic management
- Retries/timeouts at mesh layer
- Observability
- Canary routing
- Gateway vs service mesh
- Duplicate policy risks
- Istio concepts
- AWS service networking alternatives
- When a service mesh is unnecessary

---

# 26. Distributed Systems Fundamentals

**Goal:** Understand the principles behind microservice failures and trade-offs rather than memorizing frameworks.

Coverage includes:

- Network is unreliable
- Latency
- Partial failure
- CAP theorem
- Consistency models
- Availability
- Partition tolerance
- Eventual consistency
- Replication
- Leader/follower concepts
- Quorums concepts
- Clock/time problems
- Ordering
- Duplicate delivery
- At-most/at-least/exactly-once semantics
- Consensus awareness
- Distributed locks
- Split brain concepts
- Backpressure

---

# 27. Multi-Tenancy

**Goal:** Design SaaS-style microservices that isolate tenants securely and efficiently.

Coverage includes:

- Tenant identification
- Tenant context propagation
- JWT tenant claims
- Gateway tenant handling
- Database-per-tenant
- Schema-per-tenant
- Shared-schema models
- Data isolation
- Tenant-aware caching
- Tenant-aware Kafka events
- Rate limits by tenant
- Noisy-neighbor problem
- Security boundaries
- Observability by tenant
- Scaling trade-offs

---

# 28. Architecture Governance & ADRs

**Goal:** Document and govern important architecture decisions without creating bureaucracy.

Coverage includes:

- Architecture Decision Records
- Problem/context
- Options
- Decision
- Trade-offs
- Consequences
- Architecture principles
- Technology standards
- API standards
- Security standards
- Observability standards
- Platform guardrails
- Architecture reviews
- Fitness functions concepts
- Avoiding architecture drift
- Technical debt decisions

Example ADRs for this project:

- API Gateway choice
- Kubernetes service discovery
- Sync vs async communication
- Kafka adoption
- Database-per-service
- Security model
- Saga approach
- Observability stack

---

# 29. Monolith-to-Microservices Migration

**Goal:** Break apart an existing system incrementally rather than performing a risky rewrite.

Coverage includes:

- Why migrate
- When not to migrate
- Modularize first
- Identify bounded contexts
- Strangler Fig pattern
- Extract service
- Data decomposition
- Shared database migration
- API façade/gateway
- Event introduction
- Anti-corruption layer
- Incremental traffic migration
- Backward compatibility
- Operational readiness
- Distributed monolith risk
- Migration sequencing

---

# 30. Production Architecture Scenarios

**Goal:** Connect all individual topics into realistic system-design problems.

Scenarios include:

- Order → Payment → Inventory workflow
- Payment service outage
- Inventory inconsistency
- Duplicate payment request
- Kafka duplicate event
- Kafka unavailable
- Redis unavailable
- Identity provider unavailable
- Gateway outage
- Database outage
- High-traffic sale
- 100k+ request scaling discussion
- Multi-AZ failure
- Slow downstream service
- Service decomposition challenge
- Monolith migration
- API version migration
- Multi-region architecture
- SaaS multi-tenancy
- Production incident diagnosis

---

# 31. Architect Interview Scenarios

**Goal:** Convert implementation knowledge into strong architect-level interview answers.

Practice areas include:

- Requirement clarification
- High-level architecture
- Component responsibilities
- Data flow
- API design
- Database decisions
- Sync vs async decisions
- Security model
- Failure handling
- Scalability
- Availability
- Consistency trade-offs
- Kubernetes architecture
- AWS/EKS architecture
- Observability
- Cost/complexity trade-offs
- Alternatives considered
- ADR-style decision explanation
- Principal-level follow-up questions
- Production troubleshooting

Answer framework:

```text
Requirements
    ↓
Constraints / NFRs
    ↓
High-Level Architecture
    ↓
Component Responsibilities
    ↓
Data & Communication
    ↓
Security
    ↓
Failure / Resilience
    ↓
Scaling / HA
    ↓
Observability
    ↓
Deployment
    ↓
Alternatives & Trade-offs
```

---

# End-to-End Practice System

All major topics should eventually connect to the same evolving system:

```text
                         Internet
                            |
                         AWS ALB
                            |
                     API Gateway
                            |
          +-----------------+-----------------+
          |                 |                 |
       Order             Payment          Inventory
       Service            Service           Service
          |                 |                 |
          +---------------- Kafka ------------+
                            |
                       Other Consumers

Supporting platform:

- Kubernetes / Minikube locally
- AWS EKS production design
- Redis
- Kafka
- OAuth2/OIDC Identity Provider
- OpenTelemetry
- Metrics / Logs / Traces
- Service-owned databases
```

The goal is **not** to add every technology immediately. The platform evolves topic by topic as each capability is learned.

---

# Learning Rule for Every Major Topic

Each child topic should follow this sequence:

```text
1. Concept
2. Why an Architect Needs It
3. Simple Explanation
4. Real E-Commerce Scenario
5. Architecture / Request Flow
6. Minimal Implementation
7. Deploy/Test in Minikube where applicable
8. Break It Intentionally
9. Troubleshoot / Fix
10. Production Considerations
11. AWS/EKS Mapping where applicable
12. Alternatives
13. Trade-offs
14. Anti-Patterns
15. Architect Interview Questions
16. Hands-on Challenge
17. Quick Revision
```

---

# Completion Rule

Do not mark a major topic complete just because the code works.

For **⭐ Interview Ready**, I should be able to answer:

1. What problem does this solve?
2. When should I use it?
3. When should I NOT use it?
4. How did I implement it?
5. How does it behave in Kubernetes?
6. How would I run it in AWS/EKS?
7. What happens when it fails?
8. How does it scale?
9. How is it secured?
10. How do I observe/troubleshoot it?
11. What alternatives exist?
12. What trade-offs did I accept?

---

# Current Focus

```text
Current Major Topic: 01 — API Gateway
Status: 🟡 Learning

Child curriculum:
docs/01-api-gateway/Topics.md

Current implementation platform:
Minikube / Kubernetes
```

---

# Portfolio Outcome

By the end of this curriculum, `microservices-workouts` should demonstrate:

- Java/Spring implementation ability
- Microservices architecture knowledge
- Distributed systems understanding
- Kubernetes production thinking
- AWS/EKS architecture
- Security
- Resilience
- Event-driven architecture
- Observability
- Architecture trade-off analysis
- ADRs
- Failure handling
- System-design ability
- Architect interview readiness

The repository should tell the story:

> **I do not only know individual Spring annotations and patterns. I can design, implement, deploy, troubleshoot, evolve, and defend the architectural decisions of a production microservices platform.**
