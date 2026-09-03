package com.granados.sistema.dafim.presupuesto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reserva operativa para armar un pago: aparta presupuesto de una linea
 * (renglon + fuente + programa + proyecto) y, por separado, dinero real de esa fuente. No se
 * escribe en SICOIN; al reimportar el PDF la reserva sigue viva porque
 * se identifica por claves estables, no por el id de la linea.
 *
 * Los dos montos son independientes: se puede apartar presupuesto aunque
 * el boletin de caja no alcance (montoBanco = 0).
 */
@Entity
@Table(name = "presupuesto_apartados", indexes = {
        @Index(name = "idx_apartados_estado", columnList = "estado"),
        @Index(name = "idx_apartados_anio", columnList = "anio"),
        @Index(name = "idx_apartados_clave", columnList = "renglon, fuente")})
public class Apartado {

    public static final String EST_ACTIVO = "ACTIVO";
    public static final String EST_LIBERADO = "LIBERADO";
    public static final String EST_USADO = "USADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    @Column(length = 3, nullable = false)
    private String renglon;

    @Column(length = 15, nullable = false)
    private String fuente;

    @Column(length = 10)
    private String actividadObra;

    @Column(length = 160)
    private String descripcion;

    @Column(length = 90)
    private String programa;

    @Column(length = 90)
    private String subprograma;

    @Column(length = 90)
    private String proyecto;

    @Column(length = 90)
    private String actividad;

    /** Id de presupuesto_lineas en la carga donde se aparto; distingue gemelas. */
    @Column(name = "linea_id")
    private Long lineaId;

    @Column(length = 200, nullable = false)
    private String concepto;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal montoPresupuesto;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal montoBanco;

    @Column(length = 12, nullable = false)
    private String estado;

    @Column(length = 50)
    private String usuario;

    private LocalDateTime fecha;

    private LocalDateTime fechaCambio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
    public String getActividadObra() { return actividadObra; }
    public void setActividadObra(String actividadObra) { this.actividadObra = actividadObra; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getPrograma() { return programa; }
    public void setPrograma(String programa) { this.programa = programa; }
    public String getSubprograma() { return subprograma; }
    public void setSubprograma(String subprograma) { this.subprograma = subprograma; }
    public String getProyecto() { return proyecto; }
    public void setProyecto(String proyecto) { this.proyecto = proyecto; }
    public String getActividad() { return actividad; }
    public void setActividad(String actividad) { this.actividad = actividad; }
    public Long getLineaId() { return lineaId; }
    public void setLineaId(Long lineaId) { this.lineaId = lineaId; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public BigDecimal getMontoPresupuesto() { return montoPresupuesto; }
    public void setMontoPresupuesto(BigDecimal montoPresupuesto) { this.montoPresupuesto = montoPresupuesto; }
    public BigDecimal getMontoBanco() { return montoBanco; }
    public void setMontoBanco(BigDecimal montoBanco) { this.montoBanco = montoBanco; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }
}
