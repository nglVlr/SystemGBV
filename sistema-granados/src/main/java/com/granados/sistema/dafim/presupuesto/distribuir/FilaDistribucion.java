package com.granados.sistema.dafim.presupuesto.distribuir;

import java.math.BigDecimal;

/**
 * Una fila de la hoja de distribucion de pagos: una factura del mes con
 * la clasificacion (grupo/renglon/fuente) que propone la IA o el
 * clasificador local, la linea presupuestaria elegida y si el saldo
 * alcanza para apartarla.
 *
 * CONTRATO JSON con la hoja (distribuir.js): los nombres de campo son
 * fijos y no se renombran sin avisar al frontend:
 * facturaId, descripcion, emisor, monto, grupo, renglon, fuente,
 * lineaId, explicacion, saldoSuficiente.
 */
public class FilaDistribucion {

    private Long facturaId;
    private String descripcion = "";
    private String emisor = "";
    private BigDecimal monto = BigDecimal.ZERO;
    private String grupo = "";
    private String renglon = "";
    private String fuente = "";
    private Long lineaId;
    private String explicacion = "";
    private boolean saldoSuficiente;

    public FilaDistribucion() {}

    public FilaDistribucion(Long facturaId, String descripcion, String emisor, BigDecimal monto) {
        this.facturaId = facturaId;
        this.descripcion = descripcion == null ? "" : descripcion;
        this.emisor = emisor == null ? "" : emisor;
        this.monto = monto == null ? BigDecimal.ZERO : monto;
    }

    public Long getFacturaId() { return facturaId; }
    public void setFacturaId(Long facturaId) { this.facturaId = facturaId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getEmisor() { return emisor; }
    public void setEmisor(String emisor) { this.emisor = emisor; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
    public Long getLineaId() { return lineaId; }
    public void setLineaId(Long lineaId) { this.lineaId = lineaId; }
    public String getExplicacion() { return explicacion; }
    public void setExplicacion(String explicacion) { this.explicacion = explicacion; }
    public boolean isSaldoSuficiente() { return saldoSuficiente; }
    public void setSaldoSuficiente(boolean saldoSuficiente) { this.saldoSuficiente = saldoSuficiente; }
}
