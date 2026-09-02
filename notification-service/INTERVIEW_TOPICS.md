# Interview Topics — notification-service

> Scope: this service specifically — its role as a Kafka consumer in
> [[kafka-workouts]] (see `../docs/kafka-notes.md`). No domain/persistence
> topics here on purpose — this service has no database.
> Legend: `[ ]` pending · `[x]` completed

## Kafka — This Is a Consumer
- [x] `@KafkaListener(topics = "payment-completed", groupId = "notification-group")` — `PaymentCompletedListener` calls `NotificationService.send(...)`
- [x] Manual ack (`enable-auto-commit: false`, `ack-mode: manual`) — only commit after the notification actually "sends"
- [ ] Consumer-side idempotency — deliberately skipped: no DB here, and a duplicate log line has no real consequence. Worth revisiting with a `ProcessedMessage` table if this stops being a demo.
- [ ] What happens to a malformed message — currently just `ErrorHandlingDeserializer` + the container's default retry/skip, not a real DLQ topic `payment-completed-dlq` (`docs/kafka-notes.md` C2)
- [ ] Consumer group sizing — multiple replicas of this service in one `notification-group`, partitions divided among them (`docs/kafka-notes.md` §4)

## Testing
- [ ] Embedded Kafka test: publish a `payment-completed` message, assert `NotificationService.send` gets called
- [x] `POST /notifications` — the manual-trigger path, still useful for local smoke tests independent of Kafka
