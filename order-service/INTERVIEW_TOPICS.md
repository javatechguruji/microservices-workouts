# Interview Topics — order-service

> Scope: this service specifically — it's the orchestrator, so this is where
> most of the interesting distributed-systems work lives. System-wide
> concepts (Saga theory, CQRS, etc.) live in
> [../INTERVIEW_TOPICS.md](../INTERVIEW_TOPICS.md) — this list is about
> actually implementing them here.
> Legend: `[ ]` pending · `[x]` completed

## Domain & Persistence
- [x] Application skeleton — Spring Boot + JPA + Postgres wired (`order-srv-db`)
- [x] Order entity & state machine (`PENDING` → `CONFIRMED` / `FAILED`) — driven by the Kafka consumer below, not a full Saga yet
- [ ] Order line items — modeling product references without duplicating product data

## Kafka — This Is a Consumer (of payment-service's "payment-completed")
- [x] `@KafkaListener(topics = "payment-completed", groupId = "order-group")` — `PaymentCompletedListener` calls `OrderService.updateStatus(...)` (see `docs/kafka-notes.md` §0)
- [x] Manual ack (`enable-auto-commit: false`, `ack-mode: manual`)
- [x] No dedup-by-message-id table — `updateStatus()` is naturally idempotent, so a redelivered message is a harmless no-op
- [ ] What happens to a malformed message — currently just `ErrorHandlingDeserializer` + the container's default retry/skip, not a real DLQ topic (`docs/kafka-notes.md` C2)

## Orchestration Flow (this service IS the Saga orchestrator)
- [ ] Call `product-service` to validate product exists & get current price
- [ ] Call `inventory-service` to reserve stock
- [ ] Confirm the order only after both calls succeed
- [ ] Compensating action — release the inventory reservation if order confirmation fails
- [ ] Idempotency key on order submission — same request retried shouldn't create two orders

## Resilience on the Call Path
- [ ] Resilience4j circuit breaker around each downstream call (product-service, inventory-service)
- [ ] Retry with backoff — and why retrying a "reserve stock" call is dangerous without idempotency
- [ ] Timeout budget — total request deadline split across two downstream hops
- [ ] Fallback behavior when a downstream service is unavailable (fail fast vs. queue for later)

## Observability
- [ ] Propagate a correlation/trace ID from `gateway-service` through both downstream calls
- [ ] Structured logging at each step of the orchestration for debuggability

## Testing
- [ ] Mock `product-service`/`inventory-service` (WireMock) to test orchestration logic in isolation
- [ ] Test the compensating-action path — inventory reservation succeeds, then a later step fails

## Team-Lead Scenario Bank
- [ ] "Walk me through what happens if inventory-service times out mid-order"
- [ ] "A customer double-clicked 'place order' — how do you guarantee only one order is created?"
- [ ] "Why orchestration (order-service calling out) instead of choreography (events) for this flow?"
