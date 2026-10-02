CREATE TABLE IF NOT EXISTS notification_inbox (event_id varchar(100) PRIMARY KEY,tenant varchar(80),customer_id varchar(200),order_id bigint,status varchar(30),created_at timestamptz DEFAULT now());
