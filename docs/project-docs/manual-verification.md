# Manual verification: shopping, authorization and recovery

These exercises use real persisted learning data. They verify application behavior;
installation, credentials and role configuration remain in [setup](../infra-setup/commerce-setup.md).
For a smaller service set, consult the [per-flow checklist](business-flows-and-service-dependencies.md).

## 1. Prepare the demonstration

Start shared-infra, all nine Java services with `local`, and React. Use a browser
with Developer Tools → Network; curl/Postman and Kafka UI are optional inspection
tools. Open **http://localhost:5173/**. Use the accounts/passwords in
[Keycloak setup](../infra-setup/keycloak-setup.md#application-roles-and-users).

Sign out before switching accounts, or use separate browser profiles. Keep the
created order ID for subsequent checks. Stock is real stored demo state; repeated
runs consume stock and restarting inventory does not replenish it.

## 2. Preferences and catalog

1. Sign in as customer1. Open **Your profile**, save valid contact details, DOB
   and Electronics as a preference.
2. Return to **Shop**. Expect Electronics first, five category groups, images,
   prices/discounts, ratings and stock where available.
3. Stop rating-service alone and reload Shop. Expect products to remain usable,
   with missing rating enrichment. Restart rating-service.
4. Stop product-discount-service and reload Shop. Expect catalog failure rather
   than an invented price. Restart it and reload.

**Concept:** [concurrent aggregation and explicit fallbacks](catalog-aggregation-and-preferences.md).
Profile save itself needs neither Kafka nor rating-service.

## 3. Customer cart and checkout

1. Add products with +/− quantity controls. Open Cart and verify its refreshed total.
   With unchanged seed data, two ELEC-1 headphones cost 2 × $67.15 = $134.30;
   use the currently displayed prices if you changed discounts.
2. Enter a delivery address of at least ten characters and click **Pay now**.
3. Network should show `POST /api/orders/checkout` with 202. Save the order ID.
4. The detail page polls fulfillment through CREATED, RESERVED, PAID, CONFIRMED.
   Intermediate states can pass too quickly to observe each one.
5. Reload. Expect the same items and price snapshots, not another payment/order.

**Concept:** [authoritative quote, idempotency and saga](shopping-and-fulfillment.md).
202 means stored acceptance; CONFIRMED is the completed worker outcome.

For a deterministic duplicate test, use `commerce-smoke-test.py` from the setup
guide. It repeats/concurrently submits the same key and checks a single outcome;
clicking a disabled UI button twice does not prove server idempotency.

## 4. Customer isolation and admin-assisted checkout

1. Sign in as customer2. Customer1's order should be absent from My orders.
2. Open `http://localhost:5173/#/customer/orders/ORDER_ID`. Expect unavailable/access
   denied and a 403 detail response for customer1's existing order.
3. Enter `/#/admin/customers` as customer2. Expect **Access restricted**.
4. Sign in as admin1. The order should appear in All orders within tenant demo.
5. Use **Create order**, select the profiled customer1, products/quantities and an
   address. Pay now uses the same checkout API and creates an order owned by customer1.

Admin Customers summarizes existing orders, not all registered users. See
[RBAC/ABAC](../security/Authentication%20and%20Authorization%20at%20Microservice.md).
For cross-tenant and forged-header tests, follow
[gateway security verification](../api-gateway/03-gateway-security-oauth2-oidc-jwt.md#manual-verification).

## 5. Shipping and delivery

As admin1, open a CONFIRMED shopping order, click **Ship order**, then **Mark delivered**.
Expect SHIPPED followed by DELIVERED and simulated `ECOM-ORDER_ID` tracking.
Customer1 can read the result but cannot invoke these admin-only actions.
Calling deliver before ship returns 409 for an otherwise authorized admin.
A customer calling a shipping API receives 403.

**Concept:** authorization decides who may act; the state machine decides whether
the action is valid now. Inventory/payment are not called again by these actions.

## 6. Kafka and background updates

1. Reopen customer1's **Updates** page. Expect order lifecycle entries after consumers
   catch up. There is no live push; reopen/refresh to issue a new inbox GET.
2. In Kafka UI at `http://localhost:8089`, inspect `commerce-order-events`, using the
   order ID as key. Compare publication with inbox records and consumer lag.
3. To study outage behavior, stop only the broker: `docker compose stop kafka`.
4. Create another shopping order with stock available. It can reach CONFIRMED while
   order/payment outboxes retain unpublished rows; new inbox/history effects wait.
5. Restore Kafka: `docker compose up -d kafka`. After readiness and retry, refresh
   Updates and Shop to see catch-up. Always restore the broker after the exercise.

The customer history consumer uses CONFIRMED events only; shipping must not count
the purchase again. Explicit category choices remain ahead of history, so remove
explicit choices if you want purchase history to determine the leading category.

**Concept:** [transactional outbox and idempotent consumers](../kafka/kafka-notes-scenarios.md).
Database inspection commands are in [application setup](../infra-setup/commerce-setup.md#5-database-initialization-and-configuration).

## 7. Legacy API lab

This is separate from product checkout. Start gateway/order/payment, Keycloak,
PostgreSQL and Kafka; optional notification-service logs payment events. Copy a
customer access token from the browser as described in the security guide.

```sh
ACCESS_TOKEN='PASTE_CUSTOMER1_ACCESS_TOKEN'
curl --fail-with-body http://localhost:9100/api/orders \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"customer1","amount":25.00}'
```

Set `ORDER_ID` to the returned ID, then:

```sh
ORDER_ID=123
curl --fail-with-body http://localhost:9100/payments \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"orderId\":$ORDER_ID,\"amount\":25.00}"
curl --fail-with-body "http://localhost:9100/api/orders/$ORDER_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
unset ACCESS_TOKEN
```

After event processing, expect CONFIRMED. This legacy confirmation depends on
`payment-completed`; unlike shopping, it does not use reservation/commit states.
An identical successful payment retry returns the previous payment, not a second charge.

## 8. Dependency recovery and failure expectations

| Exercise                                      | Expected behavior and what it teaches                                                   |
| --------------------------------------------- | --------------------------------------------------------------------------------------- |
| Stop payment-service before Pay now           | Accepted checkout can reserve, then wait/retry; restart payment to continue             |
| Stop inventory-service                        | Browsing can use missing stock preview, but worker cannot reserve/commit until restored |
| Change cart expectedAmount in an API request  | New checkout rejected with 409; server quote is authoritative                           |
| Repeat key with different cart/address        | 409; a retry cannot silently become another action                                      |
| Request more stock than available             | Checkout can be accepted, then becomes FAILED without payment                           |
| Restart order-service after accepted checkout | Worker resumes persisted state; duplicate-safe recipients tolerate retry                |

Use quantities within the API limit of 1–99 when testing stock shortage; exceeding
that limit tests input validation instead. Automated commerce checks include a
multi-item rollback/concurrent-stock exercise. Stop/start application processes in
IntelliJ and restore dependencies afterward; do not manually release a potentially
paid reservation or delete volumes to force a result.
