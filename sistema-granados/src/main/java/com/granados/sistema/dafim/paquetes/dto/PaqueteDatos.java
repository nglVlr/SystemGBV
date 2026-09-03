package com.granados.sistema.dafim.paquetes.dto;

import java.util.ArrayList;
import java.util.List;

/** Un paquete leido del Excel: una hoja con sus lineas y su total. */
public class PaqueteDatos {

    /** Una linea del paquete: concepto + monto en el orden de la hoja. */
    public static class Linea {
        private int orden;
        private String concepto = "";
        private double monto;

        public Linea() { }

        public Linea(int orden, String concepto, double monto) {
            this.orden = orden;
            this.concepto = concepto;
            this.monto = monto;
        }

        public int getOrden() { return orden; }
        public void setOrden(int orden) { this.orden = orden; }
        public String getConcepto() { return concepto; }
        public void setConcepto(String concepto) { this.concepto = concepto; }
        public double getMonto() { return monto; }
        public void setMonto(double monto) { this.monto = monto; }
    }

    private String nombreHoja = "";
    private final List<Linea> lineas = new ArrayList<>();
    /** Total escrito en la ultima fila de la hoja (0 si no venia). */
    private double totalEsperado;

    public String getNombreHoja() { return nombreHoja; }
    public void setNombreHoja(String nombreHoja) { this.nombreHoja = nombreHoja; }
    public List<Linea> getLineas() { return lineas; }
    public double getTotalEsperado() { return totalEsperado; }
    public void setTotalEsperado(double totalEsperado) { this.totalEsperado = totalEsperado; }

    public double sumaLineas() {
        return lineas.stream().mapToDouble(Linea::getMonto).sum();
    }
}
