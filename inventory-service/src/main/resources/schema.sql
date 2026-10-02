CREATE TABLE IF NOT EXISTS stock (sku varchar(50) PRIMARY KEY, available integer NOT NULL CHECK(available>=0));
INSERT INTO stock VALUES ('ELEC-1',100),('ELEC-2',100),('GROC-1',200),('GROC-2',200),('HOME-1',100),('HOME-2',100),('BOOK-1',100),('BOOK-2',100),('FIT-1',100),('FIT-2',100) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS reservation (tenant varchar(80), order_id bigint, state varchar(20) NOT NULL, items text NOT NULL, PRIMARY KEY(tenant,order_id));
