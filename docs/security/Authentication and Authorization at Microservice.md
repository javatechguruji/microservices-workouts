# Authentication and authorization at microservices

This guide explains the implemented learning application. Read the
[gateway security flow](../01-api-gateway/05-gateway-security-oauth2-oidc-jwt.md)
and [Keycloak setup](../infra-setup/keycloak-setup.md) alongside it.

## What is implemented

- Gateway-only JWT validation and removal/replacement of all caller identity headers.
- All five downstream HTTP services authorize trusted `X-Auth-*` headers.
- RBAC: explicit `admin` or `customer` business roles.
- Permission checks: granular operations such as `inventory:read`.
- ABAC: order ownership and persisted tenant must match the caller policy.
- Machine calls: order → gateway → inventory, and payment → gateway → order.
- A separate React frontend (`ecom-ui`) using Keycloak Authorization Code + PKCE.

This is a trusted-header POC. Direct calls to downstream ports with fabricated
headers are intentionally possible for learning, exactly as requested. JWT
validation is not duplicated downstream. ClusterIP Services alone are not a
complete network trust boundary; no NetworkPolicy/mTLS enforcement is installed.

## Roles and permission model

Realm: `ecommerce`. A colon distinguishes a permission from a role in the JWT.
Composite realm roles expand into their permission roles in `realm_access.roles`.

| Business role | Effective permissions |
| --- | --- |
| `customer` | `orders:create`, `orders:read`, `payments:create`, `payments:read`, `products:read` |
| `admin` | All permissions listed below |
| `service` | No permissions by itself; each client service account receives explicit permission roles |

Admin permissions: `orders:create`, `orders:read`, `orders:read:any`,
`orders:update`, `payments:create`, `payments:read`, `payments:read:any`,
`products:read`, `products:write`, `inventory:read`, `inventory:write`,
`notifications:send`.

The `admin` role here is an **application role**, not Keycloak administration.
Service accounts are not Keycloak administrators and receive no blanket admin role.
Permissions are simple realm roles, not a remote authorization-policy engine.

## Learning users

| Username | Password | Role | Tenant |
| --- | --- | --- | --- |
| `customer1` | `Workouts-customer1-2026!` | `customer` | `demo` |
| `customer2` | `Workouts-customer2-2026!` | `customer` | `demo` |
| `admin1` | `Workouts-admin1-2026!` | `admin` | `demo` |
| `othercustomer` | `Workouts-othercustomer-2026!` | `customer` | `other` |

`tenant` is an administrator-managed user attribute. The configuration script
restricts user-profile editing so a customer cannot change the attribute used
for ABAC. Both clients and users receive audience `gateway-service` through a
protocol mapper. Service-account tenants are `demo` in this POC. Machine lookups (including
payment order lookups) are therefore restricted to `demo`; the `othercustomer`
account demonstrates order ABAC denial, not a second fully provisioned tenant.

## Service account grants

| Client ID | Assigned roles/permissions |
| --- | --- |
| `gateway-service` | `service`, `products:read` |
| `order-service` | `service`, `inventory:read`, `products:read` |
| `payment-service` | `service`, `orders:read:any` |
| `notification-service` | `service`, `orders:read:any` |
| `product-service` | `service`, `inventory:read` |
| `inventory-service` | `service`, `products:read` |

All six original secrets remain in the [Keycloak guide](../infra-setup/keycloak-setup.md#client-ids-and-secrets).
The new browser client `security-demo-ui` is public and has **no client secret**.
It requires Authorization Code + PKCE S256; password grants remain disabled.

## Endpoint policy matrix

All paths below are called through `http://localhost:9100` in local mode.

| Method / path | Permission / role | Resource condition |
| --- | --- | --- |
| `GET /api/orders/security/me` | Any authenticated identity | Returns trusted headers |
| `GET /api/orders/security/admin` | `admin` role | RBAC demonstration |
| `POST /api/orders` | `orders:create` | Customer must use own username as customerId; admin may choose an owner within own tenant |
| `GET /api/orders` | `orders:read` or `orders:read:any` | Filters to own tenant and own orders unless read-any |
| `GET /api/orders/{id}` | `orders:read` or `orders:read:any` | Same tenant AND owner, or same tenant plus read-any |
| `PATCH /api/orders/{id}/status` | `admin` AND `orders:update` | Same tenant; normal admin role includes read-any |
| `GET /api/orders/{id}/inventory/{sku}` | `orders:read` or `orders:read:any` | Original user must pass order ABAC before service-token call |
| `POST /payments` | `payments:create` | Owner/tenant checked against the order via a machine call; admin read-any can act for tenant owners |
| `GET /payments/{id}` | `payments:read` or `payments:read:any` | Owner/tenant checked against the associated order |
| `GET /api/products/{sku}` | `products:read` | Static demonstration response |
| `POST /api/products/{sku}/adjust` | `admin` AND `products:write` | Authorization demonstration only |
| `GET /api/inventory/{sku}` | `inventory:read` | Static demonstration response including calledAs |
| `POST /api/inventory/{sku}/adjust` | `admin` AND `inventory:write` | Authorization demonstration only |
| `POST /notifications` | `notifications:send` | Existing notification operation |

Product/inventory examples report a sample availability of 42. The `adjust`
endpoints do not change inventory/catalog data; they demonstrate authorization.
The order endpoint is real persistence and payment processing remains the
existing simulated payment implementation.

### ABAC details and legacy records

`Order.tenant` is populated from the trusted caller header at creation, not from
the request JSON. `customerId` is the demo username. For a customer, it must equal
the authenticated username. Production identities should normally use stable
subject IDs rather than renameable usernames; this lab keeps the existing API.

An administrator's read-any permission bypasses the owner comparison but never
the tenant comparison. `othercustomer` creates orders in tenant `other`, so even
`admin1` in tenant `demo` receives 403 for them.

Existing orders created before this change have no tenant. They are deliberately
not automatically assigned to users and are denied/filtered by the ABAC policy.
Create new orders for security exercises. An explicit reviewed migration would
be needed to assign historical orders to tenants.

## Code map

| Implementation | Responsibility |
| --- | --- |
| Gateway `security/GatewaySecurity.java` | Signature, issuer, timestamps, audience; public health/demo paths |
| Gateway `security/IdentityHeadersFilter.java` | Strip spoofed headers; derive headers; remove Bearer token |
| Each downstream `security/Caller.java` | Parse trusted identity and apply owner/tenant predicate |
| Each downstream `security/RequireAccess.java` | Endpoint role/permission annotation |
| Each downstream `security/HeaderAuthorizationConfig.java` | Intercept HTTP handlers; fail closed if missing policy |
| Order `client/ServiceTokenClient.java` and payment equivalent | Obtain/cache client credentials token with timeouts |
| Order `client/InventoryGatewayClient.java` | Inventory call to gateway, using order identity |
| Payment `client/OrderServiceClient.java` | Order lookup through gateway, using payment identity |
| `docker/keycloak/configure-security.py` | Reconcile roles, clients, demo users and admin-managed tenant attribute |
| `docker/keycloak/security-smoke-test.py` | Real PKCE and live HTTP security checks |

The small header contract is duplicated in each independent Maven service so
each Docker image can still build from its own folder. Keep these classes in
sync; a versioned shared contract library could replace duplication later.
HTTP controllers authorize requests before invoking services. Kafka listener
processing is trusted asynchronous work and does not require HTTP headers.

## Configure Keycloak

From the repository root, with Keycloak running:

```bash
python3 docker/keycloak/configure-security.py
python3 docker/keycloak/verify-clients.py
```

The first script updates the existing realm without deleting it. It retains
client secrets from the seed, grants the listed roles, adds mappers, and creates
missing users. It does not reset existing human passwords. It is an additive
learning-setup script, not a general policy-revocation tool; remove obsolete role
grants explicitly in Keycloak when experimenting with changed policies.

For a new installation, startup imports the seed realm; also run this script to
configure user-profile edit permissions. Updating only the seed JSON does not
update an already-imported realm.

## Run and test in IntelliJ

1. Start shared infrastructure: `docker compose up -d`.
2. Run gateway, order, payment, product, inventory and notification with profile
   `local`. Their ports remain 9100–9105.
3. Order/payment local profiles include their documented learning client secrets
   as defaults. You may override them with `KEYCLOAK_CLIENT_SECRET` in each run
   configuration. Do not put an admin password in that variable.
4. Start the [React frontend](../../ecom-ui/README.md) with `npm ci` and
   `npm run dev` from `ecom-ui`, then open **http://localhost:5173/**. Use `localhost`, not
   `127.0.0.1`, because the registered redirect URI is exact.
5. Click **Sign in to your account**. Customers see their dashboard and own orders;
   admins see the admin dashboard, all tenant orders and customer summaries.
6. Use **Create order**, enter the amount and click **Place order**. Open details
   from the order list to make a demo payment. Admins can choose the owner on
   creation and update order status from details.
7. Use **Sign out** to switch users. Another customer's list excludes the first
   user's orders; navigating directly to their detail URL returns an access-denied
   screen backed by HTTP 403.

The [React walkthrough](../../ecom-ui/README.md#manual-walkthrough) covers these
screens. Raw API security checks remain in the smoke script and gateway guide.

The React application uses `keycloak-js` for Authorization Code + PKCE, logout,
SSO checks on reload and token refresh before API calls. Access and refresh tokens
stay in memory. The Vite proxy forwards API requests to the gateway; no machine
client secret is shipped to the browser.

### Expected results

| Test | Result |
| --- | --- |
| No token, including forged admin headers at gateway | 401 |
| Modified token or browser ID token used as access token | 401 |
| Customer calls admin endpoint | 403 |
| Admin calls admin endpoint | 200 |
| Customer reads own order | 200 |
| Customer reads another owner's order | 403 |
| Admin reads same-tenant order | 200 |
| Admin reads an `other` tenant order | 403 |
| Customer directly calls inventory | 403 |
| Customer calls own order's inventory endpoint | 200; inventory sees order-service identity |
| Spoofed admin headers alongside customer access token | Overwritten; identity stays customer |
| Another customer reads a payment for the first customer's order | 403 |

### Machine flow without a browser

Use the existing client ID/secret table to request a token as in the Keycloak
guide. With an order-service access token:

```bash
# Set ACCESS_TOKEN to the token obtained from Keycloak; do not use the secret here.
curl --fail-with-body http://localhost:9100/api/inventory/SKU-1 \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Expected: `calledAs` is `service-account-order-service`. The same token cannot
create orders or invoke the admin endpoint because it lacks those permissions.
A machine token does not preserve the original human's identity. For the
order-to-inventory demonstration, the user's resource policy is checked before
switching to machine identity to avoid a privileged-call bypass.

### Direct downstream header simulation (intentional POC bypass)

```bash
curl --fail-with-body http://localhost:9101/api/orders/security/me \
  -H 'X-Auth-Subject: demo-subject' \
  -H 'X-Auth-Username: customer1' \
  -H 'X-Auth-Roles: customer' \
  -H 'X-Auth-Permissions: orders:read,orders:create' \
  -H 'X-Auth-Tenant: demo'
```

This proves headers are trusted downstream, not authenticated there. Supplying
admin headers directly can impersonate admin by design. At the gateway those
same headers never substitute for a JWT and are overwritten when a JWT exists.

## Automated validation

Run each service's Maven tests, for example:

```bash
mvn -f gateway-service/pom.xml test
mvn -f order-service/pom.xml test
mvn -f payment-service/pom.xml test
```

With all six services running locally and infrastructure ready:

```bash
python3 docker/keycloak/security-smoke-test.py
```

The script performs real Authorization Code + PKCE logins for all four users,
uses real signed tokens, checks spoofing and denied routes, creates sample
orders/payments, and verifies both implemented machine hops. It prints outcomes,
not tokens or passwords. It writes learning data and does not delete it.

## Future Jenkins / Minikube deployment

No application deployment is performed by these scripts. For Jenkins:

- Build/test each service and render manifests with the produced image tag.
- Prepare `ecommerce`, then application Secret `service-client-credentials` from
  `k8s/service-client-credentials.yaml` (learning values only).
- Order/payment Deployments reference the matching secret key as
  `KEYCLOAK_CLIENT_SECRET`.
- The k8s gateway route destinations use Kubernetes Service DNS; service HTTP
  clients call `http://gateway-service:9100`.
- Token/JWK URLs use `host.minikube.internal:8180`; issuer remains
  `http://localhost:8180/realms/ecommerce`, as configured in Keycloak.
- Health probes remain unauthenticated. Do not expose downstream services outside
  the trusted boundary in a real deployment.
- For the browser demo, a gateway port-forward on local port 9100 preserves the
  registered redirect URI. Keycloak remains reachable on the Mac at port 8180.

HTTP calls use the gateway; Kafka events continue through the existing broker.
Minikube networking is not established or tested by the local smoke test.


## Validation results

All six services built successfully with Java 17. Their test suites passed
(21 tests total), including dedicated gateway header-spoofing and order RBAC/ABAC
tests. The live smoke test completed real PKCE logins for all four users and
passed 25 API checks, including JWT rejection, header replacement, ownership,
tenant isolation, permission denials, and both machine-token hops. It created
learning records. Temporary application processes were stopped afterward;
start the services in IntelliJ to use the browser page.

## Role modules in the single frontend portal

Customer pages live under `/#/customer/`; administrator pages live under
`/#/admin/`. Their dashboards, order pages and navigation belong to separate
modules. Login, API access and reusable controls are shared. The router denies
unauthorized modules before mounting their pages; downstream API checks remain
the authority for permissions, ownership and tenant isolation. See the
[frontend module guide](../../ecom-ui/README.md#one-portal-separate-role-modules).
