-- ============================================================================
--  SISTEMA GRANADOS - Datos iniciales (roles y usuarios)
-- ----------------------------------------------------------------------------
--  NOTA: el sistema tambien crea esto solo la primera vez que arranca
--  (DataInitializer). Este script es la alternativa manual. Es seguro
--  correrlo mas de una vez: los INSERT IGNORE no duplican.
--
--  Usuarios iniciales (CAMBIAR LAS CONTRASENAS despues del primer ingreso):
--    superadmin  -> SUPERADMIN (clave inicial: ver README)
--    admin_dafim / Dafim2026*      -> ADMIN_DAFIM
--    admin_rrhh  / Rrhh2026*       -> ADMIN_RRHH
-- ============================================================================

USE sistema_granados;

INSERT IGNORE INTO roles (nombre, descripcion) VALUES
  ('SUPERADMIN',  'Administrador general del sistema'),
  ('ADMIN_DAFIM', 'Direccion de Administracion Financiera (todos los modulos DAFIM)'),
  ('ADMIN_RRHH',  'Recursos Humanos (acceso completo al modulo)'),
  ('COMPRAS', 'Compras directas'),
  ('PAQUETES', 'Paquetes de facturas'),
  ('PRESUPUESTO', 'Presupuesto (ejecucion, caja, fuentes y bancos)'),
  -- DINERO queda en BD por compatibilidad; ya no se asigna ni se muestra.
  ('DINERO', 'Dinero real (caja, fuentes y bancos)'),
  ('REMUNERACIONES', 'Remuneraciones (oficio LAIP Art. 10.4)'),
  ('RRHH', 'Recursos Humanos');

-- Contrasenas cifradas con BCrypt (mismas del DataInitializer)
INSERT IGNORE INTO usuarios
  (username, password, nombre_completo, email, activo, fecha_creacion)
VALUES
  ('superadmin',
   '$2a$10$q2.f6rvj4pPPVyCeVykkXe9Rbir12v2viyrXJZf4MMXfNnVNrvcv.',
   'Administrador General', '', 1, NOW()),
  ('admin_dafim',
   '$2b$10$.niBuaPmd9MR.YMjTKzTLe2rArwRq9wzOrCMuOBb/bz5FrO323VrK',
   'Encargado DAFIM', '', 1, NOW()),
  ('admin_rrhh',
   '$2b$10$soY4u6W9C96r3kPQjhVjLO5sstv.iJVS0AXx7Y/w6gfKHJzdrmevi',
   'Encargado RRHH', '', 1, NOW());

INSERT IGNORE INTO usuarios_roles (usuario_id, rol_id)
SELECT u.id, r.id FROM usuarios u JOIN roles r
 WHERE (u.username = 'superadmin'  AND r.nombre = 'SUPERADMIN')
    OR (u.username = 'admin_dafim' AND r.nombre = 'ADMIN_DAFIM')
    OR (u.username = 'admin_rrhh'  AND r.nombre = 'ADMIN_RRHH');
