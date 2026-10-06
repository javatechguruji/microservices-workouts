# gateway-service

JWT validation, identity-header replacement and all application HTTP routing.

- [Concept, scenario, code and flow](../docs/api-gateway/topic-list.md)
- [Main implementation](src/main/resources/application.yml)
- [Application configuration](src/main/resources/application.yml)
- [Startup, profiles, ports and tests](../docs/infra-setup/commerce-setup.md)
- [Required services per flow](../docs/project-docs/business-flows-and-service-dependencies.md)
- [Client credentials and permissions](../docs/infra-setup/keycloak-setup.md)

Run locally with IntelliJ; future deployment goes through Jenkins. This service is
not a Docker Compose workload. Consult the concept guide for implemented behavior
and limitations rather than treating the realm's permission names as an endpoint list.

## Security configuration by profile

Each profile contains its complete `security.jwt` block: issuer, audience and
JWK endpoint. Each profile also defines its `security.cors.allowed-origins` setting.
Shared routing remains in `application.yml`.

- [local](src/main/resources/application-local.yml): IntelliJ uses localhost to fetch Keycloak public keys.
- [k8s](src/main/resources/application-k8s.yml): minikube uses `host.minikube.internal` to fetch those keys.

The issuer remains `http://localhost:8180/realms/ecommerce` in both profiles because
it must match the token's `iss` claim from our shared Keycloak. It identifies the
issuer; it is not the address the decoder uses to download keys. The audience
remains `gateway-service`. Both CORS profiles currently allow `http://localhost:5173` because the POC UI still
runs locally, even when gateway runs in minikube. Set `CORS_ALLOWED_ORIGINS` for
the actual frontend origin in each environment. This preserves existing behavior.

## Configuration ownership

| File                    | Responsibility                                                                            |
| ----------------------- | ----------------------------------------------------------------------------------------- |
| `application.yml`       | Application name, shared routes, port 9100, health probes and the fallback local profile. |
| `application-local.yml` | Local service addresses, JWT/CORS settings and DEBUG gateway logging for learning.        |
| `application-k8s.yml`   | Kubernetes service addresses, JWT/CORS settings and INFO gateway logging.                 |

`spring.profiles.default=local` is a fallback, not an explicitly activated profile.
IntelliJ may select `local`; the Kubernetes Deployment selects `k8s` through
`SPRING_PROFILES_ACTIVE`. Shared port 9100 matches the Dockerfile, Service and probes.

Routes use `spring.cloud.gateway.server.webflux.routes`, and Maven uses
`spring-cloud-starter-gateway-server-webflux`, matching this project's Spring Cloud
2025.0 / Gateway 4.3 release line. The older names are deprecated compatibility aliases.
See the [Spring Cloud release notes](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes).

## Observability

Use the shared `gateway-service (observable)` IntelliJ run configuration after
`mvn process-resources`, or `mvn spring-boot:run` from this module. The pinned
OpenTelemetry agent exports HTTP/JVM/Micrometer metrics, traces and Logback logs
to the shared Collector. Docker images attach the same agent automatically.
See [setup, dashboards and interview examples](../docs/infra-setup/observability-implementation-guide.md).
