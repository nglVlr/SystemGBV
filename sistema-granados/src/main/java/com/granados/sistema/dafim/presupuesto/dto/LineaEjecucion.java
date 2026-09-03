package com.granados.sistema.dafim.presupuesto.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Una linea de renglon del reporte SICOIN GL "Ejecucion de Egresos del
 * Ejercicio" (R00814981.rpt): renglon + fuente de financiamiento, con los
 * 11 importes que el reporte imprime en ese orden. El municipio compara la
 * ejecucion contra estos importes oficiales, por eso se conservan tal cual
 * (BigDecimal, sin redondeos) y no se recalculan.
 *
 * programa/subprograma/proyecto/actividad son el contexto jerarquico
 * vigente al momento de la linea; actividadObra es el codigo de la
 * columna "Act O" que el reporte asocia a cada bloque de renglones.
 */
public class LineaEjecucion implements Serializable {
    private String renglon;
    private String descripcion;
    private String fuente;
    private String programa = "";
    private String subprograma = "";
    private String proyecto = "";
    private String actividad = "";
    private String actividadObra = "";
    private BigDecimal asignado = BigDecimal.ZERO;
    private BigDecimal modificado = BigDecimal.ZERO;
    private BigDecimal vigente = BigDecimal.ZERO;
    private BigDecimal preCompromiso = BigDecimal.ZERO;
    private BigDecimal compromiso = BigDecimal.ZERO;
    private BigDecimal devengado = BigDecimal.ZERO;
    private BigDecimal pagado = BigDecimal.ZERO;
    private BigDecimal extraPresupuestario = BigDecimal.ZERO;
    private BigDecimal saldoDisponible = BigDecimal.ZERO;
    private BigDecimal saldoPorDevengar = BigDecimal.ZERO;
    private BigDecimal saldoPorPagar = BigDecimal.ZERO;

    public LineaEjecucion() {}

    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
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
