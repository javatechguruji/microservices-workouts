-- Runs once, only on first container init (empty volume).
-- The official postgres image only auto-creates the DB named by
-- POSTGRES_USER/POSTGRES_DB, so any extra per-service database needs
-- to be created explicitly here.
CREATE DATABASE "order-srv-db";
CREATE DATABASE "payment-srv-db";

-- Keycloak stores realms, clients and service accounts in the shared Postgres.
CREATE DATABASE "keycloak";
