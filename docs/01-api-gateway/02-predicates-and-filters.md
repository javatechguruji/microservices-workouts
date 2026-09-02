# API Gateway — Stage 2: Predicates & Filters

> **Parent:** `microservices-workouts/docs/01-api-gateway/Topics.md`  
> **Previous:** `01-basic-routing.md`  
> **Stage:** 02  
> **Technology:** Spring Cloud Gateway  
> **Local Runtime:** IntelliJ first; Minikube/Kubernetes architecture kept in view  
> **Production Target:** AWS EKS  
> **Example Domain:** E-Commerce — Gateway, Order, Payment, Inventory  
> **Learning style:** Understand → Implement → Test → Break → Fix → Architect Thinking → Interview

---

# 1. Stage 2 Goal

Stage 1 gave us:

```text
Client
   |
   v
Gateway :8000
   |
   +---- /orders/** ----> Order Service
   |
   +---- /payments/** --> Payment Service
```

Now we make routing more intelligent.

Spring Cloud Gateway needs to answer two different questions:

```text
1. Should this route handle this request?
                ↓
            PREDICATE


2. What should happen to the request/response?
                ↓
              FILTER
```

That distinction is the heart of Stage 2.

By the end of this stage you should be comfortable with:

- Path predicates
- Method predicates
- Header predicates
- Query predicates
- Host predicate awareness
- Combining predicates
- Route ordering and overlapping routes
- Pre-filters
- Post-filters
- Built-in filters
- `StripPrefix`
- `RewritePath`
- Request/response headers
- Global filters
- Custom filters
- Filter ordering
- Trusted/internal headers
- What logic should **not** be placed in filters
- Kubernetes/production implications

---

# 2. Why Predicates Matter

Suppose all traffic enters through:

```text
https://api.myshop.com
```

We have:

```text
GET  /api/orders/101
POST /api/orders

POST /api/payments

GET  /api/inventory/P100

GET  /actuator/health
```

Gateway must determine:

```text
Which request belongs to which route?
```

That decision is made using predicates.

Example:

```text
Request
   |
GET /api/orders/101
   |
   v
Path=/api/orders/**
   |
 MATCH
   |
   v
Order Route
```

But predicates can use more than path.

For example:

```text
Path=/api/orders/**
AND
Method=GET
```

Now the route matches only GET requests.

---

# 3. Why Filters Matter

Suppose the public API is:

```text
/api/orders/101
```

but Order Service exposes:

```text
/orders/101
```

The Gateway needs to transform the request.

```text
Client
   |
   | /api/orders/101
   v
Gateway
   |
   | remove /api
   v
/orders/101
   |
   v
Order Service
```

That transformation is a filter responsibility.

Filters can also:

- add headers
- remove headers
- rewrite paths
- add response headers
- apply rate limiting
- invoke circuit breakers
- support observability

Later stages use more powerful filters.

For now, understand the mechanism.

---

# 4. Core Mental Model

Remember this throughout the curriculum:

```text
Predicate
    =
Should this route run?


Filter
    =
What should happen before/after forwarding?
```

Another useful picture:

```text
Incoming Request
      |
      v
  Predicates
      |
    Match?
      |
      v
 Pre-Filters
      |
      v
Downstream Service
      |
      v
 Post-Filters
      |
      v
   Response
```

---

# 5. Route Anatomy

Conceptually:

```yaml
routes:
  - id: order-service
    uri: http://localhost:9091

    predicates:
      - Path=/api/orders/**

    filters:
      - StripPrefix=1
```

Three important parts:

```text
Route
 |
 +---- WHERE?
 |       URI
 |
 +---- WHEN?
 |       Predicates
 |
 +---- WHAT PROCESSING?
         Filters
```

This is one of the most useful mental models for Spring Cloud Gateway.

---

# 6. Path Predicate

You already used this in Stage 1.

Example:

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
/api/orders/101
```

Now we want a cleaner external API:

```text
/api/orders/**
```

So:

```yaml
predicates:
  - Path=/api/orders/**
```

But this creates another problem.

Order Service still exposes:

```text
/orders/**
```

Gateway would naturally forward:

```text
/api/orders/101
```

while Order expects:

```text
/orders/101
```

This is where filters enter.

---

# 7. `StripPrefix`

Suppose:

```text
Incoming:
/api/orders/101
```

Configuration:

```yaml
filters:
  - StripPrefix=1
```

The first path segment:

```text
/api
```

is removed.

Result:

```text
/orders/101
```

So:

```text
Client
   |
   | /api/orders/101
   v
Gateway
   |
 StripPrefix=1
   |
   v
/orders/101
   |
   v
Order Service
```

This allows your **public API structure** to differ from the **internal controller path**.

---

# 8. Why External and Internal Paths May Differ

This is an architectural boundary.

External clients may see:

```text
/api/v1/orders/101
```

Internally, the service may expose:

```text
/orders/101
```

Why separate them?

Because:

```text
External API contract
```

and:

```text
Internal service topology
```

are different concerns.

You may later:

- reorganize services
- split a service
- merge services
- change internal endpoints
- introduce API versions

without necessarily forcing every external client to understand the internal architecture.

The Gateway can help maintain that boundary.

---

# 9. `RewritePath`

`StripPrefix` is simple.

Sometimes we need more precise transformations.

Example:

```text
Public:
/api/v1/orders/101

Internal:
/orders/101
```

Conceptually:

```yaml
filters:
  - RewritePath=/api/v1/orders/(?<segment>.*), /orders/${segment}
```

Request:

```text
/api/v1/orders/101
```

becomes:

```text
/orders/101
```

Use rewriting only when there is a real contract reason.

Do not create complicated regex routing simply because the framework supports it.

---

# 10. `StripPrefix` vs `RewritePath`

Use the simpler tool when possible.

## StripPrefix

Good when:

```text
/api/orders/101
```

needs to become:

```text
/orders/101
```

Configuration:

```text
remove first segment
```

Simple.

---

## RewritePath

Useful when:

```text
/external/customer-orders/101
```

must become:

```text
/orders/101
```

or when API version/path structure requires a more specific transformation.

Architect principle:

> Prefer simple routing rules. Complex rewrite rules make production troubleshooting harder.

---

# 11. Method Predicate

Suppose:

```text
GET /api/orders/101
```

should go to a read path.

A Method predicate can restrict a route:

```yaml
predicates:
  - Path=/api/orders/**
  - Method=GET
```

Both conditions must match.

```text
GET /api/orders/101
```

matches.

```text
POST /api/orders
```

does not match that route.

---

# 12. Combining Predicates

Consider:

```yaml
predicates:
  - Path=/api/orders/**
  - Method=GET
```

Mental model:

```text
Path matches?
     |
    YES
     |
Method is GET?
     |
    YES
     |
 Route Matches
```

Predicates on the same route are effectively combined as conditions that must be satisfied.

This allows precise routing.

But do not overcomplicate routing with dozens of predicates when service/API design could be simpler.

---

# 13. Header Predicate

Sometimes routing depends on a header.

Example:

```text
X-API-Version: 2
```

Conceptually:

```yaml
predicates:
  - Header=X-API-Version, 2
```

Possible use cases:

- controlled API migration
- testing
- partner routing
- temporary compatibility

But be careful.

Do not turn Gateway into a giant business-rule engine:

```text
if customer type = GOLD
and region = US
and cart > $1000
and product category = electronics
route here...
```

That business logic belongs elsewhere.

---

# 14. Query Predicate

Example request:

```text
GET /api/orders?status=OPEN
```

A Query predicate can check for a parameter.

Conceptually:

```yaml
predicates:
  - Query=status
```

or match a value pattern where appropriate.

This can be useful for specialized routing, but path/API design should normally remain understandable without hidden routing behavior.

---

# 15. Host Predicate

Suppose:

```text
customer.api.myshop.com
admin.api.myshop.com
```

Host-based routing can separate traffic:

```text
customer.api.myshop.com
        |
        v
Customer-facing routes
```

and:

```text
admin.api.myshop.com
        |
        v
Admin routes
```

This becomes more interesting when combined with:

- ALB
- Ingress
- DNS
- TLS certificates

We only need awareness here.

---

# 16. Other Predicates — Awareness

Spring Cloud Gateway supports other routing conditions as well, such as concepts around:

- cookies
- remote address
- time windows

You do **not** need to memorize every built-in predicate for an architect interview.

Know:

```text
Path
Method
Header
Query
Host
```

well.

Then explain:

> Spring Cloud Gateway provides additional predicates for other request attributes when needed.

Architect interviews care more about **why you chose a routing rule** than whether you memorized every factory name.

---

# 17. Route Overlap

Suppose:

```text
Route A:
/api/orders/**

Route B:
/api/**
```

Request:

```text
/api/orders/101
```

can potentially fit both patterns.

This introduces:

```text
route specificity
route ordering
```

You should avoid ambiguous routing where possible.

Good routing should make it obvious:

```text
/api/orders/**      → Order
/api/payments/**    → Payment
/api/inventory/**   → Inventory
```

Avoid a maze of generic overlapping patterns.

---

# 18. Route Ordering

When routes can overlap, ordering becomes important.

Conceptually:

```text
More specific route
        ↓
Generic route
```

Example:

```text
/api/admin/orders/**
```

should not accidentally be swallowed by a generic:

```text
/api/**
```

The exact configuration mechanism depends on how routes are declared, but the architectural lesson is:

> **Do not rely on accidental route ordering. Make route intent explicit and test overlapping cases.**

---

# 19. What Is a Gateway Filter?

A filter intercepts the request/response around downstream forwarding.

Conceptually:

```text
Request
   |
   v
Filter A
   |
   v
Filter B
   |
   v
Service
   |
   v
Filter B
   |
   v
Filter A
   |
   v
Response
```

This is similar to middleware/interceptor concepts you already know from Spring applications.

---

# 20. Pre-Filter

A pre-filter runs before the downstream request.

Examples:

```text
Add correlation ID
Remove untrusted header
Rewrite path
Add request header
Validate edge policy
```

Flow:

```text
Client
  |
  v
Pre Filter
  |
  v
Order Service
```

---

# 21. Post-Filter

A post-filter participates after downstream processing as the response returns.

Examples:

```text
Add response header
Record timing
Clean response metadata
```

Flow:

```text
Order Service
   |
   v
Post Filter
   |
   v
Client
```

Some filters have behavior spanning both sides of the downstream call.

The important mental model is:

```text
request-side work
      ↓
downstream
      ↓
response-side work
```

---

# 22. Route-Specific Filter

Suppose only Order APIs need a particular transformation.

Attach the filter only to:

```text
order-service route
```

Example:

```yaml
- id: order-service
  uri: http://localhost:9091
  predicates:
    - Path=/api/orders/**
  filters:
    - StripPrefix=1
```

Payment can have its own route configuration.

---

# 23. Global Filter

Some behavior should apply to nearly every request.

Examples:

```text
Correlation ID
Request timing
Common logging metadata
Security-related header cleanup
```

A Global Filter is applied broadly rather than repeating the same logic on every route.

Conceptually:

```text
                Global Filter
                     |
        +------------+------------+
        |            |            |
      Order        Payment     Inventory
```

Use global filters carefully.

A mistake there affects the entire platform.

---

# 24. Route Filter vs Global Filter

Use a **route filter** when behavior belongs only to specific APIs.

Use a **global filter** when behavior truly belongs to the entire Gateway.

Example:

```text
StripPrefix
```

may be route-specific.

Example:

```text
Correlation ID propagation
```

may be global.

Architect question:

> What is the blast radius if this filter fails?

For a global filter:

```text
Potentially every API.
```

That is why global filters should remain simple and well tested.

---

# 25. Adding a Request Header

Suppose the Gateway wants to add:

```text
X-Gateway-Source: spring-cloud-gateway
```

A built-in request-header filter can do this.

Conceptually:

```yaml
filters:
  - AddRequestHeader=X-Gateway-Source, spring-cloud-gateway
```

Then Order receives:

```text
X-Gateway-Source: spring-cloud-gateway
```

This is useful for learning.

But do not use arbitrary headers as a replacement for secure identity.

---

# 26. Removing a Request Header

Imagine external clients send:

```text
X-Internal-User-Id: ADMIN
```

If internal services trust this header, that is dangerous.

The Gateway can remove untrusted client-supplied internal headers before forwarding.

Concept:

```text
Client sends:
X-Internal-User-Id: ADMIN
        |
        v
Gateway removes it
        |
        v
Service never trusts spoofed value
```

Later security stages will establish identity using validated tokens.

---

# 27. Trusted Headers — Important Security Principle

Never assume:

```text
X-User-Id
X-Tenant-Id
X-Role
```

is trustworthy merely because it exists.

A client can send arbitrary HTTP headers.

If Gateway creates trusted internal metadata:

1. remove the external version first,
2. derive the trusted value from validated identity,
3. restrict backend network exposure so clients cannot bypass Gateway,
4. preferably let downstream services validate cryptographic identity when appropriate.

We will cover this deeply in Stage 5/6.

---

# 28. Adding a Response Header

For learning, Gateway could add:

```text
X-Gateway-Version: v1
```

to the response.

This proves post-processing.

Do not expose unnecessary internal infrastructure details in production headers.

---

# 29. Removing Response Headers

Sometimes downstream systems expose headers clients should not receive.

Gateway can sanitize selected response headers.

Potential categories:

```text
internal infrastructure metadata
debug information
technology/version information
```

But security should not rely only on hiding headers.

The primary goal is reducing unnecessary exposure.

---

# 30. Custom Filter — When Do We Need One?

Built-in filters should be preferred when they solve the problem.

Create custom code when the requirement genuinely cannot be expressed safely using existing filters.

Possible example:

```text
Generate/normalize a correlation ID
```

Conceptually:

```java
@Component
public class CorrelationIdFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        // read/generate correlation id
        // mutate request
        // continue chain

        return chain.filter(exchange);
    }
}
```

The exact implementation depends on your chosen Gateway stack/version.

Do not copy custom filters blindly.

Understand:

```text
Why does this need custom code?
Could a built-in filter solve it?
What happens if it throws an exception?
Does it block?
What is its blast radius?
```

---

# 31. Keep Filters Non-Blocking Where Required

If using the reactive Spring Cloud Gateway stack, do not introduce blocking work into the request path casually.

Bad example:

```text
Global Filter
   |
   +--> slow blocking database query
   |
   +--> blocking external HTTP call
```

Now every request may suffer.

Gateway is a high-throughput infrastructure component.

Avoid:

- business DB calls
- slow blocking logic
- large computations
- unnecessary remote calls

inside filters.

---

# 32. Filter Ordering

Suppose:

```text
Filter A = Remove external X-User-Id
Filter B = Add trusted X-User-Id
```

Order matters.

Desired:

```text
Remove untrusted value
       ↓
Validate/derive identity
       ↓
Add trusted value
```

Wrong order could accidentally remove the trusted value or preserve spoofed data.

Filter ordering becomes especially important when combining:

- security
- logging
- tracing
- path rewriting
- retries
- circuit breakers

Architect lesson:

> Cross-cutting policies form a pipeline. Ordering is part of the design.

---

# 33. Our Stage 2 Target API

Stage 1 exposed:

```text
/orders/**
/payments/**
```

Now evolve the public API to:

```text
/api/orders/**
/api/payments/**
```

Internal services remain:

```text
/orders/**
/payments/**
```

Architecture:

```text
Client
   |
   | /api/orders/101
   v
Gateway
   |
 StripPrefix=1
   |
   | /orders/101
   v
Order Service
```

Payment:

```text
Client
   |
   | /api/payments/5001
   v
Gateway
   |
 StripPrefix=1
   |
   | /payments/5001
   v
Payment Service
```

This is the main Stage 2 implementation.

---

# 34. Example Route Configuration

Conceptually:

```yaml
spring:
  cloud:
    gateway:
      routes:

        - id: order-service
          uri: http://localhost:9091
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1

        - id: payment-service
          uri: http://localhost:9092
          predicates:
            - Path=/api/payments/**
          filters:
            - StripPrefix=1
```

The exact configuration namespace can vary with the Spring Cloud Gateway variant/version in your project.

Do not replace working project dependencies/configuration just to make your file identical to this example.

Preserve your existing project setup.

---

# 35. Trace One Request

Request:

```text
GET /api/orders/101
```

## Step 1 — Gateway receives it

```text
Path = /api/orders/101
```

## Step 2 — Order predicate

```text
Path=/api/orders/**
```

matches.

## Step 3 — Filter

```text
StripPrefix=1
```

transforms:

```text
/api/orders/101
```

to:

```text
/orders/101
```

## Step 4 — Destination

```text
http://localhost:9091
```

## Step 5 — Forward

```text
http://localhost:9091/orders/101
```

## Step 6 — Response

Order returns response.

Gateway returns it to the client.

---

# 36. Hands-On Exercise 1 — Public `/api` Prefix

Change your Stage 1 routes so clients call:

```bash
curl -i http://localhost:8000/api/orders/101
```

and:

```bash
curl -i http://localhost:8000/api/payments/5001
```

But the services should continue exposing:

```text
/orders/101
/payments/5001
```

Use:

```text
Path predicate
+
StripPrefix
```

Do not modify controller paths merely to make Gateway routing easier.

The Gateway should adapt the external path.

---

# 37. Hands-On Exercise 2 — Method Predicate

Create a learning route where appropriate that matches:

```text
GET
```

but not:

```text
POST
```

Observe what happens when:

```text
Path matches
```

but:

```text
Method does not.
```

Important question:

> Did downstream service reject the request, or did Gateway fail to select this route?

Know the difference.

---

# 38. Hands-On Exercise 3 — Request Header

Add a temporary learning header:

```text
X-Gateway-Source: api-gateway
```

Have Order Service log it.

Request:

```text
Client
   |
   v
Gateway adds header
   |
   v
Order logs header
```

Once understood, you may remove this learning-only header later.

---

# 39. Hands-On Exercise 4 — Response Header

Add a simple response header from Gateway.

For example:

```text
X-Gateway-Lab: stage-2
```

Verify:

```bash
curl -i ...
```

and observe it.

Again, this is for understanding filter behavior, not a recommendation to expose random headers in production.

---

# 40. Hands-On Exercise 5 — Remove Spoofed Header

Send:

```text
X-Internal-Role: ADMIN
```

from the client.

Configure Gateway to remove it before forwarding.

Order Service should confirm it did not receive the spoofed value.

This exercise prepares you for later security architecture.

---

# 41. Break It Intentionally — Wrong `StripPrefix`

Suppose request is:

```text
/api/orders/101
```

and you configure:

```text
StripPrefix=2
```

Result becomes roughly:

```text
/101
```

Order expects:

```text
/orders/101
```

Downstream may return:

```text
404
```

Important:

```text
Gateway route matched.
Gateway reached Order.
Order could not match the transformed path.
```

That is different from Gateway route-not-found.

---

# 42. Break It Intentionally — Missing Filter

Remove:

```text
StripPrefix=1
```

Request:

```text
/api/orders/101
```

Gateway forwards:

```text
/api/orders/101
```

Order expects:

```text
/orders/101
```

Result:

```text
downstream 404
```

Again, route selection may be correct while path transformation is wrong.

---

# 43. Break It Intentionally — Wrong Method

Configure:

```text
Path=/api/orders/**
Method=POST
```

Then send:

```text
GET /api/orders/101
```

The path matches but the complete route does not.

Use logs/behavior to understand the distinction.

---

# 44. Break It Intentionally — Overlapping Route

Create two temporary routes:

```text
/api/orders/**
```

and:

```text
/api/**
```

Point them to distinguishable destinations or add distinguishable response headers.

Call:

```text
/api/orders/101
```

Observe which route wins in your configuration.

Then remove the ambiguous experiment.

The goal is not to keep bad routing.

The goal is to understand why route overlap is dangerous.

---

# 45. Debugging Predicates

When a request does not route as expected, ask:

```text
1. What exact path entered Gateway?
2. Which routes could match?
3. Does Path match?
4. Does Method match?
5. Does Header match?
6. Does Query match?
7. Is another route overlapping?
8. What route was actually selected?
```

Do not jump immediately to:

```text
Order Service is broken.
```

The request may never have reached it.

---

# 46. Debugging Filters

If route matches but downstream behaves unexpectedly:

```text
1. What was the original request?
2. Which filters ran?
3. What path was produced?
4. Which headers were added/removed?
5. What destination URI was used?
6. Did downstream receive the request?
7. What exactly did downstream receive?
```

This becomes very important later with security and tracing.

---

# 47. Gateway Logging During Development

For learning, temporarily increasing Gateway routing logs can help understand route selection.

But production logging must be controlled.

Avoid logging:

- Authorization headers
- access tokens
- cookies
- passwords
- sensitive query parameters
- personal data unnecessarily

Later observability stage will formalize this.

---

# 48. Architect Scenario — API Versioning

Suppose clients use:

```text
/api/v1/orders/101
```

A new Order API becomes:

```text
/api/v2/orders/101
```

Possible approaches include:

```text
Path-based versioning
Header-based versioning
Different backend versions
Compatibility inside service
```

Gateway can participate in version routing.

But ask:

> Should Gateway own version compatibility logic, or merely route versions?

Usually Gateway should avoid becoming the place where complex response transformations and domain compatibility rules accumulate.

---

# 49. Architect Scenario — Canary Routing

Requirement:

> Send 5% of traffic to Order v2.

Do not implement this in Stage 2.

Just understand the architectural question.

Possible layers include:

```text
ALB
Ingress
Gateway
Service Mesh
Progressive delivery tooling
```

The key architect question is:

> **Which layer should own traffic splitting?**

Do not implement the same canary policy in three places.

---

# 50. Architect Scenario — Mobile vs Web

Suppose mobile and web require different payloads.

Bad immediate reaction:

```text
Put lots of transformation logic in Gateway.
```

Consider whether you actually need:

```text
BFF — Backend for Frontend
```

Architecture:

```text
Mobile
   |
Mobile BFF
   |
Services

Web
   |
Web BFF
   |
Services
```

Gateway can still provide common edge policies.

We compare Gateway vs BFF deeply later.

---

# 51. Architect Scenario — Business Routing

Requirement:

> Premium customers should use a different pricing algorithm.

Should Gateway route based on:

```text
customer premium status
```

and choose business implementation?

Usually this belongs within the domain/service architecture rather than edge routing.

Gateway routing should generally be based on API/infrastructure concerns, not become the central business decision engine.

---

# 52. Architect Scenario — Internal Header

A team proposes:

```text
Gateway validates JWT
        ↓
adds X-User-Role=ADMIN
        ↓
Order trusts header completely
```

Ask:

```text
Can clients reach Order directly?
Can a client spoof the header?
Who removes external copies?
Is the internal network trusted?
Should Order validate JWT itself?
How is service identity established?
```

This becomes Stage 5/6.

The important lesson now:

> Header manipulation is easy. Trust architecture is not.

---

# 53. Kubernetes View

Stage 2 still uses local destination URIs while learning routing.

But the filters/predicates you learn remain relevant when Gateway moves into Kubernetes.

Later:

```text
Client
   |
   | /api/orders/101
   v
Gateway Pod
   |
 StripPrefix=1
   |
   | /orders/101
   v
order-service
   |
Kubernetes Service
   |
Order Pod
```

The routing logic stays conceptually similar.

Only service addressing changes.

---

# 54. Kubernetes Ingress + Gateway Preview

Eventually:

```text
Internet
   |
Ingress / ALB
   |
Gateway
   |
Services
```

Question:

> If Ingress already supports path routing, why do we need Gateway path routing too?

Excellent architect question.

Possible answer:

Ingress/ALB may own coarse infrastructure routing:

```text
api.myshop.com → Gateway
```

while Gateway owns API-aware application routing/policies:

```text
/api/orders/** → Order
/api/payments/** → Payment
```

But this is not mandatory.

If infrastructure routing alone satisfies requirements, adding an application Gateway may be unnecessary.

We revisit this in Stage 14.

---

# 55. Filter Responsibility Rule

Before adding any Gateway filter, ask:

```text
Is this an EDGE concern?
```

Good candidates:

```text
Path transformation
CORS
Rate limiting
Request header sanitation
Authentication
Tracing metadata
```

Suspicious candidates:

```text
Calculate discount
Validate inventory availability
Check customer's credit balance
Create order
Charge payment
```

Those are domain concerns.

---

# 56. Performance Thinking

Every filter is in the request path.

Suppose:

```text
100,000 requests/sec
```

and every request executes:

```text
Global Filter A
Global Filter B
Global Filter C
Global Filter D
Global Filter E
```

Even small overhead can matter.

Therefore:

```text
Keep filters small
Avoid blocking work
Avoid unnecessary network calls
Avoid repeated parsing
Measure latency
```

This becomes important when Gateway scales.

---

# 57. Failure Blast Radius

Consider:

```text
Order-specific filter fails
```

Potential impact:

```text
Order APIs
```

Now:

```text
Global filter fails
```

Potential impact:

```text
Order
Payment
Inventory
Customer
Everything
```

Architect principle:

> Centralized components reduce duplication but increase blast radius.

Gateway is a classic example.

---

# 58. Anti-Pattern — Filter as Business Service

Bad:

```text
Gateway Filter
   |
   +--> Database
   |
   +--> Customer Service
   |
   +--> Pricing Service
   |
   +--> Inventory Service
   |
   v
Decide whether request can continue
```

Now Gateway is tightly coupled to many domain services.

One dependency slowdown can affect the entire edge.

Avoid unless there is a very strong infrastructure/security reason and the failure model is carefully designed.

---

# 59. Anti-Pattern — Giant Route File

Imagine:

```text
800 routes
2,000 rewrite rules
hundreds of header conditions
```

This becomes difficult to:

- understand
- test
- deploy
- review
- troubleshoot

Possible architectural questions:

```text
Are service boundaries wrong?
Should routes be generated/configured differently?
Should domains have separate gateways?
Do we need BFFs?
Do we need API management?
```

Do not treat Gateway configuration complexity as unavoidable.

---

# 60. Anti-Pattern — Hidden Magic

Avoid routing where developers cannot predict:

```text
Where will this request go?
```

Routing should be observable and understandable.

Good:

```text
/api/orders/** → Order
```

Harder:

```text
path + cookie + 3 headers + query regex + time + custom filter
```

Complex routing can be valid, but should be justified.

---

# 61. Custom Filter Exercise — Correlation ID Preview

We will cover tracing deeply in Stage 10.

For Stage 2, you may optionally implement a simple correlation-ID filter if your existing project does not already have one.

Desired behavior:

```text
Client
   |
   | X-Correlation-Id: ABC
   v
Gateway
   |
   | ABC
   v
Order
```

If missing:

```text
Client
   |
   | no correlation id
   v
Gateway
   |
generate UUID
   |
   v
Order
```

But keep this exercise simple.

Do not build the complete observability stack yet.

---

# 62. Correlation ID vs Trace ID

Do not confuse them.

A business/application correlation ID may be:

```text
X-Correlation-Id
```

Distributed tracing uses:

```text
trace context
```

with trace/span identifiers.

They can coexist.

Later OpenTelemetry will handle distributed trace propagation.

Do not invent a home-grown tracing system using only correlation headers.

---

# 63. Stage 2 Implementation Target

At completion:

```text
Client
   |
   v
Gateway :8000
   |
   +---- /api/orders/** --------+
   |                            |
   |                       StripPrefix
   |                            |
   |                            v
   |                     Order :9091
   |
   +---- /api/payments/** ------+
                                |
                           StripPrefix
                                |
                                v
                         Payment :9092
```

Also demonstrate:

```text
Method predicate
Request header filter
Response header filter
Header removal
```

You do not need to keep every learning-only filter after proving it works.

---

# 64. Suggested Implementation Sequence

Do not implement everything simultaneously.

## Step 1

Change:

```text
/orders/**
```

to:

```text
/api/orders/**
```

using `StripPrefix`.

Test.

---

## Step 2

Do the same for Payment.

Test.

---

## Step 3

Add Method predicate to one learning route.

Test matching and non-matching methods.

---

## Step 4

Add request header.

Verify in downstream logs.

---

## Step 5

Add response header.

Verify with `curl -i`.

---

## Step 6

Remove a spoofed internal header.

Verify downstream does not receive it.

---

## Step 7

Create an overlapping-route experiment.

Observe behavior.

Remove it.

---

## Step 8 — Optional

Implement a minimal custom/global correlation-ID filter.

Only after built-in filters are understood.

---

# 65. Coding-Agent Prompt

Use a bounded prompt like:

```text
Inspect my existing Stage 1 api-gateway, order-service and payment-service implementation first.

We are implementing API Gateway Stage 2: Predicates and Filters.

Do not redesign the project.

Goals:

1. Change the public Gateway paths to:
   /api/orders/**
   /api/payments/**

2. Keep downstream controller paths unchanged:
   /orders/**
   /payments/**

3. Use the appropriate Spring Cloud Gateway path filter to remove the /api prefix.

4. Add one simple Method predicate exercise.

5. Add one learning request header from Gateway and make the downstream service log it.

6. Add one learning response header.

7. Demonstrate removal of an untrusted client header such as X-Internal-Role.

Do NOT add:
- Kubernetes routing yet
- Eureka
- Redis
- security/JWT
- rate limiting
- circuit breaker
- retry
- Kafka
- database changes

Before changing files:
- inspect the existing Spring Boot/Spring Cloud versions
- inspect whether this Gateway project uses the WebFlux or MVC Gateway variant
- list the files you plan to change and why

After changes:
- provide exact curl commands
- explain how each predicate/filter participates in the request flow

Keep changes minimal because this is a learning exercise.
```

---

# 66. Hands-On Challenge — Do Without AI First

After the basic Stage 2 implementation works, add Inventory yourself.

Internal endpoint:

```text
GET /inventory/{productId}
```

Public endpoint:

```text
GET /api/inventory/{productId}
```

Desired flow:

```text
/api/inventory/P100
        |
        v
Gateway
        |
StripPrefix=1
        |
        v
/inventory/P100
        |
        v
Inventory Service
```

Then answer:

> Which part is the predicate?

> Which part is the filter?

> Which part is the destination?

If you can answer immediately, the mental model is working.

---

# 67. Interview Drill

Answer aloud before reading the suggested direction.

## Q1. Predicate vs Filter?

**Predicate**

```text
Determines whether a route matches.
```

**Filter**

```text
Processes/modifies request or response around routing.
```

---

## Q2. Why use `StripPrefix`?

When external API contains a prefix that the downstream service does not expose.

Example:

```text
/api/orders/101
```

to:

```text
/orders/101
```

---

## Q3. `StripPrefix` vs `RewritePath`?

`StripPrefix`:

```text
simple segment removal
```

`RewritePath`:

```text
more flexible path transformation
```

Prefer the simpler mechanism when it satisfies the contract.

---

## Q4. Route-specific vs Global Filter?

Route-specific:

```text
affects selected route(s)
```

Global:

```text
affects all or broad Gateway traffic
```

Global filters have larger blast radius.

---

## Q5. Should Gateway filters call databases?

Normally avoid it.

Gateway is an infrastructure request path.

Blocking/business data access increases:

- latency
- coupling
- failure modes
- blast radius

---

## Q6. Can Gateway trust `X-User-Id` from the client?

No.

Headers can be spoofed.

Trusted identity must come from a secure authentication/identity mechanism.

---

## Q7. Why not put all shared validation in Gateway?

Distinguish:

```text
edge validation/policy
```

from:

```text
domain validation
```

Domain rules belong to the owning service.

---

## Q8. What happens if two routes match?

Route selection/order/specificity becomes important.

Avoid ambiguous configuration and test overlapping cases explicitly.

---

## Q9. Why can a global filter be risky?

Because one defect may affect every API passing through Gateway.

---

## Q10. Does path rewriting change the client's URL?

The client still calls the public URL.

Gateway transforms the internal forwarded request.

---

# 68. Architect Challenge — Where Should API Versioning Live?

Requirement:

```text
/api/v1/orders/**
/api/v2/orders/**
```

Possible architecture:

```text
Gateway
  |
  +--> v1 → Order v1
  |
  +--> v2 → Order v2
```

But another architecture may keep one Order Service handling version compatibility.

There is no universal answer.

Evaluate:

- breaking changes
- deployment independence
- migration duration
- operational complexity
- client population
- deprecation plan

Architect answers should explain trade-offs.

---

# 69. Architect Challenge — Admin Traffic

Suppose:

```text
/api/admin/orders/**
```

and:

```text
/api/orders/**
```

exist.

Should route separation alone be treated as authorization?

No.

Routing:

```text
determines destination
```

Authorization:

```text
determines whether identity is allowed
```

Do not confuse them.

Security comes in Stage 5.

---

# 70. Architect Challenge — Gateway Transforming Responses

Requirement:

> Order returns 20 fields, mobile needs only 5. Let Gateway transform every response.

Possible warning:

Gateway may become a presentation/business transformation layer.

Consider:

- BFF
- API composition service
- GraphQL
- client-specific API
- service DTO design

Do not automatically make Gateway responsible.

---

# 71. Architect Challenge — Routing by Tenant

Suppose SaaS requirement:

```text
Tenant A → cluster A
Tenant B → cluster B
```

Gateway could potentially participate.

But architecture must consider:

- trusted tenant identity
- spoofing
- data isolation
- regional placement
- failure behavior
- configuration scale
- multi-tenancy model

This is far beyond a simple Header predicate.

Architect lesson:

> A framework feature does not automatically make an architecture correct.

---

# 72. Production Checklist for Predicates

Before production, ask:

- Are routes mutually understandable?
- Are overlaps intentional?
- Are route priorities tested?
- Are public/internal paths documented?
- Are version routes documented?
- Are generic catch-all routes dangerous?
- Are host/header-based rules based on trusted information?
- Can routing be observed?
- Are changes backward compatible?
- Is route configuration reviewed like code?

---

# 73. Production Checklist for Filters

Ask:

- Is this truly an edge concern?
- Can a built-in filter solve it?
- Does it block?
- Does it call another dependency?
- What happens if that dependency fails?
- What is its latency cost?
- What is its blast radius?
- Does it log sensitive information?
- Does filter ordering matter?
- Can clients spoof affected headers?
- Is it unit/integration tested?
- Is it observable?

---

# 74. Quick Revision

Memorize this picture:

```text
                 ROUTE

             +-------------+
Request ---> | Predicate   |
             +-------------+
                    |
                  Match
                    |
                    v
             +-------------+
             | Pre Filter  |
             +-------------+
                    |
                    v
             +-------------+
             |  Service    |
             +-------------+
                    |
                    v
             +-------------+
             | Post Filter |
             +-------------+
                    |
                    v
                 Response
```

And:

```text
Predicate = WHEN / SHOULD

Filter    = WHAT PROCESSING

URI       = WHERE
```

That one model answers many interview questions.

---

# 75. What You Should Be Able to Draw

Without notes:

```text
GET /api/orders/101
        |
        v
API Gateway
        |
Path=/api/orders/**
        |
      MATCH
        |
StripPrefix=1
        |
        v
/orders/101
        |
        v
Order Service
```

Then explain:

```text
Predicate
Filter
URI
Downstream path
```

---

# 76. Stage 2 Failure Test

You should be able to diagnose these without random code changes.

### Failure A

```text
Gateway returns 404
```

Ask:

```text
Did any route match?
```

### Failure B

```text
Order returns 404
```

Ask:

```text
Did Gateway transform the path incorrectly?
```

### Failure C

```text
Wrong service receives request
```

Ask:

```text
Are routes overlapping/order ambiguous?
```

### Failure D

```text
Downstream receives spoofed internal header
```

Ask:

```text
Did Gateway sanitize it?
Why does downstream trust it?
```

### Failure E

```text
All APIs fail after custom Global Filter deployment
```

Think:

```text
Global filter blast radius
```

---

# 77. Stage 2 Completion Checklist

Do not move to Stage 3 until the core items are complete.

## Concepts

- [ ] I can explain Predicate vs Filter.
- [ ] I understand Path predicate.
- [ ] I understand Method predicate.
- [ ] I understand Header/Query/Host predicate use cases.
- [ ] I understand multiple predicates.
- [ ] I understand route overlap/order.
- [ ] I understand pre-filter vs post-filter.
- [ ] I understand route-specific vs global filters.
- [ ] I understand `StripPrefix`.
- [ ] I understand when `RewritePath` is needed.
- [ ] I understand filter ordering.
- [ ] I understand trusted-header risks.

## Implementation

- [ ] `/api/orders/**` routes to internal `/orders/**`.
- [ ] `/api/payments/**` routes to internal `/payments/**`.
- [ ] I tested `StripPrefix`.
- [ ] I tested a Method predicate.
- [ ] I added a request header.
- [ ] I added a response header.
- [ ] I removed a spoofed internal header.
- [ ] I added Inventory routing myself.

## Failure Practice

- [ ] I tested wrong `StripPrefix`.
- [ ] I tested missing path filter.
- [ ] I tested wrong Method predicate.
- [ ] I tested overlapping routes.
- [ ] I can distinguish Gateway 404 from downstream 404.

## Architect Thinking

- [ ] I can explain why business logic should not live in filters.
- [ ] I understand global-filter blast radius.
- [ ] I can explain Gateway vs BFF at a high level.
- [ ] I can discuss where API version routing might live.
- [ ] I understand that headers are not automatically trusted identity.
- [ ] I can explain why simple routing is preferable to hidden routing magic.

## Interview

- [ ] I can answer Predicate vs Filter without notes.
- [ ] I can explain `StripPrefix` vs `RewritePath`.
- [ ] I can explain route-specific vs global filter.
- [ ] I can explain why a blocking custom filter is dangerous.
- [ ] I can explain how public and internal API paths can differ.

---

# 78. Stage 2 Interview-Ready Summary

If an interviewer asks:

> How does Spring Cloud Gateway route and process requests?

A concise architect-level answer:

> Spring Cloud Gateway evaluates configured route predicates against the incoming request. Once a route matches, the request passes through the applicable filter chain, where edge concerns such as path transformation, header policies, security, rate limiting or resilience can be applied before and after forwarding to the destination. I keep routing rules explicit and filters lightweight because global edge logic has a large blast radius. In Kubernetes, the destination would normally be a stable Kubernetes Service rather than a hard-coded Pod or instance address.

---

# 79. Stop Here

Do **not** add Eureka after this stage.

Do **not** add Redis yet.

Do **not** add JWT yet.

The next problem is:

```text
Gateway currently knows:

http://localhost:9091
http://localhost:9092
```

That is not production-grade.

Pods are dynamic.

Instances scale.

Addresses change.

So the next stage is:

```text
03-kubernetes-routing-service-discovery.md
```

We will move the system into the model you actually want to master:

```text
                Minikube / Kubernetes

                   Gateway Pod
                       |
            +----------+----------+
            |                     |
            v                     v
      order-service         payment-service
      ClusterIP Service     ClusterIP Service
            |                     |
       +----+----+           +----+----+
       |         |           |         |
     Pod 1     Pod 2       Pod 1     Pod 2
```

That is where the workout starts feeling much closer to production.

---

# Stage 2 One-Line Summary

> **Predicates decide whether a Gateway route should handle a request; filters control what happens to the request and response around forwarding, while the Gateway should keep those policies simple, observable, and free of domain business logic.**
