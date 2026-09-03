package com.granados.sistema.dafim.compras.dto;

import com.granados.sistema.dafim.compras.util.Constantes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Filas leidas de un machote (Excel de un mes ya trabajado) en espera de
 * confirmacion para cargarse al historial. Vive en la sesion.
 */
public class MachotePendiente implements Serializable {

    private static final long serialVersionUID = 1L;

    private int anio;
    private int mes;
    private String nombreArchivo = "";
    private List<FilaCompra> filas = new ArrayList<>();
    private boolean mesYaGuardado;

    public String getMesNombre() {
        return Constantes.MESES_NOMBRE.getOrDefault(mes, "");
    }

    public double getTotalMonto() {
        double t = 0;
        for (FilaCompra f : filas) t += f.getPrecio();
        return t;
    }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String n) { this.nombreArchivo = n; }
    public List<FilaCompra> getFilas() { return filas; }
    public void setFilas(List<FilaCompra> filas) { this.filas = filas; }
    public boolean isMesYaGuardado() { return mesYaGuardado; }
    public void setMesYaGuardado(boolean m) { this.mesYaGuardado = m; }
}
