package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.PersonaRemuneracion;
import com.granados.sistema.dafim.compras.dto.ResultadoValidacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorComprasServiceTest {

    private static FilaCompra fila(String renglon, double precio, String nit,
                                   String npg) {
        FilaCompra f = new FilaCompra();
        f.setRenglon(renglon); f.setPrecio(precio); f.setNit(nit);
        f.setNpg(npg); f.setCheque("1"); f.setProveedor("X");
        f.setModalidad("BAJA CUANTIA"); f.setDesc("d");
        f.setContrato("N/A"); f.setFechaPub(""); f.setFechaAdj("");
        f.setFechaCont("N/A");
        return f;
    }

    private static Cheque ch(double monto) {
        Cheque c = new Cheque();
        c.setCheque("1"); c.setNombre("X"); c.setMonto(monto);
        c.setFecha("05/06/2026");
        return c;
    }

    @Test
    void round2EsHalfEvenComoPython() {
        assertEquals(0.12, ValidadorComprasService.round2(0.125));
        // 2.675 en binario es 2.67499...: Python da 2.67 y el motor tambien
        assertEquals(0.14, ValidadorComprasService.round2(0.135));
        assertEquals(2.67, ValidadorComprasService.round2(2.675));
        assertEquals(1234.57, ValidadorComprasService.round2(1234.5678));
    }

    @Test
    void reprListaImitaPython() {
        assertEquals("['A', 'B']",
                ValidadorComprasService.reprLista(List.of("A", "B")));
        assertEquals("[]", ValidadorComprasService.reprLista(List.of()));
    }

    @Test
    void todoCuadraDaOk() {
        ResultadoValidacion rv = ValidadorComprasService.validar(
                List.of(fila("165", 100.00, "1", "E561234567"),
                        fila("029", 200.50, "2", "E567654321")),
                List.of(ch(100.00), ch(200.50)));
        assertTrue(rv.isOk());
        assertTrue(rv.getReporte().get(0).startsWith("[OK]"));
    }

    @Test
    void montoDescuadradoDaError() {
        ResultadoValidacion rv = ValidadorComprasService.validar(
                List.of(fila("165", 100.00, "1", "")),
                List.of(ch(150.00)));
        assertFalse(rv.isOk());
        assertTrue(rv.getReporte().stream().anyMatch(l -> l.startsWith("[ERR]")
                && l.contains("Monto")));
    }

    @Test
    void npgDuplicadoDaError() {
        ResultadoValidacion rv = ValidadorComprasService.validar(
                List.of(fila("165", 100.00, "1", "E561234567"),
                        fila("211", 200.00, "2", "E561234567")),
                List.of(ch(100.00), ch(200.00)));
        assertFalse(rv.isOk());
        assertTrue(rv.getReporte().stream()
                .anyMatch(l -> l.contains("NPGs duplicados: ['E561234567']")));
    }

    @Test
    void renglonExcluidoYVacioDanError() {
        ResultadoValidacion rv = ValidadorComprasService.validar(
                List.of(fila("331", 50.00, "1", ""), fila("", 25.00, "2", "")),
                List.of(ch(50.00), ch(25.00)));
        assertFalse(rv.isOk());
        assertTrue(rv.getReporte().stream()
                .anyMatch(l -> l.contains("con renglon excluido")));
        assertTrue(rv.getReporte().stream()
                .anyMatch(l -> l.contains("sin renglon")));
    }

    @Test
    void remuneracionesDetectaFaltantesYDiferencia() {
        List<FilaCompra> filas = List.of(fila029("MARIA JOSE GARCIA", 3000.00));
        List<PersonaRemuneracion> rem = List.of(
                persona("MARIA JOSE GARCIA", 3000.00),
                persona("CARMEN XILOJ TZI", 2500.00));
        List<String> obs = ValidadorComprasService.validarRemuneraciones(filas, rem);
        assertTrue(obs.stream().anyMatch(l -> l.contains("CARMEN XILOJ TZI")));
        assertTrue(obs.stream().anyMatch(l -> l.contains("Diferencia")));
    }

    private static FilaCompra fila029(String proveedor, double precio) {
        FilaCompra f = fila("029", precio, "9", "");
        f.setProveedor(proveedor);
        return f;
    }

    private static PersonaRemuneracion persona(String nombre, double monto) {
        PersonaRemuneracion p = new PersonaRemuneracion();
        p.setNombre(nombre); p.setMonto(monto); p.setCargo("");
        return p;
    }
}
