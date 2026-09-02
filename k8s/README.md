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

## Postgres lives outside the cluster, on purpose

`order-service` and `payment-service` need Postgres, but Postgres itself is
**not** deployed into minikube — it runs on the host machine via
`docker compose up -d` (`../docker-compose.yml`). minikube should only ever
run microservice containers, not infra.

That means their `application-k8s.yml` points at
`host.minikube.internal:5432`, not `localhost:5432` — from inside a Pod,
`localhost` resolves to the Pod itself, not your Mac. `host.minikube.internal`
is minikube's built-in DNS alias for the host machine (Docker driver only).

Before applying `order-service.yaml` or `payment-service.yaml`, make sure
Postgres is actually up:
```bash
docker compose up -d   # from the repo root
```
