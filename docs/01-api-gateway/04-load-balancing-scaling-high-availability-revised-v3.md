# API Gateway --- Stage 4: Load Balancing, Scaling & High Availability

> **Previous:** `03-kubernetes-routing-service-discovery.md`\
> **Stage:** 04\
> **Runtime:** Minikube\
> **Production Mapping:** AWS EKS\
> **Goal:** Prove how traffic is distributed across multiple Gateway and
> backend Pods, then practice scaling and failure recovery.\
> **Style:** Small concept → complete YAML/code → commands → test →
> expected result → architect note.

------------------------------------------------------------------------

# 1. What Are We Building?

This stage is about **availability at both routing layers**, not merely
increasing a replica number. We need the Gateway tier itself to survive
an instance loss, while backend Services continue routing to healthy
application Pods. The architect idea is to identify every runtime layer
that can become a single point of failure and give critical layers
enough independent capacity.

Stage 3 gave us this:

``` text
Client
   |
   v
Gateway Pod
   |
   v
order-service
   |
+---------+
|         |
Order P1  Order P2
```

The backend is replicated, but Gateway is still only one Pod.

For high availability we want:

``` text
                        Client
                           |
                           v
                  api-gateway Service
                     /           \
                    /             \
             Gateway Pod 1    Gateway Pod 2
                    \             /
                     \           /
                      order-service
                     /           \
                    /             \
              Order Pod 1     Order Pod 2
```

We are going to **implement and test this in Minikube**.

By the end of the stage you will practice:

-   multiple Gateway replicas
-   multiple Order replicas
-   Kubernetes Service load distribution
-   Gateway Pod failure
-   Order Pod failure
-   readiness behavior
-   rolling updates
-   resource requests/limits
-   Horizontal Pod Autoscaler
-   load generation
-   production HA mapping to EKS

------------------------------------------------------------------------

# 2. Important Mental Model --- Two Load-Balancing Layers

There are two separate distribution decisions in this architecture. The
`api-gateway` Service chooses an eligible Gateway endpoint, and later
the `order-service` Service chooses an eligible Order endpoint. These
are independent hops, so troubleshoot and measure the two layers
separately rather than treating them as one load balancer.

There are two different Service layers in our local architecture.

``` text
Mac
 |
 v
api-gateway Kubernetes Service
 |
 +---- Gateway Pod 1
 |
 +---- Gateway Pod 2
          |
          v
     order-service
          |
     +----+----+
     |         |
 Order Pod 1  Order Pod 2
```

So traffic distribution happens twice:

``` text
Layer 1
api-gateway Service
        ↓
Gateway Pods


Layer 2
order-service
        ↓
Order Pods
```

Spring Cloud Gateway is **not** manually selecting the Order Pod in our
current design.

It calls:

``` text
http://order-service:8080
```

Kubernetes Service networking handles the backend endpoint selection.

------------------------------------------------------------------------

# 3. First Make Pod Identity Visible

When every replica returns identical output, load-balancing behavior is
difficult to prove from the client side. Exposing the Pod hostname gives
us a temporary **observability marker** so we can see which Order
instance handled a request. This is useful for the lab; production APIs
normally should not expose internal Pod names externally.

If all Pods return the same JSON, you cannot easily prove which instance
handled the request.

Add the Pod hostname to the Order response.

## Order Controller

``` java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @GetMapping("/{id}")
    public Map<String, Object> getOrder(@PathVariable Long id) {

        String podName =
                System.getenv()
                        .getOrDefault("HOSTNAME", "local");

        return Map.of(
                "orderId", id,
                "status", "CONFIRMED",
                "servedBy", podName
        );
    }
}
```

Example response:

``` json
{
  "orderId": 101,
  "status": "CONFIRMED",
  "servedBy": "order-service-7f6d984f7c-k8r2x"
}
```

This is only for the workout.

------------------------------------------------------------------------

# 4. Also Make Gateway Pod Identity Visible

The Gateway has the same observability problem. A temporary response
header identifies the Gateway Pod, while the Order response identifies
the downstream Pod. Together they give us evidence for both distribution
layers without changing the actual routing design.

We want to prove which Gateway Pod handled a request too.

Create a very small Global Filter.

``` java
@Component
public class GatewayInstanceHeaderFilter
        implements GlobalFilter, Ordered {

    private static final String HEADER =
            "X-Gateway-Pod";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String podName =
                System.getenv()
                        .getOrDefault("HOSTNAME", "local");

        exchange.getResponse()
                .getHeaders()
                .set(HEADER, podName);

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
```

Imports:

``` java
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
```

Now one request can tell us:

``` text
Which Gateway Pod?
Which Order Pod?
```

Example:

``` text
Response header:
X-Gateway-Pod: api-gateway-6d4f6f6cc7-z8lph

JSON:
"servedBy": "order-service-7f6d984f7c-k8r2x"
```

That makes the lab much more useful.

------------------------------------------------------------------------

# 5. Gateway Deployment --- 2 Replicas

A Deployment's `replicas` field expresses how many Pod instances
Kubernetes should try to maintain. If one Gateway Pod disappears, the
Deployment controller notices that actual replicas are below desired
replicas and creates a replacement. Multiple replicas provide runtime
redundancy, but only Ready Pods selected by the Service are useful for
normal traffic.

Update:

``` text
kubernetes/gateway/deployment.yaml
```

``` yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: api-gateway
  namespace: ecommerce

spec:
  replicas: 2

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
          image: ecom-api-gateway:stage4
          imagePullPolicy: IfNotPresent

          ports:
            - containerPort: 8000

          env:
            - name: SPRING_PROFILES_ACTIVE
              value: k8s

          resources:
            requests:
              cpu: "100m"
              memory: "256Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"

          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8000
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 3

          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8000
            initialDelaySeconds: 20
            periodSeconds: 10
            failureThreshold: 3
```

Why:

``` text
replicas: 2
```

means Deployment continuously tries to keep two Gateway Pods running.

------------------------------------------------------------------------

# 6. Gateway Service

A Kubernetes Service gives clients a stable address while Gateway Pod
IPs can change because of restarts, scaling, or rollouts. Its selector
decides which Pods belong behind the Service, and `targetPort` decides
where traffic enters those Pods. A wrong selector can leave a healthy
Deployment unreachable because the Service has no matching endpoints.

``` text
kubernetes/gateway/service.yaml
```

``` yaml
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

The Service selector:

``` yaml
app: api-gateway
```

matches both Gateway Pods.

Architecture:

``` text
api-gateway Service
       |
   +---+---+
   |       |
 GW Pod1 GW Pod2
```

------------------------------------------------------------------------

# 7. Order Deployment --- 2 Replicas

Backend replication follows the same controller model as Gateway
replication. Two Order Pods mean Kubernetes tries to keep two Order
instances alive, but replica count alone does not guarantee fault
isolation across nodes. Production HA also needs placement across
failure domains and enough spare capacity.

``` text
kubernetes/order/deployment.yaml
```

``` yaml
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
          image: ecom-order-service:stage4
          imagePullPolicy: IfNotPresent

          ports:
            - containerPort: 8080

          resources:
            requests:
              cpu: "100m"
              memory: "256Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"

          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 3

          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
            failureThreshold: 3
```

------------------------------------------------------------------------

# 8. Order Service

`ClusterIP` is appropriate for Order Service because Order is reached
from inside the cluster through Gateway rather than exposed directly.
Gateway uses the stable Service DNS name instead of tracking Pod IPs.
This decouples application routing from Pod lifecycle events such as
scaling and replacement.

``` text
kubernetes/order/service.yaml
```

``` yaml
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

Gateway route remains:

``` yaml
uri: http://order-service:8080
```

No Pod IPs.

No manual instance list.

------------------------------------------------------------------------

# 9. Actuator Requirement

Readiness and liveness probes need an application signal that represents
Spring Boot's availability state. Actuator provides health endpoints so
Kubernetes can make traffic and restart decisions based on application
state rather than merely checking whether Java is running. Probe paths
and ports must match the application's real configuration.

The readiness/liveness endpoints above require Spring Boot Actuator.

Example Gradle dependency:

``` gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

For Spring Boot applications, enable probe exposure as appropriate for
your current Boot version/configuration.

A common configuration is:

``` yaml
management:
  endpoint:
    health:
      probes:
        enabled: true

  endpoints:
    web:
      exposure:
        include:
          - health
          - info
```

Test locally first:

``` bash
curl http://localhost:8000/actuator/health/readiness
```

and:

``` bash
curl http://localhost:8000/actuator/health/liveness
```

Expected:

``` json
{"status":"UP"}
```

Do the same for Order Service.

------------------------------------------------------------------------

# 10. Build Stage 4 Images

Minikube must be able to access the exact image referenced by the
Deployment. Building directly into Minikube avoids the common local
problem where an image exists on the Mac but not inside Minikube's
runtime. Explicit version/stage tags also make rollout and rollback
behavior easier to understand than repeatedly reusing `latest`.

From the workspace root:

``` bash
minikube image build \
  -t ecom-api-gateway:stage4 \
  ./api-gateway
```

``` bash
minikube image build \
  -t ecom-order-service:stage4 \
  ./order-service
```

If Payment/Inventory are unchanged from Stage 3, you can keep their
existing images.

Verify:

``` bash
minikube image ls | grep ecom
```

------------------------------------------------------------------------

# 11. Apply Deployments

`kubectl apply` updates Kubernetes desired state; it does not prove that
the application is ready. After applying, verify Deployment readiness
and Pod state before testing traffic. This separates **configuration
accepted** from **workload successfully running**, an important
operational distinction.

``` bash
kubectl apply -f kubernetes/gateway/
```

``` bash
kubectl apply -f kubernetes/order/
```

Verify:

``` bash
kubectl get pods -n ecommerce -o wide
```

You should see something like:

``` text
api-gateway-...     Running   1/1
api-gateway-...     Running   1/1

order-service-...   Running   1/1
order-service-...   Running   1/1
```

Check Deployments:

``` bash
kubectl get deployments -n ecommerce
```

Expected:

``` text
NAME            READY   UP-TO-DATE   AVAILABLE
api-gateway     2/2     2            2
order-service   2/2     2            2
```

------------------------------------------------------------------------

# 12. Verify Service Backends

A Service can exist even when it has no usable backends, so inspecting
endpoints is one of the fastest routing checks. EndpointSlices contain
the backend addresses Kubernetes associates with a Service. If expected
Pod IPs are missing, investigate selectors and readiness before changing
Gateway routing.

Gateway endpoints:

``` bash
kubectl get endpoints \
  api-gateway \
  -n ecommerce
```

Order endpoints:

``` bash
kubectl get endpoints \
  order-service \
  -n ecommerce
```

Also inspect EndpointSlices:

``` bash
kubectl get endpointslices \
  -n ecommerce
```

Conceptually:

``` text
api-gateway
   |
   +--> Gateway Pod IP 1
   +--> Gateway Pod IP 2


order-service
   |
   +--> Order Pod IP 1
   +--> Order Pod IP 2
```

Kubernetes Service endpoints come from matching/ready Pods. Readiness is
important because unready Pods should not receive normal Service
traffic. citeturn331361search2turn331361search4

------------------------------------------------------------------------

# 13. Get Gateway URL

`minikube service --url` is a local-development convenience that gives
the Mac a reachable address for the Service. It is not the production
ingress architecture. In EKS, external traffic normally enters through
AWS/Kubernetes load-balancing or ingress components.

``` bash
minikube service \
  api-gateway \
  -n ecommerce \
  --url
```

Example:

``` text
http://127.0.0.1:54321
```

Store it:

``` bash
export GATEWAY_URL=$(minikube service \
  api-gateway \
  -n ecommerce \
  --url)
```

Verify:

``` bash
echo $GATEWAY_URL
```

------------------------------------------------------------------------

# 14. Practical Test --- Which Gateway and Order Pod Served Me?

This test creates a simple trace across two runtime layers. The response
header identifies the Gateway Pod and the response body identifies the
Order Pod. Repeating the call gives observable evidence of replica usage
instead of assuming replicas are receiving traffic.

Run:

``` bash
curl -i \
  $GATEWAY_URL/api/orders/101
```

Look for:

``` text
X-Gateway-Pod
```

and:

``` json
"servedBy"
```

Example:

``` text
X-Gateway-Pod: api-gateway-6d4f6f6cc7-z8lph
```

``` json
{
  "orderId": 101,
  "status": "CONFIRMED",
  "servedBy": "order-service-7f6d984f7c-k8r2x"
}
```

Now you have evidence for both layers.

------------------------------------------------------------------------

# 15. Run 20 Requests

A 20-request loop is useful for functional observation, but it is not a
meaningful performance benchmark. HTTP connection reuse and local
networking can make a small sample look uneven. Use this step to observe
identities; later use a load generator plus metrics for capacity
conclusions.

``` bash
for i in {1..20}; do
  echo "---- Request $i ----"

  curl -s -D - \
    $GATEWAY_URL/api/orders/101 \
    -o /tmp/order-response.json \
    | grep -i X-Gateway-Pod

  cat /tmp/order-response.json
  echo
done
```

Observe:

``` text
Gateway Pod name
Order Pod name
```

You may see different Pods.

Do **not** expect:

``` text
GW1
GW2
GW1
GW2
```

or:

``` text
Order1
Order2
Order1
Order2
```

in perfect alternation.

------------------------------------------------------------------------

# 16. Complex Concept --- Why Distribution May Look Uneven

Kubernetes Service distribution does not promise perfect
request-by-request round robin. Services route network traffic toward
eligible endpoints, while HTTP clients can reuse established connections
for multiple requests. A sequence such as A, A, A, B can therefore be
normal; the important question is whether healthy endpoints are eligible
and the system distributes load under realistic traffic.

Kubernetes Service gives traffic a stable virtual endpoint and routes
connections to eligible backends. It does not promise that your 20 HTTP
requests will alternate perfectly between Pods. Persistent HTTP
connections, connection pooling, proxy implementation, and Service
routing behavior can make short tests appear uneven. Kubernetes also
exposes traffic policies/preferences rather than promising simplistic
request-by-request round robin.
citeturn331361search0turn331361search3

For our architecture:

``` text
Gateway HTTP client
      |
may reuse connection
      |
order-service
      |
same backend may handle
multiple HTTP requests
```

So:

> **Load balanced does not mean perfectly alternating requests.**

This is a useful architect interview point.

------------------------------------------------------------------------

# 17. Test Gateway Pod Failure

Deleting a Gateway Pod tests two mechanisms: the Service must stop
depending on the disappearing endpoint, and the Deployment must restore
the desired replica count. The surviving Ready replica provides
immediate capacity while the replacement starts. HA therefore depends on
**already-running redundancy**, not only on Kubernetes recreating failed
Pods.

First list Gateway Pods:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=api-gateway
```

Example:

``` text
api-gateway-6d4f...-aaa
api-gateway-6d4f...-bbb
```

Open another terminal:

``` bash
while true; do
  date

  curl \
    --max-time 2 \
    -s \
    $GATEWAY_URL/api/orders/101

  echo
  sleep 1
done
```

Now delete one Gateway Pod:

``` bash
kubectl delete pod \
  -n ecommerce \
  api-gateway-6d4f...-aaa
```

Watch:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=api-gateway \
  -w
```

What should happen?

``` text
One Gateway Pod deleted
        ↓
Deployment notices desired=2, actual=1
        ↓
Replacement Pod created
        ↓
Remaining ready Gateway continues serving
        ↓
New Pod becomes Ready
        ↓
Service has two ready backends again
```

You may see an occasional failure during a crude local test depending on
timing/network behavior, but the architecture should no longer depend on
one Gateway instance.

------------------------------------------------------------------------

# 18. Verify Gateway Endpoints During Failure

Watching endpoints during failure shows the routing plane changing
independently from Deployment desired state. As a Pod terminates or
becomes unready, it should leave normal ready traffic; when the
replacement becomes Ready, it joins again. This is the practical
connection between probes, Services, and self-healing.

Before deletion:

``` bash
kubectl get endpoints \
  api-gateway \
  -n ecommerce
```

Delete one Pod.

Run repeatedly:

``` bash
watch kubectl get endpoints \
  api-gateway \
  -n ecommerce
```

You should see the endpoint set change as Pods terminate and
replacements become ready.

This proves that:

``` text
Service
```

is tracking backend availability.

------------------------------------------------------------------------

# 19. Test Order Pod Failure

Deleting an Order Pod tests downstream availability without changing
Gateway configuration. Gateway continues calling the stable
`order-service` name while Kubernetes uses remaining eligible endpoints
and the Deployment creates a replacement. This is why applications
should depend on Service identity rather than individual Pod addresses.

List:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service
```

Keep requests running:

``` bash
while true; do
  curl \
    --max-time 2 \
    -s \
    $GATEWAY_URL/api/orders/101
  echo
  sleep 1
done
```

Delete one Order Pod:

``` bash
kubectl delete pod \
  -n ecommerce \
  <order-pod-name>
```

Watch:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service \
  -w
```

Expected architecture behavior:

``` text
Gateway
   |
order-service
   |
remaining ready Order Pod
```

Deployment creates a replacement.

Gateway configuration remains unchanged.

------------------------------------------------------------------------

# 20. Scale Order Manually

Manual scaling changes the Deployment's desired replica count.
Kubernetes creates extra Order Pods and, after they become Ready, the
Service can route to them; scaling down removes excess Pods. Gateway
still calls exactly the same Service URL, making horizontal scaling
transparent to the caller.

Scale:

``` bash
kubectl scale deployment \
  order-service \
  -n ecommerce \
  --replicas=4
```

Verify:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service
```

Endpoints:

``` bash
kubectl get endpoints \
  order-service \
  -n ecommerce
```

Now architecture is:

``` text
order-service
   |
+--+--+--+--+
|  |  |  |  |
P1 P2 P3 P4
```

Gateway still uses:

``` yaml
uri: http://order-service:8080
```

Nothing changes in Spring configuration.

Scale back:

``` bash
kubectl scale deployment \
  order-service \
  -n ecommerce \
  --replicas=2
```

------------------------------------------------------------------------

# 21. Scale Gateway Manually

Gateway scaling is independent from Order scaling. More Gateway replicas
add capacity at the routing/policy tier, while more Order replicas add
business-service capacity. An architect should scale the constrained
layer rather than assuming every service needs the same replica count.

``` bash
kubectl scale deployment \
  api-gateway \
  -n ecommerce \
  --replicas=4
```

Verify:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=api-gateway
```

Gateway Service automatically tracks the matching ready Pods.

No client URL change:

``` text
GATEWAY_URL
```

remains the same.

Scale back:

``` bash
kubectl scale deployment \
  api-gateway \
  -n ecommerce \
  --replicas=2
```

------------------------------------------------------------------------

# 22. Why Resource Requests Matter for HPA

## 1. What Section 22 Means

The Horizontal Pod Autoscaler (HPA) needs a baseline to decide how busy
a Pod is. For percentage-based CPU scaling, that baseline is the
container's `resources.requests.cpu`. HPA compares the actual CPU usage
reported by Metrics Server with the requested CPU and converts the
result into a percentage.

``` yaml
resources:
  requests:
    cpu: "100m"
    memory: "256Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"
```

In this example:

-   `100m` is the requested CPU and is the baseline used by
    CPU-utilization HPA.
-   `500m` is the maximum CPU the container is allowed to consume.
-   `1000m` equals one complete CPU core.
-   HPA calculates CPU utilization against the **request**, not the
    limit.

## 2. How HPA Calculates CPU Utilization

``` text
CPU utilization = actual CPU usage / requested CPU × 100
```

Example:

``` text
Requested CPU = 100m
Actual usage  = 60m

Utilization = 60m / 100m × 100
            = 60%
```

If the HPA target is 50%, this Pod is above the target.

``` yaml
metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 50
```

## 3. Why `requests.cpu` Is Important

Knowing only that a Pod uses `60m` CPU does not tell Kubernetes whether
the Pod is busy. If the request is `100m`, the usage is 60%; if the
request is `500m`, the same usage is only 12%. Without a CPU request,
percentage-based CPU HPA has no baseline from which to calculate
utilization.

The configured request must also be realistic:

  -----------------------------------------------------------------------
         CPU request       Actual usage    HPA utilization Possible
                                                           effect
  ------------------ ------------------ ------------------ --------------
               `50m`              `60m`               120% HPA may scale
                                                           too
                                                           aggressively

              `100m`              `60m`                60% Reasonable for
                                                           a 50% target

              `500m`              `60m`                12% HPA may scale
                                                           too slowly

      Not configured              `60m`   Cannot calculate Utilization
                                                           metric may be
                                                           unavailable
  -----------------------------------------------------------------------

## 4. How HPA Calculates the Replica Count

HPA approximately uses this formula:

``` text
desired replicas = current replicas × current average utilization / target utilization
```

Assume:

``` text
Current replicas            = 2
Current average utilization = 65%
Target utilization          = 50%
```

Calculation:

``` text
Desired replicas = 2 × 65 / 50
                 = 2.6
```

Kubernetes cannot create a fraction of a Pod, so it rounds the result
**up**:

``` text
2.6 → 3 Pods
```

There are already two Pods, so the Deployment creates one additional
Pod:

``` text
Existing Pods   = 2
Additional Pods = 1
Total Pods      = 3
```

### Understanding from the question

The `2.6` value is only an intermediate mathematical result. It does not
mean Kubernetes temporarily creates 2.6 Pods. HPA converts it into a
whole-number desired replica count of 3 and updates the Deployment
accordingly.

## 5. What Happens to `replicas: 2` in the Deployment?

The Deployment may initially contain:

``` yaml
spec:
  replicas: 2
```

Once HPA manages this Deployment, HPA dynamically updates the
Deployment's desired replica count. Therefore, `replicas: 2` should not
be understood as a permanent limit. The allowed range comes from the
HPA:

``` yaml
minReplicas: 2
maxReplicas: 5
```

HPA can therefore manage the Deployment within this range:

``` text
2 Pods → 3 Pods → 4 Pods → 5 Pods
```

It will not scale below 2 or above 5.

## 6. What If the Calculation Produces 5.5?

Assume the HPA calculation produces:

``` text
Calculated replicas = 5.5
```

HPA first rounds it up:

``` text
5.5 → 6 Pods
```

But the configuration allows a maximum of only five Pods:

``` yaml
maxReplicas: 5
```

Therefore, HPA caps the final desired count at five:

``` text
Calculated value     = 5.5
Rounded value        = 6
Maximum allowed      = 5
Final desired count  = 5 Pods
```

### Understanding from the question

Kubernetes will not create the sixth Pod because `maxReplicas` is a
strict upper boundary. Even if CPU usage remains above the target, HPA
keeps the Deployment at five Pods until the maximum is increased or the
workload decreases.

## 7. Final Replica Rule

Conceptually, HPA performs these steps:

``` text
1. Calculate the required replica count.
2. Round a fractional result up to a whole Pod.
3. Enforce minReplicas and maxReplicas.
4. Update the Deployment's desired replica count.
5. The Deployment creates or removes Pods.
```

For the current configuration:

``` text
Final replicas = bounded between 2 and 5
```

Examples:

    Calculated result   Rounded result   Final result Reason
  ------------------- ---------------- -------------- ----------------------------
                `1.4`                2              2 Meets `minReplicas: 2`
                `2.6`                3              3 Within the allowed range
                `4.1`                5              5 Within the allowed range
                `5.5`                6              5 Capped by `maxReplicas: 5`

## 8. If Five Pods Are Still Overloaded

When all five Pods remain above the CPU target, HPA cannot add more Pods
because it has reached `maxReplicas`. An architect should investigate
whether to:

-   Increase `maxReplicas` after checking available node capacity.
-   Add Kubernetes nodes or enable Cluster Autoscaler if Pods cannot be
    scheduled.
-   Increase the CPU request or limit only when application measurements
    justify it.
-   Optimize expensive application logic, database calls, or downstream
    calls.
-   Check whether CPU is really the correct scaling metric; request
    rate, queue depth, or latency may sometimes be more useful.

## 9. Request Versus Limit

``` text
CPU request = scheduling reservation and HPA percentage baseline
CPU limit   = maximum CPU the container may consume
```

Example:

``` text
Request = 100m
Limit   = 500m
Usage   = 200m
```

HPA calculates:

``` text
200m / 100m × 100 = 200% utilization
```

It does **not** calculate `200m / 500m = 40%`, because utilization-based
HPA uses the request as its baseline.

## 10. Complete Flow

``` text
Metrics Server reports CPU usage
        ↓
HPA compares usage with resources.requests.cpu
        ↓
HPA calculates average utilization across the Pods
        ↓
HPA calculates and rounds the required replica count
        ↓
HPA enforces minReplicas and maxReplicas
        ↓
HPA updates the Deployment's desired replica count
        ↓
Deployment creates or removes Pods
```

## Architect / Interview Summary

-   CPU-utilization HPA calculates utilization relative to
    `resources.requests.cpu`.
-   Resource requests must be configured and should be based on measured
    application behavior.
-   A fractional calculation such as `2.6` is rounded up to 3 Pods.
-   HPA controls the Deployment replica count after it is enabled.
-   `minReplicas` and `maxReplicas` are hard scaling boundaries.
-   A calculated value of `5.5` rounds to 6 but is capped at 5 when
    `maxReplicas: 5`.
-   Reaching `maxReplicas` while CPU remains high indicates that
    capacity, configuration, application efficiency, or the selected
    metric should be reviewed.

# 23. Enable Metrics Server in Minikube

**Metrics Server** supplies current Pod and node CPU/memory usage
through the Kubernetes Metrics API. CPU-based HPA needs this data before
it can make scaling decisions. In Minikube we therefore enable Metrics
Server first, then verify that `kubectl top` returns metrics.

Check addons:

``` bash
minikube addons list
```

Enable Metrics Server:

``` bash
minikube addons enable metrics-server
```

Wait until available:

``` bash
kubectl get pods \
  -n kube-system \
  | grep metrics
```

Then:

``` bash
kubectl top pods \
  -n ecommerce
```

You should eventually see CPU/memory metrics.

If metrics are temporarily unavailable immediately after enabling, give
the components time to collect samples and retry.

------------------------------------------------------------------------

# 24. HPA for API Gateway

A **HorizontalPodAutoscaler (HPA)** changes a workload's desired replica
count according to observed metrics. Here we keep at least 2 Gateway
Pods for availability and allow scaling up to 5 when CPU pressure rises.
HPA updates the Deployment's desired replica count; the Deployment then
creates or removes Pods.

Create:

``` text
kubernetes/gateway/hpa.yaml
```

``` yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler

metadata:
  name: api-gateway
  namespace: ecommerce

spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-gateway

  minReplicas: 2
  maxReplicas: 5

  behavior:
    scaleDown:
      stabilizationWindowSeconds: 60

  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```

Kubernetes currently uses `autoscaling/v2` as the stable HPA API, and
HPA can scale Deployments based on resource or custom metrics.
citeturn331361search1turn331361search5

Apply:

``` bash
kubectl apply \
  -f kubernetes/gateway/hpa.yaml
```

Inspect:

``` bash
kubectl get hpa \
  -n ecommerce
```

Detailed:

``` bash
kubectl describe hpa \
  api-gateway \
  -n ecommerce
```

------------------------------------------------------------------------

# 25. HPA for Order Service

Gateway and Order Service are different workloads and can become busy
for different reasons. Giving Order Service its own HPA lets it scale
according to **its own** resource pressure instead of copying the
Gateway replica count. Independent scaling is a core microservices
benefit.

Create:

``` text
kubernetes/order/hpa.yaml
```

``` yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler

metadata:
  name: order-service
  namespace: ecommerce

spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service

  minReplicas: 2
  maxReplicas: 6

  behavior:
    scaleDown:
      stabilizationWindowSeconds: 60

  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```

Apply:

``` bash
kubectl apply \
  -f kubernetes/order/hpa.yaml
```

Watch:

``` bash
kubectl get hpa \
  -n ecommerce \
  -w
```

------------------------------------------------------------------------

# 26. Important --- HPA Is Not Instant

HPA is a **reactive control loop**, not an instant traffic detector.
Kubernetes reads metrics, calculates a desired replica count, creates
new Pods, and waits for them to become Ready. A sudden spike can
therefore arrive before extra capacity exists, so production systems
still need baseline capacity and headroom.

HPA runs as a control loop, not continuously. Kubernetes documentation
notes the controller evaluates metrics periodically; scaling also has
stabilization and startup/readiness considerations.
citeturn331361search1

So do not expect:

``` text
load starts
↓
1 second later
5 Pods
```

Think:

``` text
metrics collected
    ↓
HPA evaluates
    ↓
desired replica count changes
    ↓
Deployment creates Pods
    ↓
Pods start
    ↓
readiness passes
    ↓
new capacity receives traffic
```

This delay is why architecture still needs:

``` text
capacity headroom
rate limiting
load shedding
proper requests/limits
```

Autoscaling is not a magic instantaneous shield.

------------------------------------------------------------------------

# 27. Generate Load from Your Mac

To verify autoscaling we need sustained traffic that creates measurable
resource pressure. A tool such as `hey` sends concurrent requests while
we watch CPU, HPA decisions, and replica counts. This local test proves
behavior; it is not a production performance benchmark because Minikube
and your laptop differ greatly from EKS.

If you have `hey`:

``` bash
brew install hey
```

Then:

``` bash
hey \
  -z 60s \
  -c 50 \
  $GATEWAY_URL/api/orders/101
```

Meaning:

``` text
-z 60s = run for 60 seconds
-c 50  = 50 concurrent workers
```

In another terminal:

``` bash
kubectl get hpa \
  -n ecommerce \
  -w
```

and:

``` bash
kubectl get pods \
  -n ecommerce \
  -w
```

Also:

``` bash
kubectl top pods \
  -n ecommerce
```

------------------------------------------------------------------------

# 28. If HPA Does Not Scale

An HPA that does not scale is not automatically broken. Scaling happens
only when the configured metric requires a higher replica count. A
Gateway can process substantial network traffic with modest CPU, so
inspect actual metrics and HPA events before changing thresholds.

Do not assume it is broken.

Check:

``` bash
kubectl describe hpa \
  api-gateway \
  -n ecommerce
```

and:

``` bash
kubectl top pods \
  -n ecommerce
```

Possible reasons:

``` text
Traffic is too light
Gateway is efficient and CPU stays low
Metrics Server not ready
CPU requests missing
Target percentage too high
Load is mainly downstream, not Gateway CPU
```

For a controlled lab, you can temporarily lower:

``` yaml
averageUtilization: 20
```

and increase load.

Restore a sensible value after the experiment.

Do **not** add fake CPU-heavy business logic to Gateway simply to make
HPA look impressive.

------------------------------------------------------------------------

# 29. Why Gateway and Order May Scale Differently

Microservices perform different work and therefore have different
scaling characteristics. Gateway may mainly route network I/O, while
Order Service may execute business logic, serialization, and database
work. Their resources, HPA targets, and replica counts should be
measured independently.

Suppose:

``` text
Gateway:
mostly routing/network I/O

Order:
JSON processing
business logic
database calls
```

Their bottlenecks differ.

You may observe:

``` text
Order CPU rises
Gateway CPU stays low
```

That is normal.

Do not use:

``` text
same HPA values
same replica counts
same resources
```

for every microservice just because it is convenient.

Each workload should be measured independently.

------------------------------------------------------------------------

# 30. Test Readiness Behavior

A Pod being **Running** only means its container/process is running; it
does not guarantee that the application can serve users. A readiness
probe answers: *should this Pod receive normal Service traffic now?*
This matters during startup, scaling, and deployments because Spring
Boot may still be initializing.

Kubernetes readiness determines whether a Pod is ready to receive
Service traffic; failed readiness removes it from ready Service
backends. citeturn331361search2turn331361search4

To make this practical, add a learning readiness controller to Order
Service only if you want to manually toggle readiness.

A better Spring Boot-native approach is to use Actuator availability
states, but for a simple controlled lab you can create a temporary
readiness test endpoint only if needed.

However, do **not** permanently implement fake health logic in
production code.

The easiest practical observation is during rollout:

``` bash
kubectl rollout restart deployment \
  order-service \
  -n ecommerce
```

Watch:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service \
  -w
```

And:

``` bash
watch kubectl get endpoints \
  order-service \
  -n ecommerce
```

A new Pod should not become a normal ready endpoint until readiness
succeeds.

------------------------------------------------------------------------

# 31. Rolling Update Practice

A **rolling update** replaces an old application version gradually
instead of stopping all old Pods first. Kubernetes starts new Pods and
removes old ones according to the Deployment strategy, while readiness
decides when new capacity is safe to use. This lets us deploy while
keeping the service available.

Build a new Order image.

For example, change response:

``` java
"version", "stage4-v2"
```

Build:

``` bash
minikube image build \
  -t ecom-order-service:stage4-v2 \
  ./order-service
```

Update Deployment:

``` bash
kubectl set image \
  deployment/order-service \
  order-service=ecom-order-service:stage4-v2 \
  -n ecommerce
```

Watch rollout:

``` bash
kubectl rollout status \
  deployment/order-service \
  -n ecommerce
```

Inspect Pods:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=order-service
```

Keep sending requests during the rollout:

``` bash
while true; do
  curl -s \
    $GATEWAY_URL/api/orders/101
  echo
  sleep 0.5
done
```

Observe old and new versions during the transition.

------------------------------------------------------------------------

# 32. Configure RollingUpdate Explicitly

`maxUnavailable` and `maxSurge` control how aggressively Pods are
replaced during a rollout. `maxUnavailable: 0` avoids intentionally
reducing available replicas, while `maxSurge: 1` permits one temporary
extra Pod. We trade a little temporary capacity for better deployment
availability.

Order Deployment:

``` yaml
spec:
  strategy:
    type: RollingUpdate

    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
```

Meaning:

``` text
maxUnavailable: 0
```

tries to avoid intentionally taking an existing ready replica down
before replacement capacity is available.

``` text
maxSurge: 1
```

allows one extra Pod temporarily during rollout.

For two replicas:

``` text
Normal:
2 Pods

During rollout:
up to 3 Pods
```

This is useful for availability, but costs temporary extra capacity.

------------------------------------------------------------------------

# 33. Gateway Rolling Update

Gateway is part of the critical request path, so its deployment strategy
matters too. Multiple stateless Gateway replicas allow old and new
versions to overlap while ready instances continue serving traffic. This
is one practical benefit of making Gateway instances interchangeable.

Use the same idea for Gateway:

``` yaml
strategy:
  type: RollingUpdate

  rollingUpdate:
    maxUnavailable: 0
    maxSurge: 1
```

Now deploy a new Gateway image:

``` bash
minikube image build \
  -t ecom-api-gateway:stage4-v2 \
  ./api-gateway
```

``` bash
kubectl set image \
  deployment/api-gateway \
  api-gateway=ecom-api-gateway:stage4-v2 \
  -n ecommerce
```

Keep traffic running.

Check:

``` text
Are requests still served?
Which Gateway Pod handled them?
```

This is much closer to production deployment thinking than simply
running `kubectl apply`.

------------------------------------------------------------------------

# 34. Roll Back a Bad Deployment

A release can pass build checks and still fail because of configuration,
integration, or runtime behavior. Kubernetes Deployment revisions let us
restore a previous rollout. Rollback is therefore part of a safe
deployment strategy, not merely an emergency command.

Suppose the new Gateway image is bad.

Check history:

``` bash
kubectl rollout history \
  deployment/api-gateway \
  -n ecommerce
```

Rollback:

``` bash
kubectl rollout undo \
  deployment/api-gateway \
  -n ecommerce
```

Watch:

``` bash
kubectl rollout status \
  deployment/api-gateway \
  -n ecommerce
```

This is an important production skill.

------------------------------------------------------------------------

# 35. Practical Failure --- Bad Readiness Path

This experiment demonstrates why **Running and Ready are different
states**. With a wrong readiness URL, Java can keep running while
Kubernetes excludes the Pod from normal Service traffic. Readiness
protects users from an instance that has not satisfied its
traffic-serving contract.

Change:

``` yaml
readinessProbe:
  httpGet:
    path: /wrong-readiness
    port: 8000
```

Apply.

Watch:

``` bash
kubectl get pods \
  -n ecommerce
```

The Gateway Pod can be:

``` text
Running
```

but:

``` text
0/1 Ready
```

Inspect:

``` bash
kubectl describe pod \
  -n ecommerce \
  <gateway-pod>
```

You should see readiness probe failures.

Check Service endpoints:

``` bash
kubectl get endpoints \
  api-gateway \
  -n ecommerce
```

This proves:

``` text
Running != Ready
```

Fix the readiness path afterward.

------------------------------------------------------------------------

# 36. Practical Failure --- Bad Liveness Path

A liveness probe asks a stronger question: *is this application
unhealthy in a way where restarting it is the right recovery?* Repeated
liveness failure causes container restarts. A bad liveness probe can
therefore restart healthy-but-slow Pods during overload and make an
incident worse.

Do this only as a short experiment.

Set liveness to a path that always fails.

Watch:

``` bash
kubectl get pods \
  -n ecommerce \
  -w
```

Inspect:

``` bash
kubectl describe pod \
  -n ecommerce \
  <pod-name>
```

You should see restart behavior.

Then fix it immediately.

Why this matters:

A bad liveness probe can make an otherwise working application restart
repeatedly and worsen an incident. Kubernetes documentation explicitly
cautions that poorly designed liveness probes can contribute to
cascading failures. citeturn331361search4

------------------------------------------------------------------------

# 37. Session Affinity Experiment

**Session affinity** influences whether traffic from the same client is
repeatedly directed toward the same backend Pod. Kubernetes Services can
use `ClientIP` affinity when a real requirement exists. Stateless REST
APIs normally avoid depending on it because any healthy Pod should be
able to handle any request.

By default, Kubernetes Service session affinity is normally:

``` yaml
sessionAffinity: None
```

You can temporarily experiment with:

``` yaml
spec:
  sessionAffinity: ClientIP
```

on `order-service`.

Then repeated traffic from the same client source may prefer the same
backend according to the Service session-affinity behavior.

Check current Service:

``` bash
kubectl get svc \
  order-service \
  -n ecommerce \
  -o yaml
```

After the experiment, restore:

``` yaml
sessionAffinity: None
```

Why?

Most stateless REST microservices should not require sticky routing
unless there is a real requirement.

------------------------------------------------------------------------

# 38. Why Sticky Sessions Are Usually Suspicious

Sticky sessions can make one Pod special by keeping client-specific
state there. If that Pod fails, the state may disappear, weakening
horizontal scaling and recovery. Stateless tokens or shared external
state usually make replicas interchangeable and easier to scale.

Bad architecture:

``` text
Customer session stored only in Gateway Pod memory

Customer
  |
must always hit Gateway Pod 1
```

Now Pod 1 failure loses state.

Better:

``` text
stateless JWT/request
```

or where server-side session is required:

``` text
shared session store
```

For high availability:

> Prefer stateless Gateway instances whenever practical.

------------------------------------------------------------------------

# 39. PodDisruptionBudget --- Production Concept + Local YAML

A **PodDisruptionBudget (PDB)** expresses how much availability
Kubernetes should preserve during supported *voluntary* disruptions such
as maintenance-driven evictions. With two Gateway replicas,
`minAvailable: 1` says at least one should remain available during such
workflows. It does not protect against crashes or every failure type.

A PodDisruptionBudget helps protect availability during **voluntary
disruptions**, such as node maintenance/eviction workflows.

Create:

``` text
kubernetes/gateway/pdb.yaml
```

``` yaml
apiVersion: policy/v1
kind: PodDisruptionBudget

metadata:
  name: api-gateway
  namespace: ecommerce

spec:
  minAvailable: 1

  selector:
    matchLabels:
      app: api-gateway
```

Apply:

``` bash
kubectl apply \
  -f kubernetes/gateway/pdb.yaml
```

Inspect:

``` bash
kubectl get pdb \
  -n ecommerce
```

Important:

A PDB is **not** protection against every failure.

It does not stop:

``` text
Pod crash
process crash
node hardware failure
```

It mainly constrains voluntary disruption workflows.

------------------------------------------------------------------------

# 40. Topology Spread --- Production Architecture

Multiple replicas help only if they are not all exposed to the same
infrastructure failure. **Topology spread constraints** guide the
scheduler to distribute Pods across domains such as nodes or
availability zones. In EKS this helps avoid losing every Gateway replica
because one node or AZ fails.

Minikube is normally a small/single-node local environment, so you
cannot meaningfully prove multi-AZ placement there.

But in EKS, do not want:

``` text
Gateway Pod 1
Gateway Pod 2
        |
same node
same AZ
```

because one node/AZ event could affect both.

A Kubernetes topology-spread example:

``` yaml
topologySpreadConstraints:
  - maxSkew: 1

    topologyKey:
      topology.kubernetes.io/zone

    whenUnsatisfiable: ScheduleAnyway

    labelSelector:
      matchLabels:
        app: api-gateway
```

A node-level constraint can use:

``` text
kubernetes.io/hostname
```

Production intent:

``` text
Gateway replicas
    ↓
spread across nodes
    ↓
preferably across AZs
```

We will go deeper in EKS architecture.

------------------------------------------------------------------------

# 41. Anti-Affinity Alternative

**Pod anti-affinity** is another scheduler mechanism for discouraging
similar Pods from being placed together. For Gateway, it can prefer
different nodes so a single node failure does not remove all replicas.
It overlaps with topology spread, so choose the rule that best expresses
the requirement rather than enabling both blindly.

Another pattern is Pod anti-affinity.

Example:

``` yaml
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100

        podAffinityTerm:
          labelSelector:
            matchExpressions:
              - key: app
                operator: In
                values:
                  - api-gateway

          topologyKey: kubernetes.io/hostname
```

This expresses:

``` text
Prefer not to place Gateway replicas
on the same node.
```

Topology spread is often easier to reason about for balanced
distribution.

You do not need both automatically.

------------------------------------------------------------------------

# 42. What Happens if All Gateway Pods Fail?

A Kubernetes Service is a stable routing abstraction; it cannot serve
application traffic by itself. If all Gateway Pods are unavailable or
unready, the Service has no healthy application endpoints. Backend
services may still be healthy, but the external API path is unavailable.

Architecture:

``` text
Client
   |
api-gateway Service
   |
   X no ready Gateway endpoints
```

Backend Order may be perfectly healthy.

But externally:

``` text
system API unavailable
```

This is why Gateway is critical infrastructure.

Check:

``` bash
kubectl get endpoints \
  api-gateway \
  -n ecommerce
```

If no ready endpoints:

``` text
Service exists
but nothing can serve the request.
```

------------------------------------------------------------------------

# 43. What Happens if All Order Pods Fail?

Gateway availability and downstream availability are separate concerns.
Gateway can stay healthy while Order Service has no ready Pods. Order
requests then fail downstream while unrelated routes may still work;
later resilience patterns help contain this kind of partial failure.

``` text
Gateway healthy
      |
order-service
      |
      X
no ready endpoints
```

Gateway is available but Order API cannot complete.

Other routes may remain healthy:

``` text
Payment
Inventory
Customer
```

This is why we later add:

``` text
timeouts
circuit breakers
bulkheads
```

A healthy Gateway does not make downstream services healthy.

------------------------------------------------------------------------

# 44. Gateway as Bottleneck

End-to-end capacity is limited by the weakest layer in the request path.
Ten Order Pods do not help if every request first passes through one
overloaded Gateway Pod. We therefore measure and scale Gateway and
backend services independently.

Suppose:

``` text
10 Order Pods
10 Payment Pods
10 Inventory Pods
```

but:

``` text
1 Gateway Pod
```

All external traffic still passes through one Gateway instance.

Possible bottlenecks:

``` text
CPU
memory
connections
event loop/thread pools
TLS
filters
logging
downstream connection pools
```

So backend scaling alone may not improve end-to-end capacity.

You must measure each layer.

------------------------------------------------------------------------

# 45. Load Test and Observe Both Layers

A useful load test is not just a requests-per-second number. Observe
Gateway CPU, Order CPU, HPA decisions, replica counts, latency, and
errors while traffic runs. That tells us **where the bottleneck actually
is** before deciding what to scale.

Start load:

``` bash
hey \
  -z 60s \
  -c 100 \
  $GATEWAY_URL/api/orders/101
```

While load runs:

``` bash
kubectl top pods \
  -n ecommerce
```

Also:

``` bash
kubectl get hpa \
  -n ecommerce
```

Questions:

``` text
Which Pods use CPU?

Did Gateway scale?
Did Order scale?
Which reached its target first?
Did latency improve?
Did error rate change?
```

This is architect-level capacity thinking.

------------------------------------------------------------------------

# 46. Useful Load-Test Numbers

Performance numbers should be read together. Throughput shows how much
work completes, while latency and error rate show what users experience
as load increases. If adding Pods does not improve them, the bottleneck
may be a database, external dependency, shared lock, or another
constrained resource.

`hey` output will give values such as:

``` text
requests/sec
average latency
fastest
slowest
status distribution
```

Do not conclude:

``` text
more Pods = always faster
```

If bottleneck is:

``` text
database
external API
single lock
Kafka partition
```

then adding Pods may not help.

Scaling must target the actual bottleneck.

------------------------------------------------------------------------

# 47. Complex Concept --- HPA Metric Choice

HPA should ideally scale on a metric that represents the workload's real
pressure. CPU is convenient for a lab, but a Gateway may become
constrained by request rate, active connections, latency, or downstream
waiting while CPU stays moderate. Production metric choice should follow
the actual bottleneck.

CPU is easy for a lab.

Production Gateway scaling might be better informed by:

``` text
request rate
active connections
event-loop saturation
latency
queue depth
```

depending on architecture.

Why?

A Gateway can be I/O-bound while CPU remains moderate.

Similarly an Order Service may be limited by:

``` text
DB connection pool
```

before CPU reaches 80%.

So:

``` text
CPU HPA
```

is a useful starting point, not a universal architecture answer.

------------------------------------------------------------------------

# 48. Gateway Replicas and Redis Later

Horizontal scaling works best when Gateway replicas behave consistently.
If rate-limit counters lived only in each Pod's memory, each replica
would see a different usage count. A shared store such as Redis provides
common distributed state when that state is genuinely required.

Today Gateway is mostly stateless.

Later rate limiting may introduce:

``` text
Redis
```

Architecture:

``` text
Gateway Pod 1 ----+
                  |
Gateway Pod 2 ----+---- Redis
                  |
Gateway Pod 3 ----+
```

Why shared Redis?

Because per-Pod in-memory quotas could produce inconsistent limits.

High availability works best when Gateway instances avoid unique local
state.

------------------------------------------------------------------------

# 49. Gateway Replicas and Authentication Later

JWT-based APIs fit horizontal scaling well because the information
required to validate a request travels with the token. Each Gateway
replica can validate it independently using trusted issuer key material.
The client therefore does not need to return to the same Gateway Pod.

JWT validation can often be performed independently by every Gateway
replica:

``` text
Gateway 1 validates JWT
Gateway 2 validates JWT
Gateway 3 validates JWT
```

No sticky session required for basic bearer-token APIs.

This is one reason stateless OAuth2/JWT APIs fit horizontal scaling
well.

------------------------------------------------------------------------

# 50. EKS Production Mapping

Minikube teaches the same core Kubernetes objects used in EKS, but the
external traffic layer changes in production. Instead of treating
NodePort as the final architecture, EKS commonly places AWS networking
such as an ALB in front. The internal Gateway → Service → Pods pattern
still applies.

Local:

``` text
Mac
 |
NodePort
 |
api-gateway Service
 |
Gateway Pods
```

Production EKS:

``` text
Internet
   |
Route 53
   |
WAF
   |
ALB
   |
Gateway Service / Ingress integration
   |
Gateway Pods
   |
Backend ClusterIP Services
   |
Backend Pods
```

In EKS, `LoadBalancer`/Ingress-related components and the AWS Load
Balancer Controller usually replace our simplistic local NodePort
exposure pattern.

The Kubernetes Service still provides a stable abstraction over Pods.
Kubernetes Service types include ClusterIP, NodePort, and LoadBalancer,
each with different exposure semantics. citeturn331361search3

------------------------------------------------------------------------

# 51. Multi-AZ Production Goal

High availability must survive more than an individual Pod crash. In
AWS, an Availability Zone is a larger failure domain, so critical
replicas should be distributed across nodes and preferably AZs. That
also requires enough remaining capacity to carry traffic when one
failure domain is unavailable.

Target:

``` text
                 ALB
                  |
        +---------+---------+
        |                   |
      AZ-A                AZ-B
        |                   |
   Gateway Pod         Gateway Pod
        |                   |
     services             services
```

If one AZ fails:

``` text
other AZ still has Gateway capacity
```

This requires:

``` text
multiple nodes
multiple AZs
replicas
scheduling strategy
load balancer health
backend capacity
data-tier HA
```

Simply setting:

``` yaml
replicas: 2
```

does not automatically guarantee multi-AZ resilience.

------------------------------------------------------------------------

# 52. Production Scaling Checklist

Production scaling combines **availability, elasticity, safe deployment,
and observability**. Replica count alone does not handle slow startup,
bad releases, uneven placement, or sudden demand. The controls below
complement each other and should be selected based on requirements
rather than enabled mechanically.

For Gateway:

-   multiple replicas
-   stateless design where possible
-   resource requests
-   resource limits
-   readiness
-   liveness
-   startup probe when startup is slow
-   HPA
-   load testing
-   rolling update
-   rollback
-   multiple nodes/AZs
-   topology spread / anti-affinity as needed
-   shared external state where required
-   monitoring
-   capacity headroom

For services:

-   scale independently
-   correct readiness
-   own resource profile
-   own HPA thresholds
-   own bottleneck analysis

------------------------------------------------------------------------

# 53. Failure Exercise Matrix

Architecture knowledge becomes stronger when you intentionally break one
layer and predict the result first. These exercises separate Pod
failure, probe failure, rollout behavior, and autoscaling so their
symptoms become recognizable. Before each test, state your expected
result and then compare it with what Kubernetes actually does.

Complete these.

  ------------------------------------------------------------------------
  Exercise               Command / Change           What You Should
                                                    Observe
  ---------------------- -------------------------- ----------------------
  Delete Gateway Pod     `kubectl delete pod ...`   other Gateway
                                                    remains + replacement
                                                    created

  Delete Order Pod       `kubectl delete pod ...`   Service routes to
                                                    ready backend +
                                                    replacement

  Gateway 2→4            `kubectl scale ...`        Gateway endpoints
                                                    increase

  Order 2→4              `kubectl scale ...`        Order endpoints
                                                    increase

  Bad readiness          wrong health path          Pod Running but not
                                                    Ready

  Bad liveness           wrong liveness path        restart count
                                                    increases

  Rolling update         `kubectl set image`        old/new Pods
                                                    transition

  Rollback               `kubectl rollout undo`     previous revision
                                                    restored

  HPA load               `hey ...`                  CPU/replicas observed

  Sticky session         `ClientIP`                 affinity behavior
                                                    experiment
  ------------------------------------------------------------------------

------------------------------------------------------------------------

# 54. Troubleshooting Commands to Memorize

Kubernetes troubleshooting is easier when you inspect layers instead of
changing YAML randomly. Start with desired state, then Pods,
Services/endpoints, metrics/events, logs, and rollout state. The
commands below are worth becoming comfortable with because they quickly
reveal which layer is failing.

``` bash
kubectl get deployments -n ecommerce
```

``` bash
kubectl get pods -n ecommerce -o wide
```

``` bash
kubectl get svc -n ecommerce
```

``` bash
kubectl get endpoints -n ecommerce
```

``` bash
kubectl get endpointslices -n ecommerce
```

``` bash
kubectl get hpa -n ecommerce
```

``` bash
kubectl top pods -n ecommerce
```

``` bash
kubectl describe pod \
  -n ecommerce \
  <pod>
```

``` bash
kubectl logs \
  -n ecommerce \
  <pod>
```

``` bash
kubectl rollout status \
  deployment/api-gateway \
  -n ecommerce
```

``` bash
kubectl rollout history \
  deployment/api-gateway \
  -n ecommerce
```

------------------------------------------------------------------------

# 55. Troubleshooting Scenario --- One Gateway Pod Never Gets Traffic

When one replica appears unused, first prove whether it is actually an
eligible Service backend. A Pod may be Running but excluded because
labels do not match or readiness fails. Also, a small HTTP sample can
look uneven because connections may be reused, so inspect endpoints
before blaming load balancing.

Check:

``` bash
kubectl get pods \
  -n ecommerce \
  -l app=api-gateway \
  --show-labels
```

Then:

``` bash
kubectl get endpoints \
  api-gateway \
  -n ecommerce
```

Questions:

``` text
Does Service selector match the Pod?
Is Pod Ready?
Is the endpoint present?
Are you testing enough independent connections?
```

Do not assume uneven short-test distribution means the Service is
broken.

------------------------------------------------------------------------

# 56. Troubleshooting Scenario --- HPA Says `<unknown>`

`<unknown>` usually means HPA cannot currently obtain the metric needed
for its calculation. First verify that Metrics Server returns Pod
metrics and that CPU requests exist for percentage-based CPU targets.
`kubectl describe hpa` is especially useful because its conditions and
events usually explain the problem.

Check:

``` bash
kubectl top pods \
  -n ecommerce
```

If metrics unavailable:

``` text
Metrics Server problem
```

Check:

``` bash
kubectl get pods \
  -n kube-system
```

Then:

``` bash
kubectl describe hpa \
  api-gateway \
  -n ecommerce
```

Also ensure CPU requests exist.

------------------------------------------------------------------------

# 57. Troubleshooting Scenario --- Scaling Adds Pods but They Never Receive Traffic

Creating more Pods does not automatically make them Service backends.
They must have matching labels, pass readiness, and expose the expected
port. If replicas increase but traffic ignores the new Pods, inspect
EndpointSlices/endpoints to see whether Kubernetes actually admitted
them into the routing set.

Check:

``` text
Readiness
Service selector
Pod labels
EndpointSlices
targetPort
application startup
```

Run:

``` bash
kubectl get endpoints \
  order-service \
  -n ecommerce
```

If new Pod IP is absent, investigate readiness/selector.

------------------------------------------------------------------------

# 58. Architect Question --- Is Kubernetes Service a Load Balancer?

The term **load balancer** can describe different architectural layers.
A Kubernetes Service provides stable cluster addressing and traffic
distribution toward eligible Pods, while an AWS ALB is an external
Layer-7 cloud load balancer with HTTP routing features. Both distribute
traffic, but they solve different parts of the system.

At the service-to-Pod level, Kubernetes Service provides a stable
virtual endpoint and traffic routing/load distribution to eligible
backends.

But it is different from an external cloud load balancer such as ALB.

Example:

``` text
ALB
 |
Gateway Pods
```

is external/L7 cloud traffic distribution.

``` text
order-service
 |
Order Pods
```

is cluster-internal Service routing.

Do not collapse all of these into the single word:

``` text
load balancer
```

without explaining the layer.

------------------------------------------------------------------------

# 59. Architect Question --- Why Not Let ALB Route Directly to Every Microservice?

There is no rule that every EKS system must contain Spring Cloud
Gateway. ALB/Ingress can route directly to services when those
capabilities satisfy the requirements. Gateway is justified when
application-level policies or custom Spring behavior add enough value to
justify another runtime hop.

It can in some architectures.

You must first ask what Spring Cloud Gateway adds:

``` text
application-level filters
token relay
Spring-specific policies
custom routing
central API behavior
```

If ALB + Ingress already meets all requirements, an extra Gateway can be
unnecessary.

If Gateway provides valuable API policies, then:

``` text
ALB
 ↓
Gateway
 ↓
Services
```

can be justified.

We compare this deeply in Stage 14.

------------------------------------------------------------------------

# 60. Architect Question --- Why Minimum 2 Gateway Replicas?

Two Gateway replicas provide already-running redundancy when one
instance disappears. HPA cannot replace this baseline because it reacts
after metrics change and new Pods still require startup time. Two is an
HA learning baseline, not a universal production capacity number, and
placement across failure domains also matters.

One Pod creates a straightforward single-instance runtime dependency.

Two improves availability.

But:

``` text
2 replicas
```

is not a magic production number.

Capacity may require:

``` text
3
5
20
```

depending on load and failure requirements.

And two replicas on one node do not protect against node failure.

Replica count must be combined with scheduling and infrastructure HA.

------------------------------------------------------------------------

# 61. Architect Question --- What Happens if Node Dies?

A node failure is larger than a Pod failure because every Pod on that
node can disappear together. Kubernetes can schedule replacements on
healthy nodes, but recovery takes time and requires spare capacity.
Replicas should therefore be spread across nodes and remaining capacity
must carry traffic during recovery.

In a real multi-node cluster:

``` text
Pods on failed node disappear/unavailable
```

Kubernetes can schedule replacements on healthy nodes, subject to
available capacity and scheduling constraints.

During that time:

``` text
remaining replicas
```

must carry traffic.

Therefore you need:

``` text
capacity headroom
multiple nodes
replica distribution
readiness
autoscaling
```

Minikube cannot fully simulate a real multi-AZ EKS failure model in a
typical single-node setup.

------------------------------------------------------------------------

# 62. Architect Question --- HPA vs Fixed Replicas?

Fixed minimum replicas and HPA solve different problems. `minReplicas`
provides already-running baseline availability/capacity, while HPA adds
or removes replicas as measured demand changes. For a critical Gateway
we normally want both redundancy before the spike and elasticity
afterward.

Use both concepts together.

HPA:

``` text
minReplicas: 2
maxReplicas: 5
```

means:

``` text
HA baseline = 2
elastic capacity = up to 5
```

Do not configure:

``` text
minReplicas: 1
```

for a critical Gateway merely because autoscaling exists.

If the only Pod dies, HPA is not a substitute for already-available
redundancy.

------------------------------------------------------------------------

# 63. Architect Question --- Why Readiness Before Adding Pod to Traffic?

A newly started container may not yet be a usable application instance.
Spring Boot may still be creating its context, initializing pools, or
loading configuration. Readiness lets Kubernetes wait until the
application can serve before including it as a normal backend.

New Spring Boot Pod may be:

``` text
process started
```

but still:

``` text
Spring context initializing
connection pools warming
configuration loading
```

Readiness prevents normal traffic from reaching it until it can serve.

This is essential during:

``` text
scaling
rolling updates
recovery
```

------------------------------------------------------------------------

# 64. Architect Question --- Why Is Liveness Dangerous?

Liveness has a powerful consequence: repeated failure can restart the
container. That helps when the process is genuinely stuck, but hurts
when it is only slow because of temporary overload or a dependency
issue. Restarting healthy-but-busy replicas can reduce capacity and
cause cascading failure.

Because it can convert temporary overload into restart loops.

Example:

``` text
Traffic spike
   ↓
health endpoint becomes slow
   ↓
liveness fails
   ↓
Kubernetes restarts Pod
   ↓
less capacity
   ↓
remaining Pods get more load
   ↓
more failures
```

Use liveness for situations where restart is actually the appropriate
recovery action. Kubernetes documentation warns that incorrect liveness
design can cause cascading failures. citeturn331361search4

------------------------------------------------------------------------

# 65. Architect Question --- Gateway Scaling vs Service Scaling

Gateway and backend services are separate Deployments with different
responsibilities and bottlenecks. They should be observed and scaled
independently instead of assuming the entire request path needs the same
replica count. Scaling decisions should follow the pressure measured on
each workload.

They are independent.

Example:

``` text
Gateway CPU 25%
Order CPU 85%
```

Scale Order.

Another case:

``` text
Gateway connection pressure high
Order CPU 20%
```

Scale Gateway.

Architecture should observe each workload separately.

------------------------------------------------------------------------

# 66. Stage 4 Coding-Agent Prompt

This prompt is intentionally constrained so a coding agent extends the
existing Stage 3 project instead of redesigning it. The architectural
decisions remain yours: native Kubernetes Service DNS, stateless Gateway
replicas, probes, and independent scaling. Review generated manifests
rather than treating agent-generated YAML as automatically
production-ready.

``` text
Inspect my existing Stage 3 Gateway, Order Service and Kubernetes manifests.

Implement Stage 4 only:
Load balancing, scaling and high availability in Minikube.

Requirements:

1. api-gateway Deployment:
   - 2 replicas
   - CPU/memory requests and limits
   - readiness probe
   - liveness probe
   - RollingUpdate strategy
   - maxUnavailable: 0
   - maxSurge: 1

2. order-service Deployment:
   - 2 replicas
   - same production-style basics
   - readiness/liveness
   - RollingUpdate strategy

3. Add a small learning-only mechanism:
   - Gateway response header exposes Gateway Pod HOSTNAME
   - Order response exposes Order Pod HOSTNAME
   so I can prove both load-balancing layers.

4. Keep:
   Gateway -> http://order-service:8080

5. Add HPA manifests:
   - Gateway: min 2, max 5
   - Order: min 2, max 6
   - autoscaling/v2
   - CPU utilization target

6. Add a Gateway PDB:
   minAvailable: 1

7. Do NOT add:
   - Eureka
   - Redis
   - JWT
   - circuit breaker
   - retry
   - service mesh
   - Helm

8. Give exact commands for:
   - build images with minikube image build
   - apply manifests
   - verify endpoints
   - call Gateway repeatedly
   - delete a Gateway Pod
   - delete an Order Pod
   - scale both deployments
   - enable Minikube metrics-server
   - inspect HPA
   - generate load
   - perform rolling update
   - rollback

Before modifying files:
- inspect current ports
- inspect Actuator configuration
- inspect current labels/selectors
- list planned changes

Keep the changes minimal and explain every Kubernetes field added.
```

------------------------------------------------------------------------

# 67. Your Hands-On Assignment

The assignment uses **evidence-based learning**: configure a behavior,
predict the outcome, execute it, and inspect Kubernetes state. For
architect interviews, explaining what should happen during Pod loss or
scaling is much stronger when you have personally observed endpoints,
readiness, HPA, and rollout behavior.

Do not mark Stage 4 complete until you personally do these:

### Part A --- HA

``` text
Gateway replicas = 2
Order replicas   = 2
```

Prove both Pod identities.

### Part B --- Failure

Delete:

``` text
1 Gateway Pod
1 Order Pod
```

Keep traffic running.

### Part C --- Manual scaling

``` text
Gateway 2 → 4 → 2
Order   2 → 4 → 2
```

Inspect endpoints every time.

### Part D --- Rolling update

Deploy new Order image while traffic is running.

Then rollback once.

### Part E --- Autoscaling

Enable Metrics Server.

Apply HPA.

Generate load.

Observe metrics and replica count.

Even if your Gateway does not scale because CPU remains low, understand
**why** from actual metrics.

------------------------------------------------------------------------

# 68. Stage 4 Completion Checklist

Use this checklist as a gate rather than a reading tracker. A topic is
complete only when you can explain the mechanism without the document
and reproduce the important experiment. If a checkbox exposes
confusion---especially around probes, HPA, or failure recovery---repeat
that lab before moving on.

## Implementation

-   [ ] Gateway has 2 replicas.
-   [ ] Order has 2 replicas.
-   [ ] Gateway Pod name is visible in response headers.
-   [ ] Order Pod name is visible in response.
-   [ ] Resource requests/limits configured.
-   [ ] Readiness configured.
-   [ ] Liveness configured.
-   [ ] RollingUpdate configured.
-   [ ] Gateway PDB created.
-   [ ] Metrics Server enabled.
-   [ ] Gateway HPA created.
-   [ ] Order HPA created.

## Practice

-   [ ] Called Gateway repeatedly and observed Pod identities.
-   [ ] Deleted one Gateway Pod while traffic was running.
-   [ ] Deleted one Order Pod while traffic was running.
-   [ ] Scaled Gateway manually.
-   [ ] Scaled Order manually.
-   [ ] Inspected Service endpoints.
-   [ ] Tested bad readiness.
-   [ ] Observed bad liveness restart behavior.
-   [ ] Performed rolling update.
-   [ ] Performed rollback.
-   [ ] Generated load.
-   [ ] Watched `kubectl top`.
-   [ ] Watched HPA.

## Architecture

-   [ ] I understand the two load-distribution layers.
-   [ ] I understand why short tests may not alternate Pods perfectly.
-   [ ] I understand stateless Gateway design.
-   [ ] I understand why HPA requires metrics and resource requests.
-   [ ] I understand why autoscaling is not instantaneous.
-   [ ] I understand readiness vs liveness.
-   [ ] I understand fixed minimum replicas + HPA.
-   [ ] I understand that 2 replicas on one node is not full HA.
-   [ ] I understand Minikube vs EKS HA limitations.
-   [ ] I can explain why ALB and Kubernetes Service solve different
    layers.

------------------------------------------------------------------------

# 69. Quick Interview Revision

These interview answers are intentionally short, but they should be
backed by the mechanisms practiced above. In an interview, start concise
and expand when asked: name the Kubernetes object involved, explain what
happens during failure, and mention the important trade-off or
limitation.

### Q: How do you make Spring Cloud Gateway highly available on Kubernetes?

> Run multiple stateless Gateway replicas behind a Kubernetes
> Service/load-balancing entry point, use readiness/liveness
> appropriately, define resource requests and limits, scale
> horizontally, use HPA where suitable, and distribute replicas across
> nodes/AZs in production. Avoid unique local state that creates
> sticky-session dependency.

### Q: Who load balances Order Pods in our design?

> Gateway calls the stable `order-service` Kubernetes Service.
> Kubernetes Service networking routes connections to eligible Order
> endpoints. Spring Cloud Gateway is not directly choosing Pod IPs in
> this design.

### Q: If HPA exists, why do we still start with 2 replicas?

> Autoscaling reacts after metrics indicate demand. Minimum replicas
> provide already-running redundancy. HPA is elastic capacity, not an
> instant replacement for HA.

### Q: Why can CPU be a poor Gateway scaling metric?

> Gateway workloads can be I/O/connection-bound. CPU may stay modest
> while request rate, active connections, or latency rise, so production
> autoscaling may need more representative metrics.

### Q: What is the role of readiness during rolling deployment?

> A replacement Pod should not receive Service traffic until it is
> ready. That allows old ready capacity to keep serving while the new
> Pod initializes.

------------------------------------------------------------------------

# 70. Next Stage

Stage 5 adds **identity and authorization at the API entry point**. Keep
the Stage 4 availability model intact while security is added;
authentication should not accidentally introduce sticky local state or a
new single point of failure. We will again separate concept, mechanism,
implementation, failure cases, and production implications.

After this stage we have:

``` text
Kubernetes routing
+
multiple Gateway replicas
+
multiple backend replicas
+
failure recovery
+
manual scaling
+
autoscaling
+
rolling deployment
```

The next Stage is:

``` text
05 — Gateway Security: OAuth2, OIDC & JWT
```

Architecture will evolve to:

``` text
                 Keycloak / IdP
                      |
                    OIDC
                      |
Client ---- JWT ----> Gateway Pods
                      |
                validate token
                      |
                      v
                 Order Service
                      |
              validate / authorize
```

That stage will also be implementation-first:

``` text
Keycloak configuration
JWT
Spring Security
Gateway Resource Server
protected/public routes
roles
curl/token tests
invalid/expired tokens
service-side validation
```

------------------------------------------------------------------------

# Stage 4 One-Line Summary

> **High availability is not just `replicas: 2`: we must prove traffic
> can reach multiple ready instances, survive Pod loss, scale
> independently, roll out safely, and map those Kubernetes behaviors to
> node/AZ-level production architecture.**
