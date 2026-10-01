# product-service

Product catalog microservice — part of the [microservices-workouts](../README.md) e-commerce system. Runs on port 9104.


## Local and staging execution

Run the application in IntelliJ with profile `local` (the default), or run
`mvn spring-boot:run -Dspring-boot.run.profiles=local` in this directory.
Local H2 data remains in `./data/productdb` relative to the working directory.

The multi-stage `Dockerfile` builds with Java 17 and exposes port 9104.
From the repository root, the future Jenkins build step can use:

```bash
docker build -t product-service:stage1 ./product-service
```

Jenkins should run tests before the image build, replace the example tag with
an immutable version, and deploy [the application manifest](../k8s/product-service.yaml).
The manifest selects `k8s`, exposes a ClusterIP Service at `product-service:9104`,
and configures startup, readiness, and liveness probes using Actuator.
No application container is added to Docker Compose.

Staging currently retains embedded H2 in a Pod `emptyDir`: data survives a
container restart in the same Pod but is lost when the Pod is replaced,
including during deployment. Keep one replica. `Recreate` avoids overlapping
old and new Pods with independent databases during rollout, at the cost of
downtime. The H2 console is disabled in `k8s`. External persistent storage is
a separate future change; this manifest does not deploy a database server.


## Security

Call application APIs through the gateway on port 9100 with a Keycloak access
token. The gateway validates JWTs and derives trusted `X-Auth-*` headers;
downstream controllers enforce roles, permissions and resource policies.
See [authentication and authorization](../docs/security/Authentication%20and%20Authorization%20at%20Microservice.md)
for browser login, machine calls and direct-header POC testing.
