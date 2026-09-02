# API Gateway — Stage 3: Kubernetes Routing & Service Discovery

> **Previous:** `02-predicates-and-filters-practical.md`  
> **Stage:** 03  
> **Runtime:** Minikube on local Mac  
> **Production Mapping:** AWS EKS  
> **Goal:** Replace hard-coded service instances with Kubernetes-native service discovery.  
> **Approach:** Kubernetes Service + DNS first. No Eureka. No unnecessary Spring Cloud Kubernetes DiscoveryClient yet.

---

# 1. What Problem Are We Solving?

At the end of Stage 2, Gateway still has configuration like:

```yaml
uri: http://localhost:9091
```

That works when Gateway and Order Service run directly on your Mac.

It is not the architecture we want in Kubernetes.

Imagine Order Service has three Pods:

```text
order-service Pod 1   10.244.0.21
order-service Pod 2   10.244.0.22
order-service Pod 3   10.244.0.23
```

Gateway should **not** know these Pod IPs.

Pods are temporary:

```text
Pod dies
   ↓
Kubernetes creates replacement
   ↓
New Pod may have a different IP
```

We need a stable address.

Kubernetes gives us:

```text
Service
+
DNS
```

Target architecture:

```text
                 Minikube

              Gateway Pod
                   |
                   |
          http://order-service:8080
                   |
                   v
        +----------------------+
        | order-service        |
        | Kubernetes Service   |
        +----------------------+
             |          |
             v          v
         Order Pod   Order Pod
```

The Gateway knows only:

```text
order-service
```

It does not know individual Pod addresses.

---

# 2. Important Concept — Service vs Pod

This is the main concept for this stage.

A **Pod** is an application instance.

```text
Order Pod
```

A **Service** is a stable network endpoint in front of matching Pods.

```text
order-service
```

Example:

```text
Gateway
   |
   | order-service:8080
   v
Kubernetes Service
   |
   +------> Order Pod 1
   |
   +------> Order Pod 2
```

If Pod 1 dies:

```text
Gateway
   |
   | still calls order-service
   v
Kubernetes Service
   |
   +------> Order Pod 2
   |
   +------> New Order Pod 3
```

Gateway configuration does not change.

That is the important architectural benefit.

---

# 3. Why We Are NOT Adding Eureka

In a traditional non-Kubernetes setup you may see:

```text
Order instances
      |
      | register
      v
    Eureka

Gateway
   |
   | asks Eureka
   v
Order instances
```

In Kubernetes:

```text
Deployment
   |
creates Pods
   |
Service selects Pods
   |
CoreDNS gives Service a DNS name
```

So our basic service discovery becomes:

```text
Gateway
   |
   | DNS: order-service
   v
Kubernetes Service
   |
Order Pods
```

We do not need to add Eureka simply because older Spring Cloud examples use it.

---

# 4. Do We Need Spring Cloud Kubernetes DiscoveryClient?

Not for our first production-style implementation.

There are two approaches you should understand.

## Approach A — Kubernetes-native DNS

Gateway route:

```yaml
uri: http://order-service:8080
```

Kubernetes resolves:

```text
order-service
```

This is simple and platform-native.

We will use this.

---

## Approach B — Spring DiscoveryClient

Spring Cloud Kubernetes can expose Kubernetes Services through Spring's `DiscoveryClient`.

Then Spring Cloud Gateway can use patterns such as:

```text
lb://order-service
```

This can be useful when you specifically want Spring Cloud's discovery/load-balancer abstraction.

But it also introduces:

```text
additional dependencies
Kubernetes API access
RBAC considerations
more framework behavior
```

For our architecture, we first choose the simpler model:

```text
Gateway
   ↓
Kubernetes DNS
   ↓
ClusterIP Service
   ↓
Pods
```

We will understand `DiscoveryClient` as an alternative, not add it automatically.

---

# 5. Stage 3 Target Architecture

We will deploy:

```text
Namespace: ecommerce

Gateway
Order
Payment
Inventory
```

Architecture:

```text
                     Minikube

                   API Gateway
                       Pod
                        |
          +-------------+-------------+
          |             |             |
          v             v             v
   order-service  payment-service inventory-service
     ClusterIP       ClusterIP       ClusterIP
          |             |             |
       +--+--+        +--+--+       +--+--+
       |     |        |     |       |     |
      Pod   Pod      Pod   Pod     Pod   Pod
```

For this stage, two Order replicas are enough to prove the idea.

---

# 6. Suggested Project Structure

Keep Kubernetes manifests separate from application code.

```text
microservices-workouts/
│
├── api-gateway/
├── order-service/
├── payment-service/
├── inventory-service/
│
└── kubernetes/
    ├── namespace.yaml
    │
    ├── gateway/
    │   ├── deployment.yaml
    │   └── service.yaml
    │
    ├── order/
    │   ├── deployment.yaml
    │   └── service.yaml
    │
    ├── payment/
    │   ├── deployment.yaml
    │   └── service.yaml
    │
    └── inventory/
        ├── deployment.yaml
        └── service.yaml
```

Later we can introduce:

```text
ConfigMaps
Secrets
Kustomize/Helm
EKS
```

Do not add all of them now.

---

# 7. Namespace

Create:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce
```

File:

```text
kubernetes/namespace.yaml
```

Apply:

```bash
kubectl apply -f kubernetes/namespace.yaml
```

Verify:

```bash
kubectl get namespaces
```

We will deploy all Stage 3 services into:

```text
ecommerce
```

---

# 8. Order Deployment

Assume Order Service listens internally on:

```text
8080
```

Using the same container port across microservices is perfectly fine because each Pod has its own network namespace.

Example:

```yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: order-service
  namespace: ecommerce

spec:
  replicas: 2

  selector:
    matchLabels:
      app: order-service

  template:
    metadata:
      labels:
        app: order-service

    spec:
      containers:
        - name: order-service
          image: ecom-order-service:stage3
          imagePullPolicy: IfNotPresent

          ports:
            - containerPort: 8080
```

Important relationship:

```text
Deployment selector
        ↓
app: order-service

Pod label
        ↓
app: order-service
```

These must match.

---

# 9. Order Service

Now create the stable network endpoint:

```yaml
apiVersion: v1
kind: Service

metadata:
  name: order-service
  namespace: ecommerce

spec:
  type: ClusterIP

  selector:
    app: order-service

  ports:
    - name: http
      port: 8080
      targetPort: 8080
```

The most important line for Gateway is:

```yaml
metadata:
  name: order-service
```

That gives us the service name:

```text
order-service
```

Inside the same namespace, Gateway can call:

```text
http://order-service:8080
```

---

# 10. Understand `port` vs `targetPort`

This is commonly confused.

```yaml
ports:
  - port: 8080
    targetPort: 8080
```

Meaning:

```text
Caller
   |
   | order-service:8080
   v
Kubernetes Service
   |
   | targetPort 8080
   v
Order Pod :8080
```

`port`:

```text
port exposed by the Kubernetes Service
```

`targetPort`:

```text
port on the selected Pod/container
```

They do not have to be identical.

Example:

```yaml
port: 80
targetPort: 8080
```

would mean:

```text
Gateway
   |
   | order-service:80
   v
Service
   |
   | 8080
   v
Order Pod
```

For our lab, keeping both `8080` makes troubleshooting easier.

---

# 11. How Does Service Know Which Pods Are Order Pods?

This line:

```yaml
selector:
  app: order-service
```

matches Pods containing:

```yaml
labels:
  app: order-service
```

So:

```text
Service selector
app=order-service
        |
        v
+------------------+
| Order Pod 1      |
| app=order-service|
+------------------+

+------------------+
| Order Pod 2      |
| app=order-service|
+------------------+
```

If labels do not match:

```text
Service exists
     |
     X
No backend Pods
```

This is one of the most useful Kubernetes troubleshooting checks.

---

# 12. Verify Service Endpoints

After deployment:

```bash
kubectl get pods -n ecommerce -o wide
```

Example:

```text
order-service-xxx   10.244.0.21
order-service-yyy   10.244.0.22
```

Now:

```bash
kubectl get service order-service -n ecommerce
```

Then:

```bash
kubectl get endpoints order-service -n ecommerce
```

or inspect EndpointSlices:

```bash
kubectl get endpointslices -n ecommerce
```

You should see backend addresses corresponding to Order Pods.

Mental model:

```text
order-service
     |
     +--> 10.244.0.21:8080
     |
     +--> 10.244.0.22:8080
```

Gateway should not need these addresses.

---

# 13. Gateway Configuration Changes

Stage 2:

```yaml
uri: http://localhost:9091
```

Stage 3:

```yaml
uri: http://order-service:8080
```

Example:

```yaml
spring:
  cloud:
    gateway:
      routes:

        - id: order-service
          uri: http://order-service:8080
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1

        - id: payment-service
          uri: http://payment-service:8080
          predicates:
            - Path=/api/payments/**
          filters:
            - StripPrefix=1

        - id: inventory-service
          uri: http://inventory-service:8080
          predicates:
            - Path=/api/inventory/**
          filters:
            - StripPrefix=1
```

Notice what disappeared:

```text
localhost
9091
9092
9093
Pod IPs
```

Gateway knows stable Kubernetes Service names.

---

# 14. Important — Why `localhost` Fails Inside Gateway Pod

Suppose Gateway Pod contains:

```yaml
uri: http://localhost:9091
```

Inside that Pod:

```text
localhost
```

means:

```text
Gateway Pod itself
```

It does **not** mean:

```text
your Mac
Order Pod
another Kubernetes Pod
```

So:

```text
Gateway Pod
   |
localhost:9091
   |
   X
```

unless Order happens to be running inside the same Pod.

Correct:

```text
Gateway Pod
   |
order-service:8080
   |
Kubernetes Service
   |
Order Pod
```

---

# 15. Gateway Deployment

Example:

```yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: api-gateway
  namespace: ecommerce

spec:
  replicas: 1

  selector:
    matchLabels:
      app: api-gateway

  template:
    metadata:
      labels:
        app: api-gateway

    spec:
      containers:
        - name: api-gateway
          image: ecom-api-gateway:stage3
          imagePullPolicy: IfNotPresent

          ports:
            - containerPort: 8000
```

One replica is enough for this learning stage.

Later we will scale Gateway and discuss high availability.

---

# 16. Gateway Service

We need a Kubernetes Service in front of Gateway too.

```yaml
apiVersion: v1
kind: Service

metadata:
  name: api-gateway
  namespace: ecommerce

spec:
  type: NodePort

  selector:
    app: api-gateway

  ports:
    - name: http
      port: 8000
      targetPort: 8000
```

Why NodePort here?

Because we need to access Gateway from outside the Minikube cluster during the lab.

Backend services remain:

```text
ClusterIP
```

because external clients should not access them directly.

---

# 17. ClusterIP vs NodePort in Our Architecture

Use:

```text
Gateway       → NodePort for local Minikube access

Order         → ClusterIP
Payment       → ClusterIP
Inventory     → ClusterIP
```

Architecture:

```text
Your Mac
   |
   v
Gateway NodePort
   |
   v
Gateway Pod
   |
   +------> Order ClusterIP
   |
   +------> Payment ClusterIP
   |
   +------> Inventory ClusterIP
```

Backend services are internal.

That is closer to the production security boundary we want.

In AWS EKS, external exposure will eventually be through something like:

```text
ALB / Ingress
```

rather than using NodePort as the user-facing architecture.

---

# 18. Payment and Inventory Services

Payment:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: payment-service
  namespace: ecommerce

spec:
  type: ClusterIP

  selector:
    app: payment-service

  ports:
    - port: 8080
      targetPort: 8080
```

Inventory:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: inventory-service
  namespace: ecommerce

spec:
  type: ClusterIP

  selector:
    app: inventory-service

  ports:
    - port: 8080
      targetPort: 8080
```

Their Deployments follow the same pattern as Order.

Do not memorize YAML.

Understand:

```text
Deployment
    ↓ creates/manages
Pods

Service
    ↓ selects
Pods

DNS name
    ↓ resolves to
Service
```

---

# 19. Build Images for Minikube

The exact image workflow depends on how your Minikube runtime is configured.

A convenient approach is:

```bash
minikube image build \
  -t ecom-order-service:stage3 \
  ./order-service
```

Similarly:

```bash
minikube image build \
  -t ecom-payment-service:stage3 \
  ./payment-service

minikube image build \
  -t ecom-inventory-service:stage3 \
  ./inventory-service

minikube image build \
  -t ecom-api-gateway:stage3 \
  ./api-gateway
```

Verify:

```bash
minikube image ls
```

Your Deployment image names must exactly match the images you built.

---

# 20. Apply Everything

Example sequence:

```bash
kubectl apply -f kubernetes/namespace.yaml

kubectl apply -f kubernetes/order/
kubectl apply -f kubernetes/payment/
kubectl apply -f kubernetes/inventory/
kubectl apply -f kubernetes/gateway/
```

Verify:

```bash
kubectl get all -n ecommerce
```

You should eventually see:

```text
Gateway Pod
Order Pods
Payment Pod(s)
Inventory Pod(s)

api-gateway Service
order-service Service
payment-service Service
inventory-service Service
```

---

# 21. Test Gateway from Your Mac

Use:

```bash
minikube service api-gateway \
  -n ecommerce \
  --url
```

Suppose it returns:

```text
http://127.0.0.1:54321
```

Test:

```bash
curl http://127.0.0.1:54321/api/orders/101
```

Expected flow:

```text
Mac
 |
 v
Gateway NodePort
 |
 v
Gateway Pod
 |
 | DNS lookup: order-service
 v
Order ClusterIP Service
 |
 v
One Order Pod
```

Then test:

```bash
curl http://127.0.0.1:54321/api/payments/5001
```

and:

```bash
curl http://127.0.0.1:54321/api/inventory/P100
```

---

# 22. Prove Kubernetes DNS Works

Do not just trust the architecture diagram.

Enter Gateway Pod.

First get its name:

```bash
kubectl get pods -n ecommerce
```

Then:

```bash
kubectl exec \
  -it \
  -n ecommerce \
  <gateway-pod-name> \
  -- sh
```

Depending on the image, utilities such as `nslookup` or `curl` may not be installed.

For a clean debugging approach, create a temporary curl Pod:

```bash
kubectl run curl-test \
  --rm \
  -it \
  -n ecommerce \
  --image=curlimages/curl \
  -- sh
```

Inside:

```bash
curl http://order-service:8080/orders/101
```

If DNS/service networking is correct, you should receive the Order response.

You can also use a suitable DNS debugging image when you specifically need `nslookup`.

---

# 23. Service DNS Names

If Gateway and Order are in the same namespace:

```text
order-service
```

is normally enough.

The more explicit name is:

```text
order-service.ecommerce
```

The fully qualified cluster DNS form is typically:

```text
order-service.ecommerce.svc.cluster.local
```

So these represent increasing levels of qualification:

```text
order-service

order-service.ecommerce

order-service.ecommerce.svc.cluster.local
```

For services in the same namespace, prefer the simple service name unless you have a reason not to.

---

# 24. Cross-Namespace Scenario

Suppose later:

```text
Gateway namespace: edge

Order namespace: ecommerce
```

Now:

```text
http://order-service:8080
```

from Gateway may search its own namespace:

```text
edge
```

Instead use:

```text
http://order-service.ecommerce:8080
```

or the FQDN:

```text
http://order-service.ecommerce.svc.cluster.local:8080
```

This is an important interview scenario.

---

# 25. Practical Failure 1 — Wrong Service Name

Change Gateway:

```yaml
uri: http://orders-service:8080
```

Notice the extra `s`.

Actual Kubernetes Service:

```text
order-service
```

Call Gateway.

Expected category of failure:

```text
DNS/service resolution failure
```

Troubleshoot:

```bash
kubectl get svc -n ecommerce
```

Check exact name.

This is different from:

```text
route predicate mismatch
```

Gateway may select the route perfectly but fail to resolve the destination.

---

# 26. Practical Failure 2 — Service Exists but Selector Is Wrong

Change Order Service selector:

```yaml
selector:
  app: orders-service
```

while Pods have:

```yaml
labels:
  app: order-service
```

Now:

```bash
kubectl get svc -n ecommerce
```

shows the Service exists.

But:

```bash
kubectl get endpoints order-service -n ecommerce
```

may show no backend endpoints.

Architecture:

```text
Gateway
   |
   v
order-service
   |
   X
No selected Pods
```

This is a classic Kubernetes production issue.

Troubleshooting rule:

```text
Service exists
      ↓
Check endpoints
      ↓
If empty
      ↓
Check selector vs Pod labels
```

---

# 27. Practical Failure 3 — Wrong `targetPort`

Suppose Order Pod listens on:

```text
8080
```

but Service has:

```yaml
targetPort: 9091
```

Service discovery still works.

DNS still works.

Service exists.

Endpoints may exist.

But traffic is sent to the wrong Pod port.

Flow:

```text
Gateway
   |
order-service:8080
   |
Service
   |
targetPort 9091
   |
Order Pod
   |
   X
nothing listening
```

This is why Kubernetes troubleshooting must separate:

```text
DNS
Service
Endpoints
Port mapping
Application
```

---

# 28. Practical Failure 4 — Delete an Order Pod

This is one of the most important exercises.

Start with:

```yaml
replicas: 2
```

Check:

```bash
kubectl get pods -n ecommerce -l app=order-service -o wide
```

Call Gateway several times.

Now delete one Order Pod:

```bash
kubectl delete pod \
  -n ecommerce \
  <one-order-pod-name>
```

Immediately watch:

```bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service \
  -w
```

You should see Kubernetes create a replacement because Deployment wants:

```text
replicas = 2
```

Gateway configuration remains:

```yaml
uri: http://order-service:8080
```

No Gateway change.

No new Pod IP added to Gateway.

That is the core lesson.

---

# 29. Prove Which Pod Served the Request

For the lab, make Order Service return its Pod/host name.

Kubernetes automatically gives the container a hostname that commonly reflects the Pod name.

Example:

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @GetMapping("/{id}")
    public Map<String, Object> getOrder(
            @PathVariable Long id) {

        String hostName =
                System.getenv()
                      .getOrDefault("HOSTNAME", "local");

        return Map.of(
                "orderId", id,
                "status", "CONFIRMED",
                "servedBy", hostName
        );
    }
}
```

Call repeatedly:

```bash
for i in {1..10}; do
  curl -s http://<gateway-url>/api/orders/101
  echo
done
```

You may observe responses from different Pods over multiple connections/requests depending on connection reuse and Kubernetes networking behavior.

Do not expect perfect alternating:

```text
Pod1
Pod2
Pod1
Pod2
```

Load distribution is not guaranteed to be round-robin at the HTTP-request level, especially with persistent connections.

That distinction matters.

---

# 30. Complex Concept — Who Is Actually Load Balancing?

With this configuration:

```yaml
uri: http://order-service:8080
```

Spring Cloud Gateway is **not choosing a Pod from a list itself**.

Gateway connects to:

```text
order-service
```

Kubernetes networking provides the stable Service abstraction and forwards traffic to eligible backend endpoints.

Mental model:

```text
Spring Gateway
     |
     | "send to order-service"
     v
Kubernetes Service
     |
     | selects/forwards to backend
     v
Order Pod
```

This differs from:

```text
lb://order-service
```

with Spring Cloud LoadBalancer/DiscoveryClient, where Spring participates in selecting a discovered service instance.

For our first Kubernetes architecture, we intentionally use Kubernetes-native service routing.

---

# 31. `http://order-service` vs `lb://order-service`

This is an important architect/interview question.

## Option 1

```yaml
uri: http://order-service:8080
```

Meaning:

```text
Use Kubernetes DNS + Service networking.
```

Gateway sees one stable Service endpoint.

Advantages:

```text
simple
Kubernetes-native
no DiscoveryClient required
less RBAC/framework complexity
```

---

## Option 2

```yaml
uri: lb://order-service
```

Meaning:

```text
Use Spring Cloud's load-balancer/discovery abstraction.
```

This requires the appropriate discovery implementation and Spring Cloud LoadBalancer.

Potential reasons:

```text
client-side service instance selection
Spring Cloud discovery abstraction
metadata-aware discovery requirements
framework-specific load balancing behavior
```

But don't use it just because:

```text
"microservices should use lb://"
```

On Kubernetes, native Service DNS is often sufficient.

---

# 32. When Would Spring Cloud Kubernetes DiscoveryClient Be Useful?

Suppose you have a requirement where the application itself needs:

```text
List<ServiceInstance>
```

or needs Spring Cloud discovery integration.

Spring Cloud Kubernetes can query Kubernetes service/endpoints information.

That introduces considerations such as:

```text
ServiceAccount
RBAC
get/list/watch permissions
namespace scope
Endpoint/EndpointSlice access
```

Our Gateway does not need that complexity merely to call:

```text
order-service
```

So:

```text
Requirement first
Dependency second
```

is the architect approach.

---

# 33. Readiness — Why It Matters

Imagine Kubernetes starts a new Order Pod.

The Java process is still starting:

```text
JVM starting
Spring context loading
DB connection initializing
```

We don't want production traffic sent before the application is ready.

That is what a readiness probe helps with.

Example:

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080

  initialDelaySeconds: 10
  periodSeconds: 5
```

When Pod is not ready, it should not participate as a normal ready backend endpoint for Service traffic.

This becomes important when:

```text
scaling
rolling deployment
slow startup
dependency initialization
```

---

# 34. Liveness vs Readiness

Do not confuse them.

## Readiness

Question:

```text
Can this Pod receive traffic?
```

If no:

```text
keep Pod running
but stop sending normal traffic
```

## Liveness

Question:

```text
Is this application stuck/broken and should Kubernetes restart it?
```

Simplified:

```text
Readiness → traffic decision

Liveness  → restart decision
```

We will cover health probes more deeply in deployment/observability sections, but service discovery is where readiness first becomes important.

---

# 35. Example Order Deployment with Probes

Assuming Spring Boot Actuator probes are configured:

```yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: order-service
  namespace: ecommerce

spec:
  replicas: 2

  selector:
    matchLabels:
      app: order-service

  template:
    metadata:
      labels:
        app: order-service

    spec:
      containers:
        - name: order-service
          image: ecom-order-service:stage3
          imagePullPolicy: IfNotPresent

          ports:
            - containerPort: 8080

          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5

          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
```

Do not blindly copy probe timings into production.

They depend on actual application startup and failure characteristics.

---

# 36. Failure Exercise — Readiness

For a controlled learning experiment, make one Pod temporarily become unready or deploy a version whose readiness endpoint fails.

Observe:

```bash
kubectl get pods -n ecommerce
```

and:

```bash
kubectl get endpoints order-service -n ecommerce
```

The important idea:

```text
Running Pod
```

does not automatically mean:

```text
Ready backend receiving traffic
```

This matters during rolling deployments.

---

# 37. What Happens When We Scale Order?

Run:

```bash
kubectl scale deployment \
  order-service \
  -n ecommerce \
  --replicas=4
```

Verify:

```bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service
```

Gateway still has:

```yaml
uri: http://order-service:8080
```

Nothing changes in Gateway.

Architecture changes automatically:

```text
Before:

order-service
   |
 +---+
 |   |
P1  P2


After:

order-service
   |
 +---+---+---+
 |   |   |   |
P1  P2  P3  P4
```

This is one reason Kubernetes Services are so valuable.

---

# 38. Scale Back Down

```bash
kubectl scale deployment \
  order-service \
  -n ecommerce \
  --replicas=2
```

Again:

```text
No Gateway configuration change.
```

This is what we want from service discovery.

---

# 39. What if Service Name Changes?

Suppose:

```text
order-service
```

becomes:

```text
order-api
```

Gateway configuration:

```yaml
uri: http://order-service:8080
```

will stop resolving unless we preserve compatibility.

Service names are part of the internal platform contract.

Treat them carefully.

This is similar to changing:

```text
database hostname
Kafka topic
API endpoint
```

A stable name matters.

---

# 40. Configuration Problem

We now have:

```yaml
uri: http://order-service:8080
```

which works inside Kubernetes.

But if you run Gateway directly from IntelliJ on your Mac:

```text
order-service
```

may not resolve because that DNS name belongs to the Kubernetes cluster.

We need environment-specific configuration.

For example:

```text
local profile:
http://localhost:9091

k8s profile:
http://order-service:8080
```

---

# 41. Practical Profile Configuration

`application.yml`:

```yaml
spring:
  application:
    name: api-gateway
```

`application-local.yml`:

```yaml
services:
  order:
    url: http://localhost:9091

  payment:
    url: http://localhost:9092

  inventory:
    url: http://localhost:9093
```

`application-k8s.yml`:

```yaml
services:
  order:
    url: http://order-service:8080

  payment:
    url: http://payment-service:8080

  inventory:
    url: http://inventory-service:8080
```

Routes:

```yaml
spring:
  cloud:
    gateway:
      routes:

        - id: order-service
          uri: ${services.order.url}
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1
```

This lets the same Gateway code run in:

```text
IntelliJ
Minikube
EKS
```

with environment-specific values.

Later, Kubernetes ConfigMaps and deployment configuration will improve this further.

---

# 42. Activate Kubernetes Profile

In Gateway Deployment:

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: k8s
```

Now the Pod uses:

```text
application-k8s.yml
```

Local IntelliJ can use:

```text
local
```

This is much better than manually editing URLs every time you switch environments.

---

# 43. Complete Gateway Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: api-gateway
  namespace: ecommerce

spec:
  replicas: 1

  selector:
    matchLabels:
      app: api-gateway

  template:
    metadata:
      labels:
        app: api-gateway

    spec:
      containers:
        - name: api-gateway
          image: ecom-api-gateway:stage3
          imagePullPolicy: IfNotPresent

          ports:
            - containerPort: 8000

          env:
            - name: SPRING_PROFILES_ACTIVE
              value: k8s
```

The application chooses Kubernetes service URLs through its profile.

---

# 44. Troubleshooting Flow

If this fails:

```text
Client
  ↓
Gateway
  ↓
order-service
  ↓
Order Pod
```

do not randomly change YAML.

Use this order.

## Step 1 — Gateway reachable?

```bash
kubectl get pods -n ecommerce -l app=api-gateway
kubectl logs -n ecommerce <gateway-pod>
```

## Step 2 — Does Gateway route match?

Check Gateway route configuration.

## Step 3 — Does Service exist?

```bash
kubectl get svc order-service -n ecommerce
```

## Step 4 — Does Service have endpoints?

```bash
kubectl get endpoints order-service -n ecommerce
```

or:

```bash
kubectl get endpointslices -n ecommerce
```

## Step 5 — Are Pods ready?

```bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service
```

## Step 6 — Can another Pod call the Service?

```bash
kubectl run curl-test \
  --rm \
  -it \
  -n ecommerce \
  --image=curlimages/curl \
  -- \
  curl http://order-service:8080/orders/101
```

## Step 7 — Check application logs

```bash
kubectl logs \
  -n ecommerce \
  <order-pod-name>
```

This isolates the failing layer.

---

# 45. Very Useful Debugging Matrix

| Symptom | First Area to Check |
|---|---|
| Gateway itself unreachable | Gateway Pod / Gateway Service |
| Gateway 404 | Route predicate |
| Unknown host / DNS error | Service name / namespace / DNS |
| Service exists, no backend | Selector / endpoints / readiness |
| Connection refused | targetPort / application port |
| Downstream 404 | Gateway path rewrite / controller path |
| One Pod repeatedly fails | Pod logs / readiness |
| Works direct from debug Pod, fails via Gateway | Gateway config |
| Works in IntelliJ, fails in K8s | profile / DNS / Service config |

---

# 46. Scenario — Pod IP Hard-Coding

Never configure:

```yaml
uri: http://10.244.0.21:8080
```

Why?

Delete Pod:

```bash
kubectl delete pod ...
```

replacement may become:

```text
10.244.0.37
```

Gateway still calls:

```text
10.244.0.21
```

and fails.

Correct:

```yaml
uri: http://order-service:8080
```

Stable abstraction:

```text
Gateway
   |
Service name
   |
dynamic Pods
```

---

# 47. Scenario — Gateway Calls Order Across Namespace

Assume:

```text
api-gateway → namespace edge
order-service → namespace ecommerce
```

Gateway config:

```yaml
uri: http://order-service.ecommerce:8080
```

Flow:

```text
Gateway Pod
namespace=edge
      |
      | order-service.ecommerce
      v
Order Service
namespace=ecommerce
```

This is one reason namespace design matters.

Later we can add:

```text
NetworkPolicy
```

to control whether such traffic is allowed.

---

# 48. Scenario — Multiple Services with Same Name

Suppose:

```text
dev namespace:
order-service

prod namespace:
order-service
```

This is completely valid.

DNS context separates them.

```text
order-service.dev

order-service.prod
```

A Pod in `dev` calling:

```text
order-service
```

normally resolves within `dev`.

A Pod in `prod` calling:

```text
order-service
```

normally resolves within `prod`.

This is useful for environment isolation.

---

# 49. Architect Question — Should Dev and Prod Be Namespaces or Separate Clusters?

For a local lab:

```text
namespaces
```

are fine.

For real production, many organizations use stronger separation:

```text
different AWS accounts
different EKS clusters
different VPCs
```

depending on:

- risk
- compliance
- blast radius
- cost
- operational model

Do not conclude:

```text
namespace = complete production isolation
```

It is an isolation boundary, but not equivalent to a separate cluster/account.

---

# 50. Architect Question — Why ClusterIP for Backend Services?

Because:

```text
Order
Payment
Inventory
```

should generally be internal.

We want:

```text
Internet
   |
   v
Gateway
   |
   v
Order
```

not:

```text
Internet ------> Order
Internet ------> Payment
Internet ------> Inventory
```

ClusterIP exposes the Service inside the cluster rather than as a direct external endpoint.

Later:

```text
NetworkPolicy
security
mTLS/service mesh if required
```

can strengthen east-west controls.

---

# 51. Architect Question — Is Kubernetes Service Discovery Client-Side or Server-Side?

With our chosen architecture:

```yaml
uri: http://order-service:8080
```

Gateway is not fetching all Order Pods and selecting one using Spring code.

It calls a stable Kubernetes Service.

So conceptually:

```text
Gateway
  |
  | one stable destination
  v
Kubernetes Service
  |
  | platform networking
  v
Pod
```

That is closer to platform/server-side service routing than Spring client-side instance selection.

If using:

```text
DiscoveryClient + Spring Cloud LoadBalancer
```

the application participates more directly in instance discovery/selection.

---

# 52. Architect Question — What Happens During Pod Replacement?

Example:

```text
Order Pod A
Order Pod B
```

Pod A dies.

Deployment creates:

```text
Order Pod C
```

Kubernetes updates the Service's backend endpoint information.

Gateway continues using:

```text
order-service
```

It does not need to know:

```text
A disappeared
C appeared
```

This decoupling is exactly what we want.

---

# 53. Architect Question — Is Kubernetes Service Enough for Every Load-Balancing Requirement?

No.

Kubernetes Service is excellent for basic service connectivity/load distribution.

But advanced requirements may include:

```text
weighted routing
traffic splitting
zone awareness
request-level policies
advanced retries
mTLS
outlier detection
canary deployments
```

Those may involve:

```text
Gateway
Ingress
ALB
Service Mesh
progressive delivery tooling
```

depending on the requirement.

Do not overload one component with every traffic-management responsibility.

---

# 54. Architect Question — Why Not Use Spring Cloud Kubernetes Discovery Automatically?

Because Kubernetes DNS already solves our current requirement.

Adding DiscoveryClient means more moving parts.

Use it when we need features from that abstraction, not because the library exists.

Good architect answer:

> On Kubernetes I first evaluate native Service DNS because it provides stable service addressing without requiring the application to query Kubernetes APIs. I would add Spring Cloud Kubernetes DiscoveryClient only if we have a concrete requirement for Spring's discovery abstraction or client-side instance metadata/selection.

---

# 55. EKS Mapping

What you learn in Minikube maps directly to EKS concepts.

Minikube:

```text
Gateway Pod
   |
ClusterIP Service
   |
Order Pods
```

EKS:

```text
Gateway Pod
   |
ClusterIP Service
   |
Order Pods
```

The fundamental Kubernetes service discovery model is the same.

What changes around it:

```text
AWS VPC
EKS nodes
ALB
IAM
security groups
Route 53
ECR
CloudWatch / observability
```

So Minikube is useful here because the Kubernetes concepts are real, even though the surrounding cloud infrastructure is local.

---

# 56. Full Stage 3 Request Flow

Client:

```text
GET /api/orders/101
```

Flow:

```text
Mac
 |
 v
Minikube Gateway Service
 |
 v
Gateway Pod
 |
 | Path=/api/orders/**
 |
 | StripPrefix=1
 v
/orders/101
 |
 | URI=http://order-service:8080
 v
CoreDNS / Kubernetes DNS
 |
 v
order-service ClusterIP
 |
 v
Ready Order endpoint
 |
 v
Order Pod
 |
 v
OrderController
```

This connects Stage 1 + Stage 2 + Stage 3.

---

# 57. Hands-On Exercise 1

Deploy:

```text
Gateway       1 replica
Order         2 replicas
```

Keep Payment/Inventory optional until Order flow works.

Prove:

```text
Mac → Gateway → order-service → Order Pod
```

Do not add all services before the first flow works.

---

# 58. Hands-On Exercise 2

Return:

```text
HOSTNAME
```

from Order response.

Call Gateway repeatedly.

Observe which Pod serves requests.

Then delete one Pod.

Call again.

Verify:

```text
Gateway config did not change.
```

---

# 59. Hands-On Exercise 3

Scale:

```text
2 → 4
```

Order replicas.

Verify:

```bash
kubectl get endpoints order-service -n ecommerce
```

or EndpointSlices.

Then:

```text
4 → 2
```

Again, Gateway configuration remains unchanged.

---

# 60. Hands-On Exercise 4

Break the Service selector intentionally.

Example:

```yaml
selector:
  app: wrong-order-service
```

Apply.

Check:

```bash
kubectl get endpoints order-service -n ecommerce
```

Understand why Service exists but has no backend.

Fix it.

---

# 61. Hands-On Exercise 5

Break:

```yaml
targetPort
```

Example:

```yaml
targetPort: 9999
```

Observe the failure.

Then explain:

```text
DNS works
Service exists
selector works
endpoint exists
but application port mapping is wrong
```

This is excellent interview troubleshooting practice.

---

# 62. Hands-On Exercise 6

Move only conceptually first:

```text
Gateway namespace = edge
Order namespace = ecommerce
```

Before actually changing manifests, answer:

```text
What hostname should Gateway use?
```

Answer:

```text
order-service.ecommerce
```

Then optionally implement the cross-namespace exercise.

---

# 63. Coding-Agent Prompt

Use a bounded prompt:

```text
Inspect my existing api-gateway, order-service, payment-service,
inventory-service and current Kubernetes folder first.

We are implementing API Gateway Stage 3:
Kubernetes-native routing and service discovery using Minikube.

Architecture decision:
Use Kubernetes Services + DNS.
Do NOT add Eureka.
Do NOT add Spring Cloud Kubernetes DiscoveryClient unless I explicitly
ask for the alternative implementation.

Goals:

1. Create namespace: ecommerce.

2. Deploy api-gateway, order-service, payment-service and
   inventory-service to Minikube.

3. Backend services must use ClusterIP.

4. Gateway should be externally reachable for local testing.

5. Gateway must route using Kubernetes Service DNS:
   http://order-service:8080
   http://payment-service:8080
   http://inventory-service:8080

6. Use 2 replicas for Order Service.

7. Add a simple way for Order Service response/logs to identify
   which Pod served the request.

8. Keep local IntelliJ URLs separate from Kubernetes URLs using
   Spring profiles/configuration rather than manually changing code.

9. If Actuator is already present, configure reasonable readiness
   and liveness probes. Do not add unrelated observability work.

10. Do not add:
    - Eureka
    - Redis
    - JWT
    - rate limiting
    - circuit breaker
    - Kafka
    - service mesh
    - Helm

Before modifying anything:
- inspect existing ports and Dockerfiles
- inspect existing Spring profiles
- list files that need changes
- explain why each change is required

After implementation:
- provide image build commands for Minikube
- kubectl apply commands
- verification commands
- curl tests
- Pod deletion test
- scale test
- selector failure test
- targetPort failure test

Keep the implementation focused on Kubernetes service discovery.
```

---

# 64. Interview Drill

## Q1. How does Gateway discover Order Service in Kubernetes?

Our implementation uses:

```text
Kubernetes Service DNS.
```

Gateway calls:

```text
http://order-service:8080
```

rather than individual Pod IPs.

---

## Q2. Why don't you use Eureka?

Kubernetes already provides service registration/discovery through its Service/DNS/endpoints model.

Adding Eureka would duplicate the basic discovery responsibility without a requirement.

---

## Q3. What happens when Order Pod IP changes?

Nothing changes in Gateway.

The Kubernetes Service maintains the stable destination and its backend endpoints are updated as Pods change.

---

## Q4. What if the Service exists but traffic cannot reach any Pod?

Check:

```text
Service selector
Pod labels
Endpoints / EndpointSlices
Pod readiness
targetPort
application port
```

---

## Q5. `port` vs `targetPort`?

```text
port
=
Service-facing port

targetPort
=
backend Pod port
```

---

## Q6. Why use ClusterIP for Order?

Because Order is an internal backend service and should normally be reached through the platform's controlled entry path rather than exposed directly.

---

## Q7. `http://order-service` vs `lb://order-service`?

```text
http://order-service
```

uses Kubernetes Service DNS/networking.

```text
lb://order-service
```

uses Spring Cloud LoadBalancer plus a DiscoveryClient-compatible discovery mechanism.

Use the simpler Kubernetes-native model unless requirements justify client-side Spring discovery.

---

## Q8. How does Gateway call a Service in another namespace?

Use a namespace-qualified DNS name:

```text
order-service.ecommerce
```

or the full service DNS name.

---

## Q9. Why is readiness related to service discovery?

A Pod may be running but not ready to receive traffic.

Readiness controls whether it should participate as a ready backend for normal Service traffic.

---

## Q10. Is Minikube learning useful for EKS?

Yes.

Core concepts remain:

```text
Deployment
Pod
Service
DNS
readiness
scaling
service-to-service communication
```

EKS adds AWS infrastructure around Kubernetes.

---

# 65. Stage 3 Completion Checklist

## Core understanding

- [ ] I understand Pod vs Service.
- [ ] I understand why Pod IP must not be hard-coded.
- [ ] I understand ClusterIP.
- [ ] I understand Service selector vs Pod labels.
- [ ] I understand `port` vs `targetPort`.
- [ ] I understand Kubernetes DNS.
- [ ] I understand same-namespace vs cross-namespace DNS.
- [ ] I understand why we are not using Eureka.
- [ ] I understand native DNS vs Spring DiscoveryClient.
- [ ] I understand readiness at a practical level.

## Implementation

- [ ] `ecommerce` namespace created.
- [ ] Gateway deployed to Minikube.
- [ ] Order deployed with 2 replicas.
- [ ] `order-service` ClusterIP created.
- [ ] Payment Service created.
- [ ] Inventory Service created.
- [ ] Gateway uses Kubernetes Service names.
- [ ] Local and K8s URLs are separated by configuration/profile.
- [ ] Gateway is reachable from my Mac.
- [ ] `/api/orders/**` works end to end.

## Failure practice

- [ ] I tested a wrong Service name.
- [ ] I tested a wrong selector.
- [ ] I inspected empty/missing endpoints.
- [ ] I tested a wrong `targetPort`.
- [ ] I deleted an Order Pod.
- [ ] I watched Kubernetes replace it.
- [ ] Gateway continued using the same Service name.

## Scaling

- [ ] I scaled Order from 2 to 4 replicas.
- [ ] I inspected Pods and endpoints.
- [ ] I scaled back to 2.
- [ ] I understand why Gateway configuration never changed.

## Interview

- [ ] I can explain Kubernetes-native service discovery.
- [ ] I can explain why Eureka is unnecessary here.
- [ ] I can explain `http://service` vs `lb://service`.
- [ ] I can troubleshoot Service-with-no-endpoints.
- [ ] I can explain readiness vs liveness.
- [ ] I can explain cross-namespace service DNS.

---

# 66. What We Have Connected So Far

Stage 1:

```text
Gateway routing
```

Stage 2:

```text
Predicates
Filters
Path transformation
```

Stage 3:

```text
Kubernetes-native service discovery
```

Combined:

```text
Client
   |
   v
Gateway
   |
Path Predicate
   |
StripPrefix / RewritePath
   |
Kubernetes Service DNS
   |
ClusterIP Service
   |
Ready Pod
```

This is now much closer to a real production microservices request path.

---

# 67. Next Stage

The next natural question is:

```text
Order Service has multiple Pods.

How does traffic distribution behave?

What happens during scaling?

What happens when Gateway itself has multiple replicas?

Where does ALB/Ingress fit?

Where does Kubernetes load balancing stop
and Gateway load balancing begin?
```

That becomes the next stage:

```text
Stage 4 — Load Balancing, Gateway Scaling & High Availability
```

We will connect:

```text
Client
   |
ALB / local Minikube entry
   |
Multiple Gateway Pods
   |
Kubernetes Services
   |
Multiple backend Pods
```

and deliberately test scaling and failures rather than just drawing the architecture.
