CREATE TABLE IF NOT EXISTS product_rating (sku varchar(50) PRIMARY KEY, average numeric(2,1), reviews integer);
INSERT INTO product_rating VALUES ('ELEC-1',4.7,128),('ELEC-2',4.4,86),('GROC-1',4.8,210),('HOME-1',4.6,74),('BOOK-1',4.9,53),('FIT-1',4.5,96) ON CONFLICT DO NOTHING;
