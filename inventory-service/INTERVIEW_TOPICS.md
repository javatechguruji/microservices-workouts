# Interview Topics — inventory-service

> Scope: this service specifically — stock management and the concurrency
> problems that come with it. System-wide topics (Saga, service discovery
> philosophy, k8s) live in [../INTERVIEW_TOPICS.md](../INTERVIEW_TOPICS.md).
> Legend: `[ ]` pending · `[x]` completed

## Domain & Persistence
- [x] Application skeleton — Spring Boot + JPA + H2 wired (`inventorydb`)
- [ ] Inventory entity design (productId, quantity available, quantity reserved)
- [ ] Modeling "reserved vs. available" stock correctly

## Core Operations
- [ ] `GET` current stock for a product
- [ ] `POST` reserve stock (called by order-service during checkout)
- [ ] `POST` release/rollback a reservation (compensating action if the order fails downstream)
- [ ] Restocking / inventory adjustment endpoint

## Concurrency (the interesting part of this service)
- [ ] Optimistic locking (`@Version`) to prevent lost updates on concurrent stock decrements
- [ ] Race condition: two orders reserving the last unit at the same time — how this service prevents overselling
- [ ] Pessimistic locking as an alternative — when it's actually justified here
- [ ] Idempotent reserve calls — same order retried shouldn't double-decrement stock (ties to the idempotency topic in [../INTERVIEW_TOPICS.md](../INTERVIEW_TOPICS.md))

## Testing
- [ ] Concurrency test — fire N parallel reserve requests for M available units, assert no overselling
- [ ] `@DataJpaTest` for repository-level locking behavior

## Team-Lead Scenario Bank
- [ ] "Two orders raced for the last unit in stock — walk me through what happens in your implementation"
- [ ] "order-service retried a reservation call after a timeout — how do you guarantee it doesn't double-reserve?"
- [ ] "Optimistic vs. pessimistic locking here — which do you pick and why?"
