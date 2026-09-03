package com.granados.sistema.dafim.remuneraciones.dto;

import java.io.Serializable;

/** Una fila de empleado extraida de una planilla SICOIN GL (R00815454). */
public class FilaPlanilla implements Serializable {

    private String renglon = "";
    private String nombre = "";
    private String cargo = "";
    private String dependencia = "";
    private double totalDevengado;
    private double igss;
    private double fianza;
    private double otrasDeducciones;
    private double boniLey;
    private double bonifMunicipal;
    private double otrosIngresos;
    private double totalRecibir;

    public double descuentos() {
        return igss + fianza + otrasDeducciones;
    }

    public double totalIngresos() {
        return totalDevengado + boniLey + bonifMunicipal + otrosIngresos;
    }

    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getDependencia() { return dependencia; }
    public void setDependencia(String dependencia) { this.dependencia = dependencia; }
    public double getTotalDevengado() { return totalDevengado; }
    public void setTotalDevengado(double totalDevengado) { this.totalDevengado = totalDevengado; }
    public double getIgss() { return igss; }
    public void setIgss(double igss) { this.igss = igss; }
    public double getFianza() { return fianza; }
    public void setFianza(double fianza) { this.fianza = fianza; }
    public double getOtrasDeducciones() { return otrasDeducciones; }
    public void setOtrasDeducciones(double otrasDeducciones) { this.otrasDeducciones = otrasDeducciones; }
    public double getBoniLey() { return boniLey; }
    public void setBoniLey(double boniLey) { this.boniLey = boniLey; }
    public double getBonifMunicipal() { return bonifMunicipal; }
    public void setBonifMunicipal(double bonifMunicipal) { this.bonifMunicipal = bonifMunicipal; }
    public double getOtrosIngresos() { return otrosIngresos; }
    public void setOtrosIngresos(double otrosIngresos) { this.otrosIngresos = otrosIngresos; }
    public double getTotalRecibir() { return totalRecibir; }
    public void setTotalRecibir(double totalRecibir) { this.totalRecibir = totalRecibir; }
}
