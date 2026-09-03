package com.granados.sistema.dafim.presupuesto.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de parsear el reporte SICOIN GL "Ejecucion de Egresos del
 * Ejercicio": el periodo impreso en el encabezado, el ejercicio (anio
 * fiscal, tomado del inicio del periodo) y las lineas de renglon.
 *
 * totalVigente/totalDevengado/totalPagado son las sumas calculadas de las
 * lineas (no el renglon TOTAL del PDF): sirven para cuadrar al centavo
 * contra los totales impresos y validar que ninguna fila se perdio.
 */
public class EjecucionParseada implements Serializable {
    private LocalDate periodoDesde;
    private LocalDate periodoHasta;
    private int anio;
    private List<LineaEjecucion> lineas = new ArrayList<>();
    private BigDecimal totalVigente = BigDecimal.ZERO;
    private BigDecimal totalDevengado = BigDecimal.ZERO;
    private BigDecimal totalPagado = BigDecimal.ZERO;

    public EjecucionParseada() {}

    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public void setPeriodoDesde(LocalDate periodoDesde) { this.periodoDesde = periodoDesde; }
    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public void setPeriodoHasta(LocalDate periodoHasta) { this.periodoHasta = periodoHasta; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public List<LineaEjecucion> getLineas() { return lineas; }
    public void setLineas(List<LineaEjecucion> lineas) { this.lineas = lineas; }
    public BigDecimal getTotalVigente() { return totalVigente; }
    public void setTotalVigente(BigDecimal totalVigente) { this.totalVigente = totalVigente; }
    public BigDecimal getTotalDevengado() { return totalDevengado; }
    public void setTotalDevengado(BigDecimal totalDevengado) { this.totalDevengado = totalDevengado; }
    public BigDecimal getTotalPagado() { return totalPagado; }
    public void setTotalPagado(BigDecimal totalPagado) { this.totalPagado = totalPagado; }
}
