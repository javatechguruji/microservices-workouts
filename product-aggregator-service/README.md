# product-aggregator-service

PostgreSQL product catalog, local images, concurrent aggregation and authoritative quotes.

- [Concept, scenario, code and flow](../docs/project-docs/catalog-aggregation-and-preferences.md)
- [Main implementation](src/main/java/com/tip/ecommerce/product/CatalogController.java)
- [Application configuration](src/main/resources/application.yml)
- [Startup, profiles, ports and tests](../docs/infra-setup/commerce-setup.md)
- [Required services per flow](../docs/project-docs/business-flows-and-service-dependencies.md)
- [Client credentials and permissions](../docs/infra-setup/keycloak-setup.md)

Run locally with IntelliJ; future deployment goes through Jenkins. This service is
not a Docker Compose workload. Consult the concept guide for implemented behavior
and limitations rather than treating the realm's permission names as an endpoint list.

## Observability

Use the shared `product-aggregator-service (observable)` IntelliJ run configuration after
`mvn process-resources`, or `mvn spring-boot:run` from this module. The pinned
OpenTelemetry agent exports HTTP/JVM/Micrometer metrics, traces and Logback logs
to the shared Collector. Docker images attach the same agent automatically.
See [setup, dashboards and interview examples](../docs/infra-setup/observability-implementation-guide.md).
