# Docker Compose support files

This folder holds configuration and initialization files used by the shared
infrastructure services in the root [docker-compose.yml](../docker-compose.yml).
The folder name is an organizational choice; Docker does not require it.

Microservices run in IntelliJ with the `local` profile now. Their Dockerfiles
live in their respective service folders, and their Kubernetes manifests live
in [k8s/](../k8s/) for future Jenkins deployments to Minikube. This folder does
not deploy microservices.

## Files

| File | Purpose |
| --- | --- |
| [postgres/init-databases.sql](postgres/init-databases.sql) | Creates the `order-srv-db`, `payment-srv-db`, and `keycloak` databases in the shared Postgres instance. |

| [keycloak/ecommerce-realm.json](keycloak/ecommerce-realm.json) | Initial realm, six confidential service clients, public PKCE client, demo users and role mappings. Imported only when the realm is absent. |
| [keycloak/verify-clients.py](keycloak/verify-clients.py) | Requests tokens for all six clients and verifies incorrect secrets are rejected. |

| [keycloak/configure-security.py](keycloak/configure-security.py) | Applies learning roles, users, mappers and service-account grants to the live realm. |
| [keycloak/security-smoke-test.py](keycloak/security-smoke-test.py) | Exercises real PKCE login, gateway validation, RBAC/ABAC and machine calls; creates sample data. |

## How the Postgres script is used

Compose mounts the script read-only inside the Postgres container:

```yaml
volumes:
  - ./docker/postgres/init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql:ro
```

The Postgres image executes initialization scripts in
`/docker-entrypoint-initdb.d/` only when starting with an empty database data
directory. This creates the application databases during the first setup.

- Restarting or recreating the container with the existing initialized volume
  does not run the script again.
- Editing this script does not update an existing database. Apply later
  database changes explicitly or through database migrations.
- This folder contains setup files, not live database data. Postgres data is
  stored in the Docker volume `workouts-postgres-data`.
- Do not delete the data volume just to rerun initialization; that would
  remove the existing databases.

## Related files

- [docker-compose.yml](../docker-compose.yml): starts shared Postgres, Redis,
  Kafka, Kafka UI, and Keycloak.
- [Infrastructure setup guides](../docs/infra-setup/README.md): setup, configuration,
  credentials, and microservice examples for all five Compose components.

Run Compose commands from the repository root, not this folder.
