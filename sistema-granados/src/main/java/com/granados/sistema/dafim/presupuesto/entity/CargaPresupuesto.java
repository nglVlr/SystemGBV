package com.granados.sistema.dafim.presupuesto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una carga es una importacion del PDF SICOIN de ejecucion presupuestaria.
 * Guarda los metadatos y totales del archivo subido; el detalle vive en
 * LineaPresupuesto. La carga ACTIVA es la mas reciente: al subir otra del
 * mismo periodo, la anterior pasa a REEMPLAZADA y deja de mostrarse.
 */
@Entity
@Table(name = "presupuesto_cargas", indexes = {
        @Index(name = "idx_cargas_anio", columnList = "anio")})
public class CargaPresupuesto {

    public static final String EST_ACTIVA = "ACTIVA";
    public static final String EST_REEMPLAZADA = "REEMPLAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    private LocalDate periodoDesde;

    private LocalDate periodoHasta;

    @Column(length = 200)
    private String nombreArchivo;

    private LocalDateTime fechaCarga;

    @Column(length = 50)
    private String usuario;

    private Integer totalLineas;

    @Column(precision = 14, scale = 2)
    private BigDecimal totalVigente;

    @Column(precision = 14, scale = 2)
    private BigDecimal totalDevengado;

    @Column(precision = 14, scale = 2)
    private BigDecimal totalPagado;

    @Column(length = 15)
    private String estado;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public void setPeriodoDesde(LocalDate periodoDesde) { this.periodoDesde = periodoDesde; }
    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public void setPeriodoHasta(LocalDate periodoHasta) { this.periodoHasta = periodoHasta; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(LocalDateTime fechaCarga) { this.fechaCarga = fechaCarga; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public Integer getTotalLineas() { return totalLineas; }
    public void setTotalLineas(Integer totalLineas) { this.totalLineas = totalLineas; }
    public BigDecimal getTotalVigente() { return totalVigente; }
    public void setTotalVigente(BigDecimal totalVigente) { this.totalVigente = totalVigente; }
    public BigDecimal getTotalDevengado() { return totalDevengado; }
    public void setTotalDevengado(BigDecimal totalDevengado) { this.totalDevengado = totalDevengado; }
    public BigDecimal getTotalPagado() { return totalPagado; }
    public void setTotalPagado(BigDecimal totalPagado) { this.totalPagado = totalPagado; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
