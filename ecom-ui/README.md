# Ecom — Order workspace

A React application with customer and administrator screens backed by the real
order and payment APIs. Data is persisted in PostgreSQL; dashboards and tables
use the orders returned by the gateway, not mock data.

## Start locally

1. Start shared infrastructure and apply the [Keycloak configuration](../docs/infra-setup/keycloak-setup.md).
2. Run gateway and order-service with profile `local` in IntelliJ. For checkout,
   also run payment-service, notification-service and Kafka. All six services
   are needed for the broader API smoke suite.
3. Use Node.js 22.12+ in the 22.x line or a newer supported LTS:

   ```sh
   cd ecom-ui
   npm ci
   npm run dev
   ```

4. Open **http://localhost:5173/** and click **Sign in to your account**. Credentials
   are in [Keycloak setup — Application roles and users](../docs/infra-setup/keycloak-setup.md#application-roles-and-users).

Restart order-service after pulling these changes to apply the new server-side
order amount, customer username and missing-status validation.

## Customer screens

- **Dashboard:** own order count, pending/confirmed counts, total order value,
  recent orders and a status summary.
- **My orders:** searchable order list, status filter, pagination and detail links.
- **Create order:** the customer is fixed to the signed-in username. Enter a
  positive USD amount and click **Place order**.
- **Order details:** order number, owner, created date, total and status. Pending
  orders offer **Pay now** using the existing simulated payment service. A receipt
  is shown after payment; **Refresh order** fetches the eventual confirmation.

## Administrator screens

- **Admin dashboard:** metrics and recent orders across customers in the same tenant.
- **All orders:** search by order ID or customer username, filter by status and
  inspect any order in the administrator's tenant.
- **Customers:** customer summaries derived from existing orders, with links to
  their orders. This is not a complete Keycloak user directory.
- **Create order for a customer:** enter an existing customer's username and the
  amount. Suggestions come from previous orders. The current API validates the
  username's format, but does not query Keycloak to verify that the user exists;
  use the configured customer usernames from the setup guide.
- **Manage order:** change an order's status to Pending, Confirmed or Failed.
  These are the statuses supported by the current domain model.

An admin sees all customers **within their own tenant**, not other tenants.
Customer management navigation and status controls are absent for customers.
Directly entering another customer's order URL still invokes the backend policy
and displays an access-denied screen. Hiding a button is not the security boundary.

## Manual walkthrough

1. Sign in as `customer1`. Create an order for `37.50`, save its ID, and find it in
   **My orders**. Open its details and try **Pay now**. This does not charge money.
2. Sign out and sign in as `customer2`. The order is absent from **My orders**.
   Open `http://localhost:5173/#/customer/orders/ORDER_ID`: access must be denied.
3. Sign in as `admin1`. **All orders** includes customer1's order. Use **Create
   order** to create another order for `customer1`; open it and update its status.
4. Inspect **Customers** and the dashboard. The data reflects orders in tenant
   `demo`. Return as customer1 to see the admin-created order and updated status.
5. Sign in as `othercustomer`. Tenant `demo` orders are absent and their detail
   URLs are denied. This user's own orders belong to tenant `other`.

For JWT, role, header-spoofing and cross-service API checks, see the
[gateway security guide](../docs/01-api-gateway/05-gateway-security-oauth2-oidc-jwt.md).
The old API playground and raw JSON editor have been replaced by these screens.

## Implementation and configuration

### One portal, separate role modules

The public sign-in page is shared. After identity loading, a customer enters
`/#/customer/dashboard` and an administrator enters `/#/admin/dashboard`.
These are independent page components, not one dashboard with an `admin` flag.
Each module owns its routes, layout configuration, navigation and pages, and is
loaded as a separate JavaScript bundle when authorized.

```text
src/
  App.jsx                       # Compose session and router
  app/                          # Route normalization, role guards, lazy modules
  modules/
    customer/
      CustomerModule.jsx
      CustomerLayout.jsx
      pages/                    # Dashboard, My orders, Create order, Details
    admin/
      AdminModule.jsx
      AdminLayout.jsx
      pages/                    # Dashboard, All orders, Create order, Details, Customers
      components/               # Admin-only order status editor
  shared/
    auth/                       # Session provider and public sign-in page
    layout/                     # Visual shell and denied/not-found states
    orders/                     # Order data, reusable form/table/detail/payment controls
  auth.js                       # Keycloak adapter, SSO restore, token refresh
  api.js                        # Bearer requests to gateway API paths
  orders.js                     # API errors and display formatting
```

| Page | Customer route | Admin route |
| --- | --- | --- |
| Dashboard | `/customer/dashboard` | `/admin/dashboard` |
| Orders | `/customer/orders` | `/admin/orders` |
| Create order | `/customer/orders/new` | `/admin/orders/new` |
| Order details | `/customer/orders/:id` | `/admin/orders/:id` |
| Customer activity | — | `/admin/customers` |

Routes follow `http://localhost:5173/#`. Shared controls handle common rendering
and behavior; module pages decide which controls to compose. For example, the
customer create page fixes the owner to the caller, while the admin create page
owns customer selection. Only the admin details page includes the status editor.

The router checks the identity returned by the gateway before mounting a module.
A customer entering an admin URL sees **Access restricted**. A user with neither
supported role is denied. A user with both roles defaults to admin and can enter
both modules; an admin-only user does not implicitly receive the customer role.
Old `/orders/...` links redirect into the caller's default module; old `/customers`
links redirect to the guarded admin module. Frontend guards organize the experience;
backend role, permission, ownership and tenant checks remain authoritative.

To add a future role module, give it its own module, layout and pages, register its
lazy entry point and guard in `app/`, then add route tests. Keep role-specific
business decisions inside the module and extract only genuinely shared controls.
The portal remains a single application and deployment, with one Keycloak client.

Keycloak's public client remains `security-demo-ui`, with callback/logout URL
`http://localhost:5173/`. It is a client identifier, not the application name.
The adapter uses Authorization Code + PKCE S256, refreshes expiring tokens before
requests, and keeps tokens in memory. The application temporarily saves the desired screen in session storage for
the SSO redirect; the adapter manages its own OAuth correlation state. No machine credentials are shipped to React.

Vite proxies `/api`, `/payments` and `/notifications` to the gateway; it never
calls a downstream service directly. Defaults work without an environment file.
Copy `.env.example` to `.env.local` to override public configuration. Never put
secrets in `VITE_*` variables. Compose remains infrastructure-only.

The current order API contains an owner, total, status and timestamps; it does
not model product line items, shipping, tax, customer profiles or currencies.
The UI displays totals as USD. Dashboard totals include all statuses and are
**order value**, not revenue. Checkout simulates success and uses Kafka for
asynchronous order confirmation; refresh to see the actual server status.
The configured payment service account belongs to tenant `demo`, so checkout
is supported there; cross-tenant checkout requires a separate backend design.
Status edits currently permit any of the three enum values and do not implement
a fulfillment workflow. These limits are inherited from the current services.

## Verification

```sh
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Browser tests require gateway, order and payment services plus their shared
infrastructure. They cover customer creation/payment, reload/SSO, token refresh,
module routing and unauthorized module denial, legacy links, list/detail access, admin creation for a customer, status updates, cross-user and
cross-tenant denial, logout and mobile layout. Tests use the realm seed's learning
passwords and create persistent records. Traces are disabled. Screenshots are saved
under ignored `test-results/` for visual review.

Use `npm run format` to keep the React source consistently formatted.

To preview a build, stop the development server first and run `npm run preview`.
It uses the same port and proxy for local verification. Production hosting needs
a suitable reverse proxy and matching Keycloak origins; Vite preview is not a
production deployment server.

References: [Keycloak JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter),
[Vite server proxy](https://vite.dev/config/server-options#server-proxy).
