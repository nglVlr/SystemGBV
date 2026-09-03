package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Valor del mapa BD_CONTRATOS {nit -> info} del Python. */
public class ContratoInfo implements Serializable {
    private String nombre = "";
    private String contrato = "N/A";
    private String cargo = "";
    private String npg = "";
    private Integer anio;

    public ContratoInfo() {}

    public ContratoInfo(String nombre, String contrato, String cargo, String npg, Integer anio) {
        this.nombre = nombre;
        this.contrato = (contrato == null || contrato.isEmpty()) ? "N/A" : contrato;
        this.cargo = cargo == null ? "" : cargo;
        this.npg = npg == null ? "" : npg;
        this.anio = anio;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getContrato() { return contrato; }
    public void setContrato(String contrato) { this.contrato = contrato; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getNpg() { return npg; }
    public void setNpg(String npg) { this.npg = npg; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
}
