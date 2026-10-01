> Historical conceptual notes: the implemented POC uses **client credentials via the gateway**, not user-token relay. See [current gateway flow](05-gateway-security-oauth2-oidc-jwt.md) and [microservice security](../security/Authentication%20and%20Authorization%20at%20Microservice.md) for the active implementation.

# API Gateway --- Stage 6: Token Relay & Service-to-Service Security

> **Previous:** `05-oauth2-oidc-jwt-gateway-security.md`\
> **Next:** `07-rate-limiting-redis.md`\
> **Runtime:** Minikube / Kubernetes\
> **Identity Provider:** Keycloak\
> **Goal:** Understand how user identity and service identity safely
> move across Gateway and microservice boundaries.\
> **Learning:** Understand → Implement → Test → Break → Observe →
> Explain as Architect.

------------------------------------------------------------------------

# 1. Why This Stage Exists

Stage 5 answered:

``` text
Who is calling Gateway?
Is the JWT valid?
Does the caller have permission?
```

Stage 6 answers a different question:

``` text
After Gateway authenticates the caller,
what identity should downstream services receive?

And when Order calls Inventory,
should Inventory see the USER identity
or the ORDER-SERVICE identity?
```

The original curriculum intentionally separates these topics because
**token validation** and **identity propagation** are not the same
architectural decision.

------------------------------------------------------------------------

# 2. The Two Core Architectures

## Architecture A --- Propagate User Token

``` text
Customer
   |
User Access Token
   v
Gateway
   |
same/delegated user token
   v
Order Service
   |
user identity available
```

Use this when the downstream service needs to authorize based on the end
user.

Example:

``` text
Customer 101
   |
GET /orders/500
   |
Order Service must know:
"Is order 500 owned by customer 101?"
```

## Architecture B --- Use Service Identity

``` text
Order Service
     |
Client Credentials
     v
Keycloak
     |
Service Access Token
     v
Inventory Service
```

Use this when Inventory should authorize **Order Service itself**, not
the original customer.

Example:

``` text
Order Service reserves stock
```

Inventory may care that:

``` text
caller = trusted order-service
permission = inventory.reserve
```

rather than which browser user initiated the workflow.

------------------------------------------------------------------------

# 3. User Identity vs Service Identity

These are separate security subjects.

``` text
User identity:
customer1
admin1

Service identity:
order-service
payment-service
inventory-service
```

A common mistake is to treat:

``` text
"request originated because of a user"
```

as equivalent to:

``` text
"every downstream call should use the user's token"
```

That is not always correct.

The downstream authorization requirement decides which identity belongs
in the call.

------------------------------------------------------------------------

# 4. What Is Token Relay?

Token relay means an OAuth-aware component forwards an access token to a
downstream resource.

Conceptually:

``` text
Client
  |
Access Token
  v
Gateway
  |
Access Token
  v
Order
```

Spring Cloud Gateway integrates with Spring Security and can relay
access tokens when Gateway acts as an OAuth2 client. Current Spring
Cloud Gateway documentation separates Resource Server support from
OAuth2 Client/token-relay support.

Important:

> **Token relay is not the same as JWT validation.**

``` text
Resource Server
= validates token presented to it

OAuth2 Client
= obtains/manages OAuth access tokens for outbound use

Token Relay
= puts an appropriate access token on downstream request
```

------------------------------------------------------------------------

# 5. Stage 5 vs Stage 6

Stage 5:

``` text
Client JWT
   ↓
Gateway validates
   ↓
Order validates
```

Stage 6 goes deeper:

``` text
Which token reaches Order?
Should Gateway relay it?
Should Order propagate it to Inventory?
Should Order instead obtain its own service token?
How do scopes/roles differ?
How do we prevent header spoofing?
```

These are identity-flow decisions.

------------------------------------------------------------------------

# 6. Case 1 --- Incoming Bearer Token Already Exists

Suppose the client sends:

``` http
Authorization: Bearer eyJ...
```

Gateway validates it as a Resource Server.

If Gateway simply proxies that header unchanged, Order can validate the
same bearer token.

Flow:

``` text
Client
  |
User JWT
  v
Gateway
  | validate
  |
User JWT
  v
Order
  | validate again
```

In this simple bearer-token proxy case, you do **not automatically need
`TokenRelay`** just to keep the existing `Authorization` header.

This distinction prevents unnecessary OAuth2 Client configuration.

------------------------------------------------------------------------

# 7. When `TokenRelay` Becomes Useful

TokenRelay is useful when Gateway participates as an OAuth2 **Client**,
for example:

``` text
Browser
   |
OIDC Login
   v
Gateway/BFF
   |
OAuth2AuthorizedClient
   |
TokenRelay
   v
Order Service
```

Gateway has an authorized client context and needs to attach the access
token to the downstream request.

Typical dependency:

``` gradle
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
```

Current Spring Cloud Gateway documentation describes token relay as an
OAuth2-client capability for forwarding the user's access token
downstream.

------------------------------------------------------------------------

# 8. TokenRelay Configuration Shape

When Gateway is configured as an OAuth2 Client, a route can use:

``` yaml
filters:
  - TokenRelay=
```

or a specific client registration:

``` yaml
filters:
  - TokenRelay=my-client
```

Conceptually:

``` text
Gateway authenticated OAuth user
      |
OAuth2AuthorizedClient
      |
TokenRelay extracts access token
      |
Authorization: Bearer ...
      |
downstream
```

Do not add this configuration unless Gateway actually has the
corresponding OAuth2 Client setup.

------------------------------------------------------------------------

# 9. Resource Server + OAuth2 Client Can Coexist

A Gateway may have both roles.

``` text
Incoming side:
Resource Server

Outgoing OAuth side:
OAuth2 Client
```

Example:

``` text
Client
  |
Bearer JWT
  v
Gateway
  |
validate JWT as Resource Server
  |
obtain/use another OAuth token as Client
  v
Downstream
```

This is valid, but more complex.

Architect question:

> Why does Gateway need both roles?

If you cannot answer that clearly, avoid adding both merely because
Spring supports them.

------------------------------------------------------------------------

# 10. Our Practical Stage 6 Scenario

We will use:

``` text
Customer
   |
User JWT
   v
Gateway
   |
User JWT
   v
Order Service
   |
Client Credentials
   v
Keycloak
   |
Order Service Token
   v
Inventory Service
```

This lets us practice both identity models:

``` text
Gateway → Order
user identity

Order → Inventory
service identity
```

------------------------------------------------------------------------

# 11. Why Not Send the User Token to Inventory?

Sometimes you should.

Sometimes you should not.

Suppose the operation is:

``` text
POST /orders
```

Order Service internally reserves inventory.

Inventory's authorization rule may be:

``` text
Only ORDER_SERVICE may reserve inventory.
```

Then a service token is appropriate.

If instead Inventory exposes:

``` text
GET /inventory/user-visible-stock
```

where behavior depends on user permissions, propagating user identity
may be appropriate.

The question is:

``` text
What identity does the downstream business rule require?
```

------------------------------------------------------------------------

# 12. Client Credentials Mental Model

Client Credentials has no human user.

``` text
Order Service
    |
client_id
client credential
    |
    v
Authorization Server
    |
access token
    |
    v
Inventory
```

The token represents:

``` text
order-service
```

not:

``` text
customer1
```

Spring Security's current OAuth2 Client support includes Client
Credentials and obtains an access token on behalf of the client itself.

------------------------------------------------------------------------

# 13. Keycloak Service Account

Create confidential client:

``` text
Client ID:
order-service
```

Enable:

``` text
Client authentication: ON
Service accounts roles: ON
```

Give the service account only the permission needed by Inventory.

Example:

``` text
SYSTEM_ORDER
```

Better production-style naming could be permission-oriented:

``` text
inventory.reserve
inventory.read
```

depending on how scopes/roles are modeled.

------------------------------------------------------------------------

# 14. Least Privilege

Bad:

``` text
order-service token:
ADMIN
SUPERUSER
all APIs
```

Better:

``` text
order-service:
inventory.reserve
inventory.read
```

The service should receive only permissions required for its business
responsibilities.

If Order is compromised, least privilege reduces blast radius.

------------------------------------------------------------------------

# 15. Store the Client Secret Safely

Do not commit:

``` yaml
client-secret: abc123
```

Use Kubernetes Secret.

Example:

``` bash
kubectl create secret generic order-oauth-client \
  -n ecommerce \
  --from-literal=client-id=order-service \
  --from-literal=client-secret='<SECRET>'
```

Order Deployment:

``` yaml
env:
  - name: ORDER_OAUTH_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: order-oauth-client
        key: client-id

  - name: ORDER_OAUTH_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: order-oauth-client
        key: client-secret
```

------------------------------------------------------------------------

# 16. Order Service OAuth2 Client Dependency

Add to Order Service:

``` gradle
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
```

Order is already a Resource Server from Stage 5.

Now it has two roles:

``` text
Resource Server
= validate incoming user token

OAuth2 Client
= obtain service token for Inventory
```

------------------------------------------------------------------------

# 17. Client Registration

`application-k8s.yml`:

``` yaml
spring:
  security:
    oauth2:
      client:
        registration:
          inventory-client:
            provider: keycloak
            client-id: ${ORDER_OAUTH_CLIENT_ID}
            client-secret: ${ORDER_OAUTH_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope:
              - inventory.reserve

        provider:
          keycloak:
            issuer-uri: ${JWT_ISSUER_URI}
```

Important:

``` text
registration id = inventory-client
OAuth client id = order-service
```

They do not have to be the same string.

------------------------------------------------------------------------

# 18. What Happens Internally?

When Order needs an Inventory token:

``` text
Order
  |
OAuth2AuthorizedClientManager
  |
Is a valid token already available?
  |
  +-- yes → reuse
  |
  +-- no/expired
         |
         v
   Keycloak token endpoint
         |
   client_credentials
         |
         v
   access token
```

Spring Security manages OAuth2 authorized clients and token acquisition
so business code does not manually construct token HTTP requests
everywhere.

------------------------------------------------------------------------

# 19. Why Not Call the Token Endpoint Manually?

You could write:

``` java
restClient.post()
    .uri("/token")
    .body(...)
```

for every service call.

But then you own:

``` text
token request construction
credential handling
expiry
reuse
error handling
OAuth details
```

Spring Security already provides OAuth2 Client abstractions for this.

Prefer the framework unless you have a specific reason not to.

------------------------------------------------------------------------

# 20. Service Token Caching / Reuse

Do not obtain a new Client Credentials token for every Inventory request
if the existing token is still valid.

Bad:

``` text
Order request 1 → Keycloak token request
Order request 2 → Keycloak token request
Order request 3 → Keycloak token request
```

This creates unnecessary:

``` text
latency
IdP load
failure dependency
```

OAuth client management should reuse an access token while valid and
renew it when needed.

------------------------------------------------------------------------

# 21. RestClient / WebClient Choice

Your application programming model does not force all outbound calls to
be reactive.

For a Spring MVC Order Service, current Spring Security supports OAuth2
Client integration with modern HTTP clients such as `RestClient`, and
also supports `WebClient` where appropriate.

Choose the client consistent with your service architecture.

For this workout, keep the existing Order HTTP client style if practical
rather than rewriting unrelated code just for security.

------------------------------------------------------------------------

# 22. Conceptual Outbound Client

The outbound Inventory client should result in:

``` http
Authorization: Bearer <ORDER_SERVICE_ACCESS_TOKEN>
```

The business code should conceptually remain:

``` java
inventoryClient.reserve(productId, quantity);
```

Security plumbing should not leak into every controller/service method.

------------------------------------------------------------------------

# 23. Inventory as Resource Server

Inventory adds:

``` gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```

Configuration:

``` yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
```

Inventory validates the incoming service token independently.

------------------------------------------------------------------------

# 24. Inventory Authorization Rule

Example:

``` java
@Configuration
@EnableWebSecurity
public class InventorySecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**")
                .permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/inventory/*/reserve"
                )
                .hasRole("SYSTEM_ORDER")

                .anyRequest()
                .authenticated()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            );

        return http.build();
    }
}
```

If Keycloak roles are nested as in Stage 5, reuse the appropriate role
converter.

------------------------------------------------------------------------

# 25. End-to-End Identity Flow

Customer calls:

``` text
POST /api/orders
```

### Hop 1

``` text
Customer JWT
   ↓
Gateway validates CUSTOMER
```

### Hop 2

``` text
Customer JWT
   ↓
Order validates CUSTOMER
```

### Hop 3

Order needs Inventory:

``` text
Order OAuth client
   ↓
Client Credentials
   ↓
Keycloak
   ↓
service token
```

### Hop 4

``` text
Order service token
   ↓
Inventory validates SYSTEM_ORDER
```

The identity changes intentionally at the service boundary.

------------------------------------------------------------------------

# 26. Do Not Accidentally Forward Both Tokens as One Identity

Order may possess:

``` text
incoming user token
outbound service token
```

These represent different subjects.

Do not overwrite/relabel identity carelessly.

Good mental model:

``` text
incoming security context:
customer1

outbound service credential:
order-service
```

Keep them conceptually separate.

------------------------------------------------------------------------

# 27. What If Inventory Needs Both User and Service Context?

This is a real advanced scenario.

Inventory might need to know:

``` text
Calling service = order-service
End user       = customer1
```

Naive solutions include forwarding:

``` text
X-User-Id: customer1
```

But that raises trust/spoofing questions.

Possible architecture approaches include:

``` text
delegated/user token
token exchange
signed identity context
service identity + trusted propagated user context
```

The exact model depends on security requirements and IdP capabilities.

Do not invent a second identity by trusting arbitrary headers.

------------------------------------------------------------------------

# 28. Trusted Internal Headers --- Why They Are Dangerous

Bad:

``` text
Client sends:
X-User-Id: admin1
X-Role: ADMIN
```

If Gateway simply forwards those headers and Inventory trusts them:

``` text
client controls identity
```

That is a security flaw.

If internal identity headers are used, Gateway must:

``` text
remove untrusted incoming versions
derive identity from authenticated token
set controlled headers
```

and downstream services must have a strong reason to trust that
boundary.

Signed tokens are usually easier to reason about.

------------------------------------------------------------------------

# 29. Header Spoofing Scenario

External request:

``` http
Authorization: Bearer <CUSTOMER token>
X-User-Role: ADMIN
```

Correct outcome:

``` text
JWT says CUSTOMER
spoofed header says ADMIN
```

Security decisions must use the trusted JWT/security context, not the
caller-provided header.

Test this deliberately.

------------------------------------------------------------------------

# 30. Scopes vs Roles for Service-to-Service

Example role:

``` text
SYSTEM_ORDER
```

Example scopes:

``` text
inventory.read
inventory.reserve
```

A scope-oriented service API often communicates permissions more
precisely.

Example:

``` text
order-service client
scope: inventory.reserve
```

Inventory can require:

``` text
SCOPE_inventory.reserve
```

Spring commonly maps OAuth scopes to authorities prefixed with:

``` text
SCOPE_
```

This can be cleaner than creating broad service roles for every
operation.

------------------------------------------------------------------------

# 31. Role-Based Example

Keycloak token:

``` json
{
  "realm_access": {
    "roles": [
      "SYSTEM_ORDER"
    ]
  }
}
```

Inventory:

``` java
.hasRole("SYSTEM_ORDER")
```

Simple and fine for the workout.

------------------------------------------------------------------------

# 32. Scope-Based Example

Token:

``` json
{
  "scope": "inventory.read inventory.reserve"
}
```

Spring authority:

``` text
SCOPE_inventory.reserve
```

Inventory:

``` java
.hasAuthority("SCOPE_inventory.reserve")
```

Architecturally:

``` text
roles = actor/business category
scopes = delegated API permissions
```

Not an absolute law, but a useful model.

------------------------------------------------------------------------

# 33. Service Account in Kubernetes Is NOT OAuth Service Identity

This distinction is commonly confused.

Kubernetes ServiceAccount:

``` text
used by Pods to authenticate to Kubernetes/API/platform mechanisms
```

OAuth client/service identity:

``` text
used by application to authenticate to another protected application API
```

Example:

``` text
order-service Pod
Kubernetes SA: order-k8s-sa

OAuth client:
order-service
```

They solve different identity problems.

------------------------------------------------------------------------

# 34. AWS/EKS Identity Adds Another Layer

In EKS, a workload can also have AWS identity for calls to AWS services.

Example:

``` text
Order Pod
  |
AWS workload identity
  |
S3 / SQS / Secrets Manager
```

That is separate from:

``` text
OAuth service identity
  |
Inventory API
```

Architects need to distinguish:

``` text
Kubernetes identity
AWS cloud identity
application/API identity
end-user identity
```

------------------------------------------------------------------------

# 35. Zero Trust Between Services

Old assumption:

``` text
inside network = trusted
```

Modern approach:

``` text
network location alone is insufficient
```

Inventory should know:

``` text
who called?
is token valid?
is caller allowed?
```

even if the request originates inside the cluster.

Combine:

``` text
OAuth/JWT identity
+
NetworkPolicy
+
least privilege
```

for stronger defense.

------------------------------------------------------------------------

# 36. NetworkPolicy Complements OAuth

OAuth answers:

``` text
Who are you?
What are you allowed to do?
```

NetworkPolicy answers:

``` text
Which Pods can even establish a connection?
```

Example:

``` text
Only order-service Pods
can connect to inventory-service
```

plus:

``` text
Inventory still validates SYSTEM_ORDER token
```

This is defense in depth.

------------------------------------------------------------------------

# 37. mTLS Awareness

mTLS provides mutual certificate-based authentication at the transport
layer.

``` text
Order certificate
     ↓
Inventory verifies caller certificate

Inventory certificate
     ↓
Order verifies server certificate
```

mTLS can provide strong workload identity and encrypted transport.

OAuth/JWT provides application-level identity/claims/permissions.

They can coexist.

Do not answer:

``` text
mTLS replaces OAuth
```

or:

``` text
OAuth makes mTLS useless
```

They protect different layers.

------------------------------------------------------------------------

# 38. User Token Propagation Trade-offs

Advantages:

``` text
downstream knows end user
fine-grained authorization
audit can retain user context
```

Trade-offs:

``` text
token audience may not match every service
larger trust surface
service becomes coupled to external-user claims
delegation semantics can become unclear
```

Do not forward a user token everywhere merely because it is already
available.

------------------------------------------------------------------------

# 39. Service Token Trade-offs

Advantages:

``` text
clear machine identity
least-privilege service permissions
downstream decoupled from end-user token structure
```

Trade-offs:

``` text
end-user context may be lost
IdP/token acquisition dependency
credential lifecycle required
```

Use it when downstream authorization is based on the calling service.

------------------------------------------------------------------------

# 40. Token Audience Matters

A token issued for:

``` text
order-api
```

should not automatically be accepted by:

``` text
inventory-api
```

merely because the issuer is trusted.

Production designs should model and validate audience/resource
boundaries appropriately.

This is one reason blindly propagating one user token to every internal
service can be problematic.

------------------------------------------------------------------------

# 41. Token Exchange Awareness

Sometimes we want:

``` text
incoming user token
      |
Order Service
      |
exchange/delegation
      v
new token appropriate for Inventory
```

The new token may preserve delegated user context while being targeted
to the downstream resource.

Modern Spring Security has OAuth client grant support including
token-exchange capabilities, but this is an advanced design and depends
on authorization-server support/policy.

For this Stage, understand the purpose; do not add token exchange unless
our architecture requires it.

------------------------------------------------------------------------

# 42. Why Not Put Client Credentials in Gateway for Everything?

That would make every downstream request look like:

``` text
caller = gateway
```

Then services lose meaningful caller context.

Gateway service identity may be appropriate for some internal
architecture, but it should not erase user or originating-service
identity without a deliberate reason.

Avoid creating a "God Gateway identity".

------------------------------------------------------------------------

# 43. Should Internal Service-to-Service Traffic Go Through Gateway?

Usually:

``` text
external/north-south:
Client → Gateway → Service
```

Internal/east-west:

``` text
Order → Inventory
```

does not need to route back through the public API Gateway just to
obtain security.

Why?

``` text
extra hop
extra latency
gateway coupling
potential bottleneck
incorrect trust boundary
```

Service-to-service identity should work directly between services.

------------------------------------------------------------------------

# 44. Client Credential Rotation

A client secret is not permanent configuration.

Production process should support:

``` text
issue new credential
deploy/update safely
overlap if supported
revoke old credential
audit
```

Hardcoding a secret in an image makes rotation operationally painful.

------------------------------------------------------------------------

# 45. Keycloak Down --- What Happens?

Separate two flows.

### Existing user token

Gateway/Order may continue validating already-issued JWTs if required
signing keys are available locally/cached.

### Order needs a new service token

Client Credentials requires the token endpoint.

``` text
Keycloak down
   ↓
new service token cannot be obtained
```

If Order already has a still-valid service token, it can normally
continue using it until expiry.

This is why token reuse matters for availability as well as performance.

------------------------------------------------------------------------

# 46. Service Token Expiry

Do not create very long-lived service tokens just to avoid IdP
dependency.

Balance:

``` text
short lifetime
→ smaller stolen-token window
→ more frequent token acquisition

long lifetime
→ less token traffic
→ larger credential exposure window
```

Choose based on risk and operational requirements.

------------------------------------------------------------------------

# 47. Client Credentials and User Authorization

Important Spring Security warning:

``` text
Client Credentials token represents the CLIENT,
not the current user.
```

If your application lets every authenticated user trigger an operation
that automatically uses a powerful service token, then downstream sees
only:

``` text
order-service is allowed
```

Order Service itself must still verify whether the original user was
allowed to cause that action.

Example:

``` text
CUSTOMER cannot invoke admin-only cancellation

Order must reject BEFORE using
its privileged service credential.
```

------------------------------------------------------------------------

# 48. Confused Deputy Problem

A trusted service can become a **confused deputy** if an unprivileged
user tricks it into using its stronger service credentials on their
behalf.

Example:

``` text
Customer
   |
asks Order to reserve arbitrary warehouse stock
   |
Order has SYSTEM_ORDER privilege
   |
Inventory trusts Order
```

Therefore:

> Before using service credentials, the calling service must authorize
> the initiating action according to its own business rules.

Service identity does not eliminate user authorization.

------------------------------------------------------------------------

# 49. Practical Inventory Endpoint

Example:

``` java
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<Void> reserve(
            @PathVariable Long productId,
            @RequestParam int quantity) {

        return ResponseEntity.ok().build();
    }
}
```

Protect it so only the Order service identity can call it.

------------------------------------------------------------------------

# 50. Practical Test --- User Token Directly to Inventory

Call Inventory using Customer token:

``` bash
curl -i \
  http://localhost:8082/inventory/101/reserve?quantity=1 \
  -X POST \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Expected:

``` text
403
```

because CUSTOMER is authenticated but is not the authorized service
identity for reservation.

------------------------------------------------------------------------

# 51. Practical Test --- Order Service Token

Obtain Client Credentials token for `order-service` and call:

``` bash
curl -i \
  http://localhost:8082/inventory/101/reserve?quantity=1 \
  -X POST \
  -H "Authorization: Bearer $ORDER_SERVICE_TOKEN"
```

Expected:

``` text
200
```

assuming the token contains the required service role/scope.

This proves authentication and authorization are about **which identity
is intended for that API**.

------------------------------------------------------------------------

# 52. End-to-End Test Through Order

Call:

``` bash
curl -i \
  "$GATEWAY_URL/api/orders" \
  -X POST \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "productId": 101,
        "quantity": 1
      }'
```

Expected flow:

``` text
CUSTOMER JWT
  ↓
Gateway
  ↓
Order authorizes customer
  ↓
Order gets/reuses service token
  ↓
Inventory authorizes SYSTEM_ORDER
```

------------------------------------------------------------------------

# 53. Test Missing Client Secret

Temporarily remove/break:

``` text
ORDER_OAUTH_CLIENT_SECRET
```

Expected:

``` text
Order can still authenticate incoming customer JWT
```

but:

``` text
Order → Inventory service-token acquisition fails
```

This proves inbound authentication and outbound OAuth Client
configuration are independent concerns.

------------------------------------------------------------------------

# 54. Test Expired Service Token

Allow service token to expire or use a short lab lifetime.

The OAuth client manager should obtain a new token when needed rather
than repeatedly sending an expired token.

Observe:

``` text
Keycloak token endpoint calls
Inventory response
Order logs
```

Do not log the access token itself.

------------------------------------------------------------------------

# 55. Test Keycloak Outage

1.  Obtain a service token.
2.  Make successful Order → Inventory call.
3.  Stop Keycloak.
4.  Call while cached service token is still valid.
5.  Call after a new token becomes necessary.

Observe the difference.

This demonstrates:

``` text
resource-server local validation
vs
new OAuth token acquisition
```

------------------------------------------------------------------------

# 56. Observability

Useful security telemetry:

``` text
authenticated user principal
calling service/client id
route
downstream service
OAuth token acquisition failures
401 count
403 count
correlation/trace ID
```

Never record:

``` text
access token
refresh token
client secret
password
```

Audit identity without leaking credentials.

------------------------------------------------------------------------

# 57. Kubernetes Architecture

``` text
                    Keycloak
                      ^
                      |
      +---------------+---------------+
      |                               |
  JWT validation                Client Credentials
      |                               |
Client → Gateway → Order --------------+
                    |
                 Service JWT
                    |
                    v
                Inventory
```

Kubernetes Services provide connectivity.

OAuth/JWT provides application identity.

NetworkPolicy can reduce connectivity.

These layers solve different problems.

------------------------------------------------------------------------

# 58. EKS Production Mapping

Production may involve:

``` text
Internet
   |
ALB
   |
Gateway Pods
   |
User JWT
   |
Order Pods
   |
OAuth service token
   |
Inventory Pods
```

Alongside:

``` text
AWS workload identity
for AWS APIs

Kubernetes ServiceAccount
for Kubernetes/platform identity

OAuth service identity
for protected application APIs
```

Do not collapse these identities into one conceptual bucket.

------------------------------------------------------------------------

# 59. Architect Scenario --- Propagate User or Service Token?

Good answer:

> I decide based on the downstream authorization model. If the
> downstream service must enforce permissions for the end user, I
> propagate/delegate user identity. If the operation is an internal
> capability granted to the calling service, I use a service identity
> such as Client Credentials. I avoid forwarding user tokens everywhere
> by default.

------------------------------------------------------------------------

# 60. Architect Scenario --- Why Validate Again Downstream?

> Token relay propagates identity; it does not mean the downstream
> service should blindly trust the Gateway. The downstream resource
> server validates the token and enforces its own authorization
> boundary.

------------------------------------------------------------------------

# 61. Architect Scenario --- Why Client Credentials?

> It gives a machine workload its own OAuth identity. Inventory can
> authorize `order-service` with least-privilege scopes/roles without
> pretending the internal operation is performed directly by a human
> user.

------------------------------------------------------------------------

# 62. Architect Scenario --- Why Not Trust `X-User-Id`?

> Headers are caller-controlled unless a trusted component removes
> spoofed values and recreates them after authentication. Signed tokens
> provide a stronger verifiable identity boundary. If internal identity
> headers are used, the trust model must be explicit.

------------------------------------------------------------------------

# 63. Architect Scenario --- Kubernetes ServiceAccount vs OAuth Client?

> Kubernetes ServiceAccount identifies the workload to
> Kubernetes/platform mechanisms. OAuth client identity identifies the
> application to another OAuth-protected API. They are separate trust
> domains even when they belong to the same Pod.

------------------------------------------------------------------------

# 64. Architect Scenario --- mTLS or OAuth?

> They solve different layers. mTLS provides mutually authenticated
> encrypted transport/workload identity, while OAuth tokens carry
> application authorization claims and delegation semantics.
> High-security systems can use both.

------------------------------------------------------------------------

# 65. Architect Scenario --- Keycloak Failure

> Existing JWT validation can often continue while valid signing keys
> are available, but obtaining a new Client Credentials token requires
> the authorization server. I reuse valid service tokens and design IdP
> HA because token issuance is a runtime dependency.

------------------------------------------------------------------------

# 66. Architect Scenario --- Confused Deputy

> A service token may have stronger downstream privileges than the
> initiating user. The calling service must authorize the user's
> requested action before exercising its service credential; otherwise
> it can become a confused deputy.

------------------------------------------------------------------------

# 67. Anti-Patterns

Avoid:

``` text
Forward every user JWT to every service.
Use one SUPER_ADMIN service token everywhere.
Trust X-User-Id from external clients.
Store client secrets in Git.
Request a new service token for every API call.
Send internal service traffic back through public Gateway unnecessarily.
Treat Kubernetes ServiceAccount as OAuth application identity.
Disable downstream JWT validation because "Gateway already checked it."
Log tokens for debugging.
```

------------------------------------------------------------------------

# 68. Hands-On Assignment

## Part A --- User Token

-   Call Gateway with CUSTOMER JWT.
-   Verify Gateway authenticates it.
-   Verify Order receives and validates it.
-   Attempt spoofed identity headers and prove they do not override JWT
    identity.

## Part B --- Service Identity

-   Create Keycloak `order-service` client.
-   Enable Client Credentials/service account.
-   Give only `SYSTEM_ORDER` or equivalent inventory scope.
-   Put client secret in Kubernetes Secret.
-   Configure Order as OAuth2 Client.

## Part C --- Inventory

-   Configure Inventory as Resource Server.
-   Allow reserve endpoint only to service role/scope.
-   CUSTOMER token direct call → 403.
-   Order service token → success.

## Part D --- End to End

Prove:

``` text
Customer
→ Gateway
→ Order
→ service-token acquisition
→ Inventory
```

## Part E --- Failures

-   wrong client secret;
-   expired service token;
-   Keycloak unavailable;
-   CUSTOMER token directly to service-only endpoint;
-   spoofed internal identity header.

------------------------------------------------------------------------

# 69. Completion Checklist

## Concepts

-   [ ] Token relay vs JWT validation.
-   [ ] Resource Server vs OAuth2 Client.
-   [ ] User identity vs service identity.
-   [ ] Client Credentials.
-   [ ] Service account in Keycloak.
-   [ ] User-token propagation.
-   [ ] Service-token propagation.
-   [ ] Delegation.
-   [ ] Roles vs scopes.
-   [ ] Least privilege.
-   [ ] Token audience.
-   [ ] Token exchange awareness.
-   [ ] Header spoofing.
-   [ ] Kubernetes SA vs OAuth identity.
-   [ ] mTLS awareness.
-   [ ] Zero Trust.
-   [ ] Confused deputy.

## Implementation

-   [ ] Gateway preserves/relays correct user token.
-   [ ] Order validates incoming user token.
-   [ ] Order OAuth2 Client configured.
-   [ ] Client Credentials works.
-   [ ] Client secret stored in Kubernetes Secret.
-   [ ] Inventory validates service token.
-   [ ] Inventory enforces service permission.

## Testing

-   [ ] CUSTOMER token → Gateway/Order success.
-   [ ] CUSTOMER token direct to service-only Inventory API → 403.
-   [ ] Order service token → Inventory success.
-   [ ] Spoofed role/header does not gain permission.
-   [ ] Wrong service client secret fails token acquisition.
-   [ ] IdP outage behavior observed.
-   [ ] No credentials are logged.

------------------------------------------------------------------------

# 70. Coding-Agent Prompt

``` text
Inspect the existing Stage 5 microservices-workouts implementation first.

Implement Stage 6 only:
Token Relay & Service-to-Service Security.

Architecture:
Client -> Gateway -> Order -> Inventory
Keycloak is the IdP.
Gateway is WebFlux.
Order and Inventory are Spring Boot services.
Minikube/Kubernetes is the runtime.

Required identity model:

A. Client -> Gateway -> Order
- use the authenticated user's access token
- Gateway validates JWT
- Order independently validates JWT
- do not invent X-User-Id/X-Role authentication

B. Order -> Inventory
- Order acts as OAuth2 Client
- use Client Credentials
- Keycloak client id: order-service
- service account permission: SYSTEM_ORDER (or equivalent existing service scope)
- Inventory independently validates the service access token
- reserve API accepts service identity, not normal CUSTOMER identity

Implementation requirements:
1. Inspect Spring Boot/Spring Security versions before choosing APIs.
2. Add oauth2-client dependency to Order only if missing.
3. Configure client registration using issuer URI and environment variables.
4. Store client secret in Kubernetes Secret.
5. Reuse Spring Security OAuth2 client/authorized-client support rather than manually calling token endpoint in business code.
6. Preserve existing Stage 5 user-token security.
7. Reuse existing role converter if Keycloak realm roles require it.
8. Do not route Order -> Inventory back through public Gateway.
9. Never log access tokens or client secrets.
10. Keep existing probes/resources/HPA.

Provide tests for:
- CUSTOMER user token through Gateway -> Order
- spoofed X-User-Id/X-Role ignored
- CUSTOMER token direct to Inventory service-only endpoint -> 403
- Order service token -> Inventory success
- wrong client secret
- Keycloak outage before/after service token expiry
- verify actual principal/authorities safely

Explain why user token and service token are different identities.
```

------------------------------------------------------------------------

# 71. Rapid Interview Revision

### Token relay?

Forwarding an OAuth access token to a downstream protected resource.

### Resource Server?

Validates incoming access tokens.

### OAuth2 Client?

Obtains/manages access tokens for outbound protected-resource calls.

### Client Credentials?

Machine-to-machine grant where the token represents the client/service,
not a user.

### User token or service token?

Choose according to downstream authorization requirements.

### Why not forward user token everywhere?

Audience, least privilege, coupling and delegation semantics may be
wrong.

### Why not trust identity headers?

They are spoofable unless created inside an explicit trusted boundary.

### Kubernetes ServiceAccount vs OAuth client?

Platform identity vs application/API identity.

### mTLS?

Transport/workload authentication; complementary to OAuth application
authorization.

### Biggest service-token risk?

A privileged service can become a confused deputy if it fails to
authorize the initiating user action.

------------------------------------------------------------------------

# 72. Correct Next Stage

The original curriculum sequence is:

``` text
05 Gateway Security — OAuth2, OIDC & JWT
06 Token Relay & Service-to-Service Security
07 Rate Limiting with Redis
08 Timeout, Retry & Circuit Breaker
09 Idempotency & Safe Retries
...
```

So after completing this document, continue with the corrected:

``` text
07-rate-limiting-redis.md
```

------------------------------------------------------------------------

# Stage 6 One-Line Summary

> **Identity propagation is an architectural choice: relay/delegate user
> identity when downstream authorization needs the user, use Client
> Credentials when a service acts on its own authority, validate every
> trusted token at the receiving service, and never reconstruct identity
> from spoofable network headers.**
