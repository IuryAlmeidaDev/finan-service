ALTER TABLE financial_transaction DROP CONSTRAINT financial_transaction_check;
ALTER TABLE financial_transaction ADD CONSTRAINT financial_transaction_check 
    CHECK (destination_account_id IS NULL OR destination_account_id <> account_id OR type = 'BALANCE_ADJUSTMENT');

ALTER TABLE financial_transaction DROP CONSTRAINT financial_transaction_check2;
ALTER TABLE financial_transaction ADD CONSTRAINT financial_transaction_check2 
    CHECK (
        (type = 'TRANSFER' AND destination_account_id IS NOT NULL AND category_id IS NULL)
        OR
        (type IN ('INCOME', 'EXPENSE') AND destination_account_id IS NULL AND category_id IS NOT NULL)
        OR
        (type = 'BALANCE_ADJUSTMENT' AND category_id IS NULL AND (destination_account_id IS NULL OR destination_account_id = account_id))
    );
