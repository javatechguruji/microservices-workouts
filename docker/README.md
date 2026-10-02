# Shared infrastructure support files

Root [docker-compose.yml](../docker-compose.yml) starts infrastructure. This folder
holds seed/configuration helpers and local product images; it is not live database
storage and does not run the application microservices. Docker does not require
this folder name. Run helper/Compose commands from the repository root.

| Path                                                                         | Purpose                                                                       |
| ---------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| [postgres/init-databases.sql](postgres/init-databases.sql)                   | Creates eight business databases plus Keycloak on a fresh PostgreSQL volume   |
| [postgres/ensure-databases.py](postgres/ensure-databases.py)                 | Creates missing databases on an existing volume and applies guarded migration |
| [postgres/migrate-commerce.sql](postgres/migrate-commerce.sql)               | Extends an existing order-status constraint for fulfillment states            |
| [keycloak/ecommerce-realm.json](keycloak/ecommerce-realm.json)               | Seed realm, nine machine clients, public UI client, roles and demo users      |
| [keycloak/configure-security.py](keycloak/configure-security.py)             | Reconciles the live realm; preserves existing human passwords                 |
| [keycloak/verify-clients.py](keycloak/verify-clients.py)                     | Checks machine token grants and rejects wrong secrets/human roles             |
| [keycloak/reset-learning-passwords.py](keycloak/reset-learning-passwords.py) | Explicitly resets human learning passwords to usernames                       |
| [keycloak/security-smoke-test.py](keycloak/security-smoke-test.py)           | Real login and gateway/downstream policy checks                               |
| [keycloak/commerce-smoke-test.py](keycloak/commerce-smoke-test.py)           | Checkout, inventory, payment and event integration checks                     |
| [product-images/](product-images/)                                           | Local SVG assets read by PGS; future bucket replacement point                 |

PostgreSQL init SQL and Keycloak realm import are first-initialization mechanisms,
not ongoing synchronization. Named Docker volumes hold database/broker/Redis data;
editing seed files does not reset that data. Use the matching helper and preserve
volumes when applying changes.

[Setup guides](../docs/infra-setup/README.md) own installation and credentials.
[Concept guides](../docs/README.md) explain the service implementations. Microservice
Dockerfiles live in each service; [k8s](../k8s/README.md) holds future deployment inputs.
