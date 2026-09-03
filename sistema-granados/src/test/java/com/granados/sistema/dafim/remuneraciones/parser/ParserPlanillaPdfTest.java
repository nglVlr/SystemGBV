package com.granados.sistema.dafim.remuneraciones.parser;

import com.granados.sistema.dafim.remuneraciones.dto.FilaPlanilla;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserPlanillaPdfTest {

    @Test
    void leePlanilla022UnaPersonaSinDescuentos() {
        List<FilaPlanilla> filas = ParserPlanillaPdf.parsearLineas(List.of(
                "CONCEPTO: PLANILLA DE PERSONAL 022 CORRESPONDIENTE AL MES DE JULIO 2026",
                "003 DIRECCION DE ADMINISTRACION FINANCIERA INTEGRADA MUNICIPAL",
                "000 SIN OBRA",
                "PAREDES, YEFRI EDEY ENCARGADO DE INVENTARIO",
                "1  2,895.10  0.00  0.00  0.00  0.00  0.00  0.00  0.00  2,895.10",
                "Total por Actividad  2,895.10  0.00  0.00  0.00  0.00  0.00  0.00  0.00  2,895.10",
                "SUMAS TOTALES (Q):...............  2,895.10  0.00  0.00  0.00  0.00  0.00  0.00  0.00  2,895.10"));
        assertEquals(1, filas.size());
        FilaPlanilla f = filas.get(0);
        assertEquals("022", f.getRenglon());
        assertTrue(f.getNombre().toUpperCase().contains("PAREDES"));
        assertTrue(f.getNombre().toUpperCase().contains("YEFRI"));
        assertTrue(f.getCargo().toUpperCase().contains("INVENTARIO"));
        assertTrue(f.getDependencia().toUpperCase().contains("ADMINISTRACION"));
        assertEquals(2895.10, f.getTotalDevengado(), 0.001);
        assertEquals(0.0, f.getIgss(), 0.001);
        assertEquals(2895.10, f.getTotalRecibir(), 0.001);
    }

    @Test
    void leePlanilla011ChequeConCargoPartidoYDescuentos() {
        List<FilaPlanilla> filas = ParserPlanillaPdf.parsearLineas(List.of(
                "CONCEPTO: PAGO DE PLANILLA DE PERSONAL 011 CORRESPONDIENTE AL MES DE JULIO",
                "001 CONCEJO Y ALCALDIA",
                "000 SIN OBRA",
                "LARIOS MARROQUIN, TERESO ASISTENTE DE SERVICIOS",
                "1 PUBLICOS  3,120.00  150.69  0.00  218.40  0.00  250.00  0.00  0.00  3,000.91",
                "ROBLES ORTIZ, MARLENE JAQUELINE DIRECTORA DE LA OFICINA",
                "2 MUNICIPAL DE LA MUJER  4,000.00  193.20  0.00  280.00  0.00  250.00  0.00  0.00  3,776.80",
                "Total por Actividad  12,120.00  585.39  0.00  848.40  0.00  750.00  0.00  0.00  11,436.21",
                "003 DIRECCION DE ADMINISTRACION FINANCIERA INTEGRADA MUNICIPAL",
                "MORALES REYES, ERICK JOSE ASISTENTE DE LA DAFIM",
                "4  3,500.00  169.05  0.00  245.00  0.00  250.00  0.00  0.00  3,335.95"));
        assertEquals(3, filas.size());
        FilaPlanilla tereso = filas.get(0);
        assertEquals("011", tereso.getRenglon());
        assertTrue(tereso.getNombre().toUpperCase().contains("LARIOS"));
        assertTrue(tereso.getCargo().toUpperCase().contains("PUBLICOS"));
        assertTrue(tereso.getDependencia().toUpperCase().contains("CONCEJO"));
        assertEquals(3120.00, tereso.getTotalDevengado(), 0.001);
        assertEquals(150.69, tereso.getIgss(), 0.001);
        assertEquals(218.40, tereso.getOtrasDeducciones(), 0.001);
        assertEquals(250.00, tereso.getBoniLey(), 0.001);
        assertEquals(3000.91, tereso.getTotalRecibir(), 0.001);

        FilaPlanilla marlene = filas.get(1);
        assertTrue(marlene.getCargo().toUpperCase().contains("MUJER"));
        assertEquals(4000.00, marlene.getTotalDevengado(), 0.001);

        FilaPlanilla erick = filas.get(2);
        assertTrue(erick.getDependencia().toUpperCase().contains("ADMINISTRACION"));
        assertEquals(3500.00, erick.getTotalDevengado(), 0.001);
        assertEquals(250.00, erick.getBoniLey(), 0.001);
    }

    @Test
    void fixturesJulioLlenanCargoYDependenciaIncluyendoCargosPartidos() throws Exception {
        List<FilaPlanilla> dep = ParserPlanillaPdf.parsearLineas(leer("/parser/planilla-011-deposito-julio.txt"));
        List<FilaPlanilla> ch = ParserPlanillaPdf.parsearLineas(leer("/parser/planilla-011-cheque-julio.txt"));
        List<FilaPlanilla> r022 = ParserPlanillaPdf.parsearLineas(leer("/parser/planilla-022-julio.txt"));
        assertEquals(35, dep.size());
        assertEquals(4, ch.size());
        assertEquals(1, r022.size());
        assertTrue(dep.stream().noneMatch(f -> f.getCargo().isBlank() || f.getDependencia().isBlank()));
        assertTrue(ch.stream().noneMatch(f -> f.getCargo().isBlank() || f.getDependencia().isBlank()));

        FilaPlanilla abner = porNombre(dep, "ALVARADO");
        assertTrue(abner.getCargo().toUpperCase().contains("BODEGA"));
        assertTrue(abner.getDependencia().toUpperCase().contains("CONCEJO"));

        FilaPlanilla manfred = porNombre(dep, "ZULETA ALVARADO");
        assertTrue(manfred.getNombre().toUpperCase().contains("ERNESTO"));
        assertTrue(manfred.getCargo().toUpperCase().contains("OPERADOR"));
        assertTrue(manfred.getCargo().toUpperCase().contains("GUATECOMPRAS"));
        assertFalse(manfred.getCargo().toUpperCase().contains("ERNESTO"));

        FilaPlanilla edward = porNombre(dep, "SERRANO");
        assertTrue(edward.getNombre().toUpperCase().contains("LIGUORI"));
        assertTrue(edward.getCargo().toUpperCase().contains("RELACIONES"));
        assertFalse(edward.getCargo().toUpperCase().contains("LIGUORI"));

        FilaPlanilla cardona = porNombre(dep, "CARDONA");
        assertTrue(cardona.getNombre().toUpperCase().contains("YESENIA"));
        assertTrue(cardona.getCargo().toUpperCase().contains("RECURSOS HUMANOS"));
        assertTrue(cardona.getDependencia().toUpperCase().contains("RECURSOS HUMANOS"));

        FilaPlanilla santiago = porNombre(dep, "ZULETA REYES");
        assertTrue(santiago.getCargo().toUpperCase().contains("SECRETARIO"));
        assertTrue(santiago.getDependencia().toUpperCase().contains("SECRETARIA"));

        FilaPlanilla tereso = porNombre(ch, "LARIOS");
        assertTrue(tereso.getCargo().toUpperCase().contains("PUBLICOS"));
        assertTrue(tereso.getDependencia().toUpperCase().contains("CONCEJO"));

        assertTrue(r022.get(0).getCargo().toUpperCase().contains("INVENTARIO"));
        assertTrue(r022.get(0).getDependencia().toUpperCase().contains("FINANCIERA")
                || r022.get(0).getDependencia().toUpperCase().contains("ADMINISTR"));
    }

    private static FilaPlanilla porNombre(List<FilaPlanilla> filas, String fragmento) {
        String u = fragmento.toUpperCase();
        return filas.stream()
                .filter(f -> f.getNombre().toUpperCase().contains(u))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No esta " + fragmento));
    }

    private static List<String> leer(String recurso) throws Exception {
        try (var in = ParserPlanillaPdfTest.class.getResourceAsStream(recurso)) {
            assertTrue(in != null, "Falta fixture " + recurso);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .lines().toList();
        }
    }
}
