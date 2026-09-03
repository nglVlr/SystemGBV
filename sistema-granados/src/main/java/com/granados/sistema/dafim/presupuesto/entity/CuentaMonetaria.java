package com.granados.sistema.dafim.presupuesto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.granados.sistema.dafim.presupuesto.parser.ParserBoletinCaja;

import java.math.BigDecimal;

/**
 * Una cuenta monetaria del boletin de caja activo: codigo SICOIN (fuente
 * extendida o retencion corta) y saldos del dia. FK plana a caja_cargas.
 */
@Entity
@Table(name = "caja_cuentas", indexes = {
        @Index(name = "idx_caja_cuentas_carga", columnList = "carga_id"),
        @Index(name = "idx_caja_cuentas_codigo", columnList = "codigo")})
public class CuentaMonetaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "carga_id", nullable = false)
    private Long cargaId;

    @Column(length = 40, nullable = false)
    private String codigo;

    @Column(length = 400)
    private String descripcion;

    @Column(precision = 14, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(precision = 14, scale = 2)
    private BigDecimal montoCredito;

    @Column(precision = 14, scale = 2)
    private BigDecimal montoDebito;

    @Column(precision = 14, scale = 2)
    private BigDecimal nuevoSaldo;

    /** funcionamiento / inversion / desconocido. Sin tildes. */
    @Column(length = 20)
    private String tipo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCargaId() { return cargaId; }
    public void setCargaId(Long cargaId) { this.cargaId = cargaId; }
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
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getEtiquetaTipoDinero() {
        String t = tipo == null ? "" : tipo.strip().toLowerCase();
        if (t.isEmpty()) {
            t = ParserBoletinCaja.clasificarTipo(codigo, descripcion).id();
        }
        if ("funcionamiento".equals(t)) return "funcionamiento";
        if ("inversion".equals(t)) return "inversión";
        return "—";
    }
}
