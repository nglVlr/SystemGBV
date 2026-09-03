package com.granados.sistema.dafim.presupuesto.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Una fila de cuenta monetaria del Boletin de Caja Consolidado Diario
 * (R00815627.rpt): codigo + descripcion y los 4 importes impresos
 * (saldo anterior, credito, debito, nuevo saldo). El nuevo saldo es el
 * dinero real disponible en esa cuenta.
 */
public class LineaCuentaMonetaria implements Serializable {

    /** Tipo de pozo de caja. Identificadores sin tildes: funcionamiento / inversion. */
    public enum TipoDineroCaja {
        FUNCIONAMIENTO,
        INVERSION,
        DESCONOCIDO;

        public String id() {
            return name().toLowerCase();
        }
    }

    private String codigo;
    private String descripcion = "";
    private BigDecimal saldoAnterior = BigDecimal.ZERO;
    private BigDecimal montoCredito = BigDecimal.ZERO;
    private BigDecimal montoDebito = BigDecimal.ZERO;
    private BigDecimal nuevoSaldo = BigDecimal.ZERO;
    private TipoDineroCaja tipo = TipoDineroCaja.DESCONOCIDO;

    public LineaCuentaMonetaria() {}

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getSaldoAnterior() { return saldoAnterior; }
    public void setSaldoAnterior(BigDecimal saldoAnterior) { this.saldoAnterior = saldoAnterior; }
    public BigDecimal getMontoCredito() { return montoCredito; }
    public void setMontoCredito(BigDecimal montoCredito) { this.montoCredito = montoCredito; }
    public BigDecimal getMontoDebito() { return montoDebito; }
    public void setMontoDebito(BigDecimal montoDebito) { this.montoDebito = montoDebito; }
    public BigDecimal getNuevoSaldo() { return nuevoSaldo; }
    public void setNuevoSaldo(BigDecimal nuevoSaldo) { this.nuevoSaldo = nuevoSaldo; }
    public TipoDineroCaja getTipo() { return tipo; }
    public void setTipo(TipoDineroCaja tipo) {
        this.tipo = tipo == null ? TipoDineroCaja.DESCONOCIDO : tipo;
    }
}
