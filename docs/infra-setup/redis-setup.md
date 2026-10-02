# Shared Redis setup and optional application integration

Redis is installed in Docker Desktop's **shared-infra** project. **No current
microservice uses Redis.** The optional Java example below is a learning extension,
not part of shopping, cart, checkout or gateway rate limiting.

## Configuration

| Setting                     | Value in Compose                             |
| --------------------------- | -------------------------------------------- |
| Image / service / container | `redis:8-alpine` / `redis` / `redis`         |
| Username / password         | `default` / `WorkoutsRedis-LocalStage-2026!` |
| Local host / port           | localhost:6379                               |
| Minikube host / port        | host.minikube.internal:6379                  |
| Docker-network address      | redis:6379                                   |
| Database                    | 0                                            |
| Named volume                | workouts-redis-data at /data                 |
| Persistence                 | AOF; fsync every second                      |
| TLS                         | Not configured                               |

Compose sets `REDISCLI_AUTH`, uses it for server `--requirepass`, and lets its
health check authenticate. The named volume is created automatically. `8-alpine`
is a mutable tag; inspect the running version instead of assuming a past version.

## Install, start and verify

Run from repository root with Docker Desktop ready:

```sh
docker compose config --quiet
docker compose up -d --wait --wait-timeout 120 redis
docker compose exec -T redis redis-cli ping
docker compose exec -T redis env -u REDISCLI_AUTH redis-cli ping
docker compose exec -T redis redis-server --version
```

The authenticated PING returns PONG; removing automatic authentication returns
NOAUTH. No host Redis installation or second Redis in Minikube is required.

```sh
docker compose exec -T redis redis-cli SET demo:setup ready EX 60
docker compose exec -T redis redis-cli GET demo:setup
docker compose exec -T redis redis-cli TTL demo:setup
```

The example key expires automatically. Check persistence with
`docker compose exec -T redis redis-cli INFO persistence`.

## Optional Spring usage example: store a short-lived marker

This is **not installed in any application**. For a future blocking MVC service
exercise, add `spring-boot-starter-data-redis` using the service's existing Spring
Boot dependency management. Merge into its existing configuration:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      database: 0
      username: default
      password: ${REDIS_PASSWORD}
      connect-timeout: 2s
      timeout: 2s
```

Set `REDIS_PASSWORD` to the learning password above. Local uses localhost; future
k8s uses `REDIS_HOST=host.minikube.internal` and an application Secret supplied by
Jenkins. Do not duplicate top-level YAML keys or deploy a Redis server in k8s.

A method inside a Spring component with an injected `StringRedisTemplate` could
write and read a marker:

```java
redis.opsForValue().set("learning:marker", "ready", java.time.Duration.ofMinutes(5));
String value = redis.opsForValue().get("learning:marker");
```

This example demonstrates a TTL, not a business cache or idempotency guarantee.
For reactive PGS/gateway work, design a reactive client path rather than placing
blocking template calls on an event loop. [Rate limiting](../api-gateway/05-rate-limiting-redis.md)
is a separate future exercise.

Both profiles share the same instance/database/keys. Service/environment key prefixes
can distinguish data later without installing another Redis instance.

## Operations and password changes

```sh
docker compose ps redis
docker compose logs --tail=100 redis
docker compose stop redis
docker compose up -d --wait redis
```

To change the password, update `REDISCLI_AUTH` in Compose and this guide, then use
`docker compose up -d --wait redis`. A plain restart does not apply edited Compose
environment values. Update any future application's password/Secret and restart
that application. The existing data volume is retained.

## Troubleshooting

- Docker unavailable: start Docker Desktop and wait for `docker info` to succeed.
- Port conflict: inspect `lsof -nP -iTCP:6379 -sTCP:LISTEN` and Compose containers.
- Authentication error: server container commands inherit `REDISCLI_AUTH`; external
  clients must supply credentials themselves.
- Minikube refused connection: check host alias, published port and firewall from
  the real cluster. Node TCP reachability alone does not verify authentication.
- Missing data: named volumes are persistence, not backups; avoid volume deletion.

The host port is shared for trusted local learning. Redis is not configured with TLS.
