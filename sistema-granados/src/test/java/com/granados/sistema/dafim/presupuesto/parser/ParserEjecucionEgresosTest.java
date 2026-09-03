package com.granados.sistema.dafim.presupuesto.parser;

import com.granados.sistema.dafim.presupuesto.dto.EjecucionParseada;
import com.granados.sistema.dafim.presupuesto.dto.LineaEjecucion;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ParserEjecucionEgresosTest {

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " y vino " + actual);
    }

    private static LineaEjecucion buscar(EjecucionParseada e, String renglon, String fuente, String obra) {
        return e.getLineas().stream()
                .filter(l -> renglon.equals(l.getRenglon()) && fuente.equals(l.getFuente())
                        && obra.equals(l.getActividadObra()))
                .findFirst().orElse(null);
    }

    @Test
    void acumulaFilasPartidasYContinuacion() {
        List<String> lineas = List.of(
                "SIAF: SICOIN GL Pagina: Pagina 1 de 1",
                "Periodo del:  01/01/2026 al:   11/08/2026",
                "297 31-0151-0001 200 50,000.00 0.00 50,000.00 0.00 47,515.00 47,515.00",
                "47,515.00 0.00 2,485.00 0.00 0.00",
                "015 22-0101-0001 COMPLEMENTOS ESPECIFICOS AL PERSONAL 10,000.00 0.00 10,000.00 0.00 "
                        + "0.00 0.00 0.00 0.00 10,000.00 0.00 0.00",
                "PERMANENTE",
                "TOTAL :  60,000.00 0.00 60,000.00 0.00 47,515.00 47,515.00 47,515.00 0.00 12,485.00 0.00 0.00");
        EjecucionParseada e = ParserEjecucionEgresos.parsearLineas(lineas);
        assertEquals(2, e.getLineas().size());
        assertEquals(LocalDate.of(2026, 1, 1), e.getPeriodoDesde());
        assertEquals(LocalDate.of(2026, 8, 11), e.getPeriodoHasta());
        assertEquals(2026, e.getAnio());

        LineaEjecucion partida = e.getLineas().get(0);
        assertEquals("297", partida.getRenglon());
        assertEquals("31-0151-0001", partida.getFuente());
        assertEquals("200", partida.getActividadObra());
        assertMonto("50000.00", partida.getVigente());
        assertMonto("47515.00", partida.getDevengado());
        assertMonto("2485.00", partida.getSaldoDisponible());

        assertEquals("COMPLEMENTOS ESPECIFICOS AL PERSONAL PERMANENTE",
                e.getLineas().get(1).getDescripcion());
        assertMonto("60000.00", e.getTotalVigente());
        assertMonto("47515.00", e.getTotalDevengado());
    }

    @Test
    void leePdfReal() throws Exception {
        EjecucionParseada e;
        try (var in = getClass().getResourceAsStream("/parser/ejecucion-egresos.pdf")) {
            e = ParserEjecucionEgresos.parsear(in);
        }
        // PDF real de agosto 2026: 23 paginas, 367 filas renglon+fuente
        assertEquals(367, e.getLineas().size());
        assertEquals(LocalDate.of(2026, 1, 1), e.getPeriodoDesde());
        assertEquals(LocalDate.of(2026, 8, 11), e.getPeriodoHasta());
        assertEquals(2026, e.getAnio());

        // las sumas calculadas cuadran al centavo con la fila TOTAL del reporte
        assertMonto("70213584.41", e.getTotalVigente());
        assertMonto("36681152.27", e.getTotalDevengado());
        assertMonto("36681152.27", e.getTotalPagado());

        // primera fila del reporte, campo por campo, con su contexto jerarquico
        LineaEjecucion l011 = buscar(e, "011", "21-0101-0001", "000");
        assertNotNull(l011);
        assertEquals("PERSONAL PERMANENTE", l011.getDescripcion());
        assertEquals("01 ACTIVIDADES CENTRALES", l011.getPrograma());
        assertEquals("00 SIN SUBPROGRAMA", l011.getSubprograma());
        assertEquals("000 SIN PROYECTO", l011.getProyecto());
        assertEquals("001 CONCEJO Y ALCALDIA", l011.getActividad());
        assertMonto("1392560.00", l011.getAsignado());
        assertMonto("0.00", l011.getModificado());
        assertMonto("1392560.00", l011.getVigente());
        assertMonto("0.00", l011.getPreCompromiso());
        assertMonto("857590.00", l011.getCompromiso());
        assertMonto("857590.00", l011.getDevengado());
        assertMonto("857590.00", l011.getPagado());
        assertMonto("0.00", l011.getExtraPresupuestario());
        assertMonto("534970.00", l011.getSaldoDisponible());
        assertMonto("0.00", l011.getSaldoPorDevengar());
        assertMonto("0.00", l011.getSaldoPorPagar());

        // fila bajo el codigo de obra 200 (linea suelta "200" antes del bloque)
        LineaEjecucion l211 = buscar(e, "211", "31-0151-0001", "200");
        assertNotNull(l211);
        assertEquals("ALIMENTOS PARA PERSONAS", l211.getDescripcion());
        assertMonto("50000.00", l211.getAsignado());
        assertMonto("50000.00", l211.getVigente());
        assertMonto("47515.00", l211.getDevengado());
        assertMonto("47515.00", l211.getPagado());
        assertMonto("2485.00", l211.getSaldoDisponible());

        // descripcion partida en dos lineas fisicas
        LineaEjecucion l015 = buscar(e, "015", "22-0101-0001", "000");
        assertNotNull(l015);
        assertEquals("COMPLEMENTOS ESPECÍFICOS AL PERSONAL PERMANENTE", l015.getDescripcion());
        assertMonto("65000.00", l015.getVigente());
        assertMonto("41125.00", l015.getDevengado());
        assertMonto("23875.00", l015.getSaldoDisponible());

        // modificacion negativa (traslado de credito)
        LineaEjecucion l189 = buscar(e, "189", "22-0101-0001", "100");
        assertNotNull(l189);
        assertMonto("-30000.00", l189.getModificado());
        assertMonto("0.00", l189.getVigente());

        // descripcion con continuacion "DE USO COMUN" en la linea siguiente
        LineaEjecucion l331 = buscar(e, "331", "21-0101-0001", "300");
        assertNotNull(l331);
        assertEquals("CONSTRUCCIONES DE BIENES NACIONALES DE USO COMÚN", l331.getDescripcion());
        assertMonto("1700.00", l331.getVigente());
        assertMonto("537.50", l331.getDevengado());
        assertMonto("1162.50", l331.getSaldoDisponible());
        assertEquals("000 SIN ACTIVIDAD", l331.getActividad());

        // gemelas del programa 19: mismo proyecto/renglon/fuente, distinta actividad
        List<LineaEjecucion> gemelas19 = e.getLineas().stream()
                .filter(l -> "029".equals(l.getRenglon())
                        && "22-0101-0001".equals(l.getFuente())
                        && l.getPrograma() != null && l.getPrograma().startsWith("19 "))
                .toList();
        assertEquals(2, gemelas19.size());
        LineaEjecucion calle = gemelas19.stream()
                .filter(l -> l.getActividad() != null && l.getActividad().startsWith("001 "))
                .findFirst().orElse(null);
        LineaEjecucion servicios = gemelas19.stream()
                .filter(l -> l.getActividad() != null && l.getActividad().startsWith("002 "))
                .findFirst().orElse(null);
        assertNotNull(calle);
        assertNotNull(servicios);
        assertEquals("001 SERVICIOS PÚBLICOS MUNICIPALES", calle.getProyecto());
        assertEquals(calle.getProyecto(), servicios.getProyecto());
        assertNotEquals(calle.getActividad(), servicios.getActividad());
        assertMonto("367500.00", calle.getSaldoDisponible());
        assertMonto("34968.00", servicios.getSaldoDisponible());
    }

    /**
     * Jerarquia SICOIN: programa(2) / subprograma(2) / proyecto(3) /
     * actividad(3) / obra Act O(3) / renglon+fuente.
     * El primer nivel de 3 digitos es el proyecto; el siguiente, con nombre,
     * es la actividad. Sin ese campo, 029 / 22-0101-0001 de dos actividades
     * distintas colapsan en lineas gemelas (Q 367500 y Q 34968).
     */
    @Test
    void conservaActividadComoCampoPropioYNoPisaElProyecto() {
        EjecucionParseada e = ParserEjecucionEgresos.parsearLineas(fixtureGemelasPrograma19());
        assertEquals(2, e.getLineas().size());

        LineaEjecucion calle = e.getLineas().get(0);
        LineaEjecucion servicios = e.getLineas().get(1);

        assertEquals("19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS", calle.getPrograma());
        assertEquals("02 MEJORA DE LA GESTION MUNICIPAL", calle.getSubprograma());
        assertEquals("001 SERVICIOS PUBLICOS MUNICIPALES", calle.getProyecto());
        assertEquals("001 SERVICIOS PUBLICOS MUNICIPALES", servicios.getProyecto());
        assertEquals("000", calle.getActividadObra());
        assertEquals("000", servicios.getActividadObra());
        assertEquals("029", calle.getRenglon());
        assertEquals("22-0101-0001", calle.getFuente());

        assertEquals("001 APOYO CALLE AREA URBANA Y OTRAS COMUNIDADES", calle.getActividad());
        assertEquals("002 APOYO SERVICIOS PUBLICOS TODAS LAS COMUNIDADES", servicios.getActividad());
        assertNotEquals(calle.getActividad(), servicios.getActividad());
        assertMonto("367500.00", calle.getSaldoDisponible());
        assertMonto("34968.00", servicios.getSaldoDisponible());
        assertNotEquals(PresupuestoService.claveLinea(comoLineaPresupuesto(calle)),
                PresupuestoService.claveLinea(comoLineaPresupuesto(servicios)));
    }

    /**
     * Tras "000 SIN ACTIVIDAD" el PDF lista obras con nombre y Act O suelto
     * (300). Esa obra no debe reemplazar la actividad ni el proyecto.
     */
    @Test
    void obraNombradaNoReemplazaActividadNiProyecto() {
        List<String> lineas = List.of(
                "12ACCESO AL AGUA POTABLE  0.00  100.00  100.00  100.00  0.00  0.00  0.00",
                "01 INCREMENTO EN EL ACCESO  0.00  100.00  100.00  100.00  0.00  0.00  0.00",
                "001 FAMILIAS CON ABASTECIMIENTO  0.00  100.00  100.00  100.00  0.00  0.00  0.00",
                "000 SIN ACTIVIDAD  0.00  100.00  100.00  100.00  0.00  0.00  0.00",
                "001 MEJORAMIENTO SISTEMA DE AGUA POTABLE ALDEA  0.00  50.00  50.00  50.00  0.00  0.00  0.00",
                "300",
                "331 21-0101-0001 CONSTRUCCIONES DE BIENES NACIONALES  1,700.00  0.00  1,700.00  0.00 "
                        + "537.50  537.50  537.50  0.00  1,162.50  0.00  0.00",
                "002 MEJORAMIENTO SISTEMA DE AGUA POTABLE ALDEA  0.00  50.00  50.00  50.00  0.00  0.00  0.00",
                "300",
                "331 22-0101-0001 CONSTRUCCIONES DE BIENES NACIONALES  9,324.00  0.00  9,324.00  0.00 "
                        + "9,324.00  0.00  0.00  0.00  0.00  9,324.00  0.00");
        EjecucionParseada e = ParserEjecucionEgresos.parsearLineas(lineas);
        assertEquals(2, e.getLineas().size());

        LineaEjecucion a = e.getLineas().get(0);
        LineaEjecucion b = e.getLineas().get(1);
        assertEquals("001 FAMILIAS CON ABASTECIMIENTO", a.getProyecto());
        assertEquals("001 FAMILIAS CON ABASTECIMIENTO", b.getProyecto());
        assertEquals("000 SIN ACTIVIDAD", a.getActividad());
        assertEquals("000 SIN ACTIVIDAD", b.getActividad());
        assertEquals("300", a.getActividadObra());
        assertEquals("300", b.getActividadObra());
        assertEquals("331", a.getRenglon());
        assertEquals("21-0101-0001", a.getFuente());
        assertEquals("22-0101-0001", b.getFuente());
    }

    private static LineaPresupuesto comoLineaPresupuesto(LineaEjecucion le) {
        LineaPresupuesto l = new LineaPresupuesto();
        l.setPrograma(le.getPrograma());
        l.setSubprograma(le.getSubprograma());
        l.setProyecto(le.getProyecto());
        l.setActividad(le.getActividad());
        l.setActividadObra(le.getActividadObra());
        l.setRenglon(le.getRenglon());
        l.setFuente(le.getFuente());
        return l;
    }

    private static List<String> fixtureGemelasPrograma19() {
        return List.of(
                "19MOVILIDAD URBANA Y ESPACIOS PUBLICOS  0.00  100.00  100.00  100.00  0.00  0.00  0.00",
                "02 MEJORA DE LA GESTION MUNICIPAL  0.00  100.00  100.00  100.00  0.00  0.00  0.00",
                "001 SERVICIOS PUBLICOS MUNICIPALES  0.00  100.00  100.00  100.00  0.00  0.00  0.00",
                "001 APOYO CALLE AREA URBANA Y OTRAS COMUNIDADES  0.00  10.00  10.00  10.00  0.00  0.00  0.00",
                "000 SIN OBRA  0.00  10.00  10.00  10.00  0.00  0.00  0.00",
                "000",
                "029 22-0101-0001 OTRAS REMUNERACIONES DE PERSONAL  370,000.00  0.00  370,000.00  0.00 "
                        + "2,500.00  2,500.00  2,500.00  0.00  367,500.00  0.00  0.00",
                "002 APOYO SERVICIOS PUBLICOS TODAS LAS COMUNIDADES  0.00  90.00  90.00  90.00  0.00  0.00  0.00",
                "000 SIN OBRA  0.00  90.00  90.00  90.00  0.00  0.00  0.00",
                "000",
                "029 22-0101-0001 OTRAS REMUNERACIONES DE PERSONAL  1,200,000.00  0.00  1,200,000.00  0.00 "
                        + "1,165,032.00  1,165,032.00  1,165,032.00  0.00  34,968.00  0.00  0.00");
    }
}
