# API Gateway --- Stage 7: Rate Limiting with Redis & Traffic Protection

> **Previous:** `05-oauth2-oidc-jwt-gateway-security.md`\
> **Runtime:** Minikube / Kubernetes\
> **Gateway:** Spring Cloud Gateway WebFlux\
> **Shared Store:** Redis\
> **Goal:** Protect downstream APIs with distributed Token Bucket rate
> limiting.\
> **Learning:** Understand → Calculate → Implement → Load Test → Break →
> Observe → Explain as Architect.

------------------------------------------------------------------------

# 1. What Are We Building?

``` text
Client + JWT
     |
     v
API Gateway Service
   /       \
GW Pod-1  GW Pod-2
   \       /
      Redis
       |
 shared token bucket
       |
  allowed? -------- no ----> 429 Too Many Requests
       |
      yes
       v
 Order Service
```

Spring Cloud Gateway's `RequestRateLimiter` decides whether a request
may continue. Its Redis implementation uses the **Token Bucket**
algorithm; rejected requests return HTTP `429` by default.

------------------------------------------------------------------------

# 2. Why Rate Limit When Kubernetes Can Auto-Scale?

HPA and rate limiting solve different problems.

``` text
HPA          = add capacity when load grows
Rate limiter = control admission before work enters the system
```

Suppose Order normally handles 1,000 requests/sec and one buggy or
abusive client suddenly sends 100,000/sec. HPA is reactive, takes time,
has `maxReplicas`, may need additional nodes, and cannot make a shared
database or external payment provider infinitely scalable.

Rate limiting also provides **fairness**. Without it, one customer can
consume most shared capacity while Kubernetes simply keeps adding
infrastructure and cost.

Architect mental model:

``` text
Rate Limiter → admission/fairness/protection
HPA          → capacity adjustment
```

Production systems often need both.

------------------------------------------------------------------------

# 3. What Does Rate Limiting Protect?

It protects:

-   downstream CPU/thread/event-loop capacity;
-   databases and connection pools;
-   external APIs with their own quotas;
-   expensive payment/report/search operations;
-   other customers from a noisy neighbor;
-   cloud cost;
-   the platform from buggy retry loops or polling clients.

Important: rate limiting is useful against **accidental traffic storms**
as well as malicious abuse.

------------------------------------------------------------------------

# 4. Rate Limit vs Concurrency Limit vs Load Shedding

**Rate limit** controls work over time:

``` text
20 requests / second / user
```

**Concurrency limit** controls simultaneously active work:

``` text
maximum 50 in-flight requests
```

A caller can stay under 20 req/sec while requests each run for 30
seconds, so rate limiting alone does not guarantee safe concurrency.

**Load shedding** rejects work because the system itself is overloaded.

An architect should see rate limiting as one part of overload
protection, not the entire solution.

------------------------------------------------------------------------

# 5. Token Bucket --- Mental Model

Imagine one bucket per rate-limit key.

``` text
tokens refill over time
          |
          v
   +-------------+
   | ● ● ● ● ●   |
   |   bucket    |
   +-------------+
          |
request consumes token
```

Rules:

``` text
token available → consume → allow
no token        → reject  → 429
```

Unlike a simple "reset every minute" counter, the bucket **replenishes
over time**.

------------------------------------------------------------------------

# 6. The Three Spring Parameters

``` yaml
redis-rate-limiter.replenishRate: 5
redis-rate-limiter.burstCapacity: 10
redis-rate-limiter.requestedTokens: 1
```

Spring Cloud Gateway defines:

-   `replenishRate` --- tokens added per second;
-   `burstCapacity` --- maximum tokens the bucket can hold;
-   `requestedTokens` --- tokens consumed by each request, default 1.

------------------------------------------------------------------------

# 7. `replenishRate`

``` yaml
replenishRate: 5
```

means the bucket replenishes at:

``` text
5 tokens/sec
```

With one token per request, this supports roughly five requests/sec as
the sustainable refill rate.

Do **not** think:

``` text
"Every second a counter resets to 5."
```

It is a refill model.

------------------------------------------------------------------------

# 8. `burstCapacity`

``` yaml
burstCapacity: 10
```

means the bucket can store at most ten tokens.

If a user has been idle long enough, the bucket can fill to ten and
allow a short burst.

It never grows to:

``` text
11, 20, 100...
```

just because the caller stayed idle for a long time.

------------------------------------------------------------------------

# 9. `requestedTokens`

``` yaml
requestedTokens: 1
```

means one request costs one token.

An expensive endpoint could theoretically consume more:

``` yaml
requestedTokens: 5
```

But avoid creating a quota model that nobody can explain. Separate
route-specific limits are often clearer than many arbitrary weights.

------------------------------------------------------------------------

# 10. Calculation Example

Assume:

``` text
replenishRate   = 5/sec
burstCapacity   = 10
requestedTokens = 1
bucket initially full
```

Client quickly sends eight requests:

``` text
10 - 8 = 2 tokens approximately remaining
```

If more requests arrive immediately, available/refilled tokens are
consumed. Once there is insufficient capacity:

``` text
429 Too Many Requests
```

If the client waits two seconds:

``` text
5 tokens/sec × 2 sec = 10 tokens refill opportunity
```

but the bucket is capped at:

``` text
10
```

------------------------------------------------------------------------

# 11. Why the 11th Request Is Not Always the First 429

With `burstCapacity=10`, do not assume:

``` text
requests 1-10 → 200
request 11    → 429
```

Your first ten requests take time to execute. During that time tokens
are also replenishing.

Therefore the exact cutoff depends on timing.

This is why concurrent load testing is better than a slow shell loop
when demonstrating a burst.

------------------------------------------------------------------------

# 12. One Request Every Six Seconds

Requirement:

``` text
10 requests/minute
```

Average:

``` text
60 seconds / 10
= one request every 6 seconds
```

The important concept is that Token Bucket does not necessarily wait for
a minute boundary and suddenly reset a counter. Capacity is replenished
over time.

------------------------------------------------------------------------

# 13. Limits Below One Request/Second

Spring documents this example for:

``` text
1 request/minute
```

``` yaml
redis-rate-limiter.replenishRate: 1
redis-rate-limiter.requestedTokens: 60
redis-rate-limiter.burstCapacity: 60
```

A request costs 60 tokens while tokens replenish at 1/sec, so
approximately 60 seconds are needed to restore enough capacity.

This configuration makes sense once you understand that
`requestedTokens` can scale the effective time interval.

------------------------------------------------------------------------

# 14. Why Redis?

We already have multiple Gateway Pods.

Bad architecture:

``` text
GW-1 memory: user123 = 2 tokens
GW-2 memory: user123 = 9 tokens
```

Now the quota depends on which Pod receives the request.

If the contractual limit is 10/user and you have three independent
Gateway buckets, the effective capacity can approach 30. HPA scaling the
Gateway can accidentally change the customer's quota.

Correct model:

``` text
GW-1 ----\
GW-2 -----+---- Redis shared bucket
GW-3 ----/
```

The quota remains independent of Gateway replica count.

------------------------------------------------------------------------

# 15. Why Not Sticky Sessions?

Sticky routing plus local buckets creates stateful Gateway behavior:

-   Pod restart loses quota state.
-   Scaling changes routing.
-   failover changes the bucket;
-   correctness depends on load-balancer affinity.

For our Kubernetes design, Gateway stays stateless and distributed
rate-limit state lives in Redis.

------------------------------------------------------------------------

# 16. Dependency

Add to **Gateway only**:

``` gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
```

Spring Cloud Gateway's Redis RateLimiter requires the reactive Redis
starter.

------------------------------------------------------------------------

# 17. Redis Deployment

`kubernetes/redis/deployment.yaml`

``` yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: redis
  namespace: ecommerce

spec:
  replicas: 1

  selector:
    matchLabels:
      app: redis

  template:
    metadata:
      labels:
        app: redis

    spec:
      containers:
        - name: redis
          image: redis:7-alpine

          ports:
            - containerPort: 6379

          readinessProbe:
            tcpSocket:
              port: 6379
            initialDelaySeconds: 3
            periodSeconds: 5

          livenessProbe:
            tcpSocket:
              port: 6379
            initialDelaySeconds: 10
            periodSeconds: 10

          resources:
            requests:
              cpu: "50m"
              memory: "64Mi"
            limits:
              cpu: "250m"
              memory: "256Mi"
```

This single Redis Pod is for learning, not production HA.

------------------------------------------------------------------------

# 18. Redis Service

`kubernetes/redis/service.yaml`

``` yaml
apiVersion: v1
kind: Service

metadata:
  name: redis
  namespace: ecommerce

spec:
  type: ClusterIP

  selector:
    app: redis

  ports:
    - name: redis
      port: 6379
      targetPort: 6379
```

Apply:

``` bash
kubectl apply -f kubernetes/redis/

kubectl get pods -n ecommerce -l app=redis
kubectl get svc -n ecommerce redis
kubectl get endpoints -n ecommerce redis
```

Gateway uses:

``` text
redis:6379
```

not the Redis Pod IP.

------------------------------------------------------------------------

# 19. Gateway Redis Configuration

`application-k8s.yml`:

``` yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
```

Gateway Deployment:

``` yaml
env:
  - name: REDIS_HOST
    value: redis

  - name: REDIS_PORT
    value: "6379"
```

------------------------------------------------------------------------

# 20. What Is the Rate-Limit Key?

The key answers:

``` text
"Whose bucket is this?"
```

Possible keys:

``` text
user ID
tenant ID
OAuth client ID
API key
trusted source IP
route + tenant
```

This is an **architecture/business decision**, not just Java plumbing.

Ask:

> Who owns the quota?

------------------------------------------------------------------------

# 21. Use Authenticated Principal

Stage 5 already validated JWT.

Create:

``` java
@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver authenticatedUserKeyResolver() {

        return exchange ->
                exchange.getPrincipal()
                        .map(Principal::getName);
    }
}
```

Imports:

``` java
import java.security.Principal;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
```

Now:

``` text
customer1 → customer1 bucket
admin1    → admin1 bucket
```

A trusted authenticated identity is better than a caller-controlled
query parameter.

------------------------------------------------------------------------

# 22. Why Not `?user=customer1`?

Bad:

``` text
/api/orders?user=a
/api/orders?user=b
/api/orders?user=c
```

If the query parameter determines the bucket, the caller can rotate
values to obtain new buckets.

Spring's documentation uses a query parameter as a simple example but
explicitly says that approach is not recommended for production.

Use a trusted identity wherever possible.

------------------------------------------------------------------------

# 23. Configure the Order Route

``` yaml
spring:
  cloud:
    gateway:
      routes:

        - id: order-service

          uri: http://order-service:8080

          predicates:
            - Path=/api/orders/**

          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@authenticatedUserKeyResolver}"
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                redis-rate-limiter.requestedTokens: 1

            - RewritePath=/api/orders/(?<segment>.*),/orders/${segment}
```

Meaning:

``` text
refill     = 5 tokens/sec
max bucket = 10
request    = 1 token
```

`RequestRateLimiter` should use its named-argument configuration; Spring
documentation notes that shortcut notation is not supported.

------------------------------------------------------------------------

# 24. Authentication + Rate-Limit Flow

Conceptually:

``` text
Request
   |
Spring Security
   |
validate JWT
   |
authenticated Principal
   |
Gateway route
   |
RequestRateLimiter
   |
KeyResolver
   |
Redis
   |
allow / reject
```

This is why security came before rate limiting: we can base the quota on
a trusted identity.

------------------------------------------------------------------------

# 25. Empty Key

If the `KeyResolver` cannot produce a key, Spring Cloud Gateway denies
empty keys by default.

For our protected Order API this is sensible.

But normally Stage 5 security should already reject an unauthenticated
caller before it reaches the principal-based rate-limit decision.

------------------------------------------------------------------------

# 26. Build and Deploy

``` bash
minikube image build \
  -t ecom-api-gateway:stage6 \
  ./api-gateway
```

``` bash
kubectl set image \
  deployment/api-gateway \
  api-gateway=ecom-api-gateway:stage6 \
  -n ecommerce
```

``` bash
kubectl rollout status \
  deployment/api-gateway \
  -n ecommerce
```

Keep at least two Gateway replicas for the distributed-state experiment.

------------------------------------------------------------------------

# 27. Basic Test

``` bash
curl -i \
  "$GATEWAY_URL/api/orders/101" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Expected under available quota:

``` text
200
```

Spring's Redis limiter can return rate-limit headers such as remaining
tokens and configured refill/burst information.

Inspect the headers with `curl -i`.

------------------------------------------------------------------------

# 28. Trigger 429

``` bash
for i in {1..20}
do
  curl -s \
    -o /dev/null \
    -w "$i -> %{http_code}\n" \
    "$GATEWAY_URL/api/orders/101" \
    -H "Authorization: Bearer $ACCESS_TOKEN"
done
```

Eventually expect:

``` text
200
...
429
```

The exact request number varies because tokens replenish while the loop
runs.

------------------------------------------------------------------------

# 29. Better Burst Test with `hey`

``` bash
brew install hey
```

``` bash
hey \
  -n 100 \
  -c 20 \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$GATEWAY_URL/api/orders/101"
```

Observe:

``` text
200 count
429 count
latency
requests/sec
```

The goal is not benchmarking your Mac. It is proving that excessive
traffic is rejected **before Order Service performs the work**.

------------------------------------------------------------------------

# 30. Prove Refill

After receiving 429:

``` bash
sleep 2
```

At 5 tokens/sec, two seconds gives roughly ten tokens of refill
opportunity, capped by `burstCapacity=10`.

Call again:

``` bash
curl -i \
  "$GATEWAY_URL/api/orders/101" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

The request should be allowed when sufficient capacity has replenished.

------------------------------------------------------------------------

# 31. Prove Separate User Buckets

Obtain tokens for:

``` text
Customer A
Customer B
```

Exhaust A's bucket.

Immediately call as B.

If the key is authenticated principal:

``` text
A bucket != B bucket
```

B should not inherit A's exhausted quota.

This proves the importance of `KeyResolver`.

------------------------------------------------------------------------

# 32. Prove Shared State Across Gateway Pods

This is the most important Kubernetes experiment.

Verify:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=api-gateway
```

Use the Gateway Pod response header/logging introduced in Stage 4.

Generate traffic and confirm:

``` text
requests hit GW-1 and GW-2
```

while the same user still has **one shared rate-limit budget**.

That proves Redis is coordinating the replicas.

------------------------------------------------------------------------

# 33. Scale Gateway and Prove Quota Does Not Multiply

Start with:

``` bash
kubectl scale \
  deployment/api-gateway \
  --replicas=2 \
  -n ecommerce
```

Test.

Then:

``` bash
kubectl scale \
  deployment/api-gateway \
  --replicas=4 \
  -n ecommerce
```

Test again.

Expected:

``` text
Gateway processing capacity increases
```

but:

``` text
per-user quota does NOT double
```

because all replicas share Redis.

This is an excellent architect interview demonstration.

------------------------------------------------------------------------

# 34. Per-User vs Per-Client vs Per-Tenant

There is no universal key.

**Per user**

``` text
customer123
```

Good for user fairness.

**Per OAuth client**

``` text
partner-A
```

Good for integration contracts.

**Per tenant**

``` text
company-ABC
```

Good for SaaS plans.

**Composite**

``` text
tenant + route
```

Useful when a tenant has separate quotas for different APIs.

Always start from the business owner of the quota.

------------------------------------------------------------------------

# 35. IP-Based Limits --- Be Careful

IP limiting can help with anonymous APIs and basic abuse protection, but
IP is not one user.

Many users may share:

``` text
corporate NAT
mobile carrier NAT
proxy
```

And in Kubernetes/AWS the apparent address may pass through:

``` text
ALB
Ingress
reverse proxy
X-Forwarded-For
```

Only trust forwarded client addresses when trusted proxies are
configured to sanitize/set them correctly.

------------------------------------------------------------------------

# 36. Anonymous APIs

For:

``` text
/login
/register
/public/search
```

there may be no authenticated principal.

Possible strategies:

``` text
trusted IP
API key
device/client identifier
global route bucket
```

Do not force one KeyResolver onto every route.

------------------------------------------------------------------------

# 37. Different APIs Need Different Limits

Example:

``` text
GET products     → 100 req/sec
GET orders       → 20 req/sec
POST payments    → 5 req/sec
```

A payment may involve:

``` text
database write
payment provider
fraud service
event publishing
```

so its cost/risk is different from a cached product lookup.

Rate limits should reflect business behavior and measured capacity.

------------------------------------------------------------------------

# 38. Rate-Limit Headers

Spring Cloud Gateway documents Redis rate-limit headers including:

``` text
X-RateLimit-Remaining
X-RateLimit-Replenish-Rate
X-RateLimit-Burst-Capacity
X-RateLimit-Requested-Tokens
```

These are useful for testing and potentially for client contracts.

Decide deliberately which details should be exposed externally in
production.

------------------------------------------------------------------------

# 39. What Should a Client Do with 429?

Bad:

``` text
429 → retry immediately → 429 → retry immediately
```

Better:

``` text
429
 ↓
back off
 ↓
retry according to API contract
```

Do not configure blind Gateway retries for 429. That would fight the
traffic-control mechanism.

------------------------------------------------------------------------

# 40. Retry and Rate Limiting Can Conflict

Suppose an upstream component automatically retries every 429 five
times.

``` text
one rejected request
        ↓
five additional requests
```

Traffic protection becomes traffic amplification.

Architects should define:

``` text
retryable status codes
retry owner
max attempts
backoff
jitter
```

Rate limiting and resilience must be designed together.

------------------------------------------------------------------------

# 41. Redis Failure --- Important Architecture Decision

Redis is now in the admission-control path.

If Redis is unavailable, Gateway cannot reliably know distributed bucket
state.

Architectural question:

``` text
Fail closed?
reject when quota cannot be verified

or

Fail open?
allow traffic when limiter fails
```

Possible thinking:

``` text
payment/fraud-sensitive endpoint
→ stronger reason to fail closed

low-risk product browsing
→ availability may favor fail open
```

Do not assume framework behavior equals your business policy. Test the
exact Spring Cloud Gateway version/configuration you deploy.

------------------------------------------------------------------------

# 42. Redis Failure Experiment

Stop Redis:

``` bash
kubectl scale \
  deployment/redis \
  --replicas=0 \
  -n ecommerce
```

Call:

``` bash
curl -i \
  "$GATEWAY_URL/api/orders/101" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Record:

``` text
HTTP status
latency
Gateway logs
error behavior
```

Restore:

``` bash
kubectl scale \
  deployment/redis \
  --replicas=1 \
  -n ecommerce
```

The lesson is not to memorize an assumed failure mode; it is to know
what your deployed stack actually does and decide whether that behavior
meets business requirements.

------------------------------------------------------------------------

# 43. Redis Is Now a Shared Dependency

Our Minikube Redis is one Pod.

Fine for learning.

Not production HA.

If all Gateway replicas depend on one Redis:

``` text
Redis failure
     ↓
rate-limit decisions across all Gateway Pods affected
```

Production design may require:

``` text
managed Redis
replication
automatic failover
multi-AZ
monitoring
capacity planning
security
```

------------------------------------------------------------------------

# 44. Does Rate-Limit Redis Need Durable Persistence?

For:

``` text
5 requests/sec
```

losing a few seconds of bucket state after restart may be acceptable.

For:

``` text
1 million requests/month
billing quota
```

a short-lived token bucket should not be your authoritative accounting
ledger.

This leads to an important distinction:

``` text
Rate limit = short-term traffic protection
Quota      = longer-term entitlement/usage
```

A platform may need both.

------------------------------------------------------------------------

# 45. HPA + Rate Limiting Together

Example:

``` text
Gateway HPA: 2 → 5
Order HPA:   2 → 6

Rate limit:
20 req/sec/user
```

HPA handles legitimate aggregate growth.

Rate limiting controls individual caller behavior.

Because Redis is shared, a new Gateway replica does not create new
customer quota.

------------------------------------------------------------------------

# 46. Per-User Limit Does Not Guarantee Global Safety

Suppose:

``` text
100 users × 20 req/sec
=
2,000 req/sec
```

but Order safely handles:

``` text
500 req/sec
```

Every user can be within policy while the system is overloaded.

Capacity design must consider:

``` text
per-user limits
active user count
global limits
downstream capacity
burst behavior
HPA maximum
database capacity
```

Per-user fairness is not the same as total system protection.

------------------------------------------------------------------------

# 47. Layered Traffic Protection

A mature architecture can have:

``` text
Internet
   |
WAF / edge controls
   |
ALB / Ingress
   |
API Gateway
   |
Service
```

Example responsibilities:

``` text
Edge    → broad abuse/DDoS controls
Gateway → user/client/tenant API policy
Service → domain-specific concurrency protection
```

Avoid duplicating identical policies at every layer.

------------------------------------------------------------------------

# 48. Rate Limiting Is Not Complete DDoS Protection

A huge volumetric attack may consume network or edge infrastructure
**before requests reach Spring Cloud Gateway**.

Therefore:

> Gateway rate limiting is application-level traffic governance, not a
> complete DDoS solution.

Edge/network protection is a separate concern.

------------------------------------------------------------------------

# 49. Observability

Track:

``` text
allowed requests
429 count
route
rate-limit policy/key category
Redis latency
Redis errors
Gateway latency
downstream request rate
```

Do not blindly use raw user IDs as high-cardinality metric labels.

A useful operational question is:

``` text
Are 429s caused by one abusive client,
or is our configured limit unrealistic?
```

------------------------------------------------------------------------

# 50. Troubleshooting --- Everything Is 429

Check:

``` text
burstCapacity accidentally 0?
requestedTokens > usable capacity?
wrong KeyResolver?
Redis problem?
wrong route?
```

Spring documents that setting Redis `burstCapacity` to zero blocks all
requests.

------------------------------------------------------------------------

# 51. Troubleshooting --- No 429 Ever Appears

Check:

``` text
RequestRateLimiter attached?
correct route matched?
correct Spring profile?
Redis reachable?
test traffic fast enough?
burstCapacity too large?
different users creating separate buckets?
```

Testing one request every two seconds will not prove a 10 req/sec limit.

------------------------------------------------------------------------

# 52. Troubleshooting --- Users Affect Each Other

Likely the resolver returns the same key.

Bad:

``` java
return Mono.just("user");
```

That creates one global bucket named:

``` text
user
```

instead of separate user buckets.

------------------------------------------------------------------------

# 53. Troubleshooting --- Quota Multiplies After Scaling Gateway

That suggests rate-limit state is local or Gateway replicas are not
sharing the same Redis/configuration.

Check:

``` text
same Redis Service?
same profile?
same key resolver?
same route policy?
```

A contractual per-user quota should not change because Kubernetes added
Gateway Pods.

------------------------------------------------------------------------

# 54. Troubleshooting Redis Connectivity

``` bash
kubectl run redis-test \
  -n ecommerce \
  --rm -it \
  --image=redis:7-alpine \
  -- \
  redis-cli \
  -h redis \
  ping
```

Expected:

``` text
PONG
```

Also inspect:

``` bash
kubectl get svc redis -n ecommerce
kubectl get endpoints redis -n ecommerce
```

This separates Kubernetes DNS/network problems from Gateway
configuration.

------------------------------------------------------------------------

# 55. Architect Interview --- Why Not Just HPA?

> HPA adds capacity reactively; it does not enforce caller fairness or
> protect finite downstream dependencies. Rate limiting controls
> admission before work reaches the service. I use HPA for legitimate
> load growth and rate limiting for traffic governance and protection.

------------------------------------------------------------------------

# 56. Architect Interview --- Why Redis?

> Gateway runs multiple stateless replicas. Local buckets would make
> effective quota depend on which Pod receives traffic and how many
> replicas HPA creates. Shared Redis state keeps the caller's rate-limit
> budget consistent across Gateway instances.

------------------------------------------------------------------------

# 57. Architect Interview --- Why Token Bucket?

> Token Bucket supports a controlled sustained rate plus configurable
> short bursts. `replenishRate` controls refill, `burstCapacity`
> controls stored burst capacity, and `requestedTokens` controls request
> cost.

------------------------------------------------------------------------

# 58. Architect Interview --- How Do You Pick the Key?

> Start with the business owner of the quota. For authenticated consumer
> APIs it may be user or tenant identity; for partner integrations it
> may be OAuth client/API key; for anonymous endpoints it may include
> trusted source IP. Avoid caller-controlled identity values.

------------------------------------------------------------------------

# 59. Architect Interview --- How Do You Pick the Numbers?

Do not say:

``` text
10/sec because it sounds reasonable.
```

Use:

``` text
load tests
database capacity
downstream provider limits
normal user behavior
tenant plan/SLA
cost per request
burst pattern
HPA maximum
```

Rate-limit numbers are business/capacity policy expressed in
configuration.

------------------------------------------------------------------------

# 60. Architect Interview --- Redis Fails?

> Redis is in the admission-control path, so failure behavior must be
> explicit. I test the actual framework behavior, decide whether the API
> requires fail-open or fail-closed semantics, and make the shared
> rate-limit store highly available so it does not become a single
> failure point.

------------------------------------------------------------------------

# 61. Production EKS Mapping

``` text
                     Internet
                        |
                 WAF / Edge controls
                        |
                       ALB
                        |
               API Gateway replicas
                  /           \
               GW-1           GW-N
                  \           /
                  Shared Redis
                        |
                RequestRateLimiter
                        |
                   Order Service
                        |
                       RDS
```

Production questions:

``` text
Where does Redis run?
Is it multi-AZ?
What happens when Redis fails?
What owns the quota?
Are limits route-specific?
Do we need global limits?
How are 429s monitored?
What retry behavior do clients use?
Does Gateway HPA change quota?  (It should not.)
```

------------------------------------------------------------------------

# 62. Hands-On Assignment

## Redis

-   deploy Redis;
-   expose as ClusterIP;
-   verify `PONG`;
-   inspect endpoints.

## Gateway

-   add reactive Redis dependency;
-   configure Redis Service DNS;
-   implement principal `KeyResolver`;
-   configure Order `RequestRateLimiter`;
-   keep Stage 5 JWT security;
-   keep multiple Gateway replicas.

## Token Bucket

Use:

``` text
replenishRate   = 5
burstCapacity   = 10
requestedTokens = 1
```

Prove:

-   normal requests succeed;
-   burst causes 429;
-   waiting restores capacity.

## Distributed Behavior

-   run at least two Gateway Pods;
-   prove one user's quota is shared;
-   scale Gateway 2 → 4;
-   prove quota does not multiply.

## Identity

-   exhaust Customer A;
-   call as Customer B;
-   prove separate buckets.

## Failure

-   stop Redis;
-   record actual behavior;
-   restore Redis;
-   decide desired production fail-open/fail-closed policy.

------------------------------------------------------------------------

# 63. Completion Checklist

## Concepts

-   [ ] Why HPA does not replace rate limiting.
-   [ ] Token Bucket.
-   [ ] `replenishRate`.
-   [ ] `burstCapacity`.
-   [ ] `requestedTokens`.
-   [ ] Refill calculations.
-   [ ] Why Redis is needed with multiple Gateway Pods.
-   [ ] KeyResolver.
-   [ ] user/client/tenant/IP key trade-offs.
-   [ ] 429 behavior.
-   [ ] rate limit vs quota.
-   [ ] rate limit vs concurrency limit.
-   [ ] Redis failure trade-off.

## Implementation

-   [ ] Redis runs in Minikube.
-   [ ] Gateway uses reactive Redis.
-   [ ] principal KeyResolver works.
-   [ ] Order route has RequestRateLimiter.
-   [ ] Stage 5 JWT security still works.
-   [ ] multiple Gateway Pods share limiter state.

## Testing

-   [ ] normal request → 200.
-   [ ] burst → 429.
-   [ ] wait → allowed again.
-   [ ] different users → different buckets.
-   [ ] scale Gateway → quota unchanged.
-   [ ] Redis outage observed.
-   [ ] Redis recovery tested.
-   [ ] rate-limit headers inspected.

------------------------------------------------------------------------

# 64. Coding-Agent Prompt

``` text
Inspect my existing Stage 5 microservices-workouts project first.

Implement Stage 7 only:
distributed API rate limiting using Spring Cloud Gateway WebFlux + Redis.

Current architecture:
- Spring Cloud Gateway WebFlux
- JWT/OAuth2 security already implemented
- authenticated Principal available
- multiple Gateway replicas
- Order Service behind Kubernetes ClusterIP
- Minikube
- existing probes, HPA, security and routing must remain

Requirements:

1. Gateway
   - add spring-boot-starter-data-redis-reactive if missing
   - Redis from REDIS_HOST / REDIS_PORT
   - KeyResolver based on authenticated Principal
   - RequestRateLimiter on Order route
   - replenishRate=5
   - burstCapacity=10
   - requestedTokens=1
   - preserve JWT and Authorization propagation

2. Kubernetes
   - kubernetes/redis/deployment.yaml
   - kubernetes/redis/service.yaml
   - ClusterIP only
   - readiness/liveness
   - basic requests/limits
   - Gateway connects to redis:6379

3. Do NOT add
   - Eureka
   - sticky sessions
   - local per-Pod rate-limit state
   - service mesh
   - circuit breaker yet
   - custom Token Bucket implementation
   - query-parameter user identity

4. Before changing files inspect
   - Spring Boot/Spring Cloud versions
   - current WebFlux Gateway property structure
   - route YAML
   - Principal name produced by Stage 5
   - Gateway Deployment labels/container name
   - existing filters
   - exact files to modify

5. Provide exact tests
   - Redis PING
   - normal authenticated request
   - burst causing 429
   - wait/refill
   - Customer A vs B
   - shared limit across multiple Gateway Pods
   - scale Gateway 2 -> 4 and prove quota unchanged
   - stop Redis and record actual failure behavior
   - restore Redis

Never log JWTs.
Do not invent failure behavior; observe it.
Explain non-obvious mechanisms concisely.
```

------------------------------------------------------------------------

# 65. Rapid Interview Revision

### Why rate limiting?

Admission control, fairness, downstream protection and cost control.

### Why Redis?

Shared state across stateless Gateway replicas.

### Token Bucket?

Requests consume tokens; tokens replenish over time; insufficient tokens
produce rejection.

### `replenishRate`?

Token refill rate.

### `burstCapacity`?

Maximum stored tokens / temporary burst capacity.

### `requestedTokens`?

Cost of one request.

### Default rejection?

`429 Too Many Requests`.

### Best key?

The trusted identity that owns the quota; no universal answer.

### Rate limiting vs HPA?

Rate limiter controls admission; HPA adjusts capacity.

### Rate limiting vs quota?

Rate limiting protects short-term traffic; quota is longer-term
entitlement/accounting.

### Is it DDoS protection?

Not by itself; edge/network controls are also required.

------------------------------------------------------------------------

# 66. Next Stage

``` text
Stage 8 — Resilience:
Timeouts, Retries & Circuit Breaker
```

Core question:

``` text
What happens when a downstream service becomes slow or unavailable?
```

We will cover:

``` text
timeouts
retry safety
idempotency relationship
retry storms
circuit-breaker states
fallback
Resilience4j
Kubernetes failure tests
metrics
production trade-offs
```

------------------------------------------------------------------------

# Stage 7 One-Line Summary

> **Rate limiting is admission control: use a trusted key to give
> callers controlled Token Buckets, keep bucket state shared across
> Gateway replicas, reject excess traffic before it consumes downstream
> capacity, and design the limiter together with autoscaling, retries,
> observability and Redis availability.**
