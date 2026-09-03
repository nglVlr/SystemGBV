package com.granados.sistema.dafim.paquetes;

import com.granados.sistema.dafim.paquetes.dto.FacturaSatDatos;
import com.granados.sistema.dafim.paquetes.parser.ParserFacturaSat;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserFacturaSatTest {

    @Test
    void leeFacturaRealDeLaSat() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/parser/factura.pdf")) {
            assertNotNull(in, "Falta src/test/resources/parser/factura.pdf");
            FacturaSatDatos f = ParserFacturaSat.parsear(in, "factura.pdf");
            assertEquals("", f.getError());
            assertEquals("566A86B4-381D-4513-8AA3-2B9807E0BA25", f.getAutorizacion());
            assertEquals("566A86B4", f.getSerie());
            assertEquals("941442323", f.getNumeroDte());
            assertEquals("318224275", f.getNitEmisor());
            assertEquals(LocalDate.of(2026, 6, 22), f.getFechaEmision());
            assertEquals(17000.00, f.getMonto(), 0.001);
            assertTrue(f.getDescripcion().toLowerCase().contains("computo")
                    || f.getDescripcion().toLowerCase().contains("c\u00f3mputo"));
        }
    }

    @Test
    void respaldaAutorizacionDesdeNombreDeArchivo() {
        FacturaSatDatos f = new FacturaSatDatos();
        f.setArchivo("11112222-3333-4444-5555-666677778888.pdf");
        String texto = "Serie: ABC123 Numero de DTE: 987654\n"
                + "Nit Emisor: 12345678\n"
                + "Fecha y hora de emision: 05-jul-2026 08:00:00\n"
                + "#No B/S Cantidad Descripcion\n"
                + "1 Servicio 1 Flete de materiales 500.00 0.00 500.00\n"
                + "TOTALES: 0.00 500.00\n";
        ParserFacturaSatTestHelper.llenar(f, texto);
        assertEquals("11112222-3333-4444-5555-666677778888", f.getAutorizacion());
        assertEquals("987654", f.getNumeroDte());
        assertEquals(500.00, f.getMonto(), 0.001);
        assertEquals(LocalDate.of(2026, 7, 5), f.getFechaEmision());
        assertTrue(f.getDescripcion().contains("Flete de materiales"));
        assertEquals("", f.getError());
    }
}
