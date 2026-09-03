package com.granados.sistema.dafim.paquetes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;

/**
 * El PDF original de una factura, en tabla aparte para que los listados
 * no carguen los archivos: solo se lee al imprimir o descargar.
 */
@Entity
@Table(name = "paq_facturas_pdf",
        indexes = @Index(name = "idx_paqpdf_factura", columnList = "factura_id"))
public class FacturaPdf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factura_id", nullable = false)
    private Long facturaId;

    @Lob
    @JdbcTypeCode(Types.BINARY)
    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] datos;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFacturaId() { return facturaId; }
    public void setFacturaId(Long facturaId) { this.facturaId = facturaId; }
    public byte[] getDatos() { return datos; }
    public void setDatos(byte[] datos) { this.datos = datos; }
}
