# Keycloak setup and credentials

Keycloak runs as `shared-infra → keycloak`. It stores identities in shared
PostgreSQL. This guide owns client/user configuration and learning credentials.
For the request flow and code, read [gateway authentication](../api-gateway/03-gateway-security-oauth2-oidc-jwt.md).

## Installed configuration

| Setting           | Repository value                                               |
| ----------------- | -------------------------------------------------------------- |
| Image / command   | `quay.io/keycloak/keycloak:26.7.4`; `start-dev --import-realm` |
| Admin console     | http://localhost:8180/admin/                                   |
| Console account   | `admin` / `admin`                                              |
| Application realm | `ecommerce`                                                    |
| Canonical issuer  | `http://localhost:8180/realms/ecommerce`                       |
| Database          | `keycloak`; PostgreSQL user/password `postgres` / `postgres`   |
| Persistence       | `workouts-postgres-data`                                       |
| Health            | Internal management port 9000, `/health/ready`                 |

This is an HTTP development-mode installation. Application administrator `admin1`
and Keycloak console administrator `admin` are separate accounts.

## Client IDs and secrets

Each of the nine microservice clients is a confidential OpenID Connect client with client-secret
authentication and **Service accounts roles** enabled. Standard/browser flow,
implicit flow and direct access/password grants are disabled. There are no
redirect URIs because client credentials does not redirect a browser.

| Client ID                    | Client secret (learning only)                 |
| ---------------------------- | --------------------------------------------- |
| `gateway-service`            | `VgfMPYo8VgzFhv5W8TwGakdmMuzeo93x4zWg5dirO-Q` |
| `order-service`              | `LBOkQHGD24s1Wg7L7Bl4iul03PWxgZ0p7oNR-LMmIQ4` |
| `payment-service`            | `DN_T6e4herop-EqSOWtVB85PNY1UEddm9MofHQzP_r0` |
| `notification-service`       | `KwwxLsvQEF52XsTegnwdQR6FDn0hsN6ofINTRa_0qfw` |
| `product-aggregator-service` | `ZRaZxGAPOI1QpmUUpcQpTmbwFWgJ91C71ckGAFNVl0M` |
| `inventory-service`          | `HCtS7tvEExUtwxHHDdzP9ry_2x1IE7ofrx4SyzTQBWM` |
| `customer-service`           | `Workouts-customer-service-2026!`             |
| `product-discount-service`   | `Workouts-product-discount-service-2026!`     |
| `rating-service`             | `Workouts-rating-service-2026!`               |

The same client registrations serve both local and Minikube instances. They are
seeded from [ecommerce-realm.json](../../docker/keycloak/ecommerce-realm.json).
Do not use the admin account as an
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

| Role       | Permissions                                                                                                                                                    |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `customer` | `orders:create`, `orders:read`, `payments:create`, `payments:read`, `products:read`, `customers:write`                                                         |
| `admin`    | Customer permissions plus `orders:read:any`, `orders:update`, `payments:read:any`, `products:write`, `inventory:read`, `inventory:write`, `notifications:send` |
| `service`  | No implicit permissions; see per-client grants below                                                                                                           |

| Service client               | Explicit permissions (in addition to role `service`)                                                                                                      |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `gateway-service`            | `products:read`                                                                                                                                           |
| `order-service`              | `products:read`, `payments:read:any`, `payments:create`, `catalog:quote`, `inventory:reserve`, `inventory:read`, `inventory:commit`, `customers:read:any` |
| `payment-service`            | `orders:read:any`                                                                                                                                         |
| `notification-service`       | `orders:read:any`                                                                                                                                         |
| `product-aggregator-service` | `ratings:read`, `inventory:read`, `customers:read:any`, `discounts:read`                                                                                  |
| `inventory-service`          | `products:read`                                                                                                                                           |
| `customer-service`           | No outbound HTTP permission required                                                                                                                      |
| `product-discount-service`   | No outbound HTTP permission required                                                                                                                      |
| `rating-service`             | No outbound HTTP permission required                                                                                                                      |

All service accounts use tenant `demo`. Application users:

| Username        | Password        | Role       | Tenant  |
| --------------- | --------------- | ---------- | ------- |
| `customer1`     | `customer1`     | `customer` | `demo`  |
| `customer2`     | `customer2`     | `customer` | `demo`  |
| `admin1`        | `admin1`        | `admin`    | `demo`  |
| `othercustomer` | `othercustomer` | `customer` | `other` |

Tenant is an admin-editable attribute mapped into access tokens. Users cannot
edit it through their account profile after the security setup script runs.
Gateway audience is added by the `gateway-audience` protocol mapper.

### Browser client (React application)

- Client ID: `security-demo-ui`; public client, **no secret**.
- Flow: Authorization Code + PKCE S256; password and implicit grants disabled.
- Redirect URI: `http://localhost:5173/`.
- Web origin: `http://localhost:5173`.
- Post-logout redirect: the same demo page.

## Installation and database initialization

Follow [first infrastructure startup](README.md#first-startup-or-existing-volume-upgrade)
so the `keycloak` database exists before startup. For Keycloak alone after PostgreSQL
is ready:

```sh
python3 docker/postgres/ensure-databases.py
docker compose up -d --no-deps --wait --wait-timeout 240 keycloak
curl --fail http://localhost:8180/realms/ecommerce/.well-known/openid-configuration
```

The realm seed is mounted from
[ecommerce-realm.json](../../docker/keycloak/ecommerce-realm.json). Import creates an
absent realm and skips an existing one. Changing JSON and restarting is not a live
realm update. Bootstrap admin environment variables do not reset an existing admin.

## Apply the application security configuration

```sh
python3 docker/keycloak/configure-security.py
python3 docker/keycloak/verify-clients.py
```

The reconciler updates clients/mappers from the seed, creates missing users,
assigns permission composites and service grants, enables public registration,
and restricts tenant editing to administrators. Existing human passwords are
preserved. The retired `product-service` client is disabled.

Role grants are mostly additive; remove obsolete permissions explicitly when
changing a policy. The script specifically removes human default/customer/admin
grants from machines and the default-role composite from the seeded admin. The
verifier checks machine tokens do not inherit human roles. Do not treat a seed edit
as proof a live realm is configured until reconciliation and checks succeed.

If console credentials differ, set `KEYCLOAK_ADMIN_USER` and
`KEYCLOAK_ADMIN_PASSWORD` for these scripts using the actual console account.
Verification decodes diagnostic claims; the gateway performs cryptographic validation.

## Registration and profile configuration

New public registrations receive customer permissions, not administrator access.
Keycloak captures login identity/name/email; the portal separately saves email,
phone, DOB and category choices in customer-service. Checkout requires that profile.

For public customer tokens without a tenant attribute, gateway assigns `demo` only
when client is `security-demo-ui` and role is customer without admin. Explicit
tenant attributes take precedence; admin/service tokens require their configured
tenant. Users cannot select a tenant themselves.

## Request a token: curl or Postman

Use the client secret from the table above. This machine request does not use a
human username/password or browser redirect:

```sh
CLIENT_ID='order-service'
CLIENT_SECRET='COPY_ORDER_SERVICE_SECRET_FROM_TABLE'
curl --fail-with-body -X POST   http://localhost:8180/realms/ecommerce/protocol/openid-connect/token \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "client_secret=$CLIENT_SECRET"
unset CLIENT_SECRET
```

Expect `access_token`, `token_type` and `expires_in`. In Postman use OAuth 2.0,
Client Credentials, this token URL and credentials in the request body. Pass the
returned token to gateway as `Authorization: Bearer ACCESS_TOKEN`; do not send
client secrets to inventory/order APIs.

Human users use the React **Sign in to your account** button and Code+PKCE. Direct
password grants are disabled on application clients. The scripts' master-realm
`admin-cli` login is a separate administrative operation.

## IntelliJ and Minikube access: one instance, one issuer

| Setting              | IntelliJ                               | Minikube                    |
| -------------------- | -------------------------------------- | --------------------------- |
| Token URL host       | localhost:8180                         | host.minikube.internal:8180 |
| Gateway JWK URL host | localhost:8180                         | host.minikube.internal:8180 |
| Expected issuer      | http://localhost:8180/realms/ecommerce | Same canonical issuer       |

Compose fixes `KC_HOSTNAME=http://localhost:8180` and enables dynamic backchannel
addresses. Gateway profiles configure a separately reachable JWK URL. Replacing
the expected issuer with the host-access alias would mismatch the token's `iss`.
The browser still logs in on the Mac's localhost URL. Minikube connectivity must
be verified in the actual cluster; local token tests do not prove it.

Order/payment/PGS read their client secret from local defaults or
`KEYCLOAK_CLIENT_SECRET`. Future Deployments reference the
[application secret](../../k8s/service-client-credentials.yaml). Registering all
nine service clients does not mean every service currently makes outbound calls.

## Easy local learning passwords

The credential table is the reference for demo passwords. Existing self-registered
human learning users can also be reset to their username; future registrations
still choose their own password. To deliberately reset existing human accounts:

```sh
python3 docker/keycloak/reset-learning-passwords.py
python3 docker/keycloak/security-smoke-test.py --login-only
```

This resets application humans and the selected console administrator, verifies
console login, and leaves machine secrets unchanged. It does not weaken password
policies. Supply the current console password through `KEYCLOAK_ADMIN_PASSWORD`
if it differs. Normal reconciliation does not reset existing passwords.

## Operations and troubleshooting

```sh
docker compose ps keycloak
docker compose logs --tail=100 keycloak
docker compose restart keycloak
```

| Symptom                           | Check                                                                    |
| --------------------------------- | ------------------------------------------------------------------------ |
| Database missing                  | Run the database helper; preserve existing volume                        |
| Realm changes absent              | Run reconciliation; startup import skips existing realm                  |
| Invalid client                    | Correct realm/client/secret and service-account enablement               |
| Invalid redirect                  | Exact `http://localhost:5173/` callback; avoid 127.0.0.1                 |
| Issuer mismatch / JWK unavailable | Canonical issuer vs profile-specific key URL                             |
| 403 API response                  | Endpoint permission and resource owner/tenant, not only successful login |
| Console password unchanged        | Bootstrap values only apply during initialization                        |

To rotate a machine secret, update live Keycloak, the realm seed, application
configuration/deployment secret and the table together, then verify. The reconciler
updates client settings from the seed, so leaving an old seed can undo an intended
change. Never delete the realm or database to rotate a credential.

## Local startup checklist for security testing

Use [application setup](commerce-setup.md) for IntelliJ and React startup, and the
[flow checklist](../project-docs/business-flows-and-service-dependencies.md) for service
requirements. Gateway security's [manual tests](../api-gateway/03-gateway-security-oauth2-oidc-jwt.md#manual-verification)
exercise the resulting configuration; they do not create users or assign grants.
