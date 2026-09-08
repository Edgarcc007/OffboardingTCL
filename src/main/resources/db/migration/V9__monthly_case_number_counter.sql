-- Stores an independent six-digit counter for each calendar month.
-- The row update is atomic and safe for concurrent requests.

CREATE TABLE offboarding_case_monthly_counter (
    month_key  CHAR(7) PRIMARY KEY,
    last_value BIGINT NOT NULL,

    CONSTRAINT ck_offboarding_month_key
        CHECK (
            month_key ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'
        ),

    CONSTRAINT ck_offboarding_monthly_value
        CHECK (
            last_value BETWEEN 1 AND 999999
        )
);

COMMENT ON TABLE offboarding_case_monthly_counter IS
    'Monthly counter used to generate offboarding case numbers';

COMMENT ON COLUMN offboarding_case_monthly_counter.month_key IS
    'Calendar month in YYYY-MM format';

COMMENT ON COLUMN offboarding_case_monthly_counter.last_value IS
    'Last case sequence assigned during the month';
