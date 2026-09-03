package com.granados.sistema.usuarios.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.List;

/**
 * Rol del sistema. SUPERADMIN ve todo. ADMIN_DAFIM y ADMIN_RRHH se
 * conservan por compatibilidad; los permisos finos son COMPRAS, PAQUETES,
 * PRESUPUESTO (incluye caja/fuentes/bancos), REMUNERACIONES y RRHH.
 * DINERO se conserva en BD por compatibilidad; ya no es un modulo aparte.
 */
@Entity
@Table(name = "roles")
public class Rol {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String ADMIN_DAFIM = "ADMIN_DAFIM";
    public static final String ADMIN_RRHH = "ADMIN_RRHH";
    public static final String COMPRAS = "COMPRAS";
    public static final String PAQUETES = "PAQUETES";
    public static final String PRESUPUESTO = "PRESUPUESTO";
    /** Fila historica en roles. No se asigna ni se muestra; caja vive en PRESUPUESTO. */
    public static final String DINERO = "DINERO";
    public static final String REMUNERACIONES = "REMUNERACIONES";
    public static final String RRHH = "RRHH";

    public static final List<String> MODULOS_DAFIM = List.of(
            COMPRAS, PAQUETES, PRESUPUESTO, REMUNERACIONES);
    public static final List<String> MODULOS = List.of(
            COMPRAS, PAQUETES, PRESUPUESTO, REMUNERACIONES, RRHH);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String nombre;

    @Column(length = 120)
    private String descripcion;

    public Rol() {}

    public Rol(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
