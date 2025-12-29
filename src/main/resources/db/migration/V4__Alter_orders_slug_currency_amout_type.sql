DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'orders'
        AND column_name = 'slug'
    ) THEN
        ALTER TABLE orders RENAME COLUMN slug TO currency;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'orders'
        AND column_name = 'amount_cents'
        AND data_type = 'integer'
    ) THEN
        ALTER TABLE orders ALTER COLUMN amount_cents TYPE BIGINT;
    END IF;
END $$;

ALTER TABLE orders
ADD COLUMN payment_intent_id VARCHAR(255);

UPDATE orders
SET payment_intent_id = 'pi_legacy_' || id::TEXT
WHERE payment_intent_id IS NULL;

COMMENT ON COLUMN orders.currency IS 'Currency code (ex: EUR, USD)';
COMMENT ON COLUMN orders.amount_cents IS 'Amount in cents (BigInt to handle large values)';
COMMENT ON COLUMN orders.payment_intent_id IS 'Stripe Payment Intent ID (pi_xxx)';
