package com.granados.sistema.dafim.compras.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FechaUtilTest {

    @Test
    void formateaLocalDate() {
        assertEquals("05/06/2026", FechaUtil.fmt(LocalDate.of(2026, 6, 5)));
    }

    @Test
    void formateaUtilDate() {
        Calendar cal = new GregorianCalendar(2026, Calendar.JUNE, 5);
        assertEquals("05/06/2026", FechaUtil.fmt(cal.getTime()));
    }

    @Test
    void convierteIso() {
        assertEquals("05/06/2026", FechaUtil.fmt("2026-06-05"));
        assertEquals("05/06/2026", FechaUtil.fmt("2026-06-05 00:00:00"));
    }

    @Test
    void stringYaFormateadoPasaIgual() {
        assertEquals("05/06/2026", FechaUtil.fmt("05/06/2026"));
    }
}
