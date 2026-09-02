# API Gateway — Stage 1: Basic Routing & Gateway Request Flow

> **Parent:** `microservices-workouts/docs/01-api-gateway/Topics.md`  
> **Stage:** 01  
> **Technology:** Spring Cloud Gateway  
> **Learning style:** Understand → Implement → Run → Break → Fix → Architect Thinking → Interview  
> **Current scope:** Basic routing only  
> **Kubernetes:** We will understand the production direction here, but Kubernetes-native routing is implemented deeply in Stage 3.

---

# 1. What Are We Learning?

In this stage we learn the **smallest useful API Gateway implementation**.

By the end, this request should work:

```text
Client
   |
   | GET http://localhost:8000/api/orders/101
   v
API Gateway
   |
   | route matches /api/orders/**
   v
Order Service
   |
   | GET /orders/101
   v
Response
```

We are deliberately **NOT** adding:

- Eureka
- Kubernetes service discovery
- Redis
- JWT / Keycloak
- rate limiting
- circuit breaker
- retry
- Kafka
- tracing
- service mesh

Those come later.

The goal is to understand the request flow before adding infrastructure.

---

# 2. What Problem Does an API Gateway Solve?

Suppose our e-commerce system has:

```text
Order Service       :9091
Payment Service     :9092
Inventory Service   :9093
Customer Service    :9094
```

Without a Gateway, the client needs to know:

```text
http://order-service:9091/orders/101

http://payment-service:9092/payments/5001

http://inventory-service:9093/inventory/P100
```

The client now knows too much about our backend architecture.

If tomorrow we:

- change ports
- rename a service
- move services to Kubernetes
- add authentication
- introduce API versions
- add rate limiting
- change internal paths

clients may be affected.

Instead, expose one entry point:

```text
https://api.myshop.com
```

Clients see:

```text
GET /api/orders/101

POST /api/payments

GET /api/inventory/P100
```

The Gateway decides where each request goes.

---

# 3. Simple Mental Model

Think of the Gateway as the **reception desk** of the platform.

```text
Customer
   |
   v
Reception
   |
   | "Orders department is over there"
   v
Orders Department
```

The receptionist does not process the order.

Likewise:

```text
API Gateway
```

should normally **route and enforce edge policies**.

It should not contain Order business logic.

---

# 4. Why Does an Architect Care?

A developer may ask:

> How do I configure a Spring Cloud Gateway route?

An architect must also ask:

> Why are we introducing a Gateway?

> Which responsibilities belong there?

> Will it become a bottleneck?

> How do we make it highly available?

> Should internal services also call each other through it?

> Where does ALB/Ingress fit?

> What happens when the Gateway fails?

Those questions become important in later stages.

For Stage 1, remember this principle:

> **The API Gateway hides internal service topology from external clients and provides a controlled entry point into the platform.**

---

# 5. Our E-Commerce Scenario

Eventually our system will look roughly like:

```text
                     Client
                        |
                        v
                  API Gateway
                        |
        +---------------+---------------+
        |               |               |
        v               v               v
     Order           Payment        Inventory
     Service         Service         Service
```

For Stage 1 we only need:

```text
Client
   |
   v
Gateway
   |
   v
Order Service
```

Keep it small.

---

# 6. Route — The Most Important Concept Today

A Gateway route answers:

> **If this request matches some condition, where should I send it?**

A route has three important pieces:

```text
Route
 |
 +-- ID
 |
 +-- Predicate
 |
 +-- Destination URI
```

Example:

```yaml
- id: order-service
  uri: http://localhost:9091
  predicates:
    - Path=/orders/**
```

Meaning:

```text
id
    order-service

predicate
    request path must match /orders/**

destination
    http://localhost:9091
```

So:

```text
GET http://localhost:8000/orders/101
```

becomes:

```text
Gateway
   |
   v
http://localhost:9091/orders/101
```

---

# 7. Predicate

A predicate answers:

> **Does this route apply to the incoming request?**

For Stage 1 we use only a **Path Predicate**.

```yaml
predicates:
  - Path=/orders/**
```

Matches:

```text
/orders
/orders/101
/orders/customer/500
```

Does not match:

```text
/payments/101
/inventory/P100
/customers/10
```

Later we will learn:

- Method
- Header
- Query
- Host
- RemoteAddr
- multiple predicates

But not now.

---

# 8. Destination URI

The URI tells Gateway:

> **Where should I forward the request?**

For Stage 1:

```yaml
uri: http://localhost:9091
```

This is intentionally static.

In production Kubernetes we will not use:

```text
localhost:9091
```

We will eventually use a Kubernetes Service such as:

```text
http://order-service
```

and Kubernetes will route to available Order Pods.

That is Stage 3.

---

# 9. Important: `localhost` Depends on Where Gateway Runs

This is an important production concept.

If both applications run directly on your Mac:

```text
Gateway process
    |
    | localhost:9091
    v
Order process
```

works because both processes use the Mac network.

But once Gateway runs inside Kubernetes:

```text
Gateway Pod
    |
    | localhost:9091
    X
```

`localhost` means:

> **this Gateway Pod itself**

It does **not** mean your Order Service.

That is why Kubernetes-native routing later becomes:

```text
Gateway Pod
    |
    | http://order-service
    v
Kubernetes Service
    |
    v
Order Pods
```

This distinction is worth remembering for interviews.

---

# 10. External Path vs Internal Path

There are two valid designs.

## Design A — Same Path

Client calls:

```text
/orders/101
```

Order Service exposes:

```text
/orders/101
```

Gateway simply forwards it.

```text
Client
  |
  | /orders/101
  v
Gateway
  |
  | /orders/101
  v
Order Service
```

This is the simplest Stage 1 implementation.

---

## Design B — External API Prefix

Client calls:

```text
/api/orders/101
```

but Order Service exposes:

```text
/orders/101
```

Gateway must transform:

```text
/api/orders/101
```

into:

```text
/orders/101
```

That requires a filter such as `StripPrefix` or `RewritePath`.

Filters are Stage 2.

Therefore, for Stage 1 we intentionally use:

```text
/orders/**
```

end to end.

Do not add path rewriting yet.

---

# 11. Spring Cloud Gateway Request Flow

At a simplified level:

```text
Incoming Request
      |
      v
Gateway
      |
      v
Find Matching Route
      |
      v
Evaluate Predicate
      |
      | match?
      |
   +--+--+
   |     |
  No    Yes
   |     |
 404     v
       Forward
         |
         v
   Order Service
         |
         v
      Response
         |
         v
       Client
```

Later filters will sit around the forwarding process:

```text
Request
   |
Predicate
   |
Pre Filters
   |
Downstream Service
   |
Post Filters
   |
Response
```

But Stage 1 focuses on:

```text
Request → Predicate → Route → Service
```

---

# 12. Spring Cloud Gateway WebFlux vs MVC

Modern Spring Cloud Gateway has both reactive/WebFlux and MVC variants.

For our Gateway workout, use the variant already chosen/configured in your Gateway project rather than mixing both stacks.

Conceptually, both solve the same architectural problem:

```text
Client
  |
Gateway
  |
Services
```

The programming/runtime model differs.

For this curriculum, the important rule is:

> **Do not casually mix Spring MVC and WebFlux dependencies/configuration in the same Gateway project.**

When we inspect or modify the actual project, keep the dependency set consistent with the Gateway variant being used.

---

# 13. Minimum Order Service

We need one endpoint.

Example:

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {

        return new OrderResponse(
                id,
                "CUST-1001",
                250.00,
                "CONFIRMED"
        );
    }
}
```

Example DTO:

```java
public record OrderResponse(
        Long orderId,
        String customerId,
        double amount,
        String status
) {
}
```

Don't add:

- database
- JPA
- repository
- Kafka
- Payment calls

unless your existing Order Service already has them.

For this Gateway exercise, all we need is an HTTP endpoint that proves routing works.

---

# 14. Minimum Gateway Configuration

Example `application.yml`:

```yaml
server:
  port: 8000

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      routes:
        - id: order-service
          uri: http://localhost:9091
          predicates:
            - Path=/orders/**
```

Order Service:

```yaml
server:
  port: 9091

spring:
  application:
    name: order-service
```

The exact property layout can differ depending on the Spring Cloud Gateway variant/version in your project. Use the configuration style supported by the dependencies you actually created.

The architecture is what matters:

```text
Gateway :8000
    |
    | /orders/**
    v
Order :9091
```

---

# 15. What Happens for `/orders/101`?

Client sends:

```bash
curl http://localhost:8000/orders/101
```

Gateway sees:

```text
Path = /orders/101
```

Configured predicate:

```text
/orders/**
```

Result:

```text
MATCH
```

Gateway gets URI:

```text
http://localhost:9091
```

and forwards:

```text
http://localhost:9091/orders/101
```

Order Service returns:

```json
{
  "orderId": 101,
  "customerId": "CUST-1001",
  "amount": 250.0,
  "status": "CONFIRMED"
}
```

Gateway returns that response to the client.

The client does not need to know:

```text
9091
```

or even that an application named `order-service` exists.

---

# 16. First Hands-On Exercise

Do this manually or with Claude Code, but keep the scope strict.

## Step 1 — Order Service

Verify:

```text
GET http://localhost:9091/orders/101
```

works directly.

Expected:

```text
200 OK
```

Do not proceed until direct Order access works.

---

## Step 2 — Gateway

Configure one route:

```text
/orders/**
      ↓
http://localhost:9091
```

Run Gateway on:

```text
8000
```

---

## Step 3 — Test Through Gateway

Call:

```bash
curl -i http://localhost:8000/orders/101
```

Expected:

```text
HTTP/1.1 200
```

with Order response.

---

## Step 4 — Compare

Direct:

```text
localhost:9091/orders/101
```

Through Gateway:

```text
localhost:8000/orders/101
```

Both return the Order.

But externally, our intended architecture is:

```text
Client
   |
   X do not expose internal service directly
   |
Gateway
   |
Order
```

---

# 17. Verify That Gateway Really Routed the Request

Do not accept:

> "I got 200, so it must work."

Prove it.

Add a temporary Order Service log:

```java
log.info("Received request for order {}", id);
```

Call:

```bash
curl http://localhost:8000/orders/101
```

You should see the Order Service log.

Mental trace:

```text
curl
 |
 v
Gateway :8000
 |
 v
Order :9091
 |
 v
Controller
```

---

# 18. Break It Intentionally — Exercise 1

Change:

```yaml
uri: http://localhost:9091
```

to:

```yaml
uri: http://localhost:9999
```

Call:

```bash
curl -i http://localhost:8000/orders/101
```

Observe the response and Gateway logs.

Question:

> Did route matching fail, or did forwarding fail?

Answer:

The route still matched:

```text
/orders/**
```

but Gateway could not connect to the destination.

This distinction is important:

```text
Route not found
```

is different from:

```text
Destination unavailable
```

---

# 19. Break It Intentionally — Exercise 2

Restore the correct URI.

Now change:

```yaml
Path=/orders/**
```

to:

```yaml
Path=/payments/**
```

Call:

```text
/orders/101
```

Think before looking at logs.

The Gateway no longer has a matching route for that request.

Mental model:

```text
Request
   |
/orders/101
   |
Predicate /payments/**
   |
NO MATCH
```

This is a **routing/matching problem**, not a downstream connectivity problem.

---

# 20. Break It Intentionally — Exercise 3

Stop Order Service.

Keep Gateway running.

Call:

```bash
curl -i http://localhost:8000/orders/101
```

The route can still match, but the destination is unavailable.

This introduces an important distributed-systems principle:

> **Gateway availability does not imply backend availability.**

Later we will address this with:

- timeouts
- circuit breakers
- health/readiness
- observability
- graceful degradation

But not yet.

---

# 21. Common Problems

## Problem 1 — Gateway returns 404

Check:

```text
Does the Path predicate match?
```

Example:

```yaml
Path=/orders/**
```

but request is:

```text
/api/orders/101
```

No match.

---

## Problem 2 — Gateway route matches but downstream returns 404

Example:

Gateway forwards:

```text
/api/orders/101
```

but Order Controller exposes:

```text
/orders/101
```

This is an **external path vs internal path** mismatch.

Stage 2 will solve this using filters.

---

## Problem 3 — Connection refused

Check:

```text
Is Order Service running?
Is the port correct?
Is the hostname correct?
```

---

## Problem 4 — Works locally, fails in Kubernetes

Typical mistake:

```yaml
uri: http://localhost:9091
```

inside Gateway Pod.

Remember:

```text
localhost inside Gateway Pod
=
Gateway Pod
```

not Order Service.

Stage 3 fixes this using Kubernetes Services.

---

## Problem 5 — Gateway application does not start

Check:

- Spring Boot/Spring Cloud compatibility
- correct Gateway starter
- configuration property style for the selected Gateway variant/version
- accidental MVC/WebFlux dependency mixing
- YAML indentation

Do not randomly add dependencies until the application starts.

Understand the dependency problem first.

---

# 22. What Should the Gateway Own?

Good Gateway responsibilities include:

```text
Routing
Authentication at the edge
Coarse authorization
Rate limiting
CORS
Header policies
Observability
Some edge resilience
```

Bad Gateway responsibilities include:

```text
Calculate order total
Reserve inventory
Charge credit card
Apply coupon
Update database
Run Saga business workflow
```

Why?

Because then:

```text
API Gateway
```

becomes:

```text
Business Logic Monolith
```

or a **God Gateway**.

---

# 23. Should Service-to-Service Calls Go Through the Gateway?

Suppose:

```text
Order
  |
  v
Payment
```

Should Order call:

```text
Order → Gateway → Payment
```

Normally, **not just because a Gateway exists**.

External traffic:

```text
Client → Gateway → Order
```

Internal traffic can normally be:

```text
Order → Payment
```

through internal Kubernetes networking/service discovery.

Why avoid forcing all internal traffic through the edge Gateway?

It can create:

- unnecessary hop
- additional latency
- bottleneck
- tighter coupling to Gateway
- larger blast radius

Later, service mesh/service-to-service security may handle east-west concerns.

Remember:

```text
Gateway
≈ North-South traffic

Service-to-Service
≈ East-West traffic
```

This is a simplified mental model, but very useful.

---

# 24. API Gateway vs Load Balancer — First Introduction

Do not treat these as identical.

A load balancer primarily answers:

> Which instance should receive this traffic?

Example:

```text
ALB
 |
 +--> Gateway Pod 1
 +--> Gateway Pod 2
```

An API Gateway can answer:

> Which API/service should receive this request and what API policies should apply?

Example:

```text
Gateway
 |
 +-- /orders/**     → Order
 |
 +-- /payments/**   → Payment
 |
 +-- /inventory/**  → Inventory
```

In production we may have both:

```text
Internet
   |
ALB
   |
Gateway Pods
   |
Services
```

We will explore this deeply in Stage 14.

---

# 25. API Gateway vs Kubernetes Service

Another important distinction.

```text
API Gateway
```

provides API-level entry/routing/policies.

```text
Kubernetes Service
```

provides stable networking/load distribution to Pods.

Production flow:

```text
Client
  |
Gateway
  |
order-service   ← Kubernetes Service
  |
  +--> Order Pod 1
  +--> Order Pod 2
```

They solve different problems.

---

# 26. Why We Are Not Using Eureka

Traditional Spring Cloud examples often show:

```text
Gateway
   |
Eureka
   |
Order instances
```

You should understand Eureka conceptually for interviews.

But our target platform is Kubernetes.

Kubernetes already gives us:

```text
Service discovery
DNS
Service abstraction
Pod endpoint management
Load distribution
Health-aware endpoint participation
```

So our primary architecture will become:

```text
Gateway
   |
Kubernetes Service
   |
Pods
```

rather than introducing Eureka simply because older tutorials use it.

We will examine the trade-off properly in Stage 3.

---

# 27. Kubernetes Preview

Do **not** implement all of this in Stage 1.

This is where we are heading.

Local Stage 1:

```text
Gateway :8000
   |
localhost:9091
   |
Order
```

Kubernetes Stage 3:

```text
Minikube
   |
Gateway Deployment
   |
Gateway Pod
   |
http://order-service
   |
Order ClusterIP Service
   |
Order Pods
```

Production:

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
Microservice Pods
```

This gives context to what you are building today.

---

# 28. Failure Thinking

Consider:

```text
Client
  |
Gateway
  |
Order
```

There are already multiple failure points.

### Case 1

```text
Client cannot reach Gateway
```

Potential reasons:

- Gateway down
- load balancer issue
- DNS issue
- network issue

### Case 2

```text
Gateway cannot find matching route
```

Potential reason:

- route/predicate configuration

### Case 3

```text
Gateway finds route but cannot connect to Order
```

Potential reasons:

- Order down
- wrong hostname
- wrong port
- networking problem

### Case 4

```text
Gateway reaches Order but Order returns 500
```

Gateway connectivity works.

The business service failed.

Architects must distinguish these layers.

---

# 29. Troubleshooting Mental Model

When Gateway request fails, inspect in this order:

```text
1. Did request reach Gateway?
            |
            v
2. Did a route match?
            |
            v
3. What destination was selected?
            |
            v
4. Can Gateway reach destination?
            |
            v
5. Did downstream service receive request?
            |
            v
6. What did downstream service return?
```

Later Kubernetes adds:

```text
Gateway
   |
Kubernetes DNS
   |
Service
   |
Endpoints
   |
Pods
```

This layered troubleshooting approach is far more useful than randomly changing YAML.

---

# 30. Stage 1 Coding-Agent Prompt

If you use Claude Code inside IntelliJ, give it a **bounded** task.

Example:

```text
Inspect the existing api-gateway and order-service projects first.

We are learning API Gateway Stage 1: basic static routing.

Requirements:

1. Do not redesign the projects.
2. Keep the existing Java/Spring versions unless there is a compatibility issue.
3. Add only what is necessary to:
   - run Order Service on port 9091
   - expose GET /orders/{id}
   - run API Gateway on port 8000
   - route /orders/** to http://localhost:9091
4. Do not add:
   - Eureka
   - Kubernetes configuration
   - Redis
   - security
   - circuit breaker
   - retry
   - Kafka
   - database
5. Before modifying files, tell me which files need changes and why.
6. After changes, tell me exactly how to run and test them.
7. Keep the implementation minimal because this is a learning exercise.
```

Do not ask:

```text
Make my API Gateway production ready.
```

That would defeat the purpose of Stage 1.

---

# 31. Hands-On Challenge — Do This Yourself

After the first Order route works, **do not ask the coding agent to do the next part immediately**.

Add Payment yourself.

Payment Service:

```text
port: 9092
```

Endpoint:

```text
GET /payments/{id}
```

Add Gateway route:

```text
/payments/**
        ↓
http://localhost:9092
```

Expected:

```text
Client
   |
   v
Gateway :8000
   |
   +---- /orders/** ----> Order :9091
   |
   +---- /payments/** --> Payment :9092
```

Test:

```bash
curl http://localhost:8000/orders/101
```

and:

```bash
curl http://localhost:8000/payments/5001
```

This is your Stage 1 coding exercise.

---

# 32. Architect Challenge 1

Requirement:

> We now have three Order Service instances.

```text
9091
9092
9093
```

Current Gateway configuration:

```yaml
uri: http://localhost:9091
```

Question:

> What is wrong with this design?

Think before reading the answer.

## Answer

Gateway knows only one instance.

If:

```text
9091
```

fails, the other instances are useless to this route.

Also, instance locations are dynamic in a real orchestrated environment.

We need a stable service abstraction/load-balancing mechanism.

In our architecture, Kubernetes will solve this:

```text
Gateway
   |
order-service
   |
Kubernetes Service
   |
+--> Pod 1
+--> Pod 2
+--> Pod 3
```

This is Stage 3/4.

---

# 33. Architect Challenge 2

Question:

> Should we expose Order Service directly to the internet as well as through Gateway?

Normally:

```text
No.
```

If clients can bypass Gateway:

```text
Client --------> Gateway --------> Order
   \
    \----------------------------> Order
```

then Gateway policies may be bypassed.

For example:

- authentication
- rate limiting
- WAF-related flow
- request policies
- logging
- API governance

In Kubernetes, backend services will normally be internal:

```text
ClusterIP
```

while the intended external entry point is exposed through the edge architecture.

---

# 34. Architect Challenge 3

Question:

> If Gateway is the only public entry point, isn't it a single point of failure?

A **single Gateway instance** can be.

The solution is not to avoid Gateway automatically.

Production design uses:

```text
             Load Balancer
                  |
        +---------+---------+
        |                   |
   Gateway Pod 1       Gateway Pod 2
        |                   |
        +---------+---------+
                  |
               Services
```

This is why Gateway should generally be stateless and horizontally scalable.

Stage 4 covers this deeply.

---

# 35. Architect Challenge 4

Question:

> Why don't we put all common business logic in Gateway so every service can reuse it?

Because that creates centralized business coupling.

Example:

```text
Gateway
 |
 +-- pricing rules
 +-- inventory rules
 +-- payment rules
 +-- order validation
 +-- customer rules
```

Now every domain depends on the Gateway.

The Gateway becomes difficult to:

- change
- scale independently
- test
- deploy
- own

Shared edge policy is good.

Centralized domain logic is usually not.

---

# 36. Interview Questions

Try answering these aloud.

### Q1. What is an API Gateway?

Expected direction:

> A controlled entry point for client traffic that routes requests to backend services and can enforce cross-cutting edge policies.

---

### Q2. Why use a Gateway instead of letting clients call services directly?

Discuss:

- hides internal topology
- single controlled entry
- routing
- security
- rate limiting
- policy consistency
- client simplification
- API evolution

Also mention the trade-off:

- additional hop
- operational component
- possible bottleneck/failure point

---

### Q3. What is a route in Spring Cloud Gateway?

Discuss:

```text
ID
Predicate(s)
Destination URI
Filters (later)
```

---

### Q4. What is a predicate?

> A condition used to determine whether a route applies to an incoming request.

---

### Q5. What happens when no route matches?

The Gateway cannot forward using that route configuration; typically the client receives a not-found style response.

---

### Q6. What happens when route matches but downstream service is unavailable?

Route selection succeeds, but forwarding fails.

This is different from route-not-found.

---

### Q7. Why is `localhost` unsuitable when Gateway and Order run in separate Kubernetes Pods?

Because:

```text
localhost
```

always refers to the current network environment/container/Pod, not another Pod.

Use Kubernetes Service DNS.

---

### Q8. Why don't we need Eureka for our Kubernetes implementation?

Because Kubernetes provides native service discovery through Services/DNS and maintains backend endpoints dynamically.

---

### Q9. Should internal service calls always go through the API Gateway?

Usually no.

The Gateway primarily handles north-south/client traffic. Internal east-west communication can use Kubernetes Services directly, depending on architecture and security requirements.

---

### Q10. What business logic belongs in Gateway?

Normally very little domain logic.

Gateway should focus on cross-cutting edge concerns.

---

# 37. Principal-Level Follow-Up

Suppose an interviewer says:

> "You introduced Spring Cloud Gateway, but AWS already has ALB and API Gateway. Why are you operating another component?"

A weak answer is:

> "Because Spring Cloud Gateway is used in microservices."

A stronger answer starts with requirements:

```text
What policies do we need?
How much custom routing?
Are we AWS-only?
Do we need managed API management?
What is our traffic profile?
Who operates the platform?
What are latency/cost constraints?
```

Then choose.

It is completely valid for an architecture to **not use Spring Cloud Gateway** if managed AWS capabilities satisfy the requirements better.

An architect chooses based on trade-offs, not framework loyalty.

We will make this answer much stronger by Stage 14.

---

# 38. Anti-Patterns to Remember

## Anti-Pattern 1 — God Gateway

```text
Gateway contains business logic
```

Avoid.

---

## Anti-Pattern 2 — Hard-coded production instances

```text
Gateway → 10.1.5.23:9091
```

Avoid in dynamic Kubernetes environments.

---

## Anti-Pattern 3 — Everything through Gateway

```text
Order → Gateway → Payment
Inventory → Gateway → Product
Payment → Gateway → Customer
```

Avoid making the edge Gateway an unnecessary hub for all internal traffic.

---

## Anti-Pattern 4 — Gateway is the only security boundary

```text
Gateway validates user
Services trust everything
```

This can be dangerous.

Defense-in-depth comes later.

---

## Anti-Pattern 5 — One Gateway replica in production

Creates availability risk.

---

## Anti-Pattern 6 — Adding every cross-cutting concern on Day 1

This produces a complex Gateway nobody on the team understands.

Our curriculum intentionally avoids this.

---

# 39. Quick Revision

Remember these six ideas from Stage 1.

## 1

```text
Gateway = controlled entry point
```

## 2

```text
Route =
Predicate + Destination
```

## 3

```text
Predicate =
Does this request match?
```

## 4

```text
Destination URI =
Where should Gateway send it?
```

## 5

```text
localhost works only for our initial local-process setup
```

Kubernetes later uses:

```text
http://order-service
```

## 6

```text
Gateway routes business requests.

Gateway should not become the business application.
```

---

# 40. What You Should Be Able to Draw

Without looking at notes:

```text
Client
   |
   | /orders/101
   v
Gateway :8000
   |
   | Path=/orders/**
   |
   | URI=http://localhost:9091
   v
Order Service :9091
   |
   v
OrderController
```

Then explain every arrow.

---

# 41. Stage 1 Completion Checklist

Do not move to Stage 2 until these are done.

## Understanding

- [ ] I can explain why an API Gateway is used.
- [ ] I understand route.
- [ ] I understand Path predicate.
- [ ] I understand destination URI.
- [ ] I understand Gateway vs business service responsibility.
- [ ] I understand why `localhost` will change in Kubernetes.
- [ ] I understand Gateway vs Kubernetes Service at a high level.
- [ ] I understand why we are not using Eureka as our primary approach.

## Implementation

- [ ] Order Service runs.
- [ ] `GET /orders/{id}` works directly.
- [ ] Gateway runs.
- [ ] `/orders/**` routes through Gateway.
- [ ] Payment route was added by me as the exercise.

## Failure Practice

- [ ] I tested a wrong destination port.
- [ ] I tested a non-matching predicate.
- [ ] I stopped Order Service and observed Gateway behavior.
- [ ] I can distinguish route failure from downstream failure.

## Interview

- [ ] I can answer why Gateway is needed.
- [ ] I can explain its disadvantages.
- [ ] I can explain why internal calls do not automatically go through Gateway.
- [ ] I can explain why a single Gateway instance is a risk.
- [ ] I can explain why Kubernetes changes service addressing.

---

# 42. Stop Here

Once Stage 1 works, **do not immediately add more features**.

Your working architecture should remain:

```text
Client
   |
Gateway
   |
+---- Order
|
+---- Payment
```

The next stage is:

```text
02-predicates-and-filters.md
```

There we will learn:

```text
Path
Method
Header
Query
Route precedence
Pre/Post filters
Global filters
StripPrefix
RewritePath
Custom filter
```

and evolve the public API toward something cleaner such as:

```text
/api/orders/**
```

while keeping internal service paths independent.

---

# Stage 1 One-Line Summary

> **Spring Cloud Gateway receives a client request, evaluates route predicates, selects a destination, and forwards the request while hiding the internal service location from the client.**
