package com.granados.sistema.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;

/**
 * El permiso fisico escaneado (PDF o imagen) firmado por el empleado y su
 * encargado de oficina. Va en tabla aparte para que los listados no
 * carguen los archivos.
 */
@Entity
@Table(name = "rrhh_permisos_adjuntos",
        indexes = @Index(name = "idx_rrhhadj_permiso", columnList = "permiso_id"))
public class PermisoAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permiso_id", nullable = false)
    private Long permisoId;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Tipo de contenido: application/pdf, image/jpeg, image/png... */
    @Column(nullable = false, length = 60)
    private String tipo;

    @Lob
    @JdbcTypeCode(Types.BINARY)
    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] datos;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPermisoId() { return permisoId; }
    public void setPermisoId(Long permisoId) { this.permisoId = permisoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public byte[] getDatos() { return datos; }
    public void setDatos(byte[] datos) { this.datos = datos; }
}
