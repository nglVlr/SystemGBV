package com.granados.sistema.dafim.remuneraciones.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ResultadoRemuneraciones implements Serializable {

    private int mes;
    private int anio;
    private List<FilaRemuneracion> filas = new ArrayList<>();
    private List<String> alertas = new ArrayList<>();
    private String nombreExcel = "";
    private double totalIngresos;
    private double totalDescuentos;
    private double totalLiquido;

    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public List<FilaRemuneracion> getFilas() { return filas; }
    public void setFilas(List<FilaRemuneracion> filas) { this.filas = filas; }
    public List<String> getAlertas() { return alertas; }
    public void setAlertas(List<String> alertas) { this.alertas = alertas; }
    public String getNombreExcel() { return nombreExcel; }
    public void setNombreExcel(String nombreExcel) { this.nombreExcel = nombreExcel; }
    public double getTotalIngresos() { return totalIngresos; }
    public void setTotalIngresos(double totalIngresos) { this.totalIngresos = totalIngresos; }
    public double getTotalDescuentos() { return totalDescuentos; }
    public void setTotalDescuentos(double totalDescuentos) { this.totalDescuentos = totalDescuentos; }
    public double getTotalLiquido() { return totalLiquido; }
    public void setTotalLiquido(double totalLiquido) { this.totalLiquido = totalLiquido; }
}
