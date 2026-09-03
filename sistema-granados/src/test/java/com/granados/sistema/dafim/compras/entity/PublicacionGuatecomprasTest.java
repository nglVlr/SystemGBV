package com.granados.sistema.dafim.compras.entity;

import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicacionGuatecomprasTest {

    @Test
    void deRegistroCopiaCamposYParteLaFecha() {
        RegistroGuatecompras r = new RegistroGuatecompras(
                "E590038931", "27/08/2026", "BAJA CUANTIA",
                "PAGO DE SONIDO Y PANTALA", "40941442",
                "ORTIZ,ROSALES,,JUANA,PAOLA", 9600.00);
        PublicacionGuatecompras p = PublicacionGuatecompras.de(r, "CATALOGO");
        assertEquals("E590038931", p.getNpg());
        assertEquals("27/08/2026", p.getFechaTexto());
        assertEquals(LocalDate.of(2026, 8, 27), p.getFechaPub());
        assertEquals(2026, p.getAnio());
        assertEquals(8, p.getMes());
        assertEquals("BAJA CUANTIA", p.getModalidad());
        assertEquals("40941442", p.getNit());
        assertEquals("ORTIZ,ROSALES,,JUANA,PAOLA", p.getNombre());
        assertEquals(0, p.getMonto().compareTo(new BigDecimal("9600.00")));
        assertEquals("CATALOGO", p.getOrigen());
    }

    @Test
    void fechaIrreconocibleDejaAnioMesNulos() {
        RegistroGuatecompras r = new RegistroGuatecompras(
                "E568000010", "ayer", "BAJA CUANTIA", "x", "1", "A", 1);
        PublicacionGuatecompras p = PublicacionGuatecompras.de(r, "TXT_MES");
        assertEquals("ayer", p.getFechaTexto());
        assertNull(p.getFechaPub());
        assertNull(p.getAnio());
        assertNull(p.getMes());
    }
}
