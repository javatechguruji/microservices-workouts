# Interview Topics — notification-service

> Scope: this service specifically — its role as a Kafka consumer in
> [[kafka-workouts]] (see `../docs/kafka-notes.md`). No domain/persistence
> topics here on purpose — this service has no database.
> Legend: `[ ]` pending · `[x]` completed

## Kafka — This Is a Consumer
- [ ] `@KafkaListener(topics = "payment-completed", groupId = "notification-group")` — call `NotificationService.send(...)` from it (see `docs/kafka-notes.md` worked example)
- [ ] Manual ack (`enable-auto-commit: false`) — only commit after the notification actually "sends"
- [ ] Consumer-side idempotency — same message re-delivered after a crash-before-commit; what's the dedupe key without a DB here? (worth adding a `ProcessedMessage` table once this stops being a demo)
- [ ] What happens to a malformed message — DLQ topic `payment-completed-dlq` (`docs/kafka-notes.md` C2)
- [ ] Consumer group sizing — multiple replicas of this service in one `notification-group`, partitions divided among them (`docs/kafka-notes.md` §4)

## Testing
- [ ] Embedded Kafka test: publish a `payment-completed` message, assert `NotificationService.send` gets called
- [ ] `POST /notifications` — the manual-trigger path already in place, useful even after Kafka is wired for local smoke tests
