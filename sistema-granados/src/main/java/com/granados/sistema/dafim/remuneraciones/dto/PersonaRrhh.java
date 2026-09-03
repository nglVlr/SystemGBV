package com.granados.sistema.dafim.remuneraciones.dto;

import java.io.Serializable;

/** Persona del maestro RRHH usada por el motor (sin montos). */
public class PersonaRrhh implements Serializable {
    private String nombre = "";
    private String cargo = "";
    private String dependencia = "";
    private String renglon = "";

    public PersonaRrhh() {}

    public PersonaRrhh(String nombre, String cargo, String dependencia, String renglon) {
        this.nombre = nombre;
        this.cargo = cargo;
        this.dependencia = dependencia;
        this.renglon = renglon;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getDependencia() { return dependencia; }
    public void setDependencia(String dependencia) { this.dependencia = dependencia; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
}
