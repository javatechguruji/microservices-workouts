# Postgres setup and microservice usage

Back to the [infrastructure index](README.md). Configuration source:
[docker-compose.yml](../../docker-compose.yml).

## Purpose and installed configuration

Order-service and payment-service use one shared Postgres server with separate
databases. Product/inventory still use H2; this guide does not migrate them.

| Setting | Repository value |
| --- | --- |
| Image / service / container | `postgres:16` / `postgres` / `postgres` |
| Published port | `5432:5432` |
| Username / password | `postgres` / `postgres` |
| Order database | `order-srv-db` |
| Payment database | `payment-srv-db` |
| Keycloak database | `keycloak` (identity-provider storage) |
| External Docker volume | `workouts-postgres-data` |
| Container data path | `/var/lib/postgresql/data` |
| Init script | `docker/postgres/init-databases.sql` |
| Restart policy | `unless-stopped` |

The Compose service sets `POSTGRES_USER` and `POSTGRES_PASSWORD`, mounts its data
volume, and mounts the SQL script read-only into
`/docker-entrypoint-initdb.d/init-databases.sql`. The image initializes credentials
and runs that script only with an empty data directory. Changing these variables
or editing the SQL file does not modify an initialized database.
[Official Postgres image initialization behavior](https://hub.docker.com/_/postgres).

## Installation and readiness

```bash
# Docker Desktop must already be running.
docker volume create workouts-postgres-data
docker compose config --quiet
docker compose up -d postgres
docker compose ps postgres
docker compose logs --tail=100 postgres

docker compose exec -T postgres pg_isready -U postgres -d postgres
# Expected: accepting connections

docker compose exec -T postgres psql -U postgres -d postgres -c '\l'
# Expected databases include order-srv-db and payment-srv-db.
```

No host Postgres installation is required. The container includes `psql`.
For an existing volume missing a database, create only the missing database
rather than rerunning the whole initialization script:

```bash
# Run only if the corresponding database does not exist.
docker compose exec -T postgres createdb -U postgres order-srv-db
docker compose exec -T postgres createdb -U postgres payment-srv-db
```

## Connection settings

| Client | Host | Port |
| --- | --- | --- |
| IntelliJ application or database tool on Mac | `localhost` | `5432` |
| Minikube microservice | `host.minikube.internal` | `5432` |
| Infrastructure diagnostic client on Compose network | `postgres` | `5432` |

In IntelliJ's Database tool window, add a PostgreSQL data source with the host,
port, username, password and one of the database names above; test the connection.
For a Minikube node connectivity check, when staging is running:

```bash
minikube ssh -- 'nc -vz -w 5 host.minikube.internal 5432'
```

A TCP check does not verify the database password. Use the application's startup
logs or a PostgreSQL client to verify authentication.

## Example: order-service with Spring Data JPA

Security is now enabled: run the gateway too and obtain a `customer1` access
token using the [security demo/PKCE flow](../security/Authentication%20and%20Authorization%20at%20Microservice.md).
For the curl examples, set `ACCESS_TOKEN` to that token. Alternatively perform
the same create/read actions in the browser demo. Requests enter port 9100;
the owning service authorizes the caller's headers.


The following dependencies already exist in order-service and payment-service.
Do not add duplicate dependency entries:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

The current order-service profile configuration is:

```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/order-srv-db
    username: postgres
    password: postgres
```

```yaml
# application-k8s.yml
spring:
  datasource:
    url: jdbc:postgresql://host.minikube.internal:5432/order-srv-db
    username: postgres
    password: postgres
```

Payment-service uses the same pattern with `payment-srv-db`. Merge properties
under existing YAML keys rather than adding a second `spring` section. The
services currently use Hibernate `ddl-auto: update` to create/update tables for
learning. Database migrations would be a separate future improvement.

Concrete existing code:
[OrderRepository](../../order-service/src/main/java/com/tip/ecommerce/order/repository/OrderRepository.java)
and [OrderServiceImpl](../../order-service/src/main/java/com/tip/ecommerce/order/service/impl/OrderServiceImpl.java)
save and retrieve entities through JPA.

1. Start shared infrastructure, including Kafka because order-service consumes
   payment events.
2. Run order-service in IntelliJ with `SPRING_PROFILES_ACTIVE=local`.
3. Create an order (this writes sample data):

```bash
curl --fail-with-body -X POST http://localhost:9100/api/orders \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"customer1","amount":25.00}'
```

4. Use the returned `id` for `GET /api/orders/{id}`, or inspect the database:

```bash
docker compose exec -T postgres psql -U postgres -d order-srv-db \
  -c "SELECT id, customer_id, amount, status FROM orders WHERE customer_id = 'customer1';"
```

Expected: the created order appears with status `PENDING` until its payment is
processed. In future Jenkins deployments, select `k8s`; keep Postgres in Compose.
Credentials may later be injected with `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` from application Secrets instead of literals.

## Operations and troubleshooting

```bash
docker compose stop postgres
docker compose up -d postgres
docker compose restart postgres
docker volume inspect workouts-postgres-data

# Optional logical backup of the order database to a local file.
docker compose exec -T postgres pg_dump -U postgres -d order-srv-db -Fc > /tmp/order-srv-db.backup
```

- **External volume not found:** create `workouts-postgres-data` before startup.
- **Database does not exist:** inspect `\l`; an old volume does not rerun init SQL.
- **Password rejected:** the existing volume may have a different password.
  Changing Compose alone does not rotate it; connect with the existing admin
  credential and use `\password postgres` in an interactive `psql` session,
  then update application configuration and Compose together.
- **Port conflict:** inspect `lsof -nP -iTCP:5432 -sTCP:LISTEN`; avoid starting a
  competing host Postgres on the same port.
- **Table absent:** start the owning microservice so Hibernate can initialize it.
- **Minikube connection refused:** check Docker, the host port and host firewall;
  `localhost` inside a Pod is not the Mac.

Keep the volume when recreating the container. Moving the init script requires
a Compose path update, not deletion of live database data.
