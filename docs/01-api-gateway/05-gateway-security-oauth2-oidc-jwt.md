# API Gateway --- Stage 5: Gateway Security --- OAuth2, OIDC & JWT

> **Previous:**
> `04-load-balancing-scaling-high-availability-revised-v2.md`\
> **Next:** `06-token-relay-service-to-service-security.md`\
> **Runtime:** Kubernetes / Minikube\
> **Identity Provider:** Keycloak\
> **Gateway:** Spring Cloud Gateway WebFlux\
> **Goal:** Secure external API access without making the Gateway the
> only security boundary.

------------------------------------------------------------------------

# 1. What Problem Are We Solving?

So far the architecture can route and scale traffic:

``` text
Client
  |
Gateway
  |
Kubernetes Service
  |
Order Pods
```

But without security, anybody who can reach an API could potentially
call:

``` text
GET    /api/orders
POST   /api/orders
DELETE /api/orders/100
```

Stage 5 adds identity:

``` text
Who is the caller?
Is the token genuine?
Has it expired?
Was it issued by our trusted Identity Provider?
What is the caller allowed to do?
Should Order Service verify the request again?
```

Target architecture:

``` text
                   Keycloak / IdP
                        |
                 authenticate user
                        |
                        v
Client ------------- Access JWT
                        |
                        v
                 API Gateway
                 validate JWT
                 coarse authorization
                        |
                        v
                  Order Service
                  validate JWT
                  domain authorization
```

------------------------------------------------------------------------

# 2. Authentication vs Authorization

These are related but different.

## Authentication

``` text
Who are you?
```

Example:

``` text
JWT subject = customer1
```

## Authorization

``` text
What are you allowed to do?
```

Example:

``` text
CUSTOMER → read/create own orders
ADMIN    → delete any order
```

A valid JWT proves authentication only after successful validation.

It does **not** mean the caller is allowed to perform every operation.

------------------------------------------------------------------------

# 3. OAuth2, OIDC and JWT --- Keep Them Separate

These terms are often mixed together in interviews.

## OAuth 2.x

OAuth is primarily an **authorization framework**.

It defines how a client obtains an access token and uses it to access a
protected resource.

Conceptually:

``` text
Client
  |
  | obtain authorization
  v
Authorization Server
  |
Access Token
  v
Client
  |
Access Token
  v
Resource Server
```

## OpenID Connect --- OIDC

OIDC adds an **identity/authentication layer** on top of OAuth.

It answers questions such as:

``` text
Who logged in?
```

and introduces concepts such as:

``` text
ID Token
UserInfo
OIDC discovery
```

## JWT

JWT is a **token format**.

A JWT commonly looks like:

``` text
xxxxx.yyyyy.zzzzz
```

Those sections represent:

``` text
Header.Payload.Signature
```

OAuth is not JWT.

OIDC is not JWT.

OAuth access tokens can be JWTs, but they do not have to be.

------------------------------------------------------------------------

# 4. Main Actors in Our Architecture

``` text
Customer UI
    |
    v
Keycloak
    |
    | issues token
    v
Customer UI
    |
    | Bearer JWT
    v
Gateway
    |
    v
Order Service
```

Roles:

``` text
Keycloak
= Identity Provider / Authorization Server

Gateway
= Resource Server

Order Service
= Resource Server

Customer UI
= OAuth/OIDC Client
```

A **Resource Server** is an API that accepts an access token and
validates it before serving protected resources.

------------------------------------------------------------------------

# 5. Access Token vs ID Token

This distinction matters.

## ID Token

Primarily tells the client application about the authenticated user.

``` text
ID Token
→ intended for the OIDC client
```

## Access Token

Used to access protected APIs.

``` text
Access Token
→ Gateway / API
```

For:

``` http
Authorization: Bearer ...
```

we want the **access token**, not the ID token.

Interview answer:

> ID Token is primarily authentication information for the OIDC client.
> Access Token is the credential presented to a protected resource/API.

------------------------------------------------------------------------

# 6. JWT Structure

Example conceptual JWT:

``` text
eyJhbGciOiJSUzI1Ni...
.
eyJzdWIiOiJjdXN0b21lcjEi...
.
signature...
```

Decoded conceptually:

``` json
{
  "alg": "RS256",
  "kid": "key-123"
}
```

Payload:

``` json
{
  "iss": "http://keycloak/realms/ecom-realm",
  "sub": "user-123",
  "exp": 1788000000,
  "iat": 1787999000,
  "scope": "openid profile",
  "realm_access": {
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

The payload is **encoded, not encrypted**.

Never place secrets in JWT claims.

------------------------------------------------------------------------

# 7. Why the JWT Signature Matters

Suppose an attacker changes:

``` json
"roles": ["CUSTOMER"]
```

to:

``` json
"roles": ["ADMIN"]
```

The payload can physically be modified.

But the attacker cannot produce a valid signature without the issuer's
private signing key.

Gateway verifies the signature using the trusted public key.

Result:

``` text
modified payload
      +
old signature
      ↓
signature verification fails
      ↓
401
```

That is why decoding a JWT is not the same as validating it.

------------------------------------------------------------------------

# 8. Public-Key Validation

Typical asymmetric model:

``` text
Keycloak
   |
Private Key
   |
signs JWT
   v
Access Token
```

Gateway:

``` text
Access Token
   |
Public Key
   |
verify signature
```

Keycloak keeps the private key.

Resource servers obtain public signing keys through the issuer/JWK
metadata.

This means Gateway does not need Keycloak's private signing key.

------------------------------------------------------------------------

# 9. What Is JWK / JWKS?

JWK:

``` text
JSON Web Key
```

JWKS:

``` text
JSON Web Key Set
```

Keycloak exposes public keys that resource servers can use to validate
JWT signatures.

Conceptual flow:

``` text
Gateway
   |
issuer metadata
   v
Keycloak OIDC discovery
   |
jwks_uri
   v
public signing keys
```

The JWT header contains a key identifier:

``` json
{
  "kid": "abc123"
}
```

The resource server finds the corresponding public key and verifies the
signature.

------------------------------------------------------------------------

# 10. OIDC Discovery

Keycloak exposes a discovery endpoint:

``` text
/realms/{realm}/.well-known/openid-configuration
```

For our realm:

``` text
/realms/ecom-realm/.well-known/openid-configuration
```

It describes important endpoints such as:

``` text
authorization endpoint
token endpoint
userinfo endpoint
JWKS/certificate endpoint
```

This is why Spring can often be configured using only an `issuer-uri`.

------------------------------------------------------------------------

# 11. Important JWT Claims

## `iss` --- Issuer

``` text
Who issued this token?
```

Example:

``` text
https://id.example.com/realms/ecom-realm
```

Gateway should trust only the expected issuer.

## `sub` --- Subject

Usually identifies the authenticated principal.

``` text
user-123
```

## `exp` --- Expiration

Token must not be accepted indefinitely.

## `nbf` --- Not Before

Token should not be accepted before this time.

## `aud` --- Audience

``` text
Who is this token intended for?
```

Production APIs should consider audience validation so a token intended
for another resource is not accepted simply because it has the same
trusted issuer.

## Roles / Scopes

Used for authorization.

------------------------------------------------------------------------

# 12. What Spring Security Validates

With JWT Resource Server configuration, Spring Security can validate:

``` text
signature
issuer
expiration
not-before
```

and map scopes to Spring authorities.

Audience validation can also be configured when required.

The exact validation policy is part of our security architecture---not
something we should leave implicit.

------------------------------------------------------------------------

# 13. 401 vs 403

This is a common interview question.

## 401 Unauthorized

Authentication failed or is missing.

Examples:

``` text
no token
invalid token
expired token
bad signature
wrong trusted issuer
```

Think:

``` text
"I cannot establish a valid authenticated caller."
```

## 403 Forbidden

Authentication succeeded, but authorization failed.

Example:

``` text
valid CUSTOMER token
        |
DELETE /api/orders/100
        |
requires ADMIN
        ↓
403
```

Think:

``` text
"I know who you are, but you cannot do this."
```

------------------------------------------------------------------------

# 14. Roles vs Scopes

A practical mental model:

``` text
Role
→ what kind of actor / business authority?

Scope
→ what API permission was granted?
```

Examples:

``` text
Role:
CUSTOMER
ADMIN

Scope:
orders.read
orders.write
```

Spring Security commonly converts OAuth scopes to authorities such as:

``` text
SCOPE_orders.read
```

Keycloak realm roles may require a custom JWT authority converter
because they can appear inside:

``` json
realm_access.roles
```

------------------------------------------------------------------------

# 15. Our Roles

For this workout:

``` text
CUSTOMER
ADMIN
SYSTEM_ORDER
```

Meaning:

``` text
CUSTOMER
→ normal customer operations

ADMIN
→ privileged administrative operations

SYSTEM_ORDER
→ machine/service role used in later service-to-service security
```

Stage 6 will go deeper into service identity.

------------------------------------------------------------------------

# 16. Gateway Authorization vs Service Authorization

Gateway can perform **coarse-grained authorization**.

Example:

``` text
/api/admin/**
→ ADMIN only
```

But Gateway should not own domain rules such as:

``` text
Does customer 101 own order 500?
Can this order be cancelled in its current state?
Is this refund allowed?
```

Those belong to the responsible domain service.

Good separation:

``` text
Gateway
→ broad edge policy

Order Service
→ business/domain authorization
```

------------------------------------------------------------------------

# 17. Why Validate JWT Again in Order Service?

A tempting design:

``` text
Gateway validates token
       |
       v
Order Service trusts everything
```

Problem:

``` text
What if Order becomes reachable through another path?
What if internal traffic bypasses Gateway?
What if another compromised workload calls Order?
What if future architecture changes the entry path?
```

Safer model:

``` text
Gateway validates
       |
       v
Order validates again
```

This is defense in depth and aligns with Zero Trust thinking.

------------------------------------------------------------------------

# 18. Zero Trust in Simple English

Old assumption:

``` text
inside our network = trusted
```

Zero Trust principle:

``` text
network location alone does not establish trust
```

So Order asks:

``` text
Is this token genuine?
Who is the caller?
Does the caller have permission?
```

even though the request came from inside Kubernetes.

------------------------------------------------------------------------

# 19. Target Authorization Rules

Example:

``` text
/actuator/health/**
→ public for Kubernetes probes

/api/orders/**
→ CUSTOMER / ADMIN

/api/admin/**
→ ADMIN
```

Order Service:

``` text
GET /orders/**
→ CUSTOMER / ADMIN / SYSTEM_ORDER

POST /orders/**
→ CUSTOMER / ADMIN

DELETE /orders/**
→ ADMIN
```

We can refine ownership rules at service level.

------------------------------------------------------------------------

# 20. Gateway Dependencies

For Spring Cloud Gateway WebFlux:

``` gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```

Stage 5 needs Resource Server support because Gateway validates bearer
JWTs.

Do **not** add OAuth2 Client merely because OAuth is involved.

OAuth2 Client becomes relevant when Gateway itself participates as an
OAuth client/login/token-relay component, which Stage 6 discusses.

------------------------------------------------------------------------

# 21. Gateway JWT Configuration

`application-k8s.yml`:

``` yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
```

Kubernetes environment:

``` yaml
env:
  - name: JWT_ISSUER_URI
    value: "http://keycloak:8080/realms/ecom-realm"
```

Important:

> The configured issuer must match the token's `iss` claim.

A wrong issuer is not a harmless URL mismatch---it means the token came
from a different security authority than the resource server expects.

------------------------------------------------------------------------

# 22. Gateway SecurityWebFilterChain

Gateway WebFlux uses reactive Spring Security.

``` java
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http) {

        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            .authorizeExchange(exchange -> exchange
                .pathMatchers("/actuator/health/**")
                    .permitAll()

                .pathMatchers("/api/admin/**")
                    .hasRole("ADMIN")

                .pathMatchers("/api/orders/**")
                    .hasAnyRole(
                        "CUSTOMER",
                        "ADMIN",
                        "SYSTEM_ORDER"
                    )

                .anyExchange()
                    .authenticated()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            )

            .build();
    }
}
```

This establishes:

``` text
public health endpoint
protected business endpoints
JWT Resource Server
route-level authorization
```

------------------------------------------------------------------------

# 23. Keycloak Realm Roles Need Mapping

Spring automatically understands standard scope claims well.

But Keycloak realm roles often look like:

``` json
{
  "realm_access": {
    "roles": [
      "CUSTOMER",
      "offline_access"
    ]
  }
}
```

`hasRole("CUSTOMER")` expects an authority:

``` text
ROLE_CUSTOMER
```

So we need to convert:

``` text
realm_access.roles
        ↓
CUSTOMER
        ↓
ROLE_CUSTOMER
```

------------------------------------------------------------------------

# 24. Reactive Keycloak Role Converter

Conceptual implementation:

``` java
@Bean
Converter<Jwt, Mono<AbstractAuthenticationToken>>
authenticationConverter() {

    JwtAuthenticationConverter delegate =
        new JwtAuthenticationConverter();

    delegate.setJwtGrantedAuthoritiesConverter(
        new KeycloakRealmRoleConverter()
    );

    return new ReactiveJwtAuthenticationConverterAdapter(
        delegate
    );
}
```

Role converter:

``` java
public class KeycloakRealmRoleConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        Map<String, Object> realmAccess =
            jwt.getClaimAsMap("realm_access");

        if (realmAccess == null) {
            return List.of();
        }

        Object rolesObject = realmAccess.get("roles");

        if (!(rolesObject instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
            .map(Object::toString)
            .map(role ->
                new SimpleGrantedAuthority("ROLE_" + role)
            )
            .toList();
    }
}
```

Then connect the converter:

``` java
.oauth2ResourceServer(oauth2 ->
    oauth2.jwt(jwt ->
        jwt.jwtAuthenticationConverter(
            authenticationConverter()
        )
    )
)
```

------------------------------------------------------------------------

# 25. Do Not Accidentally Lose Scope Authorities

Suppose later we need both:

``` text
ROLE_CUSTOMER
```

and:

``` text
SCOPE_orders.read
```

A custom converter that returns only Keycloak realm roles can
accidentally discard Spring's default scope authorities.

Production-quality mapping should combine:

``` text
default scope authorities
+
Keycloak role authorities
```

This is a subtle but useful interview point.

------------------------------------------------------------------------

# 26. Order Service Dependencies

Order Service should also be a Resource Server:

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

Order independently validates the JWT.

------------------------------------------------------------------------

# 27. Order SecurityFilterChain

Order is a normal servlet/MVC Spring Boot service:

``` java
@Configuration
@EnableWebSecurity
public class OrderSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**")
                    .permitAll()

                .requestMatchers(
                    HttpMethod.DELETE,
                    "/orders/**"
                )
                    .hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.GET,
                    "/orders/**"
                )
                    .hasAnyRole(
                        "CUSTOMER",
                        "ADMIN",
                        "SYSTEM_ORDER"
                    )

                .requestMatchers(
                    HttpMethod.POST,
                    "/orders/**"
                )
                    .hasAnyRole(
                        "CUSTOMER",
                        "ADMIN"
                    )

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

Reuse the Keycloak role converter from the Gateway concept, adapted for
servlet security.

------------------------------------------------------------------------

# 28. Coarse vs Domain Authorization

Suppose:

``` text
customer1 owns order 100
customer2 owns order 200
```

Both have:

``` text
ROLE_CUSTOMER
```

Gateway cannot safely decide:

``` text
customer1 may access order 100
but not order 200
```

without taking ownership of Order domain data.

Order Service should perform:

``` text
authenticated user
        +
requested order
        +
order ownership/business state
        ↓
domain authorization
```

Example:

``` java
if (!order.getCustomerId().equals(currentCustomerId)
        && !isAdmin(authentication)) {
    throw new AccessDeniedException("Forbidden");
}
```

------------------------------------------------------------------------

# 29. Do Not Trust Customer ID from Request Body

Bad design:

``` json
{
  "customerId": "customer999",
  "productId": 100
}
```

and then:

``` java
order.setCustomerId(request.customerId());
```

An authenticated customer could create an order for another identity.

Better:

``` text
authenticated JWT
      ↓
trusted subject/customer claim
      ↓
derive caller identity
```

The token is the trusted identity source, not a caller-controlled
`customerId`.

------------------------------------------------------------------------

# 30. Keycloak Local Lab Setup

Run Keycloak in Minikube.

Conceptual deployment:

``` yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: keycloak
  template:
    metadata:
      labels:
        app: keycloak
    spec:
      containers:
        - name: keycloak
          image: quay.io/keycloak/keycloak:<PINNED_VERSION>
          args:
            - start-dev
          ports:
            - containerPort: 8080
```

For a learning environment, `start-dev` is acceptable.

It is **not** our production Keycloak configuration.

Pin a tested image version in the actual project instead of relying on
`latest`.

------------------------------------------------------------------------

# 31. Keycloak Service

``` yaml
apiVersion: v1
kind: Service
metadata:
  name: keycloak
  namespace: ecommerce
spec:
  selector:
    app: keycloak
  ports:
    - port: 8080
      targetPort: 8080
```

Inside namespace:

``` text
http://keycloak:8080
```

Realm issuer:

``` text
http://keycloak:8080/realms/ecom-realm
```

But there is an important issuer-address issue when tokens are obtained
from outside the cluster. We handle that shortly.

------------------------------------------------------------------------

# 32. Create Realm

Create:

``` text
ecom-realm
```

Do not use:

``` text
master
```

for application identities.

The master realm is for Keycloak administration.

------------------------------------------------------------------------

# 33. Create Roles

Realm roles:

``` text
CUSTOMER
ADMIN
SYSTEM_ORDER
```

Assign:

``` text
customer1 → CUSTOMER
admin1    → ADMIN
```

Stage 6 will use `SYSTEM_ORDER` for machine identity.

------------------------------------------------------------------------

# 34. Create Public UI Client

Example:

``` text
customer-portal-ui
```

For browser applications:

``` text
public client
Authorization Code flow
PKCE
```

Do not put a reusable client secret in browser JavaScript.

Browser apps cannot safely protect a client secret.

------------------------------------------------------------------------

# 35. Why Authorization Code + PKCE?

Simplified flow:

``` text
Browser
   |
redirect
   v
Keycloak Login
   |
Authorization Code
   v
Browser
   |
code + PKCE verifier
   v
Keycloak
   |
Access Token
```

PKCE protects the authorization-code exchange against code interception.

For modern browser/mobile clients, this is the normal direction rather
than teaching legacy password-grant flows as the primary architecture.

------------------------------------------------------------------------

# 36. Password Grant Awareness

For quick labs, people sometimes directly send:

``` text
username
password
client_id
```

to the token endpoint.

Do not make this the architecture you present in an interview for a
modern user-facing application.

Our architectural flow is:

``` text
Authorization Code + PKCE
```

If a simplified lab token-generation method is temporarily used, label
it as a testing shortcut.

------------------------------------------------------------------------

# 37. Obtain Token for Testing

After configuring the realm/client/user, obtain a CUSTOMER access token
using the chosen lab flow.

Store:

``` bash
export CUSTOMER_TOKEN='...'
```

and:

``` bash
export ADMIN_TOKEN='...'
```

Do not commit tokens to files or shell scripts.

------------------------------------------------------------------------

# 38. Inspect JWT Safely

Decode the token payload for learning.

Look for:

``` text
iss
sub
exp
aud
scope
realm_access.roles
```

Remember:

``` text
decode != validate
```

Anyone can decode an unencrypted JWT payload.

Trust comes from cryptographic validation plus claim validation.

------------------------------------------------------------------------

# 39. Test 1 --- Health Without Token

``` bash
curl -i \
  "$GATEWAY_URL/actuator/health"
```

Expected:

``` text
HTTP 200
```

Why public?

Kubernetes readiness/liveness probes should not need a user JWT.

------------------------------------------------------------------------

# 40. Test 2 --- Protected API Without Token

``` bash
curl -i \
  "$GATEWAY_URL/api/orders"
```

Expected:

``` text
401 Unauthorized
```

Request is stopped before Order business logic executes.

------------------------------------------------------------------------

# 41. Test 3 --- Valid CUSTOMER Token

``` bash
curl -i \
  "$GATEWAY_URL/api/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Expected:

``` text
200
```

Flow:

``` text
Gateway validates token
Gateway authorizes CUSTOMER
Gateway routes request
Order validates token
Order authorizes CUSTOMER
```

------------------------------------------------------------------------

# 42. Test 4 --- CUSTOMER Calls ADMIN API

``` bash
curl -i \
  "$GATEWAY_URL/api/admin/report" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Expected:

``` text
403 Forbidden
```

The token is valid.

The permission is insufficient.

------------------------------------------------------------------------

# 43. Test 5 --- ADMIN Token

``` bash
curl -i \
  "$GATEWAY_URL/api/admin/report" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected:

``` text
200
```

assuming the route and backend endpoint exist.

------------------------------------------------------------------------

# 44. Test 6 --- Tampered JWT

Take a valid token and modify its payload.

Example:

``` text
CUSTOMER → ADMIN
```

Send the altered token.

Expected:

``` text
401
```

because the signature no longer matches the content.

This is one of the best practical exercises for understanding JWT
signatures.

------------------------------------------------------------------------

# 45. Test 7 --- Expired Token

Use an expired token.

Expected:

``` text
401
```

Resource Server validates token time constraints.

------------------------------------------------------------------------

# 46. Test 8 --- Wrong Issuer

Use a correctly signed token from a different untrusted realm/issuer.

Expected:

``` text
401
```

This proves:

``` text
valid cryptographic signature alone
!=
trusted token
```

The issuer must also match the configured trust boundary.

------------------------------------------------------------------------

# 47. Test 9 --- Wrong Audience

If audience validation is configured:

``` text
token aud = some-other-api
expected  = ecommerce-api
```

Expected:

``` text
401
```

This prevents a token intended for another resource from being reused
against this API.

------------------------------------------------------------------------

# 48. Test 10 --- Direct Order Call Without Token

Expose Order temporarily for the test or call it from a temporary Pod:

``` bash
kubectl run curl-test \
  -n ecommerce \
  --rm -it \
  --image=curlimages/curl \
  -- sh
```

Then:

``` bash
curl -i http://order-service:8080/orders
```

Expected:

``` text
401
```

This proves Order does not depend solely on Gateway for authentication.

------------------------------------------------------------------------

# 49. Direct Order Call With Valid Token

Inside the cluster:

``` bash
curl -i \
  http://order-service:8080/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Expected:

``` text
200
```

Order is independently capable of validating the caller.

------------------------------------------------------------------------

# 50. What Happens During JWT Validation?

Conceptual sequence:

``` text
Request
   |
Authorization: Bearer JWT
   v
Bearer token authentication filter
   |
decode JWT
   |
read kid
   |
find trusted public key
   |
verify signature
   |
validate iss
validate exp/nbf
validate aud if configured
   |
convert claims/scopes/roles
   |
Authentication object
   |
authorization rules
   |
Controller
```

Failure before authenticated identity:

``` text
401
```

Authenticated but insufficient authority:

``` text
403
```

------------------------------------------------------------------------

# 51. Does Gateway Call Keycloak for Every Request?

With self-contained JWT validation:

``` text
No.
```

Typical behavior:

``` text
Gateway
   |
uses locally available/cached public key material
   |
validates JWT locally
```

This is one scalability advantage of JWTs.

Keycloak does not need a synchronous introspection call for every normal
JWT request.

------------------------------------------------------------------------

# 52. Then Why Does Gateway Need Keycloak/JWKS?

Signing keys can change.

Resource Server needs trusted public key material.

Conceptually:

``` text
JWT kid
  |
resource server key cache
  |
known?
 /   \
yes   no
 |     |
verify refresh/discover key material
```

Spring Security supports JWK-based key rotation.

So the IdP is not necessarily in the hot path for every request, but it
remains an important dependency for metadata/key discovery and token
issuance.

------------------------------------------------------------------------

# 53. Key Rotation

Suppose Keycloak changes:

``` text
old private key
→ new private key
```

New tokens use a new:

``` text
kid
```

Resource servers obtain updated public key material.

A good identity platform rotates keys without requiring every
microservice to be redeployed with hard-coded public keys.

Interview point:

> Prefer issuer/JWKS-based trust and planned key rotation over manually
> distributing static signing keys to every service.

------------------------------------------------------------------------

# 54. Keycloak Down --- Existing JWTs

Important failure scenario.

Suppose:

``` text
Keycloak becomes unavailable
```

A user already has:

``` text
valid unexpired JWT
```

Gateway may still validate it using already available public key
material.

So:

``` text
Keycloak outage
!=
all API requests instantly fail
```

But operations requiring:

``` text
new login
new token
token refresh
unknown signing key discovery
```

may fail.

Actual behavior depends on key availability/cache/configuration, so test
it with the exact Spring/Keycloak versions used by the project.

------------------------------------------------------------------------

# 55. JWT Revocation Trade-Off

Self-contained JWT:

``` text
issued at 10:00
expires at 10:15
```

At 10:05:

``` text
ADMIN role removed
```

The already-issued JWT may still contain:

``` text
ADMIN
```

until the token expires or another revocation/control mechanism prevents
its use.

This is a trade-off:

``` text
local validation and scalability
vs
immediate centralized revocation
```

Shorter access-token lifetime reduces the exposure window but increases
token-renewal activity.

------------------------------------------------------------------------

# 56. JWT vs Opaque Token

## JWT

``` text
self-contained claims
local validation possible
good distributed scalability
revocation can be less immediate
```

## Opaque Token

``` text
token itself carries no useful client-readable claims
resource server may use introspection
central authorization server can provide current token state
adds runtime network dependency
```

Do not say:

``` text
JWT is always better.
```

Choose based on security, revocation, latency, availability and
operational requirements.

------------------------------------------------------------------------

# 57. CSRF --- Why Are We Disabling It Here?

Our APIs use bearer tokens in the:

``` http
Authorization
```

header rather than browser cookies automatically attached to every
request.

For a stateless bearer-token API, disabling CSRF is commonly
appropriate.

But do not memorize:

``` text
REST API = always disable CSRF
```

If authentication uses cookies/browser sessions, the threat model
changes.

------------------------------------------------------------------------

# 58. CORS Is Not Authentication

CORS answers:

``` text
May browser JavaScript from origin X call this API?
```

OAuth/JWT answers:

``` text
Who is the caller?
Is the token trusted?
What may the caller do?
```

CORS is a browser policy.

It does not protect APIs from:

``` text
curl
Postman
server-to-server callers
```

Never use CORS as an authentication mechanism.

------------------------------------------------------------------------

# 59. Do Not Log Tokens

Bad:

``` java
log.info("Authorization={}",
         request.getHeaders().getFirst("Authorization"));
```

Access tokens are credentials.

Logging them can leak identity and authorization capability into:

``` text
ELK
CloudWatch
support exports
developer consoles
incident tickets
```

Log:

``` text
principal/subject
client id if appropriate
roles/scopes where safe
trace id
request path
authorization outcome
```

not the raw token.

------------------------------------------------------------------------

# 60. Kubernetes Secret vs JWT Public Configuration

This configuration:

``` text
JWT_ISSUER_URI
```

is normally not a secret.

A client secret/password is.

Use:

``` text
ConfigMap/env
→ non-secret endpoints/configuration

Secret/external secret manager
→ credentials
```

Do not put everything into Kubernetes Secret merely because it relates
to security.

------------------------------------------------------------------------

# 61. Network-Level Protection

Even with JWT validation, backend services should normally not be
publicly exposed.

Preferred:

``` text
Internet
   |
Gateway
   |
ClusterIP
   |
Order
```

not:

``` text
Internet
   +--> Gateway
   |
   +--> Order NodePort
```

Application security and network exposure should reinforce each other.

------------------------------------------------------------------------

# 62. NetworkPolicy Awareness

In production-style Kubernetes:

``` text
Gateway
   |
allowed
   v
Order
```

Other unrelated workloads can be denied by NetworkPolicy where
appropriate.

But:

``` text
NetworkPolicy != authorization
```

Order should still validate application identity.

This is defense in depth.

------------------------------------------------------------------------

# 63. Issuer URL Problem in Minikube

This is an important practical edge case.

Suppose the browser obtains a token from:

``` text
http://localhost:8080/realms/ecom-realm
```

Then token contains:

``` json
"iss":
"http://localhost:8080/realms/ecom-realm"
```

But Gateway inside Kubernetes is configured:

``` text
http://keycloak:8080/realms/ecom-realm
```

These issuer values differ.

Result:

``` text
JWT issuer validation fails
```

even though both addresses reach the same Keycloak instance.

The issuer is an **identity identifier**, not merely a network location.

------------------------------------------------------------------------

# 64. Solve Issuer Addressing Deliberately

Use one stable issuer hostname that:

``` text
clients can reach
AND
resource servers can resolve/reach
AND
matches token iss exactly
```

Possible lab approaches:

``` text
Minikube ingress/host mapping
stable local DNS name
Keycloak hostname configuration
```

Do not "solve" it by disabling issuer validation.

Production identity URLs should be stable externally meaningful names,
for example:

``` text
https://identity.example.com/realms/ecommerce
```

------------------------------------------------------------------------

# 65. Clock Skew

JWT time validation depends on clocks.

Claims:

``` text
iat
nbf
exp
```

If machines have significantly incorrect clocks, valid tokens can
appear:

``` text
not yet valid
or
already expired
```

Production nodes should have reliable time synchronization.

Spring Security permits reasonable timestamp validation behavior, but
clock skew is not an excuse for badly synchronized infrastructure.

------------------------------------------------------------------------

# 66. Failure Matrix

  Scenario                                   Expected
  ------------------------------------------ -------------------
  No token                                   401
  Malformed token                            401
  Tampered signature                         401
  Expired token                              401
  Wrong issuer                               401
  Wrong audience when enforced               401
  CUSTOMER accesses CUSTOMER API             success
  CUSTOMER accesses ADMIN API                403
  ADMIN accesses ADMIN API                   success
  Direct Order without token                 401
  Valid user requests another user's order   service-level 403
  Spoofed role header                        ignored
  Keycloak down + known key + valid JWT      may continue
  Keycloak down + new login/token needed     fails

------------------------------------------------------------------------

# 67. Security Responsibility Matrix

  Responsibility                        Gateway                   Order
  ---------------------------------- ---------- -----------------------
  Validate external JWT                     Yes                     Yes
  Basic authenticated route policy          Yes                     Yes
  Broad role checks                         Yes   Yes where appropriate
  Order ownership                            No                     Yes
  Order state/business permission            No                     Yes
  Hide backend topology                     Yes                      No
  Network exposure policy              Platform                Platform
  Token issuance                             No                      No
  Identity lifecycle                   Keycloak                Keycloak

The key principle:

> Gateway security reduces bad traffic early, but domain services remain
> responsible for their own security boundary.

------------------------------------------------------------------------

# 68. What Should NOT Go Into Gateway?

Avoid turning Gateway into:

``` text
authentication
+
order ownership logic
+
payment permission logic
+
inventory business rules
+
customer database lookups
+
every authorization rule
```

That becomes a:

``` text
God Gateway
```

Problems:

``` text
tight coupling
deployment bottleneck
domain leakage
harder scaling
larger blast radius
```

Gateway should stay focused on cross-cutting edge concerns.

------------------------------------------------------------------------

# 69. Production Security Checklist

Before calling Gateway security production-ready:

-   trusted issuer configured;
-   signature validation active;
-   token expiration validated;
-   audience strategy decided;
-   health endpoints intentionally public;
-   business routes authenticated;
-   coarse route authorization configured;
-   domain authorization remains in services;
-   backend services independently validate identity;
-   backend services not unnecessarily public;
-   roles/scopes follow least privilege;
-   tokens and secrets are never logged;
-   key rotation tested;
-   expired/tampered/wrong-issuer tokens tested;
-   401 and 403 metrics observable;
-   client secrets stored outside source control;
-   HTTPS/TLS used in production;
-   stable issuer hostname used;
-   IdP HA/failure behavior understood.

------------------------------------------------------------------------

# 70. Architect Interview Questions

## Q1. Why validate JWT at Gateway and service?

> Gateway rejects invalid or unauthorized traffic early, while service
> validation preserves the service's own trust boundary and protects
> against bypass/internal calls. This is defense in depth rather than
> assuming all internal traffic is trusted.

## Q2. OAuth vs OIDC?

> OAuth is primarily authorization/delegated API access. OIDC adds
> authentication/identity on top of OAuth.

## Q3. JWT vs OAuth?

> OAuth is a protocol/framework for authorization. JWT is a token
> format. OAuth access tokens may be JWTs or opaque tokens.

## Q4. 401 vs 403?

> 401 means authentication could not be established; 403 means the
> caller is authenticated but lacks permission.

## Q5. Why not call Keycloak for every request?

> Self-contained JWTs can be cryptographically validated locally using
> trusted public keys, reducing latency and IdP hot-path dependency.

## Q6. What happens if Keycloak is down?

> Existing valid JWTs may continue to validate if the resource server
> already has usable signing keys, while login, token issuance/refresh
> and unknown-key retrieval can fail.

## Q7. How does key rotation work?

> The issuer publishes a JWK set. Tokens identify signing keys with
> `kid`; resource servers refresh public key material as the
> authorization server rotates signing keys.

## Q8. Why validate audience?

> Issuer proves who issued the token; audience helps prove the token was
> intended for this resource/API.

## Q9. Why not put ownership checks in Gateway?

> Ownership is domain data and business authorization. Putting it in
> Gateway couples the edge layer to service internals and creates a God
> Gateway.

## Q10. Why short-lived JWTs?

> They reduce the exposure window of a stolen token and stale
> authorization claims, though they increase renewal frequency.

------------------------------------------------------------------------

# 71. Hands-On Assignment

Do not mark Stage 5 complete until you perform:

## Identity Provider

-   deploy Keycloak in Minikube;
-   create `ecom-realm`;
-   create `CUSTOMER`, `ADMIN`, `SYSTEM_ORDER`;
-   create `customer1`, `admin1`;
-   create a public Authorization Code + PKCE client;
-   inspect OIDC discovery metadata;
-   inspect JWKS/public-key endpoint.

## Gateway

-   configure Spring Security;
-   configure OAuth2 Resource Server JWT;
-   configure issuer;
-   map Keycloak realm roles;
-   keep health endpoint public;
-   protect order routes;
-   protect admin routes.

## Order

-   configure Resource Server;
-   validate JWT independently;
-   map Keycloak roles;
-   enforce method/path permissions;
-   implement domain ownership authorization.

## Failure Tests

-   no token → 401;
-   invalid token → 401;
-   tampered token → 401;
-   expired token → 401;
-   wrong issuer → 401;
-   wrong audience when configured → 401;
-   wrong role → 403;
-   direct Order without token → 401;
-   attempt another customer's order → 403;
-   Keycloak outage behavior.

------------------------------------------------------------------------

# 72. Coding-Agent Prompt

``` text
Inspect the existing microservices-workouts project and Stage 4 implementation first.

Implement Stage 5 only:
Gateway Security — OAuth2, OIDC & JWT.

Current architecture:
- Spring Cloud Gateway WebFlux
- Order Service Spring Boot MVC
- Kubernetes / Minikube
- Gateway routes to http://order-service:8080
- existing probes, replicas, HPA and routing must remain working

Identity Provider:
- Keycloak
- realm: ecom-realm
- realm roles:
  CUSTOMER
  ADMIN
  SYSTEM_ORDER

Requirements:

1. Gateway
- add Spring Security
- add OAuth2 Resource Server JWT
- issuer from JWT_ISSUER_URI
- /actuator/health/** permitAll
- /api/admin/** requires ADMIN
- /api/orders/** allows CUSTOMER, ADMIN and SYSTEM_ORDER as appropriate
- map Keycloak realm_access.roles to ROLE_*
- preserve OAuth scope authorities if a custom converter is added
- never log bearer tokens

2. Order Service
- configure OAuth2 Resource Server JWT
- validate JWT independently
- health endpoints permitAll
- GET /orders/** allows CUSTOMER, ADMIN, SYSTEM_ORDER
- POST /orders/** allows CUSTOMER, ADMIN
- DELETE /orders/** requires ADMIN
- map Keycloak roles
- keep domain authorization in Order Service

3. Kubernetes
- configure JWT_ISSUER_URI
- do not expose Order publicly just for normal application traffic
- keep health probes working
- do not commit credentials

4. Testing
Provide commands/tests for:
- health without token -> 200
- protected route without token -> 401
- valid CUSTOMER -> success
- CUSTOMER on ADMIN route -> 403
- valid ADMIN -> success
- tampered token -> 401
- expired token -> 401
- wrong issuer -> 401
- wrong audience when audience validation is enabled -> 401
- direct Order without token -> 401
- direct Order with valid token -> authorized according to service rules

5. Important
- inspect actual Spring Boot / Spring Security / Spring Cloud versions before coding
- use the APIs appropriate to those versions
- explain any Keycloak issuer-hostname configuration needed so the token's iss claim exactly matches the resource-server issuer
- do not implement Stage 6 Client Credentials/token relay yet
```

------------------------------------------------------------------------

# 73. Completion Checklist

## Mental Model

-   [ ] Authentication vs authorization.
-   [ ] OAuth vs OIDC vs JWT.
-   [ ] Access Token vs ID Token.
-   [ ] Authorization Server vs Resource Server.
-   [ ] JWT header/payload/signature.
-   [ ] `iss`, `sub`, `exp`, `nbf`, `aud`.
-   [ ] JWK/JWKS and `kid`.
-   [ ] Roles vs scopes.
-   [ ] 401 vs 403.
-   [ ] JWT vs opaque token.
-   [ ] JWT revocation trade-off.
-   [ ] Zero Trust / defense in depth.

## Implementation

-   [ ] Keycloak running.
-   [ ] `ecom-realm` created.
-   [ ] roles/users created.
-   [ ] Gateway validates JWT.
-   [ ] Gateway role mapping works.
-   [ ] Order validates JWT independently.
-   [ ] health endpoints remain available.
-   [ ] Order domain authorization exists.
-   [ ] no token/secret logging.

## Failure Behavior

-   [ ] Missing token tested.
-   [ ] Tampered token tested.
-   [ ] Expired token tested.
-   [ ] Wrong issuer tested.
-   [ ] Wrong audience understood/tested.
-   [ ] Wrong role tested.
-   [ ] Direct-service bypass tested.
-   [ ] Keycloak outage tested.
-   [ ] Key rotation understood.

------------------------------------------------------------------------

# 74. Rapid Revision

``` text
OAuth
→ authorization framework

OIDC
→ identity/authentication layer over OAuth

JWT
→ token format

Access Token
→ sent to API

ID Token
→ identity information for OIDC client

Resource Server
→ API validating access token

Issuer
→ who issued token

Audience
→ intended resource

JWKS
→ issuer's published public keys

401
→ authentication failed/missing

403
→ authenticated but forbidden

Gateway authorization
→ coarse edge policy

Service authorization
→ domain/business rules

Gateway + Service validation
→ defense in depth
```

------------------------------------------------------------------------

# 75. Correct Next Stage

After Stage 5:

``` text
06 — Token Relay & Service-to-Service Security
```

That stage answers:

``` text
Which identity should move downstream?
When should Gateway relay a user token?
When should Order use Client Credentials?
What is service identity?
How do we prevent header spoofing?
```

------------------------------------------------------------------------

# Stage 5 One-Line Summary

> **The Gateway should authenticate and reject obviously unauthorized
> traffic early, but every protected service remains responsible for
> validating trusted identity and enforcing its own domain
> authorization; OAuth defines authorization flows, OIDC adds identity,
> and JWT is only the signed token format carrying the claims.**
