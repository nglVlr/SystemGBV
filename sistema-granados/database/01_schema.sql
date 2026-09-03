-- ============================================================================
--  SISTEMA GRANADOS - Esquema de base de datos (MySQL / MariaDB)
--  Municipalidad de Granados, Baja Verapaz - DAFIM
-- ----------------------------------------------------------------------------
--  NOTA: el sistema crea estas tablas SOLO (spring.jpa.hibernate.ddl-auto =
--  update, y la propia BD con createDatabaseIfNotExist=true). Este script es
--  la referencia del esquema y sirve para crearlo a mano si se prefiere,
--  por ejemplo desde phpMyAdmin de WampServer.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS sistema_granados
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_spanish_ci;

USE sistema_granados;

-- ----------------------------- SEGURIDAD -----------------------------------

CREATE TABLE IF NOT EXISTS roles (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  nombre      VARCHAR(30)  NOT NULL,
  descripcion VARCHAR(120) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_nombre (nombre)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS usuarios (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  username        VARCHAR(50)  NOT NULL,
  password        VARCHAR(100) NOT NULL,
  nombre_completo VARCHAR(120) NULL,
  email           VARCHAR(120) NULL,
  activo          TINYINT(1)   NOT NULL DEFAULT 1,
  fecha_creacion  DATETIME     NULL,
  ultimo_acceso   DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_usuarios_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS usuarios_roles (
  usuario_id BIGINT NOT NULL,
  rol_id     BIGINT NOT NULL,
  PRIMARY KEY (usuario_id, rol_id),
  CONSTRAINT fk_ur_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
  CONSTRAINT fk_ur_rol     FOREIGN KEY (rol_id)     REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------- MODULO DAFIM: COMPRAS ----------------------------

-- Personal por contrato (renglon 029): una fila por NIT
CREATE TABLE IF NOT EXISTS contratos_029 (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  nit                 VARCHAR(20)  NOT NULL,
  nombre              VARCHAR(150) NULL,
  contrato            VARCHAR(30)  NULL,
  cargo               VARCHAR(120) NULL,
  npg                 VARCHAR(20)  NULL,
  anio                INT          NULL,
  fecha_actualizacion DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_contratos029_nit (nit)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Proveedores de bienes y servicios: una fila por NIT
CREATE TABLE IF NOT EXISTS proveedores (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  nit                 VARCHAR(20)  NOT NULL,
  nombre              VARCHAR(150) NULL,
  renglon             VARCHAR(5)   NULL,
  descripcion         VARCHAR(150) NULL,
  fecha_actualizacion DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_proveedores_nit (nit)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Historial de pagos: todas las filas de todos los meses guardados
CREATE TABLE IF NOT EXISTS historial_compras (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  anio               INT           NOT NULL,
  mes                INT           NOT NULL,
  cheque             VARCHAR(20)   NULL,
  nit                VARCHAR(20)   NULL,
  nombre             VARCHAR(150)  NULL,
  renglon            VARCHAR(5)    NULL,
  monto              DECIMAL(12,2) NULL,
  npg                VARCHAR(20)   NULL,
  modalidad          VARCHAR(30)   NULL,
  contrato           VARCHAR(30)   NULL,
  descripcion        VARCHAR(200)  NULL,
  id_usuario_proceso BIGINT        NULL,
  fecha_proceso      DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_historial_anio_mes (anio, mes),
  KEY idx_historial_nit (nit)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Bitacora de procesos: un registro por cada procesar/guardar/machote
CREATE TABLE IF NOT EXISTS procesos_mensuales (
  id                   BIGINT        NOT NULL AUTO_INCREMENT,
  anio                 INT           NOT NULL,
  mes                  INT           NOT NULL,
  fecha_proceso        DATETIME      NULL,
  id_usuario           BIGINT        NULL,
  total_filas          INT           NULL,
  total_monto          DECIMAL(14,2) NULL,
  alertas              TEXT          NULL,
  ruta_excel_generado  VARCHAR(300)  NULL,
  estado               VARCHAR(15)   NULL,
  PRIMARY KEY (id),
  KEY idx_procesos_anio_mes (anio, mes)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
