# Local observability validation

Validated on **2026-10-03 (America/Chicago)** with Docker Desktop and JDK 17.
These results describe a completed local run, not a continuously running deployment
of the application services. Repeat the commands after changes.

| Check | Result |
| --- | --- |
| `mvn -B test package` | All nine modules built; 41 tests, zero failures/errors/skips |
| Compose configuration | Valid; all nine new observability/exporter containers running in shared-infra |
| Prometheus configuration/rules | Valid; 18 alert rules loaded |
| `rules-test.yml` through promtool | Healthy and failing scrape/backlog test cases passed |
| Synthetic OTLP ingestion | Metric in Prometheus, trace in Tempo, log in Loki; trace ID preserved in log metadata |
| Nine instrumented Java applications | Health UP, JVM metrics and centralized logs for every service |
| Real HTTP propagation | Gateway and PGS spans in one trace |
| Shopping integration | Catalog, checkout, idempotency, inventory, simulated payment, fulfillment and Kafka notification checks passed |
| Kafka propagation | Order publisher and downstream consumer appeared in one trace |
| Business telemetry | Order/payment outbox gauges, HTTP histogram and snapshot freshness metric found; real checkout failure log had trace context |
| Infrastructure exporters | PostgreSQL/Redis up and Kafka broker count positive; Collector self-monitoring scraped |
| Grafana | Ten-panel dashboard provisioned; every PromQL query executed; Prometheus/Loki/Tempo datasource health OK |
| Repository hygiene | New Python files compile, Maven/IntelliJ XML parses, observability document links resolve, `git diff --check` passes |

Reproduce using:

```sh
mvn -B test package
python3 -u docker/observability/verify.py --commerce
```

The verifier starts its own applications only when ports 9100–9108 are free and
stops those processes on exit. It leaves shared-infra running. `--commerce` creates
learning records. Details from the most recent run are in
`/tmp/commerce-observability-validation`; Maven output was captured in
`/tmp/observability-maven.log` for this installation.

Not exercised in this local acceptance run: IntelliJ GUI launch, rebuilt service
Docker images/Minikube deployment, production TLS/HA, external notification delivery,
backend outage/recovery under sustained load, and durable trace-context persistence
through database outbox rows. The first two have checked-in configuration; the
remaining production extensions are described in the [operations guide](../infra-setup/observability-implementation-guide.md).
