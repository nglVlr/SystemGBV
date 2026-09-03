package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Una linea presupuestaria del PDF SICOIN (parsear_pdf). */
public class RegistroSicoin implements Serializable {
    private String cheque;
    private String nit;
    private String nombre;
    private String renglon;
    private double monto;
    private String desc;
    private String contrato = "N/A";
    private String status;
    private String fecha;

    public RegistroSicoin() {}

    public RegistroSicoin(String cheque, String nit, String nombre, String renglon,
                          double monto, String desc, String contrato, String status, String fecha) {
        this.cheque = cheque; this.nit = nit; this.nombre = nombre; this.renglon = renglon;
        this.monto = monto; this.desc = desc; this.contrato = contrato;
        this.status = status; this.fecha = fecha;
    }

    public String getCheque() { return cheque; }
    public void setCheque(String cheque) { this.cheque = cheque; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getContrato() { return contrato; }
    public void setContrato(String contrato) { this.contrato = contrato; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}
