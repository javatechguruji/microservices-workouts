# Keycloak setup and client credentials

Back to the [infrastructure index](README.md). Added September 30, 2026.
Keycloak runs in Docker Desktop under **shared-infra → keycloak**.
It authenticates application users and service accounts; the microservices themselves still run in
IntelliJ now and will be deployed through Jenkins to Minikube later.

## Installed configuration

| Setting | Value |
| --- | --- |
| Image | `quay.io/keycloak/keycloak:26.7.4` |
| Compose service / container | `keycloak` / `keycloak` |
| Command | `start-dev --import-realm` |
| Admin console | `http://localhost:8180/admin/` |
| Bootstrap admin username | `admin` |
| Bootstrap admin password | `WorkoutsKeycloak-Admin-2026!` |
| Application realm | `ecommerce` |
| Canonical issuer | `http://localhost:8180/realms/ecommerce` |
| Published port | `8180:8080` |
| Database | `keycloak` in the existing `postgres` container |
| Database user / password | `postgres` / `postgres` |
| Data persistence | Existing `workouts-postgres-data` volume |
| Health check | `/health/ready` on internal management port 9000 |

This is an HTTP development-mode installation for your trusted learning network.
The development credentials are recorded here at your request. These are learning credentials, including the demo user passwords below. [Official container setup](https://www.keycloak.org/server/containers).

## Client IDs and secrets

Each of the six microservice clients is a confidential OpenID Connect client with client-secret
authentication and **Service accounts roles** enabled. Standard/browser flow,
implicit flow and direct access/password grants are disabled. There are no
redirect URIs because client credentials does not redirect a browser.

| Client ID | Client secret |
| --- | --- |
| `gateway-service` | `VgfMPYo8VgzFhv5W8TwGakdmMuzeo93x4zWg5dirO-Q` |
| `order-service` | `LBOkQHGD24s1Wg7L7Bl4iul03PWxgZ0p7oNR-LMmIQ4` |
| `payment-service` | `DN_T6e4herop-EqSOWtVB85PNY1UEddm9MofHQzP_r0` |
| `notification-service` | `KwwxLsvQEF52XsTegnwdQR6FDn0hsN6ofINTRa_0qfw` |
| `product-service` | `ZRaZxGAPOI1QpmUUpcQpTmbwFWgJ91C71ckGAFNVl0M` |
| `inventory-service` | `HCtS7tvEExUtwxHHDdzP9ry_2x1IE7ofrx4SyzTQBWM` |

The same client registrations serve both local and Minikube instances. They are
seeded from [ecommerce-realm.json](../../docker/keycloak/ecommerce-realm.json).
Secrets were generated independently. Do not use the admin account as an
application client. Client credentials identifies the calling service, not a
human user, and normally obtains a new access token instead of a refresh token.
[Keycloak service accounts](https://www.keycloak.org/docs/latest/server_admin/#_service_accounts).

The gateway now validates access tokens with audience `gateway-service` and
forwards trusted identity headers. Downstream services enforce the roles and
permissions described below. See the [implemented security guide](../security/Authentication%20and%20Authorization%20at%20Microservice.md).

### Application roles and users

`customer` and `admin` are composite business realm roles. `service` is a marker
role with separately assigned least-privilege permission roles. These are not
Keycloak administrative roles. Colon-named realm roles represent permissions;
this POC does not use UMA or the Keycloak Authorization Services policy engine.

| Role | Permissions |
| --- | --- |
| `customer` | `orders:create`, `orders:read`, `payments:create`, `payments:read`, `products:read` |
| `admin` | Customer permissions plus `orders:read:any`, `orders:update`, `payments:read:any`, `products:write`, `inventory:read`, `inventory:write`, `notifications:send` |
| `service` | No implicit permissions; see per-client grants below |

| Service client | Explicit permissions (in addition to role `service`) |
| --- | --- |
| `gateway-service` | `products:read` |
| `order-service` | `inventory:read`, `products:read` |
| `payment-service` | `orders:read:any` |
| `notification-service` | `orders:read:any` |
| `product-service` | `inventory:read` |
| `inventory-service` | `products:read` |

All service accounts use tenant `demo`. Application users:

| Username | Password | Role | Tenant |
| --- | --- | --- | --- |
| `customer1` | `Workouts-customer1-2026!` | `customer` | `demo` |
| `customer2` | `Workouts-customer2-2026!` | `customer` | `demo` |
| `admin1` | `Workouts-admin1-2026!` | `admin` | `demo` |
| `othercustomer` | `Workouts-othercustomer-2026!` | `customer` | `other` |

Tenant is an admin-editable attribute mapped into access tokens. Users cannot
edit it through their account profile after the security setup script runs.
Gateway audience is added by the `gateway-audience` protocol mapper.

### Browser client (React application)

- Client ID: `security-demo-ui`; public client, **no secret**.
- Flow: Authorization Code + PKCE S256; password and implicit grants disabled.
- Redirect URI: `http://localhost:5173/`.
- Web origin: `http://localhost:5173`.
- Post-logout redirect: the same demo page.

Run all local services and the [React frontend](../../ecom-ui/README.md),
open the React URL and click **Sign in to your account**.
Use one of the user rows above. The application opens a customer or administrator dashboard, with order
creation, lists, details and role-appropriate actions. API-level spoofing and
machine-call checks remain in the security test guides.


## Installation and database initialization

The root [Compose file](../../docker-compose.yml) already includes Keycloak.
Its environment points at `jdbc:postgresql://postgres:5432/keycloak`. The realm
JSON is mounted read-only at `/opt/keycloak/data/import/ecommerce-realm.json`.

For a fresh Docker environment, from the repository root:

```bash
open -a Docker
# Wait until Docker Desktop is ready.
docker volume create workouts-postgres-data
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U postgres -d postgres
```

The [Postgres initialization script](../../docker/postgres/init-databases.sql)
now creates `keycloak` in addition to the order/payment databases on an empty
volume. For an existing initialized volume, check for the new database:

```bash
docker compose exec -T postgres psql -U postgres -d postgres -Atc "SELECT datname FROM pg_database WHERE datname = 'keycloak';"
```

If that returns no row, run this once (it was already performed on this machine):

```bash
docker compose exec -T postgres createdb -U postgres keycloak
```

Then start Keycloak without restarting the running Postgres container:

```bash
docker compose config --quiet
docker compose up -d --no-deps --wait --wait-timeout 240 keycloak
docker compose ps keycloak
docker compose logs --tail=100 keycloak
```

`--no-deps` assumes Postgres is already running and ready. Normal subsequent
stack startup can use `docker compose up -d`. Initial image download/schema
creation can take time; retry the readiness checks after startup if needed.

Realm import creates the realm only if absent. It **skips an existing realm**,
so restarting does not overwrite client changes or rotate credentials. Admin
bootstrap credentials apply when the master realm is first initialized.
[Realm import behavior](https://www.keycloak.org/server/importExport).

## Local startup checklist for security testing

Complete the installation above and the
[PostgreSQL](postgres-setup.md), [Kafka](kafka-setup.md) and
[Redis](redis-setup.md) setup guides first. Docker Compose contains infrastructure
only. For an already initialized workspace, start Docker Desktop, then run from
the repository root:

```bash
docker compose up -d
docker compose ps
curl --fail http://localhost:8180/realms/ecommerce/.well-known/openid-configuration
python3 docker/keycloak/configure-security.py
python3 docker/keycloak/verify-clients.py
```

Wait for Keycloak and Redis to report healthy before running the checks. If the
realm discovery request fails while Keycloak is starting, wait and retry it.
The configuration script applies the setup to an existing realm; a container
restart alone does not apply changed realm imports. Existing users keep their
passwords, so the user table assumes you have not changed those learning defaults.
Do not delete persistent volumes to resolve startup issues.

For each microservice, open its `*Application.java` in IntelliJ and create a
Spring Boot run configuration with **JDK 17**, **Active profiles = local** (or
`SPRING_PROFILES_ACTIVE=local`), and the module directory as its working directory.
Run product-service (9104), inventory-service (9105), order-service (9101),
notification-service (9103), payment-service (9102), then gateway-service (9100).
The local profiles already contain the learning infrastructure and order/payment
client credential defaults. Check for stale environment overrides if a service
connects to the wrong host or rejects its credentials. Product and inventory use
local embedded databases. Neither Minikube nor Jenkins is required for this run.

Start the React frontend in another terminal (Node.js 22.12+ in the 22.x line,
or a newer supported LTS):

```bash
cd ecom-ui
npm ci
npm run dev
```

Open **http://localhost:5173/**. The frontend is not part of Docker Compose.
For configuration, build and browser test commands, see the
[React project guide](../../ecom-ui/README.md). The old gateway static demo
was removed; rebuild gateway with `mvn clean package` using JDK 17 and restart it
to remove those files from previously built artifacts.

Use application user `admin1` for admin API tests; Keycloak console user `admin`
is a separate administration account. Open the browser demo with `localhost`,
not `127.0.0.1` or a file URL, so it matches the registered redirect URI.
Password-grant login is disabled; users sign in with Authorization Code + PKCE.

After setup, follow the [gateway manual verification guide](../01-api-gateway/05-gateway-security-oauth2-oidc-jwt.md#manual-verification--intellij-local-profile)
for browser login, RBAC, header replacement, ownership and tenant tests, including
expected HTTP results. Those steps exercise application security; they do not
create users or configure Keycloak.

## Verification completed

On September 30, 2026, Keycloak became healthy in `shared-infra`. The admin login
and all six live service-account configurations were verified. Every client
issued a token with the expected issuer/client claims and rejected an incorrect
secret. The same checks passed again after a Keycloak container restart.
Minikube connectivity was not tested. Application security is now implemented;
its checks and usage are described in the security guide.

## Apply the application security configuration

After initial realm import, and whenever updating the seed's security setup:

```bash
python3 docker/keycloak/configure-security.py
```

This reconciles clients, role composites, demo users, service-account role grants,
audience/tenant mappers, and the user-profile rule restricting tenant edits to
admins. It does not delete the realm or reset existing user passwords. It grants
roles additively; remove obsolete grants manually when experimenting with policy
changes. Existing client secrets remain the values documented above.

## Verify the realm and six clients

```bash
curl --fail http://localhost:8180/realms/ecommerce/.well-known/openid-configuration
python3 docker/keycloak/verify-clients.py
```

The verification script requests a token for every registered service, checks
issuer/client claims, and verifies that an incorrect secret is rejected. It
prints no access tokens or client secrets. Its JWT decoding is diagnostic;
application resource servers must cryptographically verify JWT signatures.

In the admin console, sign in, select realm **ecommerce**, then **Clients**.
Open a microservice client and inspect **Settings**, **Credentials**, and
**Service accounts roles**. The realm also has Keycloak built-in clients;
the six service clients and public `security-demo-ui` client are additional registrations.

## Request a token: curl or Postman

Choose a row from the credentials table. Example for order-service:

```bash
export CLIENT_ID='order-service'
export CLIENT_SECRET='LBOkQHGD24s1Wg7L7Bl4iul03PWxgZ0p7oNR-LMmIQ4'
curl --fail-with-body -X POST \
  http://localhost:8180/realms/ecommerce/protocol/openid-connect/token \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "client_secret=$CLIENT_SECRET"
```

Expected: JSON containing `access_token`, `token_type: Bearer`, and
`expires_in`. In Postman select **OAuth 2.0 → Client Credentials**, use the same
token URL and a client row above, and send client credentials in the request
body. No authorization URL, callback URL, or human user login is needed.

Call the gateway with `Authorization: Bearer <access_token>`. It is the OAuth2
resource server; downstream services authorize its trusted headers without
validating tokens themselves. Permissions limit which endpoints a client can call.

## IntelliJ and Minikube access: one instance, one issuer

| Client location | Token endpoint |
| --- | --- |
| IntelliJ / Postman on Mac | `http://localhost:8180/realms/ecommerce/protocol/openid-connect/token` |
| Minikube application | `http://host.minikube.internal:8180/realms/ecommerce/protocol/openid-connect/token` |
| Docker infrastructure diagnostic client | `http://keycloak:8080/realms/ecommerce/protocol/openid-connect/token` |

`KC_HOSTNAME=http://localhost:8180` fixes the token issuer to
`http://localhost:8180/realms/ecommerce` for all clients.
`KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` permits backchannel endpoints to reflect
the reachable request address. The admin console is intended to be opened on
the Mac. [Keycloak hostname configuration](https://www.keycloak.org/server/hostname).

Do not change the expected issuer to `host.minikube.internal` in a resource
server: it would no longer match the token's `iss`. Instead, configure the
canonical issuer plus a separately reachable JWK URL, as shown below. In a real
shared network deployment, a common DNS name and HTTPS would replace this
local-host arrangement.

## Implemented microservice integration

The gateway is a reactive JWT resource server. It validates signature, issuer,
expiry/not-before and audience. It replaces incoming `X-Auth-*` headers with
validated subject, username, tenant, roles, permissions and client ID, then
removes the Bearer token before forwarding.

Order/payment use their own `ServiceTokenClient` implementations to obtain and
cache machine tokens with HTTP timeouts. They call the gateway rather than the
destination service directly. Their local profiles contain the documented
learning secrets as defaults, overrideable through `KEYCLOAK_CLIENT_SECRET`.
The k8s profile uses application Secret `service-client-credentials` from
`k8s/service-client-credentials.yaml`. No Keycloak server is deployed into k8s.

Canonical issuer: `http://localhost:8180/realms/ecommerce`. JWK/token endpoint
hosts are `localhost:8180` locally and `host.minikube.internal:8180` for k8s.
Downstream services do not configure resource-server JWT validation in this POC.

See [gateway security](../01-api-gateway/05-gateway-security-oauth2-oidc-jwt.md)
and [microservice implementation/testing](../security/Authentication%20and%20Authorization%20at%20Microservice.md)
for exact routes, policy checks and test commands. Browser credentials are
maintained in the application users table in this setup guide.

## Operations and secret changes

```bash
docker compose logs -f keycloak
docker compose stop keycloak
docker compose up -d --no-deps --wait keycloak
docker compose restart keycloak
```

Keycloak realms, clients, keys and service accounts persist in Postgres across
container recreation. The import JSON is a reproducible initial seed, not a
live configuration synchronizer or a database backup.

To rotate a client secret, use **Clients → client → Credentials → Regenerate**,
then update the credentials table, realm seed, and application secret together.
Do not delete the realm/database to rotate a secret. Editing only this guide or
seed does not change the registered live secret. Rerun the verification script
after updating the seed to match the actual live credentials.

## Troubleshooting

- **Database keycloak missing:** create it once in the existing Postgres; old
  volumes do not rerun the init script.
- **invalid_client:** check realm, exact client ID/secret and client authentication.
- **unauthorized_client:** check both the client secret and service-account enablement; Keycloak also uses this error for invalid credentials.
- **Realm unchanged after editing seed:** startup import skips existing realms;
  apply intentional changes through the admin console/API.
- **Issuer mismatch:** compare the token `iss` with the canonical issuer above.
- **Signing keys unreachable in Minikube:** use its host-access JWK URL, not Pod localhost.
- **401/403 from your API:** token issuance and API security are separate; check
  signature, expiry, issuer, audience and the API's permission rules.
- **Port 8180 in use:** check `lsof -nP -iTCP:8180 -sTCP:LISTEN` before changing
  port mapping; also update `KC_HOSTNAME`, endpoints and expected issuer.
- **Admin password change did not apply:** bootstrap variables do not reset an
  existing admin account; change it through the account/admin flow.

Minikube pod connectivity is not asserted by local token tests. Check it when
staging is running. No Keycloak server manifests are added under `k8s/`.
