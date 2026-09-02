# Kafka Notes — Producer, Broker, Consumer

Scenario: Client -> API Gateway -> Payment Service (producer) -> Kafka -> Order Service / Notification Service (consumers)

A client can't pay before an order exists — payment is always *for* an
order. So the scenario above is really the second half of a two-step flow.
The concepts below (§1–§4, then the Producer/Broker/Consumer failure
scenarios) come first; [5. Full Flow](#5-full-flow--putting-the-concepts-together)
at the end walks the whole thing end-to-end against the actual code in
this repo, once every piece it uses has been explained.

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

## 5. Full Flow — Putting the Concepts Together

Everything above was a piece in isolation. This is where they all show up
at once, in one real request, fully wired end-to-end in this repo — no
manual stand-in endpoints needed anymore.

**High-level theory, before the diagram:**

1. **Creating the order is plain, blocking REST — not an event.** A user is waiting for a definite answer ("your order is #1, status PENDING"). There's nothing to decouple yet, so §1's whole reason for reaching for Kafka doesn't apply here at all.
2. **The payment's order-existence check is also plain, blocking REST**, for the same reason in reverse: `payment-service` needs a definite yes/no *before* it decides whether to accept the payment. Kafka communicates facts that already happened, not questions a caller is blocked waiting to have answered — so this stays a direct HTTP call, not a topic.
3. **The publish is the actual moment Kafka enters the picture.** "A payment completed" is a fact, broadcast once instead of `payment-service` calling `order-service` and `notification-service` directly and waiting on both (§1). It's keyed by `orderId`, so every event for the same order lands on the same partition and stays in order (§3). It's sent with `acks=all` and an idempotent producer, so a network hiccup on the ack can't silently create a duplicate charge event (§P3, §P4).
4. **Two independent consumer groups read that one topic, each on its own schedule** (§4) — `order-service`'s `order-group` and `notification-service`'s `notification-group` each get a full copy of the topic and their own offsets. One being slow, restarting, or briefly down never blocks or duplicates the other's work.
5. **Each consumer commits its offset manually, only after its own side effect actually succeeds.** That's what makes "at least once, never zero times" (§C1) safe in practice here: a crash right before commit means the message gets redelivered, and redelivery is harmless because updating an order's status to a value it may already have is a no-op, not a second charge.

Now the same flow, concretely:

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
  |<--201 {id, SUCCESS}----|                        |--publish "payment-completed"------------------>|                        |
  |                        |                        |                     |--consume (order-group)->|                        |
  |                        |                        |                     |          Order -> CONFIRMED                      |
  |                        |                        |                     |--consume (notification-group)------------------->|
  |                        |                        |                     |                    logs the notification
```

**Step 1 — create the order (plain REST, no Kafka):**
```bash
curl -X POST http://localhost:9101/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-1","amount":250.00}'
# -> {"id":1,"customerId":"CUST-1","amount":250.00,"status":"PENDING","createdAt":"..."}
```
`OrderController` (`order-service`) → `OrderService.createOrder()` → `Order` saved to `order-srv-db` (Postgres) with status `PENDING`.

**Step 2 — pay for that order (REST, enforces the "order must exist first" rule):**
```bash
curl -X POST http://localhost:9102/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"amount":250.00}'
# -> {"id":1,"orderId":1,"amount":250.00,"status":"SUCCESS","createdAt":"..."}
```
`PaymentController` → `PaymentServiceImpl.processPayment()` first calls `OrderServiceClient.getOrder(orderId)`, a real synchronous HTTP call to `order-service`'s `GET /api/orders/{id}`. If the order doesn't exist, `order-service` returns 404 and `payment-service` rejects the payment with 404 too — try `{"orderId":999999,...}` and see for yourself. Only if the order is confirmed to exist does it save a `Payment` row to `payment-srv-db`.

**Step 3 — the publish.** After the save, `PaymentServiceImpl` publishes a `PaymentCompletedEvent(orderId, paymentId, status)` to topic `payment-completed`, keyed by `orderId` (`KafkaTemplate<String, PaymentCompletedEvent>`, `JsonSerializer`, `acks=all` + `enable.idempotence=true` — see §P3/P4 above). This is a **direct publish inside the same method**, not the transactional outbox pattern from §P2 — a crash between the DB save and the Kafka send can still drop the event. That's a known, accepted gap for this exercise; the outbox pattern is the real fix if this were production.

**Step 4 — two independent consumers, both listening on `payment-completed`:**
- `order-service` — `PaymentCompletedListener` (`groupId: order-group`) calls `OrderService.updateStatus(id, status)`, moving the order from `PENDING` to `CONFIRMED` (or `FAILED`). No dedup-by-message-id table: `updateStatus()` is naturally idempotent, so a redelivered message after a crash-before-ack is a harmless no-op — see §C1's "make the duplicate harmless, not impossible" in practice.
- `notification-service` — `PaymentCompletedListener` (`groupId: notification-group`) calls `NotificationService.send(...)`, which just logs. No dedup here either — this service still has no DB on purpose, and a duplicate log line has no real consequence.

Both use manual offset commit (`ack-mode: manual`, `Acknowledgment.acknowledge()` only after the DB update / send succeeds — see the worked example below) and `ErrorHandlingDeserializer` wrapping `JsonDeserializer`, so a malformed message logs and gets skipped by the container's default retry/skip behavior instead of crashing the listener thread (a lighter-weight stand-in for the full DLQ setup in §C2, which isn't wired here).

**The cross-service JSON gotcha:** each service keeps its own local copy of `PaymentCompletedEvent` (different package per service — deliberate, no shared event-schema JAR). Spring's `JsonSerializer` normally stamps a `__TypeId__` header with the *producer's* fully-qualified class name, which wouldn't match the *consumer's* own class. Fixed by disabling that on the producer (`spring.json.add.type.headers: false`) and telling each consumer its own target type directly (`spring.json.value.default.type`, `spring.json.use.type.headers: false`) — matching is purely structural (same field names), not by class identity.

**Local setup, once:**
```bash
docker compose up -d          # starts Postgres AND Kafka — repo root
```
Then run each service with `./mvnw spring-boot:run` from its own folder (default `local` Spring profile — Postgres at `localhost:5432`, Kafka at `localhost:9092`). In Kubernetes, `SPRING_PROFILES_ACTIVE=k8s` switches both: Postgres to `host.minikube.internal:5432`, Kafka to `host.minikube.internal:9094` — a **different port**, not just a different host, because Kafka's client protocol redirects to whatever address the broker advertises after the first connection, and one address can't be reachable from both "outside on the Mac" and "inside a minikube Pod" — see the `kafka` service in `docker-compose.yml` for the two-listener setup, and `k8s/README.md`.

---

# WORKED EXAMPLE — Spring Boot

## What's Actually Running In This Repo

The two subsections below ("Advanced Patterns") show the outbox / idempotency-table
/ DLQ patterns conceptually — **none of that is wired up in this repo.** What's
actually implemented is simpler, on purpose. This is the real code.

**Producer — `payment-service/src/main/java/.../service/impl/PaymentServiceImpl.java`:**
```java
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String TOPIC = "payment-completed";
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;
    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    // constructor omitted

    @Override
    public PaymentDto processPayment(CreatePaymentRequest request) {
        // amount validation, then orderServiceClient.getOrder(request.orderId())
        // — see §5 Step 1/2 — omitted here

        Payment payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCreatedAt(Instant.now());
        payment = paymentRepository.save(payment);

        // Direct publish, right after the save — NOT the outbox pattern
        // below. A crash between save() and send() can drop the event.
        Long orderId = payment.getOrderId();
        PaymentCompletedEvent event = new PaymentCompletedEvent(orderId, payment.getId(), payment.getStatus().name());
        kafkaTemplate.send(TOPIC, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for order {}", TOPIC, orderId, ex);
                    }
                });

        return toDto(payment);
    }
}
```

**Producer config — `payment-service/src/main/resources/application.yml`:**
```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        # Without this, JsonSerializer stamps a __TypeId__ header with
        # payment-service's own class name — order-service and
        # notification-service each have their OWN local copy of
        # PaymentCompletedEvent (different package), so that header would
        # never match. Turning it off makes matching purely structural.
        spring.json.add.type.headers: false
```
Plus per-profile `bootstrap-servers` — `localhost:9092` (`application-local.yml`) or `host.minikube.internal:9094` (`application-k8s.yml`, a **different port** from Postgres's `host.minikube.internal:5432` — see the "cross-service JSON gotcha" and local-setup notes in §5 for why).

**Consumer — `order-service/src/main/java/.../messaging/PaymentCompletedListener.java`:**
```java
@Component
public class PaymentCompletedListener {

    private final OrderService orderService;

    // constructor omitted

    // No dedup-by-message-id table (contrast §C1) — updateStatus() is
    // naturally idempotent, so a redelivered message is a harmless no-op.
    @KafkaListener(topics = "payment-completed", groupId = "order-group")
    public void handlePaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        OrderStatus newStatus = "SUCCESS".equals(event.status()) ? OrderStatus.CONFIRMED : OrderStatus.FAILED;
        orderService.updateStatus(event.orderId(), newStatus);
        ack.acknowledge();
    }
}
```

`notification-service`'s listener is the identical shape — `groupId = "notification-group"`, its own `com.tip.ecommerce.notification.event.PaymentCompletedEvent`, and it calls `NotificationService.send(new NotificationRequest(event.orderId(), event.status()))` instead of touching a database (it has none, deliberately).

**Consumer config — `order-service/src/main/resources/application.yml`** (`notification-service`'s is the same shape, with `group-id: notification-group` and `spring.json.trusted.packages`/`spring.json.value.default.type` pointed at its own `com.tip.ecommerce.notification.event` package):
```yaml
spring:
  kafka:
    consumer:
      group-id: order-group
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: com.tip.ecommerce.order.event
        spring.json.value.default.type: com.tip.ecommerce.order.event.PaymentCompletedEvent
        spring.json.use.type.headers: false
    listener:
      ack-mode: manual
```
`ErrorHandlingDeserializer` wrapping `JsonDeserializer` means a malformed message gets logged and skipped by the listener container's default retry/skip behavior instead of crashing the whole consumer thread — a lighter-weight stand-in for the real DLQ setup shown further below (not wired here).

---

## Advanced Pattern (Not Implemented Here): Transactional Outbox Producer

The version below is what §P2 describes — durable even if Kafka is briefly
down. Worth knowing, not currently in this repo's `payment-service`.

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

## Advanced Pattern (Not Implemented Here): Consumer-Side Idempotency Table

The version below is what §C1 describes — a `ProcessedMessageRepository`
dedup check before acting. `notification-service`'s real listener (above)
skips this since it has no DB and a duplicate log line is harmless.

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

Consumer config for the version above (application.yml) — also not what's
actually configured; this variant additionally shows `session.timeout.ms`,
`max.poll.interval.ms` (§C3) and static group membership via
`group.instance.id` (§C4), none of which the real `order-group`/
`notification-group` consumers in this repo currently set:
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

## Advanced Pattern (Not Implemented Here): Dead Letter Queue Listener

Not wired in this repo — no `payment-completed-dlq` topic exists, and
neither consumer routes failed messages to one. What actually happens on a
malformed message is the `ErrorHandlingDeserializer` + default
retry/skip behavior described above.

```java
@KafkaListener(topics = "payment-completed-dlq", groupId = "dlq-alert-group")
public void handleDeadLetter(String payload) {
    alertService.notifyTeam("Message landed in DLQ: " + payload);
}
```

---

# PRACTICAL GUIDE — Tools & CLI Commands

## Setup — what's actually running in this repo

`apache/kafka:3.8.0` in **KRaft mode** (no Zookeeper — that's the modern
default), as the `kafka` service in `docker-compose.yml` (repo root),
alongside Postgres:
```bash
docker compose up -d
```
It's a **single broker**, so replication factor is capped at 1 everywhere
below (a real multi-broker cluster is what §B1–B5 above describe — not
reproducible on a laptop dev setup without running multiple broker
containers).

It also exposes **two listeners on two different ports** — `9092`
(advertised as `localhost`, for anything running directly on the Mac) and
`9094` (advertised as `host.minikube.internal`, for Pods in minikube). See
§5's "Local setup" and `k8s/README.md` for why one address can't cover
both. The commands below use `localhost:9092`, i.e. the Mac-local path.

The CLI scripts (`kafka-topics.sh` etc.) live **inside the container**, not
on your Mac's PATH — every command below is run via `docker exec
workouts-kafka /opt/kafka/bin/<script>`. If you separately have a local
Kafka CLI install, drop the `docker exec workouts-kafka /opt/kafka/bin/`
prefix and the commands are identical.

Optional: a Kafka UI tool (e.g. "Kafka UI", "Offset Explorer" / Kafdrop) gives a visual dashboard instead of command line only — useful for quickly browsing topics and messages. Not set up in this repo.

## Create a topic with partitions and replication factor

This is the actual command used to create this repo's `payment-completed`
topic — replication factor **1**, not 3, because there's only one broker:
```bash
docker exec workouts-kafka /opt/kafka/bin/kafka-topics.sh --create \
  --topic payment-completed \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092
```

## List all topics
```bash
docker exec workouts-kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

## Describe a topic (see partitions, leader, ISR)
```bash
docker exec workouts-kafka /opt/kafka/bin/kafka-topics.sh --describe \
  --topic payment-completed \
  --bootstrap-server localhost:9092
```

## Produce messages from the terminal
```bash
docker exec -it workouts-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --topic payment-completed \
  --bootstrap-server localhost:9092 \
  --property "parse.key=true" \
  --property "key.separator=:"
# then type: 1:{"orderId":1,"paymentId":1,"status":"SUCCESS"}
# — matches PaymentCompletedEvent's actual field names (orderId/paymentId/status),
# and note there's no __TypeId__ header needed: see the "cross-service JSON
# gotcha" in §5 for why the consumers deserialize this structurally.
```

## Consume messages from the terminal
```bash
docker exec -it workouts-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --topic payment-completed \
  --bootstrap-server localhost:9092 \
  --from-beginning
```

## Check consumer group offsets and lag

`order-group` and `notification-group` are the two real consumer groups
running in this repo (`order-service` and `notification-service`
respectively):
```bash
docker exec workouts-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --describe \
  --group order-group \
  --bootstrap-server localhost:9092
```

## List all consumer groups
```bash
docker exec workouts-kafka /opt/kafka/bin/kafka-consumer-groups.sh --list --bootstrap-server localhost:9092
# -> order-group
#    notification-group
```
