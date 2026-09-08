-- Evidencia específica de recepción de activos.

ALTER TABLE offboarding_task
    ADD COLUMN asset_received
        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN inventory_updated
        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN inventory_reference
        VARCHAR(200),
    ADD COLUMN validation_comments
        TEXT;


-- Solamente computadora y teléfono requieren validación.

UPDATE task_template
SET critical =
    CASE
        WHEN selection_code IN ('COMPUTER', 'PHONE')
            THEN TRUE
        ELSE FALSE
    END
WHERE selection_code IS NOT NULL;


-- Ajustar también tareas pendientes de casos existentes.

UPDATE offboarding_task
SET critical = TRUE
WHERE status IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA')
  AND system_name IN (
      'Equipo de cómputo',
      'Telefonía',
      'Activos asignados',
      'Telefonia'
  );

UPDATE offboarding_task
SET critical = FALSE
WHERE status IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA')
  AND system_name IN (
      'Directorio Activo',
      'Correo corporativo',
      'VPN / Acceso remoto',
      'SMES',
      'CMP',
      'Otros accesos'
  );
