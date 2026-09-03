package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Registro del historial mensual (independiente de JPA, para motor y export). */
public class RegistroHistorial implements Serializable {
    private int anio;
    private int mes;
    private String cheque = "";
    private String nit = "";
    private String nombre = "";
    private String renglon = "";
    private double monto;
    private String npg = "";
    private String modalidad = "";
    private String contrato = "";
    private String descripcion = "";

    public RegistroHistorial() {}

    public RegistroHistorial(int anio, int mes, String cheque, String nit, String nombre,
                             String renglon, double monto, String npg, String modalidad,
                             String contrato, String descripcion) {
        this.anio = anio; this.mes = mes; this.cheque = cheque; this.nit = nit;
        this.nombre = nombre; this.renglon = renglon; this.monto = monto; this.npg = npg;
        this.modalidad = modalidad; this.contrato = contrato; this.descripcion = descripcion;
    }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    public String getCheque() { return cheque; }
    public void setCheque(String cheque) { this.cheque = cheque; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public String getNpg() { return npg; }
    public void setNpg(String npg) { this.npg = npg; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public String getContrato() { return contrato; }
    public void setContrato(String contrato) { this.contrato = contrato; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
