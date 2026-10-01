# k8s

Application manifests for the future Jenkins deployment to Minikube.
Docker Compose manages shared infrastructure separately on the host.

## Deployment ownership

- Run applications in IntelliJ now, using the `local` profile.
- Jenkins will build, test, publish/load application images, and deploy the
  manifests here with `SPRING_PROFILES_ACTIVE=k8s`.
- Do not add microservices to `docker-compose.yml`.
- Do not add Postgres, Kafka, Redis, Kafka UI, or Keycloak workloads to this directory.
- `namespace.yaml` creates the application's `ecommerce` namespace; it is
  application deployment support, not an infrastructure server.
- Application ConfigMaps and Secret references may be added when needed.
  Credentials for external infrastructure do not require deploying that
  infrastructure inside Kubernetes.

No Jenkins pipeline exists yet. The manifests do not by themselves enforce
who can deploy; Jenkins credentials and Kubernetes RBAC will establish that
control when the pipeline is built. Do not deploy applications as part of
starting shared infrastructure.

## Current manifest inventory

- `order-service.yaml` — Deployment + Service
- `gateway-service.yaml` — Deployment + Service (NodePort)
- `payment-service.yaml` — Deployment + Service
- `notification-service.yaml` — Deployment + Service
- `product-service.yaml` — Deployment + Service (port 9104)
- `inventory-service.yaml` — Deployment + Service (port 9105)
- `service-client-credentials.yaml` — application Secret for order/payment machine clients (learning credentials)
- Additional application ConfigMaps — not written yet

No Eureka/service-registry manifest needed — services address each other by
Kubernetes Service DNS name (e.g. `http://order-service:9101`) instead of
registering with a discovery server. See `../INTERVIEW_TOPICS.md` for the
full topic checklist this maps to.

## Port sequence

All microservice ports follow one `91xx` block: `9100` gateway, `9101`
order, `9102` payment, `9103` notification, `9104` product, `9105`
inventory — one number to remember instead of six unrelated ones.

Two numbers in that neighborhood are deliberately skipped: `9090`
(Prometheus's real default port — not added to this repo yet, but likely
to be) and `9092`/`9094` (Kafka's actual ports, see below — not a
coincidence, chosen to leave room around them).

## Shared infrastructure lives outside the cluster

`order-service` and `payment-service` need Postgres; `order-service`,
`payment-service`, and `notification-service` need Kafka. Neither is
deployed into minikube — both run on the host machine via
`docker compose up -d` (`../docker-compose.yml`). minikube should only ever
run application workloads plus Kubernetes system components, not these shared
infrastructure servers. Redis, Kafka UI, and Keycloak also remain in Compose.

That means their `application-k8s.yml` points at `host.minikube.internal`
instead of `localhost` — from inside a Pod, `localhost` resolves to the Pod
itself, not your Mac. `host.minikube.internal` is minikube's built-in DNS
host-access alias; this workspace uses the Docker driver.

Kafka needs a **second port**, not just the same host alias: the
`docker-compose.yml` `kafka` service exposes `PLAINTEXT_HOST` on `9092`
(advertised as `localhost:9092`, for Mac-local processes) and a separate
`PLAINTEXT_K8S` on `9094` (advertised as `host.minikube.internal:9094`, for
Pods). One address can't serve both, because Kafka clients reconnect to
whatever address the broker *advertises* after the first handshake —
unlike Postgres, which is a plain single-hop connection. So
`application-k8s.yml` for the three Kafka-using services points at
`host.minikube.internal:9094`, not `:5432`-style port reuse.

Before a future Jenkins deployment, start shared infrastructure independently:
```bash
docker compose up -d   # from the repo root
```

Redis uses `localhost:6379` from IntelliJ and
`host.minikube.internal:6379` from Minikube, with the same password.
See [Redis configuration](../docs/infra-setup/redis-setup.md). Redis is not yet integrated
into application code; that guide contains profile and Secret examples.

## Future Jenkins pipeline contract

1. Check out the selected revision and test the selected microservice.
2. Build its Docker image with an immutable version, such as the Git commit.
3. Publish the image to a registry reachable by Minikube, or load it into the
   local Minikube runtime when the Jenkins agent has access to that runtime.
4. Prepare the `ecommerce` namespace and application configuration/credentials.
5. Render the selected application manifest with that image reference and
   apply it through Jenkins, then wait for rollout readiness.

The current image tags (`stage1`, `stage3`, `stage6`) are existing lab values.
A future pipeline must replace them with its actual image references; a
registry, Jenkins credentials, and a Jenkinsfile have not been configured.
The Jenkins agent must be able to reach the local Minikube API server.

Product and inventory now have Java 17 Dockerfiles and application manifests.
Both retain embedded H2 in a Pod `emptyDir`, use one replica and a `Recreate`
strategy, and enable Actuator startup/readiness/liveness checks. Data survives
a container restart but not Pod replacement or a new deployment. Their k8s
profiles disable the H2 console. External persistent storage and scaling are
future changes, not infrastructure deployments in this directory. Their
Service names match the gateway k8s route URLs; application HTTP clients return through the gateway.

Older exercises under `docs/` show manual `kubectl` builds, deployments, and
optional cluster add-ons for learning. They are historical lab procedures,
not the operational deployment workflow for this repository. Read-only
inspection commands remain useful; application deployment will go through
Jenkins once implemented.


## Application security configuration

Prepare `service-client-credentials.yaml` after the namespace and before the
order/payment Deployments. Jenkins should supply environment-specific secrets
instead of committing real credentials. Only the gateway validates JWTs;
downstream HTTP controllers trust gateway identity headers and authorize them.
The existing ClusterIP topology is not a NetworkPolicy/mTLS security boundary.
See [the security implementation](../docs/security/Authentication%20and%20Authorization%20at%20Microservice.md).
