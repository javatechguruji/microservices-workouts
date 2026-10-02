# Minikube configuration for future Jenkins deployments

**Status:** application Dockerfiles/manifests and `k8s` profiles exist. A Jenkinsfile,
registry workflow and automated rollout are not implemented. Run applications in
IntelliJ now; this guide prepares the intended staging environment and defines the
future pipeline contract. No infrastructure server should be deployed into Minikube.

## 1. Prerequisites and existing cluster checks

Use Docker Desktop and the installed Minikube/kubectl tools:

```sh
docker info
minikube version
kubectl version --client
minikube status
```

If no cluster is running and you are ready to prepare staging, start it with
`minikube start --driver=docker`. Confirm the intended context with
`kubectl config current-context`; commands in these guides explicitly name
`--context=minikube` to avoid using a different cluster.

## 2. Shared infrastructure access

Initialize [shared-infra](README.md) first. Existing k8s profiles use:

| Dependency                   | From Minikube               |
| ---------------------------- | --------------------------- |
| PostgreSQL                   | host.minikube.internal:5432 |
| Kafka                        | host.minikube.internal:9094 |
| Keycloak token/JWK endpoints | host.minikube.internal:8180 |
| Redis, if integrated later   | host.minikube.internal:6379 |
| Application HTTP calls       | http://gateway-service:9100 |

Gateway routes to downstream Service DNS names such as `http://order-service:9101`.
Keep the canonical JWT issuer at `http://localhost:8180/realms/ecommerce` even
though the Pod uses another host to fetch keys. See [Keycloak setup](keycloak-setup.md).

Local and k8s instances share business data, topic groups and credentials. They
are two execution profiles, not independently isolated environments.

## 3. Product image mount

Before PGS rollout, keep this running from repository root in its own terminal:

```sh
minikube mount "$(pwd)/docker/product-images:/mnt/product-images"
```

The PGS manifest expects that directory on the node and mounts it read-only at
`/app/product-images`. This is single-node learning storage, not a cloud bucket
or portable multi-node volume design. The mount process must remain available.

## 4. Jenkins deployment contract

The future pipeline should:

1. Check out and test the selected independent Maven service with JDK 17.
2. Build its Dockerfile from that service directory with an immutable image tag.
3. Push to a registry reachable by Minikube, or load into its runtime from an agent
   with the required access.
4. Prepare `k8s/namespace.yaml` and application credentials. Existing
   `service-client-credentials.yaml` contains learning values; a real pipeline
   should inject its environment credentials.
5. Render the relevant Deployment with the built image reference and deploy through
   Jenkins. Existing `stage*` tags are placeholders/lab tags, not immutable releases.
6. Wait for rollout readiness and verify gateway-to-service and host-infrastructure access.

Jenkins needs access to the local cluster API and image destination. Repository
manifests alone do not enforce “Jenkins only”; cluster RBAC and deployment credentials
will establish that when implemented. No manual application apply commands are
required for the current IntelliJ workflow.

## 5. Inspect a deployed environment

After a pipeline deployment exists:

```sh
kubectl --context=minikube -n ecommerce get deployments,pods,services
kubectl --context=minikube -n ecommerce get endpointslices
kubectl --context=minikube -n ecommerce describe deployment product-aggregator-service
```

For a temporary local gateway connection, a separate terminal may run
`kubectl --context=minikube -n ecommerce port-forward service/gateway-service 9100:9100`.
Stop the local gateway first to free that port. React's default direct API URL can then
reach the forwarded gateway; the browser still uses local Keycloak. This is access
for a deployed app, not deployment automation or a production exposure pattern.

The gateway manifest currently uses NodePort; there is no Ingress/TLS, HPA or
NetworkPolicy configuration. Read [Kubernetes routing](../kubernetes/service-discovery-and-routing.md)
and [scaling/probe concepts](../kubernetes/load-balancing-and-scaling.md)
for the code-to-behavior explanation.
