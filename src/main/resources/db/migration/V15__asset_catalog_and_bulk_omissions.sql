CREATE TABLE offboarding_asset_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_hash VARCHAR(64) NOT NULL,
    source_sheet TEXT NOT NULL,
    source_row INTEGER NOT NULL,
    asset_id TEXT NOT NULL,
    master_asset_id TEXT NOT NULL,
    asset_name TEXT NOT NULL,
    model TEXT NOT NULL,
    serial_number TEXT NOT NULL,
    dev_type TEXT NOT NULL,
    importance_level TEXT NOT NULL,
    lifetime TEXT NOT NULL,
    operating_system TEXT NOT NULL,
    user_name TEXT NOT NULL,
    department TEXT NOT NULL,
    department_area TEXT NOT NULL,
    finance TEXT NOT NULL,
    company TEXT NOT NULL,
    building_floor TEXT NOT NULL,
    location TEXT NOT NULL,
    station TEXT NOT NULL,
    status TEXT NOT NULL,
    physical_inventory TEXT NOT NULL,
    inventory_comment TEXT NOT NULL,
    change_log TEXT NOT NULL,
    modified_at_text TEXT NOT NULL,
    asset_key TEXT NOT NULL,
    user_key TEXT NOT NULL,
    name_key TEXT NOT NULL,
    search_text TEXT NOT NULL
);

CREATE INDEX ix_offboarding_asset_source
    ON offboarding_asset_catalog(source_hash);

CREATE TABLE offboarding_setup_done (
    step_id VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bulk_import_omission (
    batch_id VARCHAR(36) NOT NULL REFERENCES bulk_import_batch(id),
    row_no INTEGER NOT NULL,
    source_identifier TEXT NOT NULL,
    employee_identifier TEXT NOT NULL,
    PRIMARY KEY(batch_id,row_no)
);

ALTER TABLE bulk_import_delivery DROP CONSTRAINT ck_bulk_delivery_state;
ALTER TABLE bulk_import_delivery ADD CONSTRAINT ck_bulk_delivery_state CHECK (
    state IN (
        'NEW','PENDING','SENDING','SENT','UNKNOWN','DISABLED',
        'NO_RECIPIENTS','CONFIG_ERROR','NOT_NEEDED'
    )
);