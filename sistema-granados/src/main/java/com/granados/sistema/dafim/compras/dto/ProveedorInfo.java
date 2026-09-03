package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Valor del mapa BD_PROVEEDORES {nit -> info} del Python. */
public class ProveedorInfo implements Serializable {
    private String nombre = "";
    private String renglon = "";
    private String desc = "";

    public ProveedorInfo() {}

    public ProveedorInfo(String nombre, String renglon, String desc) {
        this.nombre = nombre; this.renglon = renglon; this.desc = desc == null ? "" : desc;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
}
