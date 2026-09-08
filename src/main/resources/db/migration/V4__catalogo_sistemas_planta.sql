-- ============================================================
-- V4: Catalogo ajustado a los sistemas reales de la planta
-- ============================================================

-- SAP es el ERP de la planta: se renombra en lugar de duplicar.
-- Si ERP y SAP fueran sistemas distintos, borrar este UPDATE
-- y agregar SAP como una fila mas en el INSERT de abajo.
UPDATE task_template
   SET system_name = 'SAP'
 WHERE system_name = 'ERP';

-- Fuera del catalogo: no aplica en la operacion de la planta.
DELETE FROM task_template
 WHERE system_name = 'Herramientas SaaS';

-- Sistemas de piso: acceso operativo directo, revocacion inmediata.
INSERT INTO task_template (system_name, task_name, critical, automatic, sla_hours, active) VALUES
('SMES', 'Revocar usuario y permisos de linea en el sistema de ejecucion de manufactura', TRUE, FALSE, 2, TRUE),
('CMP',  'Revocar acceso y permisos asignados en CMP',                                    TRUE, FALSE, 2, TRUE);
