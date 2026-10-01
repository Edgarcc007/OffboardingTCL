CREATE TABLE bulk_import_delivery (
    batch_id VARCHAR(36) PRIMARY KEY REFERENCES bulk_import_batch(id),
    upload_hash VARCHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'NEW',
    payload TEXT,
    subject VARCHAR(200),
    attempt_token VARCHAR(36),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bulk_delivery_state CHECK (
        state IN (
            'NEW','PENDING','SENDING','SENT','UNKNOWN',
            'DISABLED','NO_RECIPIENTS','CONFIG_ERROR'
        )
    )
);