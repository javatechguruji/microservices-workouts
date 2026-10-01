# Kafka setup and microservice usage

Back to the [infrastructure index](README.md). The root
[Compose file](../../docker-compose.yml) defines the broker; see also
[Kafka design notes](../kafka-notes.md).

## Purpose and configured values

Payment-service publishes `payment-completed`. Order-service updates the order,
and notification-service logs a notification. Both consumers receive events
independently because they use different consumer groups.

| Setting | Repository value |
| --- | --- |
| Image / container | `apache/kafka:3.8.0` / `kafka` |
| Mode | Single-node KRaft; combined broker and controller; no ZooKeeper |
| Node ID / controller quorum | `1` / `1@kafka:9093` |
| Cluster ID | `ci8CzldyQfKoDVpQ7YR2Vg` |
| Data volume / directory | `workouts-kafka-data` / `/var/lib/kafka/data` |
| Authentication / TLS | None; PLAINTEXT for local learning |
| Restart policy | `unless-stopped` |

The Compose environment sets `KAFKA_PROCESS_ROLES=broker,controller` and
`KAFKA_LOG_DIRS=/var/lib/kafka/data`. Internal offsets and transaction-state
replication factors are `1`, with transaction-state minimum ISR `1`, because
there is only one broker. This is a learning topology without broker redundancy.
Keep the cluster ID consistent with the existing volume.

## Listener configuration

| Listener | Advertised address | Used by |
| --- | --- | --- |
| `PLAINTEXT_HOST` | `localhost:9092` | IntelliJ applications |
| `PLAINTEXT_K8S` | `host.minikube.internal:9094` | Minikube application Pods |
| `PLAINTEXT_DOCKER` | `kafka:29092` | Kafka UI and Docker diagnostic clients |
| `CONTROLLER` | Quorum address `kafka:9093` | Internal KRaft traffic, not applications |

Only 9092 and 9094 are published on the Mac. The full listener configuration
already lives in Compose:

```yaml
KAFKA_LISTENERS: PLAINTEXT_HOST://:9092,PLAINTEXT_K8S://:9094,PLAINTEXT_DOCKER://:29092,CONTROLLER://:9093
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT_HOST://localhost:9092,PLAINTEXT_K8S://host.minikube.internal:9094,PLAINTEXT_DOCKER://kafka:29092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,PLAINTEXT_K8S:PLAINTEXT,PLAINTEXT_DOCKER:PLAINTEXT
KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT_HOST
```

A Kafka client first contacts a bootstrap server, then uses the broker's
advertised address. Therefore Minikube clients need port 9094, not merely a
host-name replacement on 9092. See [Kafka listener configuration](https://kafka.apache.org/38/configuration/broker-configs/).

## Installation and readiness

Run from the repository root with Docker Desktop running:

```bash
docker volume create workouts-kafka-data
docker compose config --quiet
docker compose up -d kafka
docker compose ps kafka
docker compose logs --tail=100 kafka

# Retry after startup if the broker is not ready yet.
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --list
```

The last command should exit successfully; an empty list is valid on a fresh
broker. No Kafka or Java installation is needed on the Mac for these container
commands. [Official Kafka Docker instructions](https://kafka.apache.org/38/getting-started/docker/).

Create the application topic explicitly for repeatable setup:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic payment-completed --partitions 1 --replication-factor 1

docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --describe --topic payment-completed
```

`--if-not-exists` preserves an existing topic and its current partition count.
Do not delete an existing topic to match this example.

## Spring Boot configuration

Payment, order and notification already depend on:

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Their profile-specific broker settings are:

```yaml
# application-local.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

```yaml
# application-k8s.yml
spring:
  kafka:
    bootstrap-servers: host.minikube.internal:9094
```

Keep each service's existing `application.yml` producer/consumer settings.
Payment uses string keys, JSON values, `acks=all`, producer idempotence, and no
Java type headers. Consumers specify their own local event class for JSON
conversion; they use manual acknowledgment and disable automatic offset commits.
Changing only the bootstrap profile preserves those settings.

## Example: payment event flow using existing code

Security is now enabled: run the gateway too and obtain a `customer1` access
token using the [security demo/PKCE flow](../security/Authentication%20and%20Authorization%20at%20Microservice.md).
For the curl examples, set `ACCESS_TOKEN` to that token. Alternatively perform
the same create/read actions in the browser demo. Requests enter port 9100;
the owning service authorizes the caller's headers.


1. Start Postgres and Kafka. Create the topic as above if needed.
2. Run order-service, payment-service and notification-service in IntelliJ with
   the `local` profile.
3. Create an order and copy its returned `id`:

```bash
curl --fail-with-body -X POST http://localhost:9100/api/orders \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"customer1","amount":25.00}'
```

4. Set that actual ID below and pay it once:

```bash
ORDER_ID=123  # Replace 123 with the ID returned above.
curl --fail-with-body -X POST http://localhost:9100/payments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":$ORDER_ID,\"amount\":25.00}"
```

[PaymentServiceImpl](../../payment-service/src/main/java/com/tip/ecommerce/payment/service/impl/PaymentServiceImpl.java)
saves the payment and calls `kafkaTemplate.send` with the order ID as key.
The event value has `orderId`, `paymentId`, and `status` fields.
[Order's listener](../../order-service/src/main/java/com/tip/ecommerce/order/messaging/PaymentCompletedListener.java)
uses `order-group`; [notification's listener](../../notification-service/src/main/java/com/tip/ecommerce/notification/messaging/PaymentCompletedListener.java)
uses `notification-group`.

5. Read the order again after asynchronous processing:

```bash
curl --fail-with-body "http://localhost:9100/api/orders/$ORDER_ID" -H "Authorization: Bearer $ACCESS_TOKEN"
```

Expected: status changes to `CONFIRMED`, and the notification console records
processing. This example writes real learning data; create a new order for each
repeat because the payment service rejects another successful payment for the
same order. The application currently uses a direct database write plus Kafka
send, not an outbox; event delivery is not atomic with the database save.

## Inspect messages and consumer groups

```bash
# Historical messages; stop with Ctrl+C. No application consumer group is used.
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 --topic payment-completed --from-beginning \
  --property print.key=true

docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group order-group

docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group notification-group
```

A group may not exist until its application has subscribed. When local and k8s
instances use the same group ID, they share partitions and split processing.
They do not each receive a separate copy; use distinct group IDs if that becomes
necessary for independent environment exercises.

## Operations and troubleshooting

```bash
docker compose logs -f kafka
docker compose stop kafka
docker compose up -d kafka
docker compose restart kafka
docker volume inspect workouts-kafka-data

# Node-level TCP check when Minikube is running.
minikube ssh -- 'nc -vz -w 5 host.minikube.internal 9094'
```

- **External volume absent:** create `workouts-kafka-data` first.
- **Bootstrap connects but sends time out:** check advertised listener addresses,
  not just the first connection. Use the correct port for the client location.
- **Replication factor error:** this setup has one broker; example topics use 1.
- **Invalid cluster ID / log directory:** check logs and existing metadata. Do
  not reformat or delete the volume as a quick fix.
- **Consumer JSON errors:** compare the event schema, configured target type,
  and deserializer settings in the actual consumer service.
- **Messages remain but UI restarts:** expected; messages live on the Kafka volume.

Minikube TCP access is only a first check; confirm a real producer and consumer
from the `k8s` applications when Jenkins deployment is available.
