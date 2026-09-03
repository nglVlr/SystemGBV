package com.granados.sistema.dafim.remuneraciones.dto;

import java.io.Serializable;

/** Fila del oficio LAIP de remuneraciones (columnas A-R). */
public class FilaRemuneracion implements Serializable {

    private int numero;
    private String renglon = "";
    private String nombre = "";
    private String cargo = "";
    private String dependencia = "";
    private double dietas;
    private double sueldoBase;
    private double honorarios;
    private double complementoAntiguedad;
    private double bonifProfesional;
    private double bonoEspecifico;
    private double bonifIncentivo;
    private double gastosFunerarios;
    private double gastosRepresentacion;
    private double otrasRemuneraciones;
    private double totalIngresos;
    private double descuentos;
    private double liquido;
    private boolean incompleta;

    public String renglonExcel() {
        if (renglon == null || renglon.isBlank()) return "";
        String r = renglon.replaceFirst("^0+", "");
        return r.isEmpty() ? "0" : r;
    }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getRenglon() { return renglon; }
    public void setRenglon(String renglon) { this.renglon = renglon; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getDependencia() { return dependencia; }
    public void setDependencia(String dependencia) { this.dependencia = dependencia; }
    public double getDietas() { return dietas; }
    public void setDietas(double dietas) { this.dietas = dietas; }
    public double getSueldoBase() { return sueldoBase; }
    public void setSueldoBase(double sueldoBase) { this.sueldoBase = sueldoBase; }
    public double getHonorarios() { return honorarios; }
    public void setHonorarios(double honorarios) { this.honorarios = honorarios; }
    public double getComplementoAntiguedad() { return complementoAntiguedad; }
    public void setComplementoAntiguedad(double v) { this.complementoAntiguedad = v; }
    public double getBonifProfesional() { return bonifProfesional; }
    public void setBonifProfesional(double bonifProfesional) { this.bonifProfesional = bonifProfesional; }
    public double getBonoEspecifico() { return bonoEspecifico; }
    public void setBonoEspecifico(double bonoEspecifico) { this.bonoEspecifico = bonoEspecifico; }
    public double getBonifIncentivo() { return bonifIncentivo; }
    public void setBonifIncentivo(double bonifIncentivo) { this.bonifIncentivo = bonifIncentivo; }
    public double getGastosFunerarios() { return gastosFunerarios; }
    public void setGastosFunerarios(double gastosFunerarios) { this.gastosFunerarios = gastosFunerarios; }
    public double getGastosRepresentacion() { return gastosRepresentacion; }
    public void setGastosRepresentacion(double gastosRepresentacion) { this.gastosRepresentacion = gastosRepresentacion; }
    public double getOtrasRemuneraciones() { return otrasRemuneraciones; }
    public void setOtrasRemuneraciones(double otrasRemuneraciones) { this.otrasRemuneraciones = otrasRemuneraciones; }
    public double getTotalIngresos() { return totalIngresos; }
    public void setTotalIngresos(double totalIngresos) { this.totalIngresos = totalIngresos; }
    public double getDescuentos() { return descuentos; }
    public void setDescuentos(double descuentos) { this.descuentos = descuentos; }
    public double getLiquido() { return liquido; }
    public void setLiquido(double liquido) { this.liquido = liquido; }
    public boolean isIncompleta() { return incompleta; }
    public void setIncompleta(boolean incompleta) { this.incompleta = incompleta; }
}
