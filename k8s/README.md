# k8s

Kubernetes manifests for the e-commerce system, one file per service:

- `order-service.yaml` — Deployment + Service
- `gateway-service.yaml` — Deployment + Service (+ Ingress or Gateway API route)
- `payment-service.yaml` — Deployment + Service
- `notification-service.yaml` — Deployment + Service
- `product-service.yaml` / `inventory-service.yaml` — not written yet
- `configmap.yaml` / `secret.yaml` — externalized config, replacing Spring Cloud Config Server (not written yet)

No Eureka/service-registry manifest needed — services address each other by
Kubernetes Service DNS name (e.g. `http://order-service:9091`) instead of
registering with a discovery server. See `../INTERVIEW_TOPICS.md` for the
full topic checklist this maps to.

## Postgres and Kafka live outside the cluster, on purpose

`order-service` and `payment-service` need Postgres; `order-service`,
`payment-service`, and `notification-service` need Kafka. Neither is
deployed into minikube — both run on the host machine via
`docker compose up -d` (`../docker-compose.yml`). minikube should only ever
run microservice containers, not infra.

That means their `application-k8s.yml` points at `host.minikube.internal`
instead of `localhost` — from inside a Pod, `localhost` resolves to the Pod
itself, not your Mac. `host.minikube.internal` is minikube's built-in DNS
alias for the host machine (Docker driver only).

Kafka needs a **second port**, not just the same host alias: the
`docker-compose.yml` `kafka` service exposes `PLAINTEXT_HOST` on `9092`
(advertised as `localhost:9092`, for Mac-local processes) and a separate
`PLAINTEXT_K8S` on `9094` (advertised as `host.minikube.internal:9094`, for
Pods). One address can't serve both, because Kafka clients reconnect to
whatever address the broker *advertises* after the first handshake —
unlike Postgres, which is a plain single-hop connection. So
`application-k8s.yml` for the three Kafka-using services points at
`host.minikube.internal:9094`, not `:5432`-style port reuse.

Before applying any manifest that needs Postgres or Kafka, make sure both
are actually up:
```bash
docker compose up -d   # from the repo root
```
