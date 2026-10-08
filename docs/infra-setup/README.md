# Setup and configuration

Use this section to prepare the environment. Use the [learning index](../README.md)
for concepts, code excerpts and business verification.

## AWS setup: reading order

1. [01 — Prepare AWS access](01-aws-cli-login.md): select the CLI and sign in as administrator for initial account setup.
2. [01b — Create your development SSO user](01b-aws-sso-user-setup.md): create the user, set password/MFA, then configure `workouts-dev`.
3. [02 — AWS VM setup and daily use](02-aws-dev-vm.md): use `workouts-dev` to install once, start/connect/stop daily, and terminate/recreate when needed.

For normal work, keep **02** open.
The component-specific guides below are references, not additional AWS installation steps.

## Deployment model

The root [docker-compose.yml](../../docker-compose.yml) owns **shared-infra**:
PostgreSQL, Kafka, Kafka UI, Redis, Keycloak and the observability stack. Run applications in IntelliJ
with `local`. Future Jenkins deployments use `k8s` in Minikube and the **same**
infrastructure instances. There are no infrastructure servers in `k8s/`.

| Component    | Guide                                                      | Local connection             | Minikube connection                                |
| ------------ | ---------------------------------------------------------- | ---------------------------- | -------------------------------------------------- |
| PostgreSQL   | [Setup](postgres-setup.md)                                 | localhost:5432               | host.minikube.internal:5432                        |
| Kafka        | [Setup](kafka-setup.md)                                    | localhost:9092               | host.minikube.internal:9094                        |
| Kafka UI     | [Setup](kafka-ui-setup.md)                                 | http://localhost:8089        | Browser tool, not an application dependency        |
| Keycloak     | [Setup and credentials](keycloak-setup.md)                 | http://localhost:8180        | http://host.minikube.internal:8180 for backchannel |
| Redis        | [Setup and password](redis-setup.md)                       | localhost:6379               | host.minikube.internal:6379                        |
| Observability | [Setup, credentials and verification](observability-implementation-guide.md) | Grafana localhost:3000; OTLP localhost:4318 | OTLP host.minikube.internal:4318 |
| Applications | [IntelliJ, frontend and database setup](commerce-setup.md) | Nine Java services and React | [Future Jenkins/Minikube setup](minikube-setup.md) |

## First startup or existing-volume upgrade

Prerequisites: Docker Desktop running, Docker Compose CLI, Python 3. Execute from
the repository root. No host installation of PostgreSQL/Kafka/Redis is required.

```sh
docker info
docker volume create workouts-postgres-data
docker volume create workouts-kafka-data
docker compose config --quiet
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U postgres -d postgres
```

Wait until PostgreSQL accepts connections, then:

```sh
python3 docker/postgres/ensure-databases.py
docker compose up -d
docker compose ps
curl --fail http://localhost:8180/realms/ecommerce/.well-known/openid-configuration
```

Wait/retry discovery until Keycloak is ready, then:

```sh
python3 docker/keycloak/configure-security.py
python3 docker/keycloak/verify-clients.py
```

Follow [Kafka setup](kafka-setup.md) to verify readiness and create both topics,
then [application setup](commerce-setup.md). PostgreSQL/Kafka external volumes must
exist before first startup. Redis's volume is created by Compose. Existing data
is reused; initialization SQL alone does not update an already initialized volume.
The database helper creates missing databases and applies the guarded order-status
constraint migration without dropping records.

## Routine use and persistence

For an initialized workspace, `docker compose up -d` starts infrastructure. A
running container is not always ready; use each guide's readiness check.
`docker compose logs --tail=100 SERVICE` helps diagnose startup.

Both application profiles share database names, Kafka topics/groups and Redis keys.
Running local and stage simultaneously can affect the same data and split consumer
work. Named volumes survive ordinary container recreation; they are not backups.
Do not delete volumes to fix missing databases or stale realm configuration.

Credentials here are intentionally documented learning credentials. Kafka/Kafka UI
have no authentication/TLS, and the published ports serve the trusted local setup.
Mutable image tags can change after an explicit pull; current runtime versions
must be inspected rather than inferred from historical installation notes.

- [IntelliJ and Maven workspace setup](intellij-maven-setup.md): import all services, choose JDK 17 and run the correctly named application classes.
