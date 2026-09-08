ALTER TABLE offboarding_case
    ADD COLUMN computer_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN computer_details VARCHAR(250),
    ADD COLUMN phone_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN phone_details VARCHAR(250),
    ADD COLUMN other_accesses TEXT;

CREATE TABLE offboarding_case_access (
    offboarding_case_id BIGINT NOT NULL,
    access_type VARCHAR(30) NOT NULL,

    CONSTRAINT pk_offboarding_case_access
        PRIMARY KEY (
            offboarding_case_id,
            access_type
        ),

    CONSTRAINT fk_offboarding_case_access_case
        FOREIGN KEY (offboarding_case_id)
        REFERENCES offboarding_case (id)
        ON DELETE CASCADE,

    CONSTRAINT ck_offboarding_case_access_type
        CHECK (
            access_type IN (
                'WINDOWS',
                'OFFICE',
                'SMES',
                'CMP',
                'VPN',
                'OTHER'
            )
        )
);

ALTER TABLE task_template
    ADD COLUMN selection_code VARCHAR(30);

UPDATE task_template
SET selection_code = 'WINDOWS'
WHERE system_name = 'Directorio Activo';

UPDATE task_template
SET selection_code = 'OFFICE'
WHERE system_name = 'Correo corporativo';

UPDATE task_template
SET selection_code = 'VPN'
WHERE system_name = 'VPN / Acceso remoto';

UPDATE task_template
SET selection_code = 'SMES'
WHERE system_name = 'SMES';

UPDATE task_template
SET selection_code = 'CMP'
WHERE system_name = 'CMP';

UPDATE task_template
SET selection_code = 'COMPUTER',
    system_name = 'Equipo de cómputo',
    task_name =
        'Recuperar computadora, cargador y accesorios asignados'
WHERE system_name = 'Activos asignados';

UPDATE task_template
SET selection_code = 'PHONE',
    system_name = 'Telefonía',
    task_name =
        'Recuperar teléfono y cancelar o reasignar la línea corporativa'
WHERE system_name = 'Telefonia';

ALTER TABLE task_template
    ADD CONSTRAINT ck_task_template_selection_code
    CHECK (
        selection_code IS NULL
        OR selection_code IN (
            'COMPUTER',
            'PHONE',
            'WINDOWS',
            'OFFICE',
            'SMES',
            'CMP',
            'VPN'
        )
    );

CREATE INDEX idx_task_template_selection
    ON task_template (selection_code)
    WHERE active = TRUE;
