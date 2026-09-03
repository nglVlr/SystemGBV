package com.granados.sistema.dafim.compras.entity;

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
 * Una fila del informe mensual ya confirmado y guardado.
 * Equivale a la hoja 'historial' de Google Sheets.
 */
@Entity
@Table(name = "historial_compras", indexes = {
        @Index(name = "idx_historial_anio_mes", columnList = "anio, mes"),
        @Index(name = "idx_historial_nit", columnList = "nit")})
public class HistorialCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer mes;

    @Column(length = 20)
    private String cheque;

    @Column(length = 20)
    private String nit;

    @Column(length = 150)
    private String nombre;

    @Column(length = 5)
    private String renglon;

    @Column(precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(length = 20)
    private String npg;

    @Column(length = 30)
    private String modalidad;

    @Column(length = 30)
    private String contrato;

    @Column(length = 200)
    private String descripcion;

    @Column(name = "id_usuario_proceso")
    private Long idUsuarioProceso;

    @Column(name = "fecha_proceso")
    private LocalDateTime fechaProceso;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }
    public String getCheque() { return cheque; }
    public void setCheque(String cheque) { this.cheque = cheque; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getNpg() { return npg; }
    public void setNpg(String npg) { this.npg = npg; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public String getContrato() { return contrato; }
    public void setContrato(String contrato) { this.contrato = contrato; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Long getIdUsuarioProceso() { return idUsuarioProceso; }
    public void setIdUsuarioProceso(Long id) { this.idUsuarioProceso = id; }
    public LocalDateTime getFechaProceso() { return fechaProceso; }
    public void setFechaProceso(LocalDateTime f) { this.fechaProceso = f; }
}
