-- Existing JPA order tables may have the old three-value enum CHECK.
-- Run once through ensure-databases.py before starting updated applications.
-- Fresh installations create the expanded constraint through JPA instead.
BEGIN;
SELECT pg_advisory_xact_lock(918001);
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid=to_regclass('orders')
             AND conname='orders_status_check'
             AND pg_get_constraintdef(oid) NOT LIKE '%SHIPPED%') THEN
    ALTER TABLE orders DROP CONSTRAINT orders_status_check;
    ALTER TABLE orders ADD CONSTRAINT orders_status_check
      CHECK (status IN ('PENDING','CONFIRMED','FAILED','SHIPPED','DELIVERED'));
  END IF;
END $$;
COMMIT;
