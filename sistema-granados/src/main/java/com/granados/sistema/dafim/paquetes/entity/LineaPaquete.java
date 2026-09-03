package com.granados.sistema.dafim.paquetes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Una linea de un paquete (concepto + monto) y la factura que le quedo
 * asignada. Si factura es null, la linea sigue pendiente.
 */
@Entity
@Table(name = "paq_lineas")
public class LineaPaquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "paquete_id")
    private PaqueteFacturas paquete;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false, length = 400)
    private String concepto;

    @Column(nullable = false)
    private double monto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "factura_id")
    private FacturaSat factura;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PaqueteFacturas getPaquete() { return paquete; }
    public void setPaquete(PaqueteFacturas paquete) { this.paquete = paquete; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public FacturaSat getFactura() { return factura; }
    public void setFactura(FacturaSat factura) { this.factura = factura; }
}
