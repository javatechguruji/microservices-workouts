# Kafka UI setup and event inspection

Kafka UI is an operator tool, not a microservice dependency. Compose runs Kafbat UI
using image `ghcr.io/kafbat/kafka-ui:latest`; browser URL is **http://localhost:8089**.
It connects to the shared broker at `kafka:29092`, with cluster display name `local`.
No login/TLS, dynamic configuration or separate volume is configured.

## Start and configure

First initialize [Kafka](kafka-setup.md), then run from repository root:

```sh
docker compose up -d kafka-ui
docker compose ps kafka kafka-ui
docker compose logs --tail=100 kafka-ui
curl --fail --location --output /dev/null --write-out '%{http_code}\n' http://localhost:8089
```

The root Compose environment contains:

```yaml
KAFKA_CLUSTERS_0_NAME: local
KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
```

`localhost` inside this container would address the UI itself. `depends_on` controls
startup order but does not prove the broker is ready. No application Maven dependency
or Java configuration is needed for this tool.

## Example: observe a real shopping order

1. Run the [business walkthrough](../project-docs/manual-verification.md) and note its order ID.
2. Open the UI, select `local`, then topic `commerce-order-events` and its messages view.
3. Find a record keyed by order ID. Inspect `eventId`, `status`, `customerId`, `tenant`
   and item categories. Shipping/delivery produce later lifecycle records.
4. Inspect groups `commerce-notifications-v1` and `customer-preferences-v1` for progress/lag.
5. Compare with the customer's Updates screen and preference history outcome.

For legacy payment learning, inspect `payment-completed`, `order-group` and
`notification-group`. A record visible in the UI proves publication, not successful
business processing. Consumption does not remove a record; retention controls that.

## Operations and troubleshooting

`docker compose stop kafka-ui` stops this tool; `docker compose up -d kafka-ui`
starts it or applies Compose changes. Neither operation deletes Kafka messages.

If the page loads but the cluster is offline, check broker readiness and Docker
network address. If groups/topics are absent, run the corresponding producer and
consumer. Inspect application logs when lag grows. UI labels can change because
`latest` is mutable; record/pin a tested digest when reproducibility is required.
Use a separate learning topic for arbitrary test messages; deleting topics or
resetting application offsets affects the shared local/stage data.
