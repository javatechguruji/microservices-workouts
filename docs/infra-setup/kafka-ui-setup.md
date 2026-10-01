# Kafka UI setup and microservice event inspection

Back to the [infrastructure index](README.md). This repository uses **Kafbat UI**,
with Compose service name `kafka-ui`. It is an operator tool for inspecting Kafka,
not an endpoint that microservices call.

## Configuration

| Setting | Repository value |
| --- | --- |
| Image | `ghcr.io/kafbat/kafka-ui:latest` |
| Service / container | `kafka-ui` / `kafka-ui` |
| Browser URL | `http://localhost:8089` |
| Port mapping | `8089:8080` |
| Cluster display name | `local` |
| Broker address from UI container | `kafka:29092` |
| Login / TLS | Not configured |
| Persistent volume | None; cluster configuration is in Compose |

The root [Compose file](../../docker-compose.yml) contains:

```yaml
kafka-ui:
  image: ghcr.io/kafbat/kafka-ui:latest
  container_name: kafka-ui
  restart: unless-stopped
  depends_on:
    - kafka
  ports:
    - "8089:8080"
  environment:
    KAFKA_CLUSTERS_0_NAME: local
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
```

The UI uses Docker service DNS to reach Kafka. `localhost:9092` inside the UI
container would point at the UI container itself. The `local` display name is
just a label: this cluster serves both IntelliJ and Minikube applications.
[Official Kafbat configuration reference](https://ui.docs.kafbat.io/configuration/configuration-file).

## Installation and verification

Follow the [Kafka guide](kafka-setup.md) first, or run:

```bash
# From repository root, with Docker Desktop running.
docker volume create workouts-kafka-data
docker compose config --quiet
docker compose up -d kafka kafka-ui
docker compose ps kafka kafka-ui
docker compose logs --tail=100 kafka-ui

# Confirm the broker is ready.
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --list

# Check the web endpoint; open the same URL in a browser.
curl --fail --location --output /dev/null --write-out '%{http_code}\n' http://localhost:8089
```

Expected: the browser loads the UI and the configured `local` cluster becomes
available. `depends_on` orders container startup but does not guarantee Kafka
is ready; check logs and retry while the broker starts.

No application Maven dependency or Spring property is needed for Kafka UI.
Applications keep the Kafka bootstrap addresses documented in the Kafka guide.
The same browser UI inspects events sent from either application profile.

## Example: inspect payment-service events

1. Start order, payment and notification locally and perform the
   [payment-event example](kafka-setup.md#example-payment-event-flow-using-existing-code).
2. Open `http://localhost:8089` and select cluster `local`.
3. Open the topics view and select `payment-completed`.
4. Open its messages view and browse from an offset that includes your test
   event. Check the key (order ID) and JSON fields `orderId`, `paymentId`,
   and `status`.
5. Open the consumer groups view. Inspect `order-group` and `notification-group`:
   offsets and lag help show whether each application has caught up.
6. Compare the record with the order-service API response and notification
   application's IntelliJ logs. UI records show publication; they do not by
   themselves prove that downstream business processing succeeded.

Exact tab labels can vary because the image uses `latest`. A topic appears only
once it exists. Consumer groups appear after subscription/offset activity.
Records do not disappear when a consumer reads them; Kafka retention controls
how long they remain.

For a separate UI learning exercise, use a dedicated topic such as `infra-demo`
instead of injecting arbitrary events into `payment-completed`. Publishing,
deleting topics, or resetting offsets in the UI changes the shared broker and
can affect both local and staging applications.

## Operations and troubleshooting

```bash
docker compose logs -f kafka-ui
docker compose stop kafka-ui
docker compose up -d kafka-ui
docker compose restart kafka-ui

# After editing its Compose environment:
docker compose up -d kafka-ui
```

- **Page unavailable:** verify port 8089 is published and the UI container is
  running; inspect logs for startup failures.
- **Page loads but cluster is offline:** confirm Kafka readiness and
  `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:29092`. Both containers must share
  `shared-infra_default`.
- **No payment records:** verify the payment call succeeded and the topic/offset
  selection is correct; inspect producer logs for publishing failures.
- **Consumer group missing:** run the corresponding application with the right
  bootstrap profile and check its startup logs.
- **High lag:** inspect the consumer's deserialization and processing failures.
- **Image changes unexpectedly after pulling:** `latest` is mutable. Record an
  image digest or pin a tested version when reproducible builds are needed.

Stopping or recreating Kafka UI does not remove Kafka messages. Their storage
belongs to the broker's `workouts-kafka-data` volume. Keep UI configuration in
Compose; dynamic runtime configuration is not enabled by this repository.
