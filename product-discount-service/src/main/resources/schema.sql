CREATE TABLE IF NOT EXISTS product_discount (sku varchar(50) PRIMARY KEY, percent integer NOT NULL CHECK(percent BETWEEN 0 AND 90));
INSERT INTO product_discount VALUES ('ELEC-1',15),('GROC-1',10),('HOME-1',20),('BOOK-2',10),('FIT-1',15) ON CONFLICT DO NOTHING;
