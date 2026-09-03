package com.granados.sistema.dafim.presupuesto.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MensajesCargaTest {

    @Test
    void jergaDelParserDeEgresosSaleComoPdfDeEjecucion() {
        String msg = MensajesCarga.errorEgresos(new IllegalStateException(
                "Fila de ejecucion con 7 importes (se esperaban 11): 011 21-0101-0001 resto"));
        assertTrue(msg.toLowerCase().contains("ejecucion de egresos"));
        assertFalse(msg.contains("Fila de ejecucion"));
        assertFalse(msg.contains("importes"));
        assertFalse(msg.contains("R008"));
    }

    @Test
    void jergaDelParserDeCajaSaleComoBoletinDeCaja() {
        String msg = MensajesCarga.errorCaja(new IllegalStateException(
                "Cuenta monetaria con 2 importes (se esperaban 4): 21-0101-0001-0-0-1 resto"));
        assertTrue(msg.toLowerCase().contains("boletin de caja"));
        assertFalse(msg.contains("Cuenta monetaria"));
        assertFalse(msg.contains("R008"));
    }

    @Test
    void errorDePdfBoxNoLlegaEnIngles() {
        String msg = MensajesCarga.errorEgresos(new IOException(
                "Error: End-of-File, expected line at offset 0"));
        assertTrue(msg.toLowerCase().contains("pdf"));
        assertFalse(msg.toLowerCase().contains("end-of-file"));
        assertFalse(msg.contains("offset"));
    }

    @Test
    void nullPointerNoSeMuestra() {
        String msg = MensajesCarga.errorCaja(new NullPointerException("null"));
        assertFalse(msg.toLowerCase().contains("nullpointer"));
        assertFalse(msg.equalsIgnoreCase("null"));
        assertTrue(msg.toLowerCase().contains("boletin de caja"));
    }

    @Test
    void mensajeClaroDelServicioSeConserva() {
        String original = "El PDF no contiene lineas de ejecucion reconocibles. "
                + "Verifica que sea el reporte de ejecucion de egresos.";
        assertEquals(original, MensajesCarga.errorEgresos(new IllegalArgumentException(original)));
    }

    @Test
    void avisoDeOtraPersonaSeConserva() {
        String original = "Otra persona esta haciendo este mismo trabajo ahora. "
                + "Espera a que termine e intenta de nuevo.";
        assertEquals(original, MensajesCarga.errorEgresos(new IllegalStateException(original)));
    }

    @Test
    void excelNoCuentaComoPdf() {
        MockMultipartFile xlsx = new MockMultipartFile(
                "archivo", "presupuesto.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});
        assertTrue(MensajesCarga.noEsPdf(xlsx));
        MockMultipartFile pdf = new MockMultipartFile(
                "archivo", "egresos.pdf", "application/pdf", new byte[] {1});
        assertFalse(MensajesCarga.noEsPdf(pdf));
    }
}
