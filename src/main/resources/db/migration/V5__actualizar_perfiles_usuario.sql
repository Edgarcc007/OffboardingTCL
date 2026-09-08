-- Eliminar temporalmente la restricción anterior para poder
-- sustituir los nombres de los perfiles.
ALTER TABLE app_user_role
    DROP CONSTRAINT IF EXISTS ck_app_user_role;

-- EJECUTOR ahora se llama IT_ENGINEER.
UPDATE app_user_role
SET role = 'IT_ENGINEER'
WHERE role = 'EJECUTOR';

-- Si una cuenta ya tiene AUDITOR y SEGURIDAD,
-- eliminar primero la asignación duplicada de SEGURIDAD.
DELETE FROM app_user_role seguridad
WHERE seguridad.role = 'SEGURIDAD'
  AND EXISTS (
      SELECT 1
      FROM app_user_role auditor
      WHERE auditor.user_id = seguridad.user_id
        AND auditor.role = 'AUDITOR'
  );

-- SEGURIDAD desaparece y sus usuarios pasan a ser
-- auditores estrictamente de solo lectura.
UPDATE app_user_role
SET role = 'AUDITOR'
WHERE role = 'SEGURIDAD';

-- Registrar el nuevo catálogo permitido de perfiles.
ALTER TABLE app_user_role
    ADD CONSTRAINT ck_app_user_role
    CHECK (
        role IN (
            'ADMIN',
            'RECURSOS_HUMANOS',
            'IT_ENGINEER',
            'AUDITOR'
        )
    );
