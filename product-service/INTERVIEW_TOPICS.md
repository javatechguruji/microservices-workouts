# Interview Topics — product-service

> Scope: this service specifically — product catalog domain and API design.
> System-wide topics (Saga, service discovery philosophy, k8s) live in
> [../INTERVIEW_TOPICS.md](../INTERVIEW_TOPICS.md). JPA/Hibernate mechanics
> in general are covered in [[springboot-data-workouts]] — this list is
> about applying them to a real product catalog.
> Legend: `[ ]` pending · `[x]` completed

## Domain & Persistence
- [x] Application skeleton — Spring Boot + JPA + H2 wired (`productdb`)
- [ ] Product entity design (SKU, price, category, stock reference)
- [ ] Repository layer — derived queries vs. `@Query` for search/filter
- [ ] DTO vs. entity exposure at the API boundary (never leak `@Entity` directly)
- [ ] Bean Validation on create/update requests

## REST API Design
- [ ] CRUD endpoints — create/read/update/delete
- [ ] List/search with pagination & sorting (ties to [[springboot-data-workouts]])
- [ ] Consistent error responses (404 not found, 409 duplicate SKU, 400 validation)
- [ ] API versioning strategy for this service specifically

## Production Concerns
- [ ] Caching product reads (cache-aside with Redis — ties to [[springboot-data-workouts]])
- [ ] Cache invalidation on product update
- [ ] Publishing a `ProductUpdated`/`ProductCreated` event for other services to consume (ties to [[kafka-workouts]])
- [ ] Read-heavy load — index design on search columns

## Testing
- [ ] `@DataJpaTest` for the repository layer
- [ ] `@WebMvcTest` for the controller layer
- [ ] Contract test for the shape `order-service` depends on (ties to [[testing-workouts]])

## Team-Lead Scenario Bank
- [ ] "Product search is slow at 1M rows — what do you check first?"
- [ ] "Design the API contract so order-service can safely depend on it long-term"
- [ ] "How do you roll out a breaking change to the Product schema without downtime?"
