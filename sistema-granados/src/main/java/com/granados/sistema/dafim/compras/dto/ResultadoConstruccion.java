package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Retorno de construirFilas: (filas, alertas, nuevos_029, nuevos_prov). */
public class ResultadoConstruccion implements Serializable {
    private List<FilaCompra> filas = new ArrayList<>();
    private List<String> alertas = new ArrayList<>();
    private List<RegistroGuatecompras> npgsSinCheque = new ArrayList<>();
    private LinkedHashMap<String, ContratoInfo> nuevos029 = new LinkedHashMap<>();
    private LinkedHashMap<String, ProveedorInfo> nuevosProv = new LinkedHashMap<>();

    public List<FilaCompra> getFilas() { return filas; }
    public void setFilas(List<FilaCompra> filas) { this.filas = filas; }
    public List<String> getAlertas() { return alertas; }
    public List<RegistroGuatecompras> getNpgsSinCheque() { return npgsSinCheque; }
    public void setNpgsSinCheque(List<RegistroGuatecompras> n) {
        this.npgsSinCheque = n == null ? new ArrayList<>() : n;
    }
    public LinkedHashMap<String, ContratoInfo> getNuevos029() { return nuevos029; }
    public LinkedHashMap<String, ProveedorInfo> getNuevosProv() { return nuevosProv; }
}
