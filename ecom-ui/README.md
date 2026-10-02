# Ecom UI

One React portal with separate customer and admin modules, real Keycloak login
and backend APIs. See [application setup](../docs/infra-setup/commerce-setup.md) for
Node/Vite configuration, startup and test commands. Credentials live in
[Keycloak setup](../docs/infra-setup/keycloak-setup.md).

## Pages and source map

| Module   | Route after `http://localhost:5173/#`      | Page                                                            |
| -------- | ------------------------------------------ | --------------------------------------------------------------- |
| Customer | `/customer/dashboard`                      | Shop: preferred categories, images, prices, ratings, quantities |
| Customer | `/customer/cart`                           | Cart, address, total and Pay now                                |
| Customer | `/customer/profile`                        | Contact, DOB and preferred categories                           |
| Customer | `/customer/orders`, `/customer/orders/:id` | Owned orders and fulfillment                                    |
| Customer | `/customer/notifications`                  | Persisted order updates                                         |
| Admin    | `/admin/dashboard`                         | Order summaries                                                 |
| Admin    | `/admin/orders`, `/admin/orders/:id`       | Same-tenant orders and shipping controls                        |
| Admin    | `/admin/orders/new`                        | Priced checkout for an existing profiled customer               |
| Admin    | `/admin/customers`                         | Customer activity derived from order records                    |

The public sign-in page is shared. `src/app` selects/guards lazy role modules;
`src/modules/customer` and `src/modules/admin` own their pages/layouts;
`src/shared` owns authentication, shared layout and reusable order views.
`auth.js` handles Keycloak, `api.js` attaches access tokens to allowed API paths.

## Learn the implementation

Read [frontend modules/cart](../docs/project-docs/frontend-modules-and-cart.md),
[gateway login](../docs/api-gateway/03-gateway-security-oauth2-oidc-jwt.md), and
[checkout](../docs/project-docs/shopping-and-fulfillment.md) for source excerpts.
Follow [manual verification](../docs/project-docs/manual-verification.md) for the customer
and admin walkthrough. Frontend guards organize pages; backend policies secure APIs.

The current portal loads identity and orders from order-service even on a profile
page. [The runtime checklist](../docs/project-docs/business-flows-and-service-dependencies.md)
explains other dependencies. Cart state is browser-local, payment/tracking simulated,
and customer/admin shopping is demo-tenant scoped. No real card details are collected.

## Verification

Unit, production-build and Playwright commands are maintained in
[application setup](../docs/infra-setup/commerce-setup.md#6-verification-commands).
Browser tests require running applications and write learning records. The project
calls gateway directly using `VITE_GATEWAY_URL` (default `http://localhost:9100`).
Gateway permits `http://localhost:5173` through CORS. Vite has no API proxy.
See [CORS guide](../docs/api-gateway/09-cors-preflight-and-browser-security.md).
Production frontend hosting is not configured here.
