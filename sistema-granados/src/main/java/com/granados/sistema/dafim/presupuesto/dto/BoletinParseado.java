package com.granados.sistema.dafim.presupuesto.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de parsear el Boletin de Caja Consolidado Diario: fecha de
 * corte, ejercicio y las cuentas monetarias (sin totales de cuenta fisica).
 * totalNuevoSaldo es la suma de los nuevos saldos de esas cuentas.
 */
public class BoletinParseado implements Serializable {
    private LocalDate fechaCorte;
    private int anio;
    private List<LineaCuentaMonetaria> cuentas = new ArrayList<>();
    private BigDecimal totalNuevoSaldo = BigDecimal.ZERO;

    public BoletinParseado() {}

    public LocalDate getFechaCorte() { return fechaCorte; }
    public void setFechaCorte(LocalDate fechaCorte) { this.fechaCorte = fechaCorte; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public List<LineaCuentaMonetaria> getCuentas() { return cuentas; }
    public void setCuentas(List<LineaCuentaMonetaria> cuentas) { this.cuentas = cuentas; }
    public BigDecimal getTotalNuevoSaldo() { return totalNuevoSaldo; }
    public void setTotalNuevoSaldo(BigDecimal totalNuevoSaldo) { this.totalNuevoSaldo = totalNuevoSaldo; }
}
