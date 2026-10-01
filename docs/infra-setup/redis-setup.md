# Redis in Docker Desktop: shared-infra

Back to the [infrastructure index](README.md). Runtime configuration is in the
[root Compose file](../../docker-compose.yml).

Installed on September 29, 2026; updated for shared IntelliJ/Minikube access on September 30, 2026. Redis is a service in the repository's root `docker-compose.yml`, whose project name is `shared-infra`. In Docker Desktop, open **Containers → shared-infra → redis**.

## Installed configuration

| Setting | Value |
| --- | --- |
| Image | `redis:8-alpine` |
| Installed server version | `8.10.2` |
| Container / Compose service | `redis` / `redis` |
| Host address | `127.0.0.1:6379` |
| Docker network address | `redis:6379` on `shared-infra_default` |
| Connection URL from Mac | `redis://default:WorkoutsRedis-LocalStage-2026%21@127.0.0.1:6379/0` |
| Database | `0` |
| Username | `default` |
| Password | `WorkoutsRedis-LocalStage-2026!` |
| TLS | Not configured |
| Persistent volume | `workouts-redis-data`, mounted at `/data` |
| Persistence | Append-only file (AOF), fsync every second |
| Restart policy | `unless-stopped` |

One Redis instance in `shared-infra` serves both profiles. Port 6379 is published on all host interfaces so Minikube can reach it. IntelliJ uses `localhost`; Minikube uses `host.minikube.internal`. Password authentication is required. The port is potentially reachable from your LAN; use this setup only on a trusted development network and do not expose it publicly.

The downloaded image digest was:

```text
redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0
```

`8-alpine` is a moving major-version tag. To reproduce the exact downloaded image, use the digest above as the Compose `image` value. Future explicit pulls may otherwise update Redis.

## Password authentication

The development password is **`WorkoutsRedis-LocalStage-2026!`**, using Redis user `default`. It is documented here as requested. This is a shared learning credential, not a production secret. Production credentials belong in a secret manager; this installation does not enable TLS.

Compose sets `REDISCLI_AUTH` inside the Redis container. The startup command uses it for `--requirepass`, while `redis-cli` and the health check use it to authenticate. Existing `docker compose exec ... redis-cli` commands therefore authenticate automatically. [Redis authentication](https://redis.io/docs/latest/operate/oss_and_stack/management/security/) and [Redis CLI authentication](https://redis.io/docs/latest/develop/tools/cli/).

For external Redis CLI examples below, set this in the same terminal before running them:

```bash
export REDISCLI_AUTH='WorkoutsRedis-LocalStage-2026!'
```

## Installation and startup

Run commands from the workspace root:

```bash
cd /Users/haneefnoorbasha/Workouts/microservices-workouts
open -a Docker
```

Wait for Docker Desktop to finish starting, then check it:

```bash
docker version
docker info --format '{{.ServerVersion}}'
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
```

The following excerpt documents the Redis configuration already in the root Compose file. Use the existing file; do not replace the whole file with this excerpt:

```yaml
name: shared-infra

services:
  redis:
    image: redis:8-alpine
    container_name: redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    # Development credential; also used by redis-cli and the health check.
    environment:
      REDISCLI_AUTH: "WorkoutsRedis-LocalStage-2026!"
    command: ["sh", "-c", 'exec redis-server --appendonly yes --appendfsync everysec --requirepass "$$REDISCLI_AUTH"']
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli ping | grep -qx PONG"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 5s

volumes:
  redis-data:
    name: workouts-redis-data
```

Validate and start just Redis:

```bash
docker compose config --quiet
docker compose up -d --wait --wait-timeout 120 redis
```

The second command downloads the image if absent, creates the volume, and starts Redis in `shared-infra`. No separate Redis installation on macOS is needed. The existing Postgres and Kafka services are not recreated by this command. See the [Redis Docker installation documentation](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack/docker/).

## Verification performed

Verification commands (the container inherits `REDISCLI_AUTH`):

```bash
docker compose exec -T redis redis-cli ping
# PONG

docker compose exec -T redis redis-server --version
# Redis server v=8.10.2 ...

docker inspect redis --format 'project={{index .Config.Labels "com.docker.compose.project"}} health={{.State.Health.Status}} ports={{json .NetworkSettings.Ports}}'
# project=shared-infra health=healthy; published port 6379

docker compose exec -T redis redis-cli SET workouts:redis:setup-check ready EX 300
docker compose restart redis
docker compose exec -T redis redis-cli GET workouts:redis:setup-check
# ready: the key survived the restart

docker run --rm --network shared-infra_default -e REDISCLI_AUTH redis:8-alpine redis-cli -h redis ping
# PONG: another container can connect

docker compose exec -T redis redis-cli DEL workouts:redis:setup-check
# 1: removed only the temporary verification key

docker image inspect redis:8-alpine --format '{{index .RepoDigests 0}}'
```

The Mac's published port was also verified without installing a Redis client:

```bash
python3 - <<'PYTHON'
import os
import socket

def send(stream, *args):
    parts = [str(arg).encode() for arg in args]
    payload = b"*%d\r\n" % len(parts)
    for part in parts:
        payload += b"$%d\r\n" % len(part) + part + b"\r\n"
    stream.write(payload)
    stream.flush()
    return stream.readline().decode().strip()

with socket.create_connection(("127.0.0.1", 6379), timeout=3) as sock:
    with sock.makefile("rwb") as stream:
        print(send(stream, "PING"))  # -NOAUTH Authentication required.
        print(send(stream, "AUTH", os.environ["REDISCLI_AUTH"]))  # +OK
        print(send(stream, "PING"))  # +PONG
PYTHON
```

## Application access

| Application location | Host | Port |
| --- | --- | --- |
| IntelliJ / Java / Maven running on the Mac | `127.0.0.1` | `6379` |
| Container on `shared-infra_default` | `redis` | `6379` |
| Container on another Docker network | Attach it to `shared-infra_default`, then use `redis` | `6379` |
| Minikube pod (`k8s` profile) | `host.minikube.internal` | `6379` |

Inside an application container, `localhost` refers to that application container. Compose services discover each other by service name on their shared network. [Docker Compose networking](https://docs.docker.com/compose/how-tos/networking/)

Microservices in this repository do not run through Compose. Run them from
IntelliJ now; Jenkins will deploy them to Minikube later. The Docker network
address above is useful for infrastructure tools and temporary diagnostic
clients only.

Minikube accesses the same published host port using `host.minikube.internal`,
matching the existing Postgres setup. [Minikube host access](https://minikube.sigs.k8s.io/docs/handbook/host-access/)

### Local and k8s profiles

For each application that uses Redis, merge these properties into its existing profile files (do not add a second top-level `spring` key).

`application-local.yml` — IntelliJ:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      username: default
      password: ${REDIS_PASSWORD}
      connect-timeout: 2s
      timeout: 2s
```

`application-k8s.yml` — Minikube staging:

```yaml
spring:
  data:
    redis:
      host: host.minikube.internal
      port: 6379
      database: 0
      username: default
      password: ${REDIS_PASSWORD}
      connect-timeout: 2s
      timeout: 2s
```

Set `REDIS_PASSWORD=WorkoutsRedis-LocalStage-2026!` in IntelliJ's run-configuration environment variables. For the future Jenkins deployment, have the pipeline supply an application Secret in namespace `ecommerce`, then reference it in each Redis-using application's Deployment. The following is a pipeline setup example, not a deployment performed now:

```bash
kubectl --context=minikube -n ecommerce create secret generic shared-redis-auth --from-literal=password='WorkoutsRedis-LocalStage-2026!' --dry-run=client -o yaml | kubectl --context=minikube -n ecommerce apply -f -
```

```yaml
# Merge into spec.template.spec.containers[].env:
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: shared-redis-auth
      key: password
```

The `ecommerce` namespace must exist first; the future pipeline should prepare it from `k8s/namespace.yaml`. The Secret and Deployment changes above are instructions, not changes applied to the stopped cluster.

Select `local` in IntelliJ's Spring Boot run configuration, or set `SPRING_PROFILES_ACTIVE=local`. Kubernetes deployments use `SPRING_PROFILES_ACTIVE=k8s`. These are configuration examples; no microservice Redis dependencies or profile files were changed.

Both profiles access the same instance and database, so they share keys. If separate data is desired later without another instance, use different key prefixes or logical database numbers.

### Check Minikube connectivity

Minikube was stopped when this change was applied, so pod connectivity has not yet been verified. When ready to run staging:

```bash
minikube start
minikube ssh -- 'nc -vz -w 5 host.minikube.internal 6379'

# Run a temporary Redis client pod, not another Redis server.
kubectl --context=minikube run redis-connectivity-check --image=redis:8-alpine --restart=Never --attach --rm --env="REDISCLI_AUTH=$REDISCLI_AUTH" --command -- redis-cli -h host.minikube.internal -p 6379 ping
# Expected: PONG
```

If node access succeeds but the pod cannot resolve the hostname, check Minikube/CoreDNS host-name resolution for your driver. If connection is refused, check Docker Desktop, the published port, and the host firewall. No Redis Deployment or Redis server pod is needed.

### Spring Boot 3.5 example

The repository uses Spring Boot 3.5. Add this dependency to the service that will use Redis; its existing Spring Boot parent manages the version:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Merge into that service's existing YAML, under its existing `spring` key:

```yaml
spring:
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:127.0.0.1}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      database: 0
      username: default
      password: ${REDIS_PASSWORD}
      connect-timeout: 2s
      timeout: 2s
```

Both profiles require the same password. The `${REDIS_PASSWORD}` placeholder keeps it out of application YAML. The starter supplies Lettuce and auto-configures `StringRedisTemplate`. [Spring Boot Redis configuration](https://docs.spring.io/spring-boot/3.5/reference/data/nosql.html#data.nosql.redis)

Example component, placed under your application's scanned package:

```java
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisExample {
    private final StringRedisTemplate redis;

    public RedisExample(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void saveExample() {
        redis.opsForValue().set("payment-service:example", "ready", Duration.ofMinutes(5));
    }

    public String readExample() {
        return redis.opsForValue().get("payment-service:example");
    }
}
```

Use service-specific key prefixes to avoid collisions. The example is documentation only: application dependencies and code have not been changed, and the Java example was not compiled as part of installation.

## Daily commands

Run from the workspace root:

```bash
# Start Redis; safe to repeat.
docker compose up -d --wait redis

# Status and logs.
docker compose ps redis
docker compose logs --tail=100 redis
docker compose logs -f redis

# Interactive Redis shell; enter QUIT to leave.
docker compose exec redis redis-cli

# Example key operations with automatic expiry.
docker compose exec -T redis redis-cli SET demo:hello world EX 60
docker compose exec -T redis redis-cli GET demo:hello
docker compose exec -T redis redis-cli TTL demo:hello
docker compose exec -T redis redis-cli --scan --pattern 'demo:*'
docker compose exec -T redis redis-cli DEL demo:hello

# Inspect persistence and storage.
docker compose exec -T redis redis-cli INFO persistence
docker compose exec -T redis redis-cli CONFIG GET appendonly appendfsync
docker volume inspect workouts-redis-data

# Stop / restart only Redis; keep its data.
docker compose stop redis
docker compose restart redis

# Apply future Compose configuration changes.
docker compose up -d --wait redis

# Optional image update; review the new version before updating.
docker compose pull redis
docker compose up -d --wait redis
```

`unless-stopped` restarts Redis when Docker starts again, unless you explicitly stopped it. Docker Desktop must itself be running. The named volume preserves data across container recreation; it is not a backup. Avoid `docker compose down -v`: it affects the shared project and can delete the Redis volume.

## Troubleshooting

- **Cannot connect to Docker:** open Docker Desktop, wait for startup, then run `docker info`. The daemon was initially stopped during this installation.
- **Port 6379 is occupied:** inspect `docker ps` and `lsof -nP -iTCP:6379 -sTCP:LISTEN`. Choose another host mapping, such as `6380:6379`, and use port `6380` from both Mac and Minikube applications. Containers still use `redis:6379`.
- **Connection refused from a local app:** run `docker compose ps redis` and the PING check above. Confirm the application uses `127.0.0.1:6379`.
- **Connection fails from an app container:** inspect `docker network inspect shared-infra_default`; attach the app and use host `redis`.
- **Authentication error:** set `REDIS_PASSWORD` to the documented password and check for a conflicting `spring.data.redis.url`. For a client container or client pod, pass `REDISCLI_AUTH`; it is not inherited automatically from the server container.
- **Configuration changed but did not take effect:** use `docker compose up -d --wait redis`; a restart alone does not apply changes to the Compose definition.

## Related files

- [docker-compose.yml](../../docker-compose.yml): Redis service, health check, port binding, and persistent volume.
- `docs/infra-setup/redis-setup.md`: this installation and operations guide.

## Verify password enforcement and change the password

```bash
# Deliberately remove automatic authentication: must return NOAUTH.
docker compose exec -T redis env -u REDISCLI_AUTH redis-cli ping

# Inherits the configured password: must return PONG.
docker compose exec -T redis redis-cli ping
```

To change the password, update `REDISCLI_AUTH` in the root Compose file and this document, then run `docker compose up -d --wait redis`. Update the application's `REDIS_PASSWORD` environment variable and the Kubernetes Secret to match, and restart application processes/pods so they read the new value. The existing data volume is retained.
