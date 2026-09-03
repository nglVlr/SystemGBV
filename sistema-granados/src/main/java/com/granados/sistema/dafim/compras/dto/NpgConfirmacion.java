package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Datos extraidos de un PDF de confirmacion de Guatecompras (parsear_pdf_npg). */
public class NpgConfirmacion implements Serializable {
    private String npg = "";
    private String nit = "";
    private String nombre = "";
    private String contrato = "";
    private String desc = "";
    private String error = "";
    private String archivo = "";

    public String getNpg() { return npg; }
    public void setNpg(String npg) { this.npg = npg; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getContrato() { return contrato; }
    public void setContrato(String contrato) { this.contrato = contrato; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }
}
