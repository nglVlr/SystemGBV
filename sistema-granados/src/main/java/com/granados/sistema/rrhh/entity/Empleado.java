package com.granados.sistema.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Empleado o contratista de la municipalidad. No es lo mismo que un
 * usuario del sistema: aqui va TODO el personal (011, 022, 029, 031...)
 * aunque nunca entre al programa.
 */
@Entity
@Table(name = "rrhh_empleados", indexes = {
        @Index(name = "idx_empleados_activo_nombre", columnList = "activo, nombre")})
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 120)
    private String cargo = "";

    /** Oficina o dependencia (DAFIM, Secretaria, DMP, Servicios Publicos...). */
    @Column(length = 80)
    private String dependencia = "";

    /** Renglon presupuestario del puesto: 011, 022, 029, 031, 035... */
    @Column(length = 5)
    private String renglon = "";

    @Column(length = 15)
    private String dpi = "";

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getDependencia() { return dependencia; }
    public void setDependencia(String dependencia) { this.dependencia = dependencia; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getDpi() { return dpi; }
    public void setDpi(String dpi) { this.dpi = dpi; }
    public LocalDate getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(LocalDate fechaIngreso) { this.fechaIngreso = fechaIngreso; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
