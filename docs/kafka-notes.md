# Kafka Notes — Producer, Broker, Consumer

Scenario: Client -> API Gateway -> Payment Service (producer) -> Kafka -> Order Service / Notification Service (consumers)

A client can't pay before an order exists — payment is always *for* an
order. So the scenario above is really the second half of a two-step flow;
see [0. Full Flow](#0-full-flow--order-service--payment-service--kafka)
below for the REST step that has to happen first, wired to the actual code
in this repo.

---

## 0. Full Flow — order-service → payment-service → Kafka

```
Client                order-service          payment-service           Kafka                 order-service        notification-service
  |                        |                        |                     |                        |                        |
  |--POST /api/orders----->|                        |                     |                        |                        |
  |                        |--save Order(PENDING)-->|                     |                        |                        |
  |<--201 {id, PENDING}----|                        |                     |                        |                        |
  |                        |                        |                     |                        |                        |
  |--POST /payments {orderId, amount}-------------->|                     |                        |                        |
  |                        |<--GET /api/orders/{id}-|                     |                        |                        |
  |                        |--200 order exists----->|                     |                        |                        |
  |                        |                        |--save Payment------>|                        |                        |
  |<--201 {id, SUCCESS}----|                        |--publish "payment-completed" (you write this)-|                        |
  |                        |                        |                     |--consume-------------->|                        |
  |                        |                        |                     |          Order -> CONFIRMED (you write this)     |
  |                        |                        |                     |--consume---------------------------------------->|
  |                        |                        |                     |                    send notification (you write this)
```

**Step 1 — create the order (plain REST, no Kafka):**
```bash
curl -X POST http://localhost:9091/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-1","amount":250.00}'
# -> {"id":1,"customerId":"CUST-1","amount":250.00,"status":"PENDING","createdAt":"..."}
```
`OrderApiController` (`order-service`) → `OrderService.createOrder()` → `Order` saved to `order-srv-db` (Postgres) with status `PENDING`.

**Step 2 — pay for that order (REST, enforces the "order must exist first" rule):**
```bash
curl -X POST http://localhost:8083/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"amount":250.00}'
# -> {"id":1,"orderId":1,"amount":250.00,"status":"SUCCESS","createdAt":"..."}
```
`PaymentController` → `PaymentService.processPayment()` first calls `OrderServiceClient.getOrder(orderId)`, a real synchronous HTTP call to `order-service`'s `GET /api/orders/{id}`. If the order doesn't exist, `order-service` returns 404 and `payment-service` rejects the payment with 404 too — try `{"orderId":999999,...}` and see for yourself. Only if the order is confirmed to exist does it save a `Payment` row to `payment-srv-db`.

**Step 3 — this is your part.** `PaymentService.processPayment()` has a `// TODO Kafka` comment marking exactly where to publish to `payment-completed` after the save succeeds (keyed by `orderId`, payload shape matching the console-producer example in §"Practical Guide" below — `{"orderId":..,"paymentId":..,"status":"SUCCESS"}`).

**Step 4 — two consumers, both listening on `payment-completed`, both your part too:**
- `order-service`: `OrderService.updateStatus(id, status)` already exists and does the DB update — wire a `@KafkaListener` that calls it, moving the order from `PENDING` to `CONFIRMED`. Until then, `PATCH /api/orders/{id}/status` does the same update manually, so you can test the transition without Kafka:
  ```bash
  curl -X PATCH http://localhost:9091/api/orders/1/status \
    -H "Content-Type: application/json" -d '{"status":"CONFIRMED"}'
  ```
- `notification-service`: `NotificationService.send(NotificationRequest)` already exists and logs the notification — wire a `@KafkaListener` that calls it. Until then, `POST /notifications` does the same thing manually:
  ```bash
  curl -X POST http://localhost:8084/notifications \
    -H "Content-Type: application/json" -d '{"orderId":1,"status":"SUCCESS"}'
  ```

**Local setup, once:**
```bash
docker compose up -d          # starts Postgres (order-srv-db, payment-srv-db) — repo root
```
Then run each service with `./mvnw spring-boot:run` from its own folder (default `local` Spring profile — Postgres reached at `localhost:5432`). In Kubernetes, `SPRING_PROFILES_ACTIVE=k8s` switches the datasource URL to `host.minikube.internal:5432`, since Postgres runs on the host machine's Docker, not inside minikube — see `k8s/README.md`.

---

## 1. Why Kafka
Payment Service should not call Order Service, Notification Service, etc. directly. Instead it publishes ONE event ("payment completed") to a Kafka topic. Downstream services consume it independently, on their own time.
- Decoupling in space: no direct service-to-service calls.
- Decoupling in time: a consumer can be down and catch up later — Kafka retains messages.

## 2. Topics, Partitions, Offsets
- A topic is like a long receipt roll — messages appended in order, never erased.
- A topic is split into partitions (multiple receipt rolls side by side) — fixed at topic creation time.
- Each message gets an offset — its position on that partition.
- A consumer tracks its own offset (a bookmark) — resumes from the last committed point after restart.

## 3. Keys and Ordering
- A message with a key (e.g. Order ID) always goes to the same partition.
- Ordering is guaranteed ONLY within a partition — never across partitions.
- No key = round robin across partitions.

## 4. Consumer Groups
- Multiple instances of the same service (e.g. 3 Notification Service pods) join as one consumer group.
- Partitions are divided among group members — no two members read the same partition at once.
- One worker can own multiple partitions; one partition is owned by only one worker at a time.
- More partitions than workers → some workers own multiple partitions.
- More workers than partitions → extra workers sit idle.
- Different consumer groups (e.g. "notification-group" vs "analytics-group") each get their own full copy of the topic and their own independent offsets.

---

# PRODUCER SIDE

## P1. Kafka Unreachable When Publishing
- Problem: Payment Service tries to publish but Kafka/broker is unreachable.
- Fix: Retry with a limited number of attempts. If still failing, persist the event locally first.

## P2. Transactional Outbox Pattern
- Problem: need a guarantee that an event is never lost even if Kafka is temporarily down.
- Fix: Payment Service writes the event to its own local DB table, in the same transaction as the business action (e.g. saving the payment). A separate relay process reads that table and retries publishing to Kafka until it succeeds.

## P3. Lost Acknowledgment / Duplicate Publish
- Problem: message reaches Kafka, but the ack back to the producer is lost on the network. Producer retries, risking a duplicate.
- Fix: Idempotent Producer — Kafka assigns a hidden producer ID and a sequence number to every message. A duplicate (same sequence number) is silently discarded by Kafka.
- Config: `enable.idempotence=true`

## P4. Durability — acks and min.insync.replicas
- `acks=all` — producer waits for confirmation from all replicas currently in the ISR (in-sync replicas list), not literally every replica that exists.
- `min.insync.replicas=2` (with replication factor 3) — Kafka refuses to accept a write at all unless at least 2 replicas are in sync. Prevents a write from being "confirmed" when only one copy (the leader) has it.
- Trade-off: this sacrifices some availability (writes may fail/retry) in exchange for guaranteed durability — the right choice for payment-critical data.

---

# BROKER SIDE

## B1. Replication
- Each partition is copied across multiple brokers — set by `replication.factor` (commonly 3).
- One broker is the leader (handles all reads/writes); the others are followers (continuously pull/copy from the leader).
- Followers pull from the leader — the leader does not push to followers.

## B2. ISR (In-Sync Replicas)
- ISR = the list of followers fully caught up with the leader (not lagging).
- Followers that fall behind (replication lag) drop out of the ISR.

## B3. Leader Election on Broker Crash
- If the leader broker dies, a new leader is elected — but ONLY from replicas in the ISR.
- This guarantees no data loss: a lagging replica is never promoted to leader.

## B4. Unclean Leader Election
- Edge case: what if every replica is lagging when the leader crashes (empty/unhealthy ISR)?
- Default: Kafka refuses to elect a lagging replica — the partition becomes temporarily unavailable rather than risk silent data loss.
- `unclean.leader.election.enable=true` can force election of a lagging replica anyway (accepts data loss for availability) — off by default, and normally left off for critical data like payments.

## B5. Choosing Replication Factor
- Replication factor 2 → only one failure tolerated, then zero backup remains until fixed. Risky.
- Replication factor 3 → survives one broker failure with a backup copy still available. This is the general-purpose default.
- Replication factor 5 → survives two simultaneous failures; more storage/network cost. Reserve for very critical data.

---

# CONSUMER SIDE

## C1. Processed but Crashed Before Commit
- Problem: worker sends the notification successfully but crashes before committing the offset. Kafka has no record it was handled, so a new worker re-reads and reprocesses the same message.
- This gap cannot be prevented — it's inherent to distributed systems ("at least once" delivery: no message loss, but duplicates possible).
- Fix: make the duplicate harmless, not impossible — consumer-side idempotency. Store a unique message ID (GUID / order reference) in a DB table; before acting, check "have I already processed this ID?" If yes, skip.

## C2. Poison Messages — Dead Letter Queue (DLQ)
- Problem: a specific message is malformed and always fails processing, blocking the partition if retried forever.
- Fix: after a few failed retries, move the message to a separate DLQ topic instead of retrying forever, and move on to the next message.
- Convention: one DLQ topic per source topic (e.g. `payment-completed-dlq`), not a single global DLQ — keeps ownership clear.
- Real-world handling: a dedicated consumer/listener sits on the DLQ topic, triggers an email/chat alert when a message lands there; engineer investigates and manually replays it into the original topic. Some teams also build a small dashboard to review DLQ items periodically.

## C3. Slow Consumer Causing False Rebalance
- Problem: a worker takes too long to process one message, can't send its heartbeat in time, so Kafka wrongly assumes it's dead and triggers an unnecessary rebalance.
- `session.timeout.ms` — how long the coordinator waits without a heartbeat before declaring a worker dead. Increasing this adds patience.
- `max.poll.interval.ms` — max allowed time between polls (a poll = asking Kafka for a new batch of messages). If processing takes too long between polls, the worker is removed from the group regardless of heartbeats.
- Real fix: don't just increase timeouts — reduce batch size per poll (e.g. fetch 5 messages instead of 100) so the worker naturally returns to poll sooner.

## C4. Planned Restarts — Static Group Membership
- Problem: a rolling deployment restarts each worker one by one. Kafka can't tell a clean restart from a crash, so it triggers a rebalance every time.
- Fix: Static Group Membership — give each worker a fixed ID (`group.instance.id`). On restart, the worker rejoins under the same ID within a grace period, and Kafka reassigns it its same partitions without a rebalance.
- Static membership doesn't replace normal rebalancing — it's a grace-period buffer on top of it. If the worker doesn't return within the window, normal rebalancing still kicks in.

---

# WORKED EXAMPLE — Spring Boot

## Producer: Payment Service publishing "payment completed"

```java
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRepository outboxRepository;

    public PaymentEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                 OutboxRepository outboxRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepository = outboxRepository;
    }

    // Step 1: write to local outbox table in the same DB transaction as the payment
    @Transactional
    public void recordPaymentCompleted(String orderId, String paymentEventJson) {
        outboxRepository.save(new OutboxEvent(orderId, paymentEventJson, "PENDING"));
    }

    // Step 2: separate relay process/scheduler reads PENDING rows and publishes
    public void relayPendingEvents() {
        outboxRepository.findByStatus("PENDING").forEach(event -> {
            // orderId used as the Kafka message key -> guarantees ordering per order
            kafkaTemplate.send("payment-completed", event.getOrderId(), event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        event.setStatus("SENT");
                        outboxRepository.save(event);
                    }
                    // on failure, leave as PENDING — will be retried on next relay run
                });
        });
    }
}
```

Producer config (application.yml):
```yaml
spring:
  kafka:
    producer:
      acks: all
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
```

## Consumer: Notification Service processing "payment completed"

```java
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventConsumer {

    private final ProcessedMessageRepository processedMessageRepository;
    private final EmailService emailService;

    public PaymentEventConsumer(ProcessedMessageRepository processedMessageRepository,
                                 EmailService emailService) {
        this.processedMessageRepository = processedMessageRepository;
        this.emailService = emailService;
    }

    @KafkaListener(topics = "payment-completed", groupId = "notification-group")
    public void handlePaymentCompleted(String messageId, String payload, Acknowledgment ack) {
        // idempotency check — skip if already processed
        if (processedMessageRepository.existsById(messageId)) {
            ack.acknowledge();
            return;
        }

        emailService.sendPaymentConfirmation(payload);

        processedMessageRepository.save(new ProcessedMessage(messageId));
        ack.acknowledge(); // manual commit — only after successful processing
    }
}
```

Consumer config (application.yml):
```yaml
spring:
  kafka:
    consumer:
      group-id: notification-group
      enable-auto-commit: false
      max-poll-records: 5
      properties:
        session.timeout.ms: 30000
        max.poll.interval.ms: 300000
        group.instance.id: notification-worker-1
```

## Dead Letter Queue listener

```java
@KafkaListener(topics = "payment-completed-dlq", groupId = "dlq-alert-group")
public void handleDeadLetter(String payload) {
    alertService.notifyTeam("Message landed in DLQ: " + payload);
}
```

---

# PRACTICAL GUIDE — Tools & CLI Commands

## Setup
- Install Kafka locally (includes CLI scripts in the `bin/` folder), or run via Docker (`confluentinc/cp-kafka` image is common).
- Optional: a Kafka UI tool (e.g. "Kafka UI", "Offset Explorer" / Kafdrop) gives a visual dashboard instead of command line only — useful for quickly browsing topics and messages.

## Create a topic with partitions and replication factor
```bash
kafka-topics.sh --create \
  --topic payment-completed \
  --partitions 3 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

## List all topics
```bash
kafka-topics.sh --list --bootstrap-server localhost:9092
```

## Describe a topic (see partitions, leader, ISR)
```bash
kafka-topics.sh --describe \
  --topic payment-completed \
  --bootstrap-server localhost:9092
```

## Produce messages from the terminal
```bash
kafka-console-producer.sh \
  --topic payment-completed \
  --bootstrap-server localhost:9092 \
  --property "parse.key=true" \
  --property "key.separator=:"
# then type: orderId123:{"amount":100,"status":"completed"}
```

## Consume messages from the terminal
```bash
kafka-console-consumer.sh \
  --topic payment-completed \
  --bootstrap-server localhost:9092 \
  --from-beginning
```

## Check consumer group offsets and lag
```bash
kafka-consumer-groups.sh \
  --describe \
  --group notification-group \
  --bootstrap-server localhost:9092
```

## List all consumer groups
```bash
kafka-consumer-groups.sh --list --bootstrap-server localhost:9092
```
