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
 * Una carga es una importacion del Boletin de Caja SICOIN. La carga ACTIVA
 * es la mas reciente: al subir otra, la anterior pasa a REEMPLAZADA.
 * Independiente de la carga de ejecucion de egresos (el boletin se
 * actualiza a diario).
 */
@Entity
@Table(name = "caja_cargas", indexes = {
        @Index(name = "idx_caja_cargas_anio", columnList = "anio")})
public class CargaCaja {

    public static final String EST_ACTIVA = "ACTIVA";
    public static final String EST_REEMPLAZADA = "REEMPLAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    private LocalDate fechaCorte;

    @Column(length = 200)
    private String nombreArchivo;

    private LocalDateTime fechaCarga;

    @Column(length = 50)
    private String usuario;

    private Integer totalCuentas;

    @Column(precision = 14, scale = 2)
    private BigDecimal totalNuevoSaldo;

    @Column(length = 15)
    private String estado;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public LocalDate getFechaCorte() { return fechaCorte; }
    public void setFechaCorte(LocalDate fechaCorte) { this.fechaCorte = fechaCorte; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(LocalDateTime fechaCarga) { this.fechaCarga = fechaCarga; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public Integer getTotalCuentas() { return totalCuentas; }
    public void setTotalCuentas(Integer totalCuentas) { this.totalCuentas = totalCuentas; }
    public BigDecimal getTotalNuevoSaldo() { return totalNuevoSaldo; }
    public void setTotalNuevoSaldo(BigDecimal totalNuevoSaldo) { this.totalNuevoSaldo = totalNuevoSaldo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
