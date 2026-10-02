# Commerce application setup

This guide starts the shopping application against the existing **shared-infra**
Docker Compose project. No application is added to Compose. Infrastructure stays
on Docker Desktop for both IntelliJ and future Jenkins/Minikube deployments.
See [the learning guide](../project-docs/shopping-and-fulfillment.md) for implementation
and [manual verification](../project-docs/manual-verification.md) for business tests; [Keycloak setup](keycloak-setup.md) contains credentials.

## 1. Start shared infrastructure

Follow [first startup or existing-volume upgrade](README.md#first-startup-or-existing-volume-upgrade).
For an already initialized environment, run `docker compose up -d` from the repository root.

`ensure-databases.py` creates only missing databases and applies the guarded
`migrate-commerce.sql` update to an existing order-status CHECK constraint; it never
drops business data. Updating
`init-databases.sql` alone does not affect an existing PostgreSQL volume. Do not
reset that volume. For a completely new machine, follow the existing
[infra setup index](README.md) first, including external volume creation.

## 2. Applications and storage

| IntelliJ project           | Port | PostgreSQL database           |
| -------------------------- | ---- | ----------------------------- |
| gateway-service            | 9100 | None                          |
| order-service              | 9101 | order-srv-db                  |
| payment-service            | 9102 | payment-srv-db                |
| notification-service       | 9103 | notification-service-db       |
| product-aggregator-service | 9104 | product-aggregator-service-db |
| inventory-service          | 9105 | inventory-service-db          |
| customer-service           | 9106 | customer-service-db           |
| product-discount-service   | 9107 | product-discount-service-db   |
| rating-service             | 9108 | rating-service-db             |

Every service owns its database even though they share one PostgreSQL container.
The local PostgreSQL credential is maintained in [PostgreSQL setup](postgres-setup.md).
All eight business-service databases use PostgreSQL; H2 is limited to applicable tests.

Import the renamed `product-aggregator-service/pom.xml` in IntelliJ and remove the
old `product-service` module reference from your IDE if it persists. Import customer-service, product-discount-service and rating-service as Maven projects as well. Use **JDK 17** for every service.

## 3. Local image store

Ten original SVG product illustrations are committed under `docker/product-images`.
PGS reads those files at runtime. With no override, it discovers `docker/product-images`
from the workspace root or `../docker/product-images` from a service folder. For
a different working directory, set IntelliJ's environment variable to the absolute path:

```text
PRODUCT_IMAGE_DIR=/absolute/path/to/microservices-workouts/docker/product-images
```

PGS logs the resolved image directory at startup. A missing directory now produces
a clear startup error instead of silently serving image 404s. An explicit
`PRODUCT_IMAGE_DIR` must exist; it never silently falls back to another directory.
Restart PGS after changing the path or updating this code. The k8s profile continues
to use its explicit `/app/product-images` mount.

PGS exposes images through `/api/products/images/FILE.svg`; the UI accesses that
path directly through gateway. Images are public product assets; profile and
catalog APIs are not public. Replace an SVG locally to experiment, and hard-refresh
the browser because images have a one-hour cache lifetime. A bucket is not needed.

## 4. Run the application

For each service, import its `pom.xml`, open the class annotated with
`@SpringBootApplication`, and create a Spring Boot run configuration. Select JDK 17,
Active profiles `local` (or environment `SPRING_PROFILES_ACTIVE=local`) and the
service directory as working directory. Check for stale environment overrides.

Start all nine applications. Prefer customer,
discount, rating, inventory, PGS, notification, payment, order, then gateway; all
must be ready before the full walkthrough. Restart services after source changes.

Alternatively, in each service's own terminal:

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The renamed PGS main class is `ProductAggregatorServiceApplication`. Gateway
routes and the Keycloak client use `product-aggregator-service`, not the old name.
Its existing learning secret was retained; the new services have their own clients.

Start React in another terminal:

```sh
cd ecom-ui
npm ci
npm run dev
```

Open **http://localhost:5173/**. Use `customer1` for shopping and `admin1` for
fulfillment; passwords are in [Application roles and users](keycloak-setup.md#application-roles-and-users).
New users can click **Create an account** and finish their profile in the portal.

Before the walkthrough, verify readiness (all nine Java services must be started):

```sh
for port in 9100 9101 9102 9103 9104 9105 9106 9107 9108; do
  curl --fail --silent --show-error "http://localhost:$port/actuator/health"
done
```

Expect health responses reporting UP after dependencies are ready. Health checks
are diagnostic direct-port calls; use gateway for business API requests.

## 5. Database initialization and configuration

Service-owned `schema.sql` files create tables and seed products, discounts,
ratings and stock with `ON CONFLICT DO NOTHING`; restarts do not reset purchases
or replenish inventory. Customer profile tables initially contain no profiles; existing saved profiles survive restarts. Order/payment keep JPA for
existing entities and use JDBC tables for the saga/outbox. This local learning
setup uses JPA schema update plus idempotent SQL initialization, not Flyway.

To inspect stock without changing it:

```sh
docker exec postgres psql -U postgres -d inventory-service-db -c 'SELECT * FROM stock ORDER BY sku'
docker exec postgres psql -U postgres -d order-srv-db -c 'SELECT order_id,state,attempts,error FROM checkout ORDER BY order_id DESC LIMIT 20'
docker exec postgres psql -U postgres -d order-srv-db -c 'SELECT event_id,published FROM commerce_outbox ORDER BY order_id DESC LIMIT 20'
docker exec postgres psql -U postgres -d payment-srv-db -c 'SELECT * FROM payment_outbox ORDER BY order_id DESC LIMIT 20'
```

Optional learning stock replenishment (explicitly adds 20 units):

```sh
docker exec postgres psql -U postgres -d inventory-service-db -c "UPDATE stock SET available=available+20 WHERE sku='ELEC-1'"
```

Ratings live in `rating-service-db.product_rating`; percentages in
`product-discount-service-db.product_discount`. Updating these records affects
future quotes; existing order items keep their original price snapshots.

## 6. Verification commands

With all applications running, from the workspace root:

```sh
python3 docker/keycloak/security-smoke-test.py
python3 docker/keycloak/commerce-smoke-test.py
cd ecom-ui
npm test
npm run build
npm run test:e2e
```

Install Chromium once with `npx playwright install chromium` if needed. Browser
checks include shopping, profiles, self-registration, simulated payment, shipping,
owner/tenant denial, refresh and mobile layout. Commerce checks cover concurrent
idempotent checkout, authoritative prices, stock rollback/release, payment retries,
and Kafka-backed notifications/preferences. Tests leave learning records in place.

## 7. Future Jenkins/Minikube configuration

Use [Minikube setup and the Jenkins contract](minikube-setup.md) for cluster
prerequisites, shared-infrastructure addresses, the PGS image mount and deployment
secrets. Application deployment remains future work; all server infrastructure
stays in Compose.

## 8. Troubleshooting

| Symptom                      | Check                                                                                     |
| ---------------------------- | ----------------------------------------------------------------------------------------- |
| Catalog unavailable          | PGS/customer/discount services and their Keycloak permissions; restart old gateway builds |
| Image 404                    | `PRODUCT_IMAGE_DIR`, filename, or the Minikube mount                                      |
| Checkout 400                 | Profile complete, address at least 10 characters, unique SKUs, quantities 1–99            |
| Checkout 409                 | Prices changed or idempotency key reused with a different cart; revisit shop/cart         |
| Order remains Reserved       | Payment service/gateway/Keycloak logs; retained state retries safely                      |
| Order remains Paid           | Inventory commit dependency; do not manually release a paid reservation                   |
| No notification              | Kafka health, outbox `published`, notification consumer logs                              |
| Profile choices do not lead  | Explicit choices precede purchase history; reload Shop after saving                       |
| Other-tenant shopping denied | Integrated checkout/catalog machine chain is intentionally scoped to demo                 |

## 9. Targeted Java verification

Use JDK 17. These checks do not need all applications running; full API/browser
smoke tests above do. See [test-layer explanations](../testing/Junit_Test_Guide.md) for what
each proves and what it leaves to integration verification.

```sh
mvn -f gateway-service/pom.xml -Dtest=IdentityHeadersFilterTest test
mvn -f order-service/pom.xml -Dtest=OrderAuthorizationTest,OrderInputValidationTest test
mvn -f payment-service/pom.xml -Dtest=PaymentServiceImplTest,PaymentControllerTest,OrderServiceClientTest,PaymentRepositoryTest test
mvn -f product-aggregator-service/pom.xml -Dtest=CatalogControllerTest test
```

## 10. Frontend configuration

The [package manifest](../../ecom-ui/package.json) requires Node >=22.12.0. Use a
compatible installed Node version, then run the startup commands in section 4.
Vite serves React at `http://localhost:5173`. The browser calls gateway directly
at `http://localhost:9100`; gateway permits the UI origin with CORS. No Vite API
proxy is configured. See [CORS implementation and tests](../api-gateway/09-cors-preflight-and-browser-security.md).

Defaults are in [vite.config.js](../../ecom-ui/vite.config.js) and
[auth.js](../../ecom-ui/src/auth.js). For overrides, copy
[.env.example](../../ecom-ui/.env.example) to `ecom-ui/.env.local` and restart Vite:

```dotenv
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=ecommerce
VITE_KEYCLOAK_CLIENT_ID=security-demo-ui
VITE_GATEWAY_URL=http://localhost:9100
```

`VITE_*` values are public browser configuration; never put machine secrets in them.
The client ID remains `security-demo-ui` even though the application is named ecom-ui.
Its callback must match Keycloak exactly. `npm run preview` uses the same UI port
after a build; stop the dev server first. Preview is not production hosting.
`VITE_GATEWAY_URL` is baked into the build; rebuild when changing it.

Each gateway profile defines its own CORS allowlist, currently defaulting to
`CORS_ALLOWED_ORIGINS=http://localhost:5173` because the UI still runs locally. Set this environment
variable on the gateway process (IntelliJ Run Configuration or Kubernetes Deployment)
for other exact UI origins, comma-separated, and restart/redeploy gateway. Use origins
without paths or trailing slashes. The gateway URL must be reachable by the browser;
a Kubernetes service name such as `gateway-service` is not a browser-facing address.
For local minikube access, port-forward gateway to 9100 as described in
[minikube setup](minikube-setup.md). Keycloak redirect URIs and Web Origins remain
separate configuration in [Keycloak setup](keycloak-setup.md).
