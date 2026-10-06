# Logs, metrics and tracing through the commerce project

For interview revision, read **section 2**. Use the remaining sections for concepts,
implementation details and hands-on checks. Installation, startup commands and
credentials are in the [setup guide](../infra-setup/observability-implementation-guide.md).

## 1. Why observability?

A healthy service and a `202 Accepted` response do not prove checkout finished.
**Metrics** reveal growing waiting work, **traces** identify slow or failed steps,
and **logs** explain individual events. Use them together to investigate.

## 2. Interview recap: diagram, responsibilities and tracing flow

```mermaid
flowchart LR
    subgraph A["Java service + OpenTelemetry Java agent"]
        LOG["Logs: application and library log messages<br/>through SLF4J / Logback"]
        MET["Metrics = numbers<br/>Spring Boot supplies built-in Micrometer measurements<br/>Our CommerceMetrics code counts waiting work<br/>The agent also measures supported operations automatically"]
        TRACE["Traces = connected records of work<br/>The agent watches HTTP, database and Kafka calls<br/>and records each supported operation as a timed span"]
        EXPORT["Agent collects Logback events,<br/>bridges Micrometer measurements,<br/>and exports all three signals"]
        LOG --> EXPORT
        MET --> EXPORT
        TRACE --> EXPORT
    end
    EXPORT -->|"Logs, metrics, traces via OTLP"| C["Collector"]
    C -->|"Logs"| L["Loki"]
    C -->|"Traces"| T["Tempo"]
    P["Prometheus"] -->|"Read measurements"| C
    P -->|"Read database, cache, Kafka numbers"| E["Helpers that report measurements"]
    P -->|"Send warnings"| AM["Alertmanager"]
    G["Grafana"] --> L
    G --> T
    G --> P
```

| Pillar | Who creates it? | What does the OpenTelemetry agent do? |
| --- | --- | --- |
| **Logs — what happened?** | Application and library code calls **SLF4J** (the logging API); **Logback** handles the log events. | Collects and exports the events; associates logs with the active trace when available. |
| **Metrics — how much, how often, how long?** | **Spring Boot** supplies built-in measurements through **Micrometer**. Our `CommerceMetrics` code adds business numbers, such as waiting checkouts. The agent also produces automatic measurements. | Exports its own measurements and those collected through its Micrometer bridge. |
| **Traces — where was time spent?** | The agent's **instrumentation** (code that automatically records supported operations) and bundled **OpenTelemetry SDK** (Software Development Kit: libraries inside the agent that create and manage spans, generate IDs, and export telemetry) create spans for supported operations. | Generates Trace IDs and Span IDs, records timing and outcomes, propagates context, and exports spans. |

- **Start the trace:** When Gateway receives a request without valid tracing context, its agent's SDK generates a **Trace ID** and a **Span ID**. A span is one timed operation; instrumentation means adding recording around a supported operation.
- **Connect the steps:** For an outgoing HTTP call, the agent creates a client span and sends context in the **`traceparent`** header. The receiving service creates a server span with the **same Trace ID**, a **new Span ID**, and a parent relationship to the caller. This repeats across supported calls; Kafka carries context in message headers.
- **Record and view:** Each service exports its own spans to the **Collector → Tempo**; Grafana displays the connected trace. Logs go to **Loki**, and **Prometheus** reads metrics from the Collector. The agent does not record every Java method; custom business steps need explicit spans. Our later checkout workers have separate traces because database rows do not preserve trace context—we correlate those stages using order/event IDs.

For deeper reference: [Concept](#3-concept) · [Implementation](#4-implementation) · [Testing](#5-testing).

## 3. Concept

### Components and their roles

| Component | Responsibility in this project |
| --- | --- |
| Java agent | Runs inside each service JVM; records supported operations and exports telemetry using OTLP, OpenTelemetry's transport protocol. |
| Collector | Separate process that receives, batches and routes telemetry. It is not the long-term store. |
| Loki / Tempo / Prometheus | Store logs / traces / metrics respectively. Prometheus scrapes metrics exposed by the Collector. |
| Grafana | Queries those stores and displays logs, charts and trace timelines. |
| Alertmanager | Groups and routes alerts evaluated by Prometheus. Our setup has no email or Slack receiver configured. |

### Metrics: terms that matter

| Term | Meaning / example |
| --- | --- |
| Counter | Cumulative events, such as failed checkout attempts; use `rate()` for events per second. A process restart can reset it. |
| Gauge | A current value that can rise or fall, such as pending checkouts. |
| Histogram / p95 | Duration buckets / estimated duration at or below which 95% of observations fall. Combine buckets before calculating p95 across replicas. |
| Cardinality | Number of distinct label combinations. Keep order IDs in logs, not metric labels, to avoid creating a time series per order. |

### Tracing and background work

- **Propagation:** HTTP uses `traceparent`; Kafka uses message headers to carry trace context. These IDs connect work; they do not authenticate callers.
- **Sampling:** `parentbased_traceidratio=1.0` records all new traces started here and follows an incoming parent's sampling decision. A log can have a Trace ID even when that trace is not stored.
- **Parallel calls:** Spans can overlap, so adding all span durations can exceed the request's elapsed time. Inspect the timeline.
- **Checkout boundary:** The checkout API saves work and returns `202`. Scheduled workers and outbox publishers run later. Our database does not persist trace context, so these runs have separate traces, correlated by order/event IDs in logs.
- **Two different backlogs:** Outbox backlog is database events waiting to be sent; Kafka consumer lag is published messages waiting to be consumed. Restarting a consumer does not publish outbox rows.

## 4. Implementation

### 4.1. Attach and configure the agent

All nine service POMs copy the agent into `target/otel/` during the build. Building
it does not activate it: select the service's **observable** IntelliJ configuration,
or start Java with these options (order-service example):

```text
-javaagent:order-service/target/otel/opentelemetry-javaagent.jar
-Dotel.javaagent.configuration-file=order-service/src/main/resources/otel.properties
```

Key settings from [order's otel.properties](../../order-service/src/main/resources/otel.properties)
(excerpt):

```properties
otel.service.name=order-service
otel.resource.attributes=service.namespace=ecommerce,deployment.environment.name=local
otel.exporter.otlp.endpoint=http://localhost:4318
otel.exporter.otlp.protocol=http/protobuf
otel.traces.exporter=otlp
otel.metrics.exporter=otlp
otel.logs.exporter=otlp
otel.traces.sampler=parentbased_traceidratio
otel.traces.sampler.arg=1.0
otel.instrumentation.micrometer.enabled=true
otel.metric.export.interval=15000
```

The agent reads configuration before Spring starts. Spring profiles do not select
agent property files. Override local defaults with deployment environment variables:
`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_RESOURCE_ATTRIBUTES`, etc.
Precedence is **JVM `-D` properties → environment variables → property file**.
The [Kubernetes deployment](../../k8s/order-service.yaml) already points to
`http://host.minikube.internal:4318`; `localhost` inside a pod means that pod.

### 4.2. Infrastructure configuration

These files are mounted into containers by [Docker Compose](../../docker-compose.yml).

| Configuration | Key behavior |
| --- | --- |
| [otel-collector.yml](../../docker/observability/otel-collector.yml) | Receives OTLP on 4317 (gRPC) / 4318 (HTTP); sends logs to Loki and traces to Tempo; exposes metrics on 9464. Pipelines use receivers → processors → exporters. |
| [prometheus.yml](../../docker/observability/prometheus.yml) | Scrapes the Collector every 15 seconds, plus infrastructure exporters for PostgreSQL, Redis and Kafka. |
| [Grafana data sources](../../docker/observability/grafana/provisioning/datasources/datasources.yml) | Provisions Prometheus, Loki and Tempo, including log TraceID links to Tempo. |
| [alerts.yml](../../docker/observability/alerts.yml) | Includes a checkout backlog alert: more than 10 pending checkouts for 5 minutes. One test order will not trigger it. |

Prometheus does **not** scrape each service's `/actuator/prometheus` in this setup.
`up{job="otel-applications"}=1` means the Collector's metrics endpoint answered,
not that every service is healthy. The Collector has bounded disk queues for logs
and traces; a prolonged outage can still lose data.

### 4.3. Application logs and business metrics

**Logs:** Our services use Logback and structured JSON console output
(`logging.structured.format.console: logstash`). The agent collects log events;
it does not tail the console file. Logstash is the format here, not a separate
Logstash server. Logs include order/event IDs for investigation; avoid tokens,
addresses and full sensitive payloads. Trace IDs are available when logging within
an active tracing context, not necessarily in startup logs.

**Business metrics:** The agent cannot infer what a waiting checkout means.
Our [order CommerceMetrics](../../order-service/src/main/java/com/tip/ecommerce/order/observability/CommerceMetrics.java)
queries unfinished rows and registers their cached count with Micrometer:

```java
// Excerpt: register a gauge that reads the cached count.
Gauge.builder("commerce.checkout.pending", this, m -> m.checkout)
    .register(registry);
```

The class refreshes checkout/outbox counts with a default 15-second delay.
[Payment CommerceMetrics](../../payment-service/src/main/java/com/tip/ecommerce/payment/observability/CommerceMetrics.java)
uses the same approach for its outbox. A failed read retains the previous count
and last-success timestamp; always check data age alongside the count.
Replicas may count the same database rows, so use `max`, not `sum`, for this backlog.
A failure counter counts attempts, not unique failed orders, and does not fall after success.

### 4.4. Useful PromQL queries

Use **Grafana → Explore → Prometheus**, with environment `local`.

**Pending checkouts by service:**

```promql
max by (service_name) (commerce_checkout_pending{deployment_environment_name="local"})
```

**Age of the last successful business-metric refresh, in seconds:**

```promql
time() - max by (service_name) (commerce_metrics_last_success_seconds{deployment_environment_name="local"})
```

**Requests per second over five minutes:**

```promql
sum by (service_name) (rate(http_server_request_duration_seconds_count{deployment_environment_name="local"}[5m]))
```

**p95 HTTP duration, in seconds:**

```promql
histogram_quantile(0.95,
  sum by (le, service_name) (
    rate(http_server_request_duration_seconds_bucket{deployment_environment_name="local"}[5m])
  )
)
```

Missing data is not zero. Low traffic can make rate and p95 charts empty or unstable.

## 5. Testing

### 5.1. Check all three signals in Grafana

Start infrastructure, all nine services with their **observable** configurations,
and the UI using the [setup guide](../infra-setup/observability-implementation-guide.md).

1. Sign in at `http://localhost:5173/` and reload the Shop page several times to
   generate catalog requests. Allow 30–60 seconds for metrics to arrive.
2. Open the [Commerce dashboard](http://localhost:3000/d/commerce-overview), select
   **local** and **Last 15 minutes**. Check request rate and p95.
3. In **Explore → Tempo**, run `{ resource.service.name = "gateway-service" }`.
   Open a recent `GET /api/products` trace and inspect the connected service spans.
4. Place an order, note its order ID, and inspect pending checkouts and refresh age
   using section 4.4. A finished checkout can already show zero pending.
5. In **Explore → Loki**, run `{service_name="order-service"} |= "eventId="`.
   Match an event near your order's time and follow its **TraceID** link to Tempo.
   For a failure log, search `|= "orderId=42 "`, replacing 42 with your ID.
   Not every successful request produces an application log.

If results are empty, check the time range, environment, service filter, agent
attachment and Collector endpoint. A startup log without a TraceID is normal.

### 5.2. Optional local failure exercise

In an isolated learning run, add an available item to the cart, then stop the local
inventory service and submit checkout. For an accepted order, inspect retry logs,
pending work and failed spans. Restart inventory with its observable configuration
and watch the order resume. Allow the worker's 10-second retry delay and metric
refresh/export time. Local and stage share databases and consumer groups, so avoid
this exercise while another environment is using them.

### 5.3. Repeatable verification

From the repository root, after setup:

```sh
# Synthetic telemetry: verifies Collector-to-storage paths.
python3 -u docker/observability/smoke-test.py

# Real services and commerce flows: creates learning orders.
python3 -u docker/observability/verify.py --commerce
```

Stop your Java applications before the second command; it needs ports 9100–9108
free and stops only the applications it starts. Omit `--commerce` for its read-only
HTTP flow. Results go to `/tmp/commerce-observability-validation`.

The synthetic check does not prove services have agents attached. The real-service
check validates application telemetry and connected HTTP/Kafka flows. See the
[validation report](validation.md) for the earlier run's results and limits;
these commands were not rerun for this documentation edit.
