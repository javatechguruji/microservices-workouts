CREATE TABLE IF NOT EXISTS payment_outbox (order_id bigint PRIMARY KEY, payment_id bigint NOT NULL, published boolean NOT NULL DEFAULT false);
