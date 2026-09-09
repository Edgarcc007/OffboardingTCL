-- V11: Nuevo perfil CONTROL_ACCESOS para gestión de biométricos y accesos físicos
ALTER TABLE app_user_role
    DROP CONSTRAINT IF EXISTS ck_app_user_role;

ALTER TABLE app_user_role
    ADD CONSTRAINT ck_app_user_role
    CHECK (
        role IN (
            'ADMIN',
            'RECURSOS_HUMANOS',
            'IT_ENGINEER',
            'AUDITOR',
            'CONTROL_ACCESOS'
        )
    );
