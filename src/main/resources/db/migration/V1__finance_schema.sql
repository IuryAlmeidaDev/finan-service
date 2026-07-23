CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE account (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    type varchar(20) NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH', 'OTHER')),
    initial_balance numeric(19,2) NOT NULL,
    initial_balance_date date NOT NULL,
    color varchar(7),
    icon_slug varchar(80),
    include_in_total boolean NOT NULL DEFAULT true,
    is_archived boolean NOT NULL DEFAULT false,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE category (
    id uuid PRIMARY KEY,
    name varchar(80) NOT NULL,
    type varchar(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    parent_category_id uuid REFERENCES category(id),
    icon_slug varchar(80),
    color varchar(7),
    is_system boolean NOT NULL DEFAULT false,
    is_archived boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (parent_category_id IS NULL OR parent_category_id <> id),
    CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE FUNCTION validate_category_parent() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    parent_type varchar(10);
    grandparent_id uuid;
BEGIN
    IF NEW.parent_category_id IS NOT NULL THEN
        SELECT type, parent_category_id
          INTO parent_type, grandparent_id
          FROM category
         WHERE id = NEW.parent_category_id;

        IF parent_type IS NULL THEN
            RAISE EXCEPTION 'parent category does not exist';
        END IF;
        IF grandparent_id IS NOT NULL THEN
            RAISE EXCEPTION 'categories support at most two levels';
        END IF;
        IF parent_type <> NEW.type THEN
            RAISE EXCEPTION 'parent and child categories must have the same type';
        END IF;
    END IF;

    IF TG_OP = 'UPDATE' AND EXISTS (
        SELECT 1 FROM category WHERE parent_category_id = NEW.id
    ) THEN
        IF NEW.parent_category_id IS NOT NULL THEN
            RAISE EXCEPTION 'categories support at most two levels';
        END IF;
        IF EXISTS (
            SELECT 1
              FROM category
             WHERE parent_category_id = NEW.id
               AND type <> NEW.type
        ) THEN
            RAISE EXCEPTION 'parent and child categories must have the same type';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER category_parent_validation
BEFORE INSERT OR UPDATE OF parent_category_id, type ON category
FOR EACH ROW EXECUTE FUNCTION validate_category_parent();

CREATE TABLE installment_group (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    description varchar(255) NOT NULL,
    total_amount numeric(19,2) NOT NULL CHECK (total_amount > 0),
    total_installments integer NOT NULL CHECK (total_installments >= 2),
    first_installment_date date NOT NULL,
    category_id uuid REFERENCES category(id),
    status varchar(10) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELED')),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recurring_rule (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    amount numeric(19,2) NOT NULL CHECK (amount > 0),
    type varchar(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category_id uuid NOT NULL REFERENCES category(id),
    description varchar(255),
    frequency varchar(20) NOT NULL CHECK (frequency IN (
        'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'BIMONTHLY',
        'QUARTERLY', 'SEMI_ANNUALLY', 'ANNUALLY'
    )),
    day_of_month integer CHECK (day_of_month BETWEEN 1 AND 31),
    day_of_week varchar(9) CHECK (day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
        'FRIDAY', 'SATURDAY', 'SUNDAY'
    )),
    start_date date NOT NULL,
    end_date date,
    auto_confirm boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    last_generated_date date,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE financial_transaction (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    destination_account_id uuid REFERENCES account(id),
    amount numeric(19,2) NOT NULL CHECK (amount > 0),
    type varchar(20) NOT NULL CHECK (type IN (
        'INCOME', 'EXPENSE', 'TRANSFER', 'BALANCE_ADJUSTMENT'
    )),
    date date NOT NULL,
    category_id uuid REFERENCES category(id),
    description varchar(255),
    notes text,
    is_paid boolean NOT NULL DEFAULT false,
    linked_task_id uuid,
    recurring_rule_id uuid REFERENCES recurring_rule(id),
    recurring_instance_index integer CHECK (recurring_instance_index > 0),
    installment_group_id uuid REFERENCES installment_group(id),
    installment_number integer CHECK (installment_number > 0),
    total_installments integer CHECK (total_installments > 0),
    is_ignored_from_budget boolean NOT NULL DEFAULT false,
    is_ignored_from_reports boolean NOT NULL DEFAULT false,
    deleted_at timestamp without time zone,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (destination_account_id IS NULL OR destination_account_id <> account_id),
    CHECK (
        (installment_number IS NULL AND total_installments IS NULL AND installment_group_id IS NULL)
        OR
        (installment_number IS NOT NULL AND total_installments IS NOT NULL
            AND installment_group_id IS NOT NULL
            AND installment_number <= total_installments)
    ),
    CHECK (
        (type = 'TRANSFER' AND destination_account_id IS NOT NULL AND category_id IS NULL)
        OR
        (type IN ('INCOME', 'EXPENSE') AND destination_account_id IS NULL AND category_id IS NOT NULL)
        OR
        (type = 'BALANCE_ADJUSTMENT' AND destination_account_id IS NULL)
    )
);

CREATE TABLE attachment (
    id uuid PRIMARY KEY,
    transaction_id uuid NOT NULL REFERENCES financial_transaction(id) ON DELETE CASCADE,
    file_name varchar(255) NOT NULL,
    file_type varchar(100) NOT NULL CHECK (file_type IN (
        'image/jpeg', 'image/png', 'application/pdf'
    )),
    file_size bigint NOT NULL CHECK (file_size > 0 AND file_size <= 10485760),
    storage_path varchar(1024) NOT NULL,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE budget (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES category(id),
    month integer NOT NULL CHECK (month BETWEEN 1 AND 12),
    year integer NOT NULL,
    limit_amount numeric(19,2) NOT NULL CHECK (limit_amount > 0),
    rollover_type varchar(20) NOT NULL DEFAULT 'NO_ROLLOVER'
        CHECK (rollover_type IN ('NO_ROLLOVER', 'FULL_ROLLOVER', 'POSITIVE_ONLY')),
    rollover_amount numeric(19,2) NOT NULL DEFAULT 0,
    include_pending boolean NOT NULL DEFAULT false,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (category_id, year, month)
);

CREATE TABLE income_goal (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES category(id),
    month integer NOT NULL CHECK (month BETWEEN 1 AND 12),
    year integer NOT NULL,
    target_amount numeric(19,2) NOT NULL CHECK (target_amount > 0),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (category_id, year, month)
);

CREATE TABLE tag (
    id uuid PRIMARY KEY,
    name varchar(50) NOT NULL UNIQUE,
    color varchar(7),
    CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE transaction_tag (
    transaction_id uuid NOT NULL REFERENCES financial_transaction(id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, tag_id)
);

CREATE INDEX idx_category_parent ON category(parent_category_id);
CREATE INDEX idx_installment_group_account ON installment_group(account_id);
CREATE INDEX idx_installment_group_category ON installment_group(category_id);
CREATE INDEX idx_recurring_rule_account ON recurring_rule(account_id);
CREATE INDEX idx_recurring_rule_category ON recurring_rule(category_id);
CREATE INDEX idx_recurring_rule_active ON recurring_rule(is_active);
CREATE INDEX idx_transaction_account_date ON financial_transaction(account_id, date);
CREATE INDEX idx_transaction_destination_account ON financial_transaction(destination_account_id);
CREATE INDEX idx_transaction_type ON financial_transaction(type);
CREATE INDEX idx_transaction_category_date ON financial_transaction(category_id, date);
CREATE INDEX idx_transaction_paid ON financial_transaction(is_paid);
CREATE INDEX idx_transaction_date ON financial_transaction(date);
CREATE INDEX idx_transaction_amount ON financial_transaction(amount);
CREATE INDEX idx_transaction_description_trgm
    ON financial_transaction USING gin (description gin_trgm_ops);
CREATE INDEX idx_transaction_notes_trgm
    ON financial_transaction USING gin (notes gin_trgm_ops);
CREATE INDEX idx_transaction_installment_group ON financial_transaction(installment_group_id);
CREATE INDEX idx_transaction_recurring_rule ON financial_transaction(recurring_rule_id);
CREATE INDEX idx_transaction_created_at ON financial_transaction(created_at);
CREATE INDEX idx_attachment_transaction ON attachment(transaction_id);
CREATE INDEX idx_transaction_tag_tag ON transaction_tag(tag_id);
