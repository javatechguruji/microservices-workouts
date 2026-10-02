-- Runs once, only on first container init (empty volume).
-- The official postgres image only auto-creates the DB named by
-- POSTGRES_USER/POSTGRES_DB, so any extra per-service database needs
-- to be created explicitly here.
CREATE DATABASE "order-srv-db";
CREATE DATABASE "payment-srv-db";

-- Keycloak stores realms, clients and service accounts in the shared Postgres.
CREATE DATABASE "keycloak";

CREATE DATABASE "product-aggregator-service-db";
CREATE DATABASE "customer-service-db";
CREATE DATABASE "product-discount-service-db";
CREATE DATABASE "rating-service-db";
CREATE DATABASE "inventory-service-db";
CREATE DATABASE "notification-service-db";
