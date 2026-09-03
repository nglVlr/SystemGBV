package com.granados.sistema.dafim.compras.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trazabilidad: quien proceso que mes, cuando, con que totales y alertas.
 */
@Entity
@Table(name = "procesos_mensuales",
        indexes = @Index(name = "idx_procesos_anio_mes", columnList = "anio, mes"))
public class ProcesoMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer mes;

    @Column(name = "fecha_proceso")
    private LocalDateTime fechaProceso;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "total_filas")
    private Integer totalFilas;

    @Column(name = "total_monto", precision = 14, scale = 2)
    private BigDecimal totalMonto;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String alertas;

    @Column(name = "ruta_excel_generado", length = 300)
    private String rutaExcelGenerado;

    /** PROCESADO (solo vista previa) o GUARDADO (confirmado en historial). */
    @Column(length = 15)
    private String estado;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }
    public LocalDateTime getFechaProceso() { return fechaProceso; }
    public void setFechaProceso(LocalDateTime f) { this.fechaProceso = f; }
    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    public Integer getTotalFilas() { return totalFilas; }
    public void setTotalFilas(Integer totalFilas) { this.totalFilas = totalFilas; }
    public BigDecimal getTotalMonto() { return totalMonto; }
    public void setTotalMonto(BigDecimal totalMonto) { this.totalMonto = totalMonto; }
    public String getAlertas() { return alertas; }
    public void setAlertas(String alertas) { this.alertas = alertas; }
    public String getRutaExcelGenerado() { return rutaExcelGenerado; }
    public void setRutaExcelGenerado(String r) { this.rutaExcelGenerado = r; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
