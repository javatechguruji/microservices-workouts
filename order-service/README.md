# order-service

Order microservice — part of the [microservices-workouts](../README.md) e-commerce system. Runs on port 9101.


## Security

Call application APIs through the gateway on port 9100 with a Keycloak access
token. The gateway validates JWTs and derives trusted `X-Auth-*` headers;
downstream controllers enforce roles, permissions and resource policies.
See [authentication and authorization](../docs/security/Authentication%20and%20Authorization%20at%20Microservice.md)
for browser login, machine calls and direct-header POC testing.
