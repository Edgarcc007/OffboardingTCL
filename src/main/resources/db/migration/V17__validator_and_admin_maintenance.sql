ALTER TABLE app_user_role DROP CONSTRAINT ck_app_user_role;
ALTER TABLE app_user_role ADD CONSTRAINT ck_app_user_role CHECK (
    role IN ('ADMIN','RECURSOS_HUMANOS','IT_ENGINEER','AUDITOR',
             'CONTROL_ACCESOS','IT_ENGINEER_VALIDATOR')
);

ALTER TABLE bulk_import_row DROP CONSTRAINT ck_bulk_row_state;
ALTER TABLE bulk_import_row ADD CONSTRAINT ck_bulk_row_state CHECK (
    state IN ('PENDING','REGISTERED','FAILED','DELETED')
);

ALTER TABLE bulk_import_row ADD CONSTRAINT ck_bulk_deleted_payload CHECK (
    state<>'DELETED' OR (
        case_id IS NULL AND case_number IS NULL
        AND payload='{}' AND error IS NULL
    )
);