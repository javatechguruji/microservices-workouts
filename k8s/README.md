# Application Kubernetes manifests

This directory owns application Deployments/Services, `namespace.yaml` and the
learning `service-client-credentials.yaml`. There are manifests for all nine Java
services. Gateway exposes NodePort; downstream Services are ClusterIP. The React
frontend has no Kubernetes manifest here.

Applications run in IntelliJ now; Jenkins will deploy them later. No pipeline is
implemented. Infrastructure servers stay in root Compose, shared by both profiles.
Application credentials do not require deploying Keycloak/PostgreSQL inside k8s.

Read [Minikube setup and the Jenkins contract](../docs/infra-setup/minikube-setup.md)
for profiles, images, secrets, infrastructure access and the required PGS image mount.
Read [Service DNS](../docs/kubernetes/service-discovery-and-routing.md)
and [replicas/probes](../docs/kubernetes/load-balancing-and-scaling.md)
for the implementation concepts.

Current tags such as `stage3`/`stage6` must be replaced with the pipeline's built
image references. There is no HPA, Ingress/TLS, NetworkPolicy or mTLS configuration.
ClusterIP alone does not enforce gateway-only access. Jenkins-only deployment
permissions will require pipeline credentials and Kubernetes RBAC when implemented.
