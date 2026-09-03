package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.RegistroHistorial;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparadorMensualServiceTest {

    private static FilaCompra fila(String nit, String proveedor, String renglon,
                                   double precio) {
        FilaCompra f = new FilaCompra();
        f.setNit(nit); f.setProveedor(proveedor); f.setRenglon(renglon);
        f.setPrecio(precio); f.setCheque("1"); f.setDesc("d");
        f.setModalidad(""); f.setNpg(""); f.setContrato("N/A");
        f.setFechaPub(""); f.setFechaAdj(""); f.setFechaCont("N/A");
        return f;
    }

    private static RegistroHistorial hist(int mes, String nit, String nombre,
                                          String renglon, double monto) {
        RegistroHistorial h = new RegistroHistorial();
        h.setAnio(2026); h.setMes(mes); h.setNit(nit); h.setNombre(nombre);
        h.setRenglon(renglon); h.setMonto(monto); h.setCheque("");
        h.setNpg(""); h.setModalidad(""); h.setContrato("");
        h.setDescripcion("");
        return h;
    }

    @Test
    void sinHistorialAvisaQueNoHayDatos() {
        List<String> obs = ComparadorMensualService.comparar(
                List.of(fila("1", "A", "165", 10.0)), List.of(), 2026, 6);
        assertEquals(1, obs.size());
        assertTrue(obs.get(0).contains("no hay datos de 5/2026"));
    }

    @Test
    void detectaNuevosSeFueronYCambios029ConZfill() {
        // el historial trae el renglon como "29": debe normalizarse a 029
        List<RegistroHistorial> hist = List.of(
                hist(5, "4040404", "MARIA GARCIA", "29", 2950.00),
                hist(5, "9999", "TRANSPORTES EL RAPIDO", "211", 1500.00),
                hist(4, "0000", "OTRO MES NO CUENTA", "165", 1.00));
        List<FilaCompra> filas = List.of(
                fila("4040404", "MARIA GARCIA", "029", 3000.00),
                fila("8080", "FERRETERIA NUEVA", "274", 500.00));
        List<String> obs = ComparadorMensualService.comparar(filas, hist, 2026, 6);
        String todo = String.join("\n", obs);
        assertTrue(todo.contains("05/2026 (2 registros)"));
        assertTrue(todo.contains("FERRETERIA NUEVA (8080)"));
        assertTrue(todo.contains("TRANSPORTES EL RAPIDO (9999)"));
        assertTrue(todo.contains("MARIA GARCIA: Q2,950.00 -> Q3,000.00"));
    }

    @Test
    void sinCambiosDaOk() {
        List<RegistroHistorial> hist = List.of(
                hist(5, "1111", "TALLER", "165", 3500.00));
        List<FilaCompra> filas = List.of(fila("1111", "TALLER", "165", 3500.00));
        List<String> obs = ComparadorMensualService.comparar(filas, hist, 2026, 6);
        assertTrue(obs.get(obs.size() - 1).contains("[OK]"));
    }
}
