# payment-service

Exact-amount simulated payments, duplicate protection and a transactional payment outbox.

- [Concept, scenario, code and flow](../docs/project-docs/shopping-and-fulfillment.md)
- [Main implementation](src/main/java/com/tip/ecommerce/payment/service/impl/PaymentServiceImpl.java)
- [Application configuration](src/main/resources/application.yml)
- [Startup, profiles, ports and tests](../docs/infra-setup/commerce-setup.md)
- [Required services per flow](../docs/project-docs/business-flows-and-service-dependencies.md)
- [Client credentials and permissions](../docs/infra-setup/keycloak-setup.md)

Run locally with IntelliJ; future deployment goes through Jenkins. This service is
not a Docker Compose workload. Consult the concept guide for implemented behavior
and limitations rather than treating the realm's permission names as an endpoint list.
