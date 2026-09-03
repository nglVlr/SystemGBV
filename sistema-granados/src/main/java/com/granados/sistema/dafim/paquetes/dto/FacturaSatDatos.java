package com.granados.sistema.dafim.paquetes.dto;

import java.time.LocalDate;

/** Datos extraidos de una factura electronica (FEL) de la SAT. */
public class FacturaSatDatos {

    private String archivo = "";
    private String autorizacion = "";
    private String serie = "";
    private String numeroDte = "";
    private String nitEmisor = "";
    private String nombreEmisor = "";
    private LocalDate fechaEmision;
    private double monto;
    private String descripcion = "";
    private String error = "";

    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }
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
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
