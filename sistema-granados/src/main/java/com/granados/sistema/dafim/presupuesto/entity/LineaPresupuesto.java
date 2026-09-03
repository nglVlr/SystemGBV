package com.granados.sistema.dafim.presupuesto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Una fila del PDF SICOIN de ejecucion presupuestaria: un renglon de gasto
 * dentro de una estructura programatica, con sus montos y saldos.
 * FK plana deliberada (cargaId sin @ManyToOne): los listados y consultas
 * no deben arrastrar la carga completa; se navega solo cuando hace falta.
 */
@Entity
@Table(name = "presupuesto_lineas", indexes = {
        @Index(name = "idx_lineas_carga", columnList = "carga_id"),
        @Index(name = "idx_lineas_carga_renglon_fuente",
                columnList = "carga_id, renglon, fuente")})
public class LineaPresupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "carga_id", nullable = false)
    private Long cargaId;

    /* El parser entrega codigo + nombre ("01 ACTIVIDADES CENTRALES"): la
       etiqueta completa sirve para agrupar en la interfaz, no cabe en 10. */
    @Column(length = 90)
    private String programa;

    @Column(length = 90)
    private String subprograma;

    @Column(length = 90)
    private String proyecto;

    @Column(length = 90)
    private String actividad;

    @Column(length = 10)
    private String actividadObra;

    @Column(length = 3)
    private String renglon;

    @Column(length = 160)
    private String descripcion;

    @Column(length = 15)
    private String fuente;

    @Column(precision = 14, scale = 2)
    private BigDecimal asignado;

    @Column(precision = 14, scale = 2)
    private BigDecimal modificado;

    @Column(precision = 14, scale = 2)
    private BigDecimal vigente;

    @Column(precision = 14, scale = 2)
    private BigDecimal preCompromiso;

    @Column(precision = 14, scale = 2)
    private BigDecimal compromiso;

    @Column(precision = 14, scale = 2)
    private BigDecimal devengado;

    @Column(precision = 14, scale = 2)
    private BigDecimal pagado;

    @Column(precision = 14, scale = 2)
    private BigDecimal extraPresupuestario;

    @Column(precision = 14, scale = 2)
    private BigDecimal saldoDisponible;

    @Column(precision = 14, scale = 2)
    private BigDecimal saldoPorDevengar;

    @Column(precision = 14, scale = 2)
    private BigDecimal saldoPorPagar;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCargaId() { return cargaId; }
    public void setCargaId(Long cargaId) { this.cargaId = cargaId; }
    public String getPrograma() { return programa; }
    public void setPrograma(String programa) { this.programa = programa; }
    public String getSubprograma() { return subprograma; }
    public void setSubprograma(String subprograma) { this.subprograma = subprograma; }
    public String getProyecto() { return proyecto; }
    public void setProyecto(String proyecto) { this.proyecto = proyecto; }
    public String getActividad() { return actividad; }
    public void setActividad(String actividad) { this.actividad = actividad; }
    public String getActividadObra() { return actividadObra; }
    public void setActividadObra(String actividadObra) { this.actividadObra = actividadObra; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
    public BigDecimal getAsignado() { return asignado; }
    public void setAsignado(BigDecimal asignado) { this.asignado = asignado; }
    public BigDecimal getModificado() { return modificado; }
    public void setModificado(BigDecimal modificado) { this.modificado = modificado; }
    public BigDecimal getVigente() { return vigente; }
    public void setVigente(BigDecimal vigente) { this.vigente = vigente; }
    public BigDecimal getPreCompromiso() { return preCompromiso; }
    public void setPreCompromiso(BigDecimal preCompromiso) { this.preCompromiso = preCompromiso; }
    public BigDecimal getCompromiso() { return compromiso; }
    public void setCompromiso(BigDecimal compromiso) { this.compromiso = compromiso; }
    public BigDecimal getDevengado() { return devengado; }
    public void setDevengado(BigDecimal devengado) { this.devengado = devengado; }
    public BigDecimal getPagado() { return pagado; }
    public void setPagado(BigDecimal pagado) { this.pagado = pagado; }
    public BigDecimal getExtraPresupuestario() { return extraPresupuestario; }
    public void setExtraPresupuestario(BigDecimal extraPresupuestario) { this.extraPresupuestario = extraPresupuestario; }
    public BigDecimal getSaldoDisponible() { return saldoDisponible; }
    public void setSaldoDisponible(BigDecimal saldoDisponible) { this.saldoDisponible = saldoDisponible; }
    public BigDecimal getSaldoPorDevengar() { return saldoPorDevengar; }
    public void setSaldoPorDevengar(BigDecimal saldoPorDevengar) { this.saldoPorDevengar = saldoPorDevengar; }
    public BigDecimal getSaldoPorPagar() { return saldoPorPagar; }
    public void setSaldoPorPagar(BigDecimal saldoPorPagar) { this.saldoPorPagar = saldoPorPagar; }
}
