# PostgreSQL setup and service database configuration

PostgreSQL is one shared server with separate databases owned by services.
[Application setup](commerce-setup.md#2-applications-and-storage) lists every database.
The gateway has no application database; Keycloak owns its separate `keycloak` database.

## Configuration and initialization

| Setting                 | Value                                |
| ----------------------- | ------------------------------------ |
| Compose image/container | `postgres:16` / `postgres`           |
| Username / password     | `postgres` / `postgres`              |
| Published port          | 5432                                 |
| External volume         | `workouts-postgres-data`             |
| Mount                   | `/var/lib/postgresql/data`           |
| Fresh-volume script     | `docker/postgres/init-databases.sql` |

Run from repository root with Docker Desktop ready:

```sh
docker volume create workouts-postgres-data
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U postgres -d postgres
```

After `accepting connections`, run:

```sh
python3 docker/postgres/ensure-databases.py
docker compose exec -T postgres psql -U postgres -d postgres -c '\l'
```

Initialization SQL runs only for an empty data directory. The helper creates
missing databases on existing volumes and applies the guarded order-status
constraint migration. Service startup initializes its own tables/seeds using
`schema.sql` and, for order/payment entities, Hibernate schema update. There is no
Flyway/Liquibase migration system. Restarting does not restock inventory.

## Application configuration example

The existing order local profile includes:

Excerpt from [application-local.yml](../../order-service/src/main/resources/application-local.yml) (surrounding code omitted):

```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/order-srv-db
  username: postgres
  password: postgres
```

The k8s profile uses the same database/credentials with host
`host.minikube.internal`. Docker infrastructure clients such as Keycloak use host
`postgres`; IntelliJ uses `localhost`. In IntelliJ Database tools, use the same
host, port, username/password and the owning database name.

Do not add another PostgreSQL Deployment to Minikube. Service-owned databases in
this POC share a PostgreSQL administrator credential; they are ownership boundaries
in application code, not independently restricted database users.

For a concrete usage example, [checkout](../project-docs/shopping-and-fulfillment.md)
shows order/payment local transactions and inventory conditional updates. Those
services call APIs rather than joining each other's tables.

## Inspect and back up

```sh
docker compose exec -T postgres psql -U postgres -d order-srv-db \
  -c 'SELECT order_id,state,attempts,error FROM checkout ORDER BY order_id DESC LIMIT 10'
docker compose exec -T postgres pg_dump -U postgres -d order-srv-db -Fc > /tmp/order-srv-db.backup
```

The query requires order-service schema initialization. The backup command writes
one database backup; it is not a backup of all service/Keycloak databases.

## Troubleshooting

- Missing database: list databases and run the helper; do not erase the volume.
- Missing table: start the owning service and inspect SQL initialization logs.
- Connection refused: check Docker, port 5432 and the selected profile.
- Existing password differs: changing Compose's `POSTGRES_PASSWORD` does not rotate
  an initialized user; change it through PostgreSQL and update applications together.
- Minikube: verify host access from the real Pod; a successful local connection
  does not establish cluster reachability.

Routine status/logs: `docker compose ps postgres` and
`docker compose logs --tail=100 postgres`. Keep the data volume across recreation.
