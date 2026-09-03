package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Un cheque IMPRESO del reporte .xls (parsear_cheques). */
public class Cheque implements Serializable {
    private String cheque;
    private String nombre;
    private double monto;
    /** LocalDate o String; el motor lo formatea con FechaUtil.fmt(). */
    private Object fecha;

    public Cheque() {}

    public Cheque(String cheque, String nombre, double monto, Object fecha) {
        this.cheque = cheque; this.nombre = nombre; this.monto = monto; this.fecha = fecha;
    }

    public String getCheque() { return cheque; }
    public void setCheque(String cheque) { this.cheque = cheque; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public Object getFecha() { return fecha; }
    public void setFecha(Object fecha) { this.fecha = fecha; }
}
