# Observability – Logs, Metrics and Tracing
## Simple Interview & Practical Revision Notes

> Based on our discussion and the uploaded commerce observability guide.

## 1. Observability – Basic Idea

- **Metrics** tell us **what/how much is happening**.
- **Traces** tell us **where time was spent or where a failure happened**.
- **Logs** tell us **what exactly happened**.
- In this project:
  - **Prometheus** → metrics
  - **Tempo** → traces
  - **Loki** → logs
  - **Grafana** → UI to investigate them

Example:

- Metrics: Payment API p95 increased to 3 seconds.
- Trace: Payment Service database call took 2.7 seconds.
- Logs: Database connection timeout occurred.

**Simple debugging flow:**

`Metrics → Traces → Logs`

---

## 2. Metrics – Basic Understanding

- Spring Boot provides many standard application measurements **through Micrometer**.
- Spring Boot handles auto-configuration/instrumentation.
- Micrometer provides the metrics API and registry.
- We normally do not write custom code just to measure standard HTTP request duration.
- Business-specific requirements generally need **custom metrics**.
- The OpenTelemetry agent can also automatically produce measurements for supported operations.

### Important metrics to practice

- **Request rate** → How many requests are coming?
- **API latency** → How long are requests taking?
- **p95 latency** → 95% of requests completed within this duration.
- **Error rate** → How many requests are failing?
- **JVM metrics** → Memory, CPU, threads, garbage collection.
- **Database/connection-pool metrics** → Are database connections getting exhausted?
- **Business metrics** → Pending checkouts, failed checkout attempts, etc.

---

## 3. Q: What is p95?

### Answer

Suppose we received **100 requests**.

If:

`p95 = 2 seconds`

it approximately means:

**95% of the requests completed within 2 seconds.**

The slowest 5% took longer.

This is useful because an average response time can sometimes hide slow requests.

---

## 4. Q: Who Records These Metrics?

### Answer

For the Spring Boot metrics path:

`Spring Boot instrumentation → Micrometer → OpenTelemetry integration → Collector → Prometheus → Grafana`

Simple responsibilities:

- **Spring Boot** → Automatically configures supported application metrics.
- **Micrometer** → Provides the metrics abstraction/API and registry.
- **OpenTelemetry agent** → Bridges/exports Micrometer measurements and creates some automatic telemetry.
- **OpenTelemetry Collector** → Receives and routes telemetry.
- **Prometheus** → Stores and queries metrics.
- **Grafana** → Displays and helps us investigate metrics.

---

## 5. Q: Do I Need Code to Measure HTTP API Response Time?

### Answer

Normally, **no custom business metric code is required** for standard HTTP request timing when the appropriate Spring Boot/OpenTelemetry instrumentation is active.

Example:

`POST /orders → request starts → processing → response → duration recorded`

So, if you simply want to know:

> How long is my `/orders` API taking?

standard instrumentation can provide that measurement.

---

## 6. Q: What About Custom Business Metrics?

### Answer

Suppose we want to know:

> How many orders are currently pending checkout?

Spring Boot/Micrometer cannot automatically understand what **pending checkout** means to our business.

Our application has to provide that information.

Example from the project:

```java
Gauge.builder("commerce.checkout.pending", this, m -> m.checkout)
     .register(registry);
```

Conceptual flow:

`Database → Repository → CommerceMetrics → Micrometer Gauge → OpenTelemetry → Collector → Prometheus → Grafana`

Micrometer does **not** automatically query the database and understand our business state.

---

## 7. Q: How Do I Find Orders Pending for More Than 3 Seconds?

### Answer

For that requirement, we need a source of time information.

Example:

```text
orderId = 101
status = CHECKOUT
checkoutStartedAt = 10:00:00
```

Current time:

```text
10:00:05
```

The order has been waiting for approximately **5 seconds**.

Conceptual query:

```sql
SELECT COUNT(*)
FROM orders
WHERE status = 'CHECKOUT'
  AND checkout_started_at < CURRENT_TIMESTAMP - 3 seconds;
```

If the query returns `7`:

`Repository → 7 → CommerceMetrics → Micrometer Gauge = 7`

**Note:** The “older than 3 seconds” condition was our learning example. The uploaded document describes querying unfinished rows; it does not state that the current implementation filters them by 3 seconds.

---

## 8. Q: What If My Database Has No Timestamp?

### Answer

If the table only contains:

```text
orderId = 101
status = CHECKOUT
```

we know the order is currently in checkout.

But from that row alone, we cannot know whether it has been there for:

- 2 seconds
- 5 seconds
- 10 minutes

We need some persisted time information, such as `checkoutStartedAt`, if we want to calculate how long it has remained in that business state.

---

## 9. Gauge vs Counter

### Gauge

A Gauge represents a current value that can **increase or decrease**.

Example:

`Pending orders: 10 → 15 → 8 → 2 → 0`

Pending checkout count is naturally a **Gauge**.

### Counter

A Counter normally represents cumulative events.

Example:

`Checkout failures: 1 → 2 → 3 → 4 → 5`

A failure counter counts attempts/events and does not decrease just because an order later succeeds.

---

## 10. Distributed Tracing

### Q: How Do Trace ID and Span ID Work?

### Answer

A **Trace ID** identifies the overall distributed request journey.

A **Span ID** identifies one operation inside that journey.

Conceptually:

```text
Trace
 |
 +-- Span
 |
 +-- Span
 |
 +-- Span
```

Example:

```text
Client
  ↓
Gateway
  ↓
Order Service
  ↓
Database
  ↓
Payment Service
  ↓
Payment DB
```

Possible trace structure:

```text
Trace ID = ABC123

Gateway          Span-1
   |
Order Service    Span-2
   |
DB query         Span-3
   |
Payment call     Span-4
      |
Payment Service  Span-5
      |
Payment DB       Span-6
```

All supported/instrumented operations can belong to the same trace while having their own Span IDs.

The OpenTelemetry agent does **not** automatically create a span for every arbitrary Java method. Custom business operations may need explicit instrumentation.

---

## 11. Q: What Is `traceparent`?

### Answer

When one microservice calls another, tracing context is propagated through the HTTP `traceparent` header.

It does **not** contain only the Trace ID.

It carries tracing context including:

- Trace ID
- Current parent Span ID
- Trace flags

Example:

```text
Order Service
Trace = ABC
Span  = 100

      |
      | traceparent
      ↓

Payment Service
Trace  = ABC     ← same Trace ID
Span   = 200     ← new Span ID
Parent = 100
```

The receiving service keeps the same Trace ID, creates a new Span ID, and connects it to the caller's span.

That parent/child relationship allows Tempo/Grafana to display the distributed trace.

---

## 12. Kafka Tracing

For HTTP:

`Tracing context → traceparent header`

For Kafka:

`Tracing context → Kafka message headers`

Example:

```text
Order Service
     |
     | Kafka event + tracing context
     ↓
   Kafka
     ↓
Notification Service
```

This allows supported Kafka producer/consumer work to participate in distributed tracing.

---

## 13. Important Checkout Boundary in This Project

The checkout API can return `202 Accepted`, while scheduled workers and outbox processing continue later.

The project document explains that database rows do **not** preserve tracing context for these later stages.

Therefore, later background processing can have **separate traces**.

The project correlates those stages using business identifiers such as:

- `orderId`
- `eventId`

This is an important interview point:

> A Trace ID is excellent for a connected distributed operation, but business IDs may still be required to correlate asynchronous work across separate traces.

---

## 14. Important Grafana Metrics to Practice

Focus on a small set that is useful in production.

### Request Rate

Question answered:

> How much traffic is my service receiving?

Generate requests and watch requests-per-second increase.

### p95 Response Time

Question answered:

> Are users experiencing slowness?

Introduce a delay in an API, generate traffic, and watch p95 increase.

### HTTP Error Rate

Question answered:

> Are requests failing?

Simulate Inventory or Payment failures and observe errors.

### JVM Metrics

Useful examples:

- Heap memory
- CPU
- Threads
- Garbage collection

These JVM examples were additional discussion and are not explicitly listed in the uploaded guide.

### Database Connection Pool

Question answered:

> Is the application running out of available database connections?

Generate concurrent traffic and monitor active/available connections if those metrics are exposed.

This connection-pool example was also an additional discussion beyond the uploaded guide.

### Business Backlog

Example:

> How many checkouts are still pending?

The project exposes a pending-checkout Gauge.

---

## 15. Practical Grafana Investigation Flow

A useful production-style exercise is:

```text
1. Generate traffic
        ↓
2. Prometheus / Grafana
   Check request rate and p95
        ↓
3. Notice high latency or errors
        ↓
4. Tempo
   Open a slow/failed trace
        ↓
5. Find the slow/failing span
        ↓
6. Loki
   Search logs using Trace ID / business ID
        ↓
7. Find the detailed cause
```

A simple rule to remember:

**Metrics tell us something is wrong.**

**Traces tell us where it is wrong.**

**Logs help explain what happened.**

---

## 16. Practical Failure Exercise

A useful exercise from the project guide:

1. Start the observability infrastructure and services.
2. Generate normal application traffic.
3. Check request rate and p95 in Grafana.
4. Stop the Inventory Service.
5. Submit a checkout.
6. Inspect pending work.
7. Inspect failed/slow spans in Tempo.
8. Inspect retry/error logs in Loki.
9. Restart Inventory Service.
10. Watch the order processing resume.

This connects metrics, traces and logs in one real troubleshooting scenario.

---

## 17. Interview Quick Revision

### Q: How do you implement observability in Spring Boot microservices?

### Simple Answer

> We use logs, metrics and distributed tracing. Spring Boot provides built-in measurements through Micrometer, and we create custom Micrometer metrics for business requirements such as pending checkouts. The OpenTelemetry Java agent collects and exports telemetry and automatically instruments supported operations such as HTTP, database and Kafka calls. Telemetry goes through the OpenTelemetry Collector. Prometheus handles metrics, Loki stores logs, Tempo stores traces, and Grafana is used to visualize and investigate them. We use Trace IDs to correlate calls across microservices and normally move from metrics to traces to logs while debugging.

### Quick Mental Model

```text
Application
   |
   | Logs + Metrics + Traces
   ↓
OpenTelemetry Agent
   ↓
OpenTelemetry Collector
   |
   +---- Metrics ----> Prometheus
   |
   +---- Logs -------> Loki
   |
   +---- Traces -----> Tempo
                         |
                         ↓
                       Grafana
```

## 18. One-Minute Revision

- **Metrics** → What/how much is happening?
- **Logs** → What exactly happened?
- **Traces** → Where was time spent?
- **Micrometer** → Metrics abstraction/API and registry.
- **Spring Boot** → Auto-configures supported metrics.
- **Gauge** → Current value that goes up/down.
- **Counter** → Cumulative events.
- **p95** → About 95% of requests complete within this duration.
- **Trace ID** → Whole distributed journey.
- **Span ID** → One operation within that journey.
- **traceparent** → Propagates tracing context between HTTP services.
- **Prometheus** → Metrics.
- **Loki** → Logs.
- **Tempo** → Traces.
- **Grafana** → Visualization/investigation.
- **Best debugging flow** → `Metrics → Traces → Logs`.
