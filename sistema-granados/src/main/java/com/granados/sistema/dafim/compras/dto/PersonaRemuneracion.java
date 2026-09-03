package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;

/** Una persona del archivo de remuneraciones R029 (parsear_remuneraciones). */
public class PersonaRemuneracion implements Serializable {
    private String nombre;
    private double monto;
    private String cargo;

    public PersonaRemuneracion() {}

    public PersonaRemuneracion(String nombre, double monto, String cargo) {
        this.nombre = nombre; this.monto = monto; this.cargo = cargo;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
}
