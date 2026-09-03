package com.granados.sistema.dafim.presupuesto.parser;

import com.granados.sistema.dafim.presupuesto.dto.BoletinParseado;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria.TipoDineroCaja;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserBoletinCajaTest {

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " y vino " + actual);
    }

    private static LineaCuentaMonetaria buscar(BoletinParseado b, String codigo) {
        return b.getCuentas().stream()
                .filter(c -> codigo.equals(c.getCodigo()))
                .findFirst().orElse(null);
    }

    @Test
    void leeCuentasFuenteYCortasConFilasPartidas() {
        List<String> lineas = List.of(
                "SIAF: SICOIN GL Pagina: 1",
                "Boletin de Caja Consolidado Diario por Cuenta Corriente",
                "Ejercicio: 2026",
                "Fecha de: 13/08/2026",
                "Cuenta Fisica No: 3130006687 CUENTA DEL TESORO 143,942.41 3,064,254.32 3,447,173.37 526,861.46",
                "118 PLAN DE PRESTACIONES DEL EMPLEADO MUNICIPAL 0.00 17,508.40 17,508.40 0.00",
                "21-0101-0001-0-0-1 Ingresos Tributarios IVA-PAZ-FUNCIONAMIENTO 0.96 412,120.00 505,164.95 93,045.91",
                "21-0101-0001-0-0-2 Ingresos Tributarios IVA-PAZ-INVERSION 0.00 1,523,400.37 1,529,400.37 6,000.00",
                "31-0101-0004-329-1-2 CODEDE-IVA PAZ-MEJORAMIENTO CAMINO RURAL CASERIO SACRAMENTO",
                "GRANADOS, BAJA VERAPAZ / INVERSION",
                "120,000.00 0.00 0.00 120,000.00",
                "31-0151-0001-0-0-1 Ingresos Propios Municipales-FUNCIONAMIENTO 5,874.64 24,195.00 41,995.00 23,674.64",
                "TOTAL : 143,942.41 4,355,529.88 3,972,610.83 526,861.46");

        BoletinParseado b = ParserBoletinCaja.parsearLineas(lineas);

        assertEquals(LocalDate.of(2026, 8, 13), b.getFechaCorte());
        assertEquals(2026, b.getAnio());
        assertEquals(5, b.getCuentas().size());

        LineaCuentaMonetaria c118 = buscar(b, "118");
        assertNotNull(c118);
        assertMonto("0.00", c118.getNuevoSaldo());

        LineaCuentaMonetaria f1 = buscar(b, "21-0101-0001-0-0-1");
        assertNotNull(f1);
        assertMonto("93045.91", f1.getNuevoSaldo());
        assertTrue(f1.getDescripcion().contains("IVA-PAZ-FUNCIONAMIENTO"));

        LineaCuentaMonetaria codede = buscar(b, "31-0101-0004-329-1-2");
        assertNotNull(codede);
        assertMonto("120000.00", codede.getNuevoSaldo());
        assertTrue(codede.getDescripcion().contains("SACRAMENTO"));
        assertTrue(codede.getDescripcion().contains("INVERSION"));

        assertMonto("242720.55", b.getTotalNuevoSaldo());
    }

    @Test
    void ignoraCuentaFisicaYCes() {
        List<String> lineas = List.of(
                "Ejercicio: 2026",
                "Fecha de: 13/08/2026",
                "Cuenta Fisica No: 3-130-00054-6 MUNICIPALIDAD GRANADOS 0.00 0.00 0.00 0.00",
                "3-130-00054-6126 CES_MUNICIPALIDAD GRANADOS 0.00 0.00 0.00 0.00",
                "201 CUOTAS I.G.S.S. 0.00 12,080.80 12,080.80 0.00");
        BoletinParseado b = ParserBoletinCaja.parsearLineas(lineas);
        assertEquals(1, b.getCuentas().size());
        assertEquals("201", b.getCuentas().get(0).getCodigo());
    }

    @Test
    void leePdfReal() throws Exception {
        BoletinParseado b;
        try (var in = getClass().getResourceAsStream("/parser/boletin-caja.pdf")) {
            assertNotNull(in, "falta boletin-caja.pdf en test resources");
            b = ParserBoletinCaja.parsear(in);
        }
        assertEquals(LocalDate.of(2026, 8, 13), b.getFechaCorte());
        assertEquals(2026, b.getAnio());
        assertTrue(b.getCuentas().size() >= 20, "cuentas=" + b.getCuentas().size());

        LineaCuentaMonetaria f1 = buscar(b, "21-0101-0001-0-0-1");
        assertNotNull(f1);
        assertMonto("93045.91", f1.getNuevoSaldo());

        LineaCuentaMonetaria sac = buscar(b, "31-0101-0004-329-1-2");
        assertNotNull(sac);
        assertMonto("120000.00", sac.getNuevoSaldo());
    }

    @Test
    void clasificaFuncionamientoPorDescripcionAntesQueElCodigo() {
        assertEquals(TipoDineroCaja.FUNCIONAMIENTO, ParserBoletinCaja.clasificarTipo(
                "21-0101-0001-0-0-2",
                "Ingresos Tributarios IVA-PAZ-FUNCIONAMIENTO"));
    }

    @Test
    void clasificaInversionPorDescripcionSinTildeYConTilde() {
        assertEquals(TipoDineroCaja.INVERSION, ParserBoletinCaja.clasificarTipo(
                "21-0101-0001-0-0-1", "Ingresos Tributarios IVA-PAZ-INVERSION"));
        assertEquals(TipoDineroCaja.INVERSION, ParserBoletinCaja.clasificarTipo(
                "21-0101-0001-0-0-1", "Ingresos Tributarios IVA-PAZ-INVERSIÓN"));
        assertEquals(TipoDineroCaja.INVERSION, ParserBoletinCaja.clasificarTipo(
                "31-0101-0004-329-1-2",
                "CODEDE-IVA PAZ-MEJORAMIENTO / INVERSION"));
    }

    @Test
    void clasificaPorUltimoSegmentoSiNoHayTextoDeTipo() {
        assertEquals(TipoDineroCaja.FUNCIONAMIENTO,
                ParserBoletinCaja.clasificarTipo("21-0101-0001-0-0-1", ""));
        assertEquals(TipoDineroCaja.INVERSION,
                ParserBoletinCaja.clasificarTipo("21-0101-0001-0-0-2", ""));
        assertEquals(TipoDineroCaja.INVERSION,
                ParserBoletinCaja.clasificarTipo("31-0101-0004-329-1-2", ""));
    }

    @Test
    void clasificaDesconocidoSiNoHayTextoNiSegmento1o2() {
        assertEquals(TipoDineroCaja.DESCONOCIDO,
                ParserBoletinCaja.clasificarTipo("21-0101-0001-0-0-9", ""));
        assertEquals(TipoDineroCaja.DESCONOCIDO,
                ParserBoletinCaja.clasificarTipo("21-0101-0001", "cuenta sin tipo"));
    }

    @Test
    void cuentasCortasDeRetencionQuedanDesconocidas() {
        assertEquals(TipoDineroCaja.DESCONOCIDO,
                ParserBoletinCaja.clasificarTipo("118", "PLAN DE PRESTACIONES DEL EMPLEADO MUNICIPAL"));
        assertEquals(TipoDineroCaja.DESCONOCIDO,
                ParserBoletinCaja.clasificarTipo("201", "CUOTAS I.G.S.S."));
        assertEquals(TipoDineroCaja.DESCONOCIDO,
                ParserBoletinCaja.clasificarTipo("301", "RETENCIONES"));
    }

    @Test
    void parseoAsignaTipoFuncionamientoEInversion() {
        List<String> lineas = List.of(
                "Fecha de: 13/08/2026",
                "21-0101-0001-0-0-1 Ingresos Tributarios IVA-PAZ-FUNCIONAMIENTO 0.96 412,120.00 505,164.95 93,045.91",
                "21-0101-0001-0-0-2 Ingresos Tributarios IVA-PAZ-INVERSION 0.00 1,523,400.37 1,529,400.37 6,000.00",
                "118 PLAN DE PRESTACIONES DEL EMPLEADO MUNICIPAL 0.00 17,508.40 17,508.40 0.00");
        BoletinParseado b = ParserBoletinCaja.parsearLineas(lineas);
        assertEquals(TipoDineroCaja.FUNCIONAMIENTO, buscar(b, "21-0101-0001-0-0-1").getTipo());
        assertEquals(TipoDineroCaja.INVERSION, buscar(b, "21-0101-0001-0-0-2").getTipo());
        assertEquals(TipoDineroCaja.DESCONOCIDO, buscar(b, "118").getTipo());
    }
}
