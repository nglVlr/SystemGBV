package com.granados.sistema.dafim.presupuesto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catalogo de fuentes de financiamiento para consultar el presupuesto
 * por fuente. El codigo (llave natural, p.ej. "31-0151-0001") viene del
 * PDF SICOIN; el nombre nace vacio y lo edita el usuario.
 */
@Entity
@Table(name = "presupuesto_fuentes")
public class FuenteFinanciamiento {

    @Id
    @Column(length = 15)
    private String codigo;

    @Column(length = 160)
    private String nombre;

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
