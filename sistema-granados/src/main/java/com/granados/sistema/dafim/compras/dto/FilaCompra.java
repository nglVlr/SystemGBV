package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Una fila del informe de compras directas (el dict "fila" del Python). */
public class FilaCompra implements Serializable {
    private String cheque = "";
    private String modalidad = "";
    private String desc = "";
    private double precio;
    private String renglon = "";
    private String proveedor = "";
    private String nit = "";
    private String npg = "";
    private String fechaPub = "";
    private String fechaAdj = "";
    private String contrato = "N/A";
    private String fechaCont = "N/A";

    public FilaCompra() {}

    public String getCheque() { return cheque; }
    public void setCheque(String cheque) { this.cheque = cheque; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNpg() { return npg; }
    public void setNpg(String npg) { this.npg = npg; }
    public String getFechaPub() { return fechaPub; }
    public void setFechaPub(String fechaPub) { this.fechaPub = fechaPub; }
    public String getFechaAdj() { return fechaAdj; }
    public void setFechaAdj(String fechaAdj) { this.fechaAdj = fechaAdj; }
    public String getContrato() { return contrato; }
    public void setContrato(String contrato) { this.contrato = contrato; }
    public String getFechaCont() { return fechaCont; }
    public void setFechaCont(String fechaCont) { this.fechaCont = fechaCont; }
}
