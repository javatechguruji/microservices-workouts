# Shared infrastructure setup

These guides describe the existing `shared-infra` project in
[the root Compose file](../../docker-compose.yml), checked September 30, 2026.
These guides document setup and usage. Redis and Keycloak were installed and
verified as part of their setup tasks; application deployment remains separate.

| Component | Guide | IntelliJ / host connection | Minikube connection |
| --- | --- | --- | --- |
| Postgres | [Setup and JPA example](postgres-setup.md) | `localhost:5432` | `host.minikube.internal:5432` |
| Kafka | [Setup and payment events](kafka-setup.md) | `localhost:9092` | `host.minikube.internal:9094` |
| Kafka UI | [Setup and event inspection](kafka-ui-setup.md) | Browser: `http://localhost:8089` | Applications do not connect to the UI |
| Keycloak | [Setup and client credentials](keycloak-setup.md) | `http://localhost:8180` | `http://host.minikube.internal:8180` (backchannel) |
| Redis | [Setup and Spring Data example](redis-setup.md) | `localhost:6379` | `host.minikube.internal:6379` |

## Architecture

Docker Compose runs infrastructure only. Start microservices in IntelliJ with
`local`; later Jenkins will deploy the application manifests in `k8s/` to
Minikube with `k8s`. Both environments share these infrastructure instances.
Application Secrets in Kubernetes provide connection credentials; they do not
deploy infrastructure servers there.

MongoDB and Prometheus are mentioned as future additions but are not configured
Compose services. H2 is embedded in product/inventory and is not a separate
infrastructure container.

## First setup

Prerequisite: Docker Desktop is installed. All shell commands in these guides
run from the repository root unless a different location is explicitly given.

```bash
cd /Users/haneefnoorbasha/Workouts/microservices-workouts
open -a Docker
# Wait for Docker Desktop to start.
docker info

# Required because these volumes are declared external in Compose.
docker volume create workouts-postgres-data
docker volume create workouts-kafka-data

docker compose config --quiet
docker compose up -d
docker compose ps
```

`up` pulls missing images. The Redis volume is created automatically. Existing
named volumes are reused. A running container is not necessarily ready: use the
component-specific readiness commands before starting applications. Redis and Keycloak have Compose health checks; Postgres and Kafka currently do not.

When adding Keycloak to an existing Postgres volume, first create its database
as described in the [Keycloak guide](keycloak-setup.md#installation-and-database-initialization).

For an existing installation, normally only `docker compose up -d` is needed.
Use `docker compose pull SERVICE` only when intentionally updating that image;
`postgres:16`, `redis:8-alpine`, and Kafka UI's `latest` are moving tags.

## Configuration and data

- The root Compose file is the runtime configuration source of truth.
- [Postgres initialization](../../docker/postgres/init-databases.sql) creates
  application databases only for a fresh data directory.
- Named volumes store data; these Markdown files do not.
- The same database names, Redis database, and Kafka topics are shared between
  local and staging. Running both simultaneously can affect the same data.
  Kafka consumers with the same group ID also share work across environments.
- Published ports are for trusted local learning. Credentials documented here
  are development credentials. Kafka and Kafka UI currently have no login/TLS.

## Routine operations

```bash
docker compose logs --tail=100
docker compose stop kafka-ui
# Starts the UI and its Kafka dependency if needed.
docker compose up -d kafka-ui
```

Stop an individual component by naming it. `docker compose down` stops the whole
shared stack. Avoid `down -v` or volume deletion as a troubleshooting shortcut:
Redis data can be removed, and manually deleting any database volume loses data.

The examples describe expected results. Redis was smoke-tested during its prior
installation; new database/message examples are instructions, not actions
performed while writing these guides. Minikube access must be checked when the
cluster is running. Jenkins deployment automation is still future work.
