# Interview Topics — payment-service

> Scope: this service specifically — payment domain, the synchronous
> order-existence check, and its role as the sole Kafka producer in
> [[kafka-workouts]] (see `../docs/kafka-notes.md`).
> Legend: `[ ]` pending · `[x]` completed

## Domain & Persistence
- [x] Application skeleton — Spring Boot + JPA + Postgres wired (`payment-srv-db`)
- [x] Payment entity (orderId, amount, status, createdAt)
- [ ] Should `Payment` also store the Kafka message ID it published, for outbox-style tracking? (ties to [[kafka-workouts]] P2 — Transactional Outbox)

## REST API Design
- [x] `POST /payments` — synchronous order-existence check via `OrderServiceClient` before saving
- [x] `GET /payments/{id}`
- [ ] What should happen on a failed payment — a distinct `PaymentStatus.FAILED` path is modeled but never triggered yet; wire a real failure rule

## Service-to-Service Calls
- [x] `OrderServiceClient` — plain `RestTemplate` call to `order-service`, 404 mapped to a clear error
- [ ] What replaces this if `order-service` is briefly down — retry? circuit breaker? (Resilience4j)
- [ ] Compare this synchronous check against making the whole "does this order exist" question itself event-driven instead

## Kafka — This Is the Producer
- [x] Publish `payment-completed` after a successful save — see `PaymentServiceImpl.processPayment()`
- [x] Same event, `status` field distinguishes SUCCESS/FAILED (matches the console-producer example in `docs/kafka-notes.md`) — vs. a separate `payment-failed` topic. Trade-offs?
- [ ] Transactional Outbox — write the event to a local table in the same transaction as the payment, relay separately (`docs/kafka-notes.md` P2). Currently a **direct** publish — a crash between the DB save and the Kafka send can still drop the event; known, accepted gap.
- [x] Idempotent producer config (`enable.idempotence=true`, `acks=all`)

## Testing
- [ ] `@DataJpaTest` for `PaymentRepository`
- [ ] Mock `OrderServiceClient` (`@MockBean`) to test the "order not found" 404 path without a real order-service running
- [ ] Embedded Kafka test verifying the event is actually published on success
