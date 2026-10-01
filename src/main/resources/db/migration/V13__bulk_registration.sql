CREATE TABLE bulk_import_batch (
    id VARCHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    owner_username VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    phase VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 1,
    document TEXT NOT NULL,
    last_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bulk_batch_phase CHECK (phase IN ('DRAFT','CONFIRMED'))
);

CREATE INDEX ix_bulk_batch_owner
    ON bulk_import_batch(owner_id, updated_at DESC);

CREATE TABLE bulk_import_row (
    batch_id VARCHAR(36) NOT NULL REFERENCES bulk_import_batch(id),
    row_no INTEGER NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    case_id BIGINT REFERENCES offboarding_case(id),
    case_number VARCHAR(30),
    error VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (batch_id, row_no),
    CONSTRAINT uq_bulk_row_case UNIQUE (case_id),
    CONSTRAINT ck_bulk_row_state
        CHECK (state IN ('PENDING','REGISTERED','FAILED')),
    CONSTRAINT ck_bulk_row_result
        CHECK (
            (state = 'REGISTERED' AND case_id IS NOT NULL AND case_number IS NOT NULL)
            OR
            (state <> 'REGISTERED' AND case_id IS NULL AND case_number IS NULL)
        )
);