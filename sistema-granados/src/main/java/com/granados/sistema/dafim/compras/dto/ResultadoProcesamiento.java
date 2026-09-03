package com.granados.sistema.dafim.compras.dto;

import com.granados.sistema.dafim.compras.util.Constantes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado completo de un procesamiento mensual, guardado en la sesion
 * entre la vista previa y la confirmacion (por eso es Serializable).
 */
public class ResultadoProcesamiento implements Serializable {

    private static final long serialVersionUID = 1L;

    private int anio;
    private int mes;
    private List<FilaCompra> filas = new ArrayList<>();
    private List<String> alertas = new ArrayList<>();
    private List<String> reporteValidacion = new ArrayList<>();
    private boolean validacionOk;
    private List<String> obsRemuneraciones = new ArrayList<>();
    private List<String> obsComparacion = new ArrayList<>();
    private Map<String, ContratoInfo> nuevos029 = new LinkedHashMap<>();
    private Map<String, ProveedorInfo> nuevosProv = new LinkedHashMap<>();
    private List<RegistroGuatecompras> npgsSinCheque = new ArrayList<>();
    private String nombreExcel = "";
    private int totalCheques;
    private double totalMonto;
    private boolean mesYaGuardado;

    public String getMesNombre() {
        return Constantes.MESES_NOMBRE.getOrDefault(mes, "");
    }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    public List<FilaCompra> getFilas() { return filas; }
    public void setFilas(List<FilaCompra> filas) { this.filas = filas; }
    public List<String> getAlertas() { return alertas; }
    public void setAlertas(List<String> alertas) { this.alertas = alertas; }
    public List<String> getReporteValidacion() { return reporteValidacion; }
    public void setReporteValidacion(List<String> r) { this.reporteValidacion = r; }
    public boolean isValidacionOk() { return validacionOk; }
    public void setValidacionOk(boolean validacionOk) { this.validacionOk = validacionOk; }
    public List<String> getObsRemuneraciones() { return obsRemuneraciones; }
    public void setObsRemuneraciones(List<String> o) { this.obsRemuneraciones = o; }
    public List<String> getObsComparacion() { return obsComparacion; }
    public void setObsComparacion(List<String> o) { this.obsComparacion = o; }
    public Map<String, ContratoInfo> getNuevos029() { return nuevos029; }
    public void setNuevos029(Map<String, ContratoInfo> n) { this.nuevos029 = n; }
    public Map<String, ProveedorInfo> getNuevosProv() { return nuevosProv; }
    public void setNuevosProv(Map<String, ProveedorInfo> n) { this.nuevosProv = n; }
    public List<RegistroGuatecompras> getNpgsSinCheque() { return npgsSinCheque; }
    public void setNpgsSinCheque(List<RegistroGuatecompras> n) {
        this.npgsSinCheque = n == null ? new ArrayList<>() : n;
    }
    public String getNombreExcel() { return nombreExcel; }
    public void setNombreExcel(String nombreExcel) { this.nombreExcel = nombreExcel; }
    public int getTotalCheques() { return totalCheques; }
    public void setTotalCheques(int totalCheques) { this.totalCheques = totalCheques; }
    public double getTotalMonto() { return totalMonto; }
    public void setTotalMonto(double totalMonto) { this.totalMonto = totalMonto; }
    public boolean isMesYaGuardado() { return mesYaGuardado; }
    public void setMesYaGuardado(boolean m) { this.mesYaGuardado = m; }
}
