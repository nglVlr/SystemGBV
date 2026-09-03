package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Una publicacion NPG del TXT de Guatecompras (parsear_txt). */
public class RegistroGuatecompras implements Serializable {
    private String npg;
    private String fecha;
    private String modalidad;
    private String desc;
    private String nit;
    private String proveedor;
    private double monto;

    public RegistroGuatecompras() {}

    public RegistroGuatecompras(String npg, String fecha, String modalidad, String desc,
                                String nit, String proveedor, double monto) {
        this.npg = npg; this.fecha = fecha; this.modalidad = modalidad;
        this.desc = desc; this.nit = nit; this.proveedor = proveedor; this.monto = monto;
    }

    public String getNpg() { return npg; }
    public void setNpg(String npg) { this.npg = npg; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
}
