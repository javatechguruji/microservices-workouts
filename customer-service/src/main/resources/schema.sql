CREATE TABLE IF NOT EXISTS customer_profile (tenant varchar(80), username varchar(200), email varchar(254) NOT NULL, phone varchar(30) NOT NULL, dob date NOT NULL, preferences text NOT NULL, PRIMARY KEY(tenant,username));
CREATE TABLE IF NOT EXISTS category_history (tenant varchar(80), username varchar(200), category varchar(80), purchases integer NOT NULL DEFAULT 0, PRIMARY KEY(tenant,username,category));
CREATE TABLE IF NOT EXISTS consumed_order (event_id varchar(100) PRIMARY KEY);
