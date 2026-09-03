package com.granados.sistema.dafim.paquetes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Un paquete del mes: una hoja del Excel que manda la oficina. */
@Entity
@Table(name = "paq_paquetes",
        indexes = @Index(name = "idx_paq_mes", columnList = "anio, mes"))
public class PaqueteFacturas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private int mes;

    /** Posicion del paquete dentro del mes: 1, 2, 3... */
    @Column(nullable = false)
    private int numero;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(name = "total_esperado")
    private double totalEsperado;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public double getTotalEsperado() { return totalEsperado; }
    public void setTotalEsperado(double totalEsperado) { this.totalEsperado = totalEsperado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
