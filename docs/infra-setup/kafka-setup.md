# Kafka setup and application configuration

The shared-infra broker is `apache/kafka:3.8.0`, single-node KRaft with broker and
controller roles. It has no authentication/TLS or broker redundancy. Data is stored
in external volume `workouts-kafka-data` at `/var/lib/kafka/data` through the explicit
`KAFKA_LOG_DIRS` setting. Keep its cluster ID consistent with the existing volume.

## Listener configuration

| Client                           | Bootstrap and advertised address          |
| -------------------------------- | ----------------------------------------- |
| IntelliJ applications            | `localhost:9092`                          |
| Minikube applications            | `host.minikube.internal:9094`             |
| Kafka UI / Compose-network tools | `kafka:29092`                             |
| KRaft controller only            | `kafka:9093`; not an application listener |

The bootstrap connection returns broker metadata. Minikube must use 9094 because
9092 advertises localhost, which would point to the application Pod on subsequent
connections. Kafka UI similarly needs the Docker-network listener, not localhost.
The exact listener configuration is in [Compose](../../docker-compose.yml).

## Installation and topic initialization

From repository root with Docker Desktop ready:

```sh
docker volume create workouts-kafka-data
docker compose up -d kafka
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --list
```

Retry the last command until the broker is ready; an empty topic list is valid.
Create both application topics explicitly:

```sh
for topic in payment-completed commerce-order-events; do
  docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic "$topic" --partitions 1 --replication-factor 1
done
```

`--if-not-exists` retains existing topics and partition counts; it does not resize
them. Do not delete topics to match this small learning topology.

## Spring configuration and microservice usage

Order, payment, notification and customer depend on `spring-kafka`. Each uses
`spring.kafka.bootstrap-servers: localhost:9092` locally and
`host.minikube.internal:9094` in its k8s profile. Keep producer/consumer settings in
their existing `application.yml`; merge under existing YAML keys.

Excerpt from [application.yml](../../payment-service/src/main/resources/application.yml) (surrounding code omitted):

```yaml
key-serializer: org.apache.kafka.common.serialization.StringSerializer   # turns the message key (orderId) into bytes to send over the network
value-serializer: org.springframework.kafka.support.serializer.JsonSerializer   # turns the event object into JSON bytes to send over the network
acks: all                    # don't treat the send as successful until every in-sync replica has a copy, not just the leader
client-id: payment-service-producer   # this producer's name, so it's easy to spot in Kafka's own logs, metrics, and quotas
compression-type: snappy     # compress each batch before sending — smaller over the network and cheaper for the broker to store
batch-size: 16384            # max size of one batch, in BYTES — not a count of messages, see linger.ms below
retries: 2147483647          # keep retrying a failed send almost forever — delivery.timeout.ms below is what actually limits the total wait, not this number
```

Payment also enables producer idempotence and disables Java type headers. Order's
commerce publisher constructs its own string producer in `CommerceScheduler`, so
editing payment producer properties does not configure both publishers.

| Topic                   | Business use                                                                 |
| ----------------------- | ---------------------------------------------------------------------------- |
| `commerce-order-events` | Order outbox to persisted notification inbox and purchase preferences        |
| `payment-completed`     | Payment outbox to legacy order confirmation and payment notification logging |

For real code examples spanning producer and consumers, read
[Kafka/outbox concepts](../kafka/kafka-notes-scenarios.md). PaymentServiceImpl writes a database
outbox; it no longer directly publishes to Kafka during payment processing.

## Inspect topics and consumer groups

```sh
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --describe --topic commerce-order-events
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 --topic commerce-order-events --from-beginning \
  --property print.key=true
```

Stop the console consumer with Ctrl+C. It is an inspection consumer, not an
application-group offset reset. Inspect a business consumer separately:

```sh
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group commerce-notifications-v1
```

Other groups: `customer-preferences-v1`, `order-group`, `notification-group`.
Groups may not exist before their applications subscribe. Instances sharing a
group divide work, including local and k8s instances using this shared broker.
Use [Kafka UI](kafka-ui-setup.md) for graphical inspection.

## Operations and troubleshooting

```sh
docker compose ps kafka
docker compose logs --tail=100 kafka
docker compose stop kafka
docker compose up -d kafka
```

A stop interrupts event delivery. Outboxes retain pending events; see
[the failure exercise](../project-docs/manual-verification.md#6-kafka-and-background-updates).
No event consumers need to be routed through the API gateway.

| Symptom                                     | Check                                                               |
| ------------------------------------------- | ------------------------------------------------------------------- |
| Bootstrap connects, later send fails        | Advertised address and client-specific port                         |
| Replication factor error                    | This broker has only one node; topic factor is 1                    |
| Group lag rises                             | Consumer database/deserialization failures and retry logs           |
| Invalid cluster metadata                    | Existing volume and cluster ID; do not reformat as a shortcut       |
| Shopping order confirms but inbox is absent | Order outbox and notification consumer, not legacy payment listener |

Minikube host reachability must be verified after cluster startup. Keep Kafka in
Compose; no broker manifest is needed inside Minikube.
