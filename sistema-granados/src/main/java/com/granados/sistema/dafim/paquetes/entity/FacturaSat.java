package com.granados.sistema.dafim.paquetes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Factura electronica (FEL) de la SAT guardada en el sistema. El numero
 * de autorizacion es UNICO: una factura jamas se puede usar dos veces.
 */
@Entity
@Table(name = "paq_facturas",
        indexes = @Index(name = "idx_paqfac_mes", columnList = "anio, mes"),
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_paqfac_autorizacion", columnNames = "autorizacion"))
public class FacturaSat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String autorizacion;

    @Column(length = 20)
    private String serie = "";

    @Column(name = "numero_dte", length = 20)
    private String numeroDte = "";

    @Column(name = "nit_emisor", length = 15)
    private String nitEmisor = "";

    @Column(name = "nombre_emisor", length = 120)
    private String nombreEmisor = "";

    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    @Column(nullable = false)
    private double monto;

    @Column(length = 600)
    private String descripcion = "";

    @Column(nullable = false)
    private int mes;

    @Column(nullable = false)
    private int anio;

    @Column(length = 150)
    private String archivo = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAutorizacion() { return autorizacion; }
    public void setAutorizacion(String autorizacion) { this.autorizacion = autorizacion; }
    public String getSerie() { return serie; }
    public void setSerie(String serie) { this.serie = serie; }
    public String getNumeroDte() { return numeroDte; }
    public void setNumeroDte(String numeroDte) { this.numeroDte = numeroDte; }
    public String getNitEmisor() { return nitEmisor; }
    public void setNitEmisor(String nitEmisor) { this.nitEmisor = nitEmisor; }
    public String getNombreEmisor() { return nombreEmisor; }
    public void setNombreEmisor(String nombreEmisor) { this.nombreEmisor = nombreEmisor; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }
}
