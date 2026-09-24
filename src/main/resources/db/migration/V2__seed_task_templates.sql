-- ============================================================
-- V2: Catalogo inicial de tareas de baja
-- ============================================================

INSERT INTO task_template (system_name, task_name, critical, automatic, sla_hours, active) VALUES
('Directorio Activo',   'Deshabilitar cuenta de dominio y cerrar sesiones activas', TRUE,  FALSE,  2, TRUE),
('Correo corporativo',  'Bloquear buzon, activar reenvio al jefe directo',          TRUE,  FALSE,  4, TRUE),
('VPN / Acceso remoto', 'Revocar certificado y credenciales de acceso remoto',      TRUE,  FALSE,  2, TRUE),
('Accesos fisicos',     'Desactivar tarjeta de proximidad y credencial',            TRUE,  FALSE,  4, TRUE),
('ERP',                 'Revocar perfil, liberar licencia y reasignar pendientes',  TRUE,  FALSE,  8, TRUE),
('Bases de datos',      'Revocar cuentas nominativas y accesos directos',           TRUE,  FALSE,  8, TRUE),
('Repositorios',        'Retirar de grupos, revocar tokens y llaves SSH',           TRUE,  FALSE,  8, TRUE),
('Herramientas SaaS',   'Desactivar licencias de CRM, mensajeria y colaboracion',   FALSE, FALSE, 24, TRUE),
('Activos asignados',   'Recuperar equipo de computo, token fisico y celular',      FALSE, FALSE, 48, TRUE),
('Telefonia',           'Cancelar o reasignar linea corporativa',                   FALSE, FALSE, 48, TRUE);