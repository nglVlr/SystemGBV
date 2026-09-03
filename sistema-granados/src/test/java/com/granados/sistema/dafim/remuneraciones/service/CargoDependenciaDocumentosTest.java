package com.granados.sistema.dafim.remuneraciones.service;

import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.parser.ParserSicoin;
import com.granados.sistema.dafim.remuneraciones.dto.FilaPlanilla;
import com.granados.sistema.dafim.remuneraciones.dto.FilaRemuneracion;
import com.granados.sistema.dafim.remuneraciones.dto.ResultadoRemuneraciones;
import com.granados.sistema.dafim.remuneraciones.parser.ParserPlanillaPdf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Texto real de planillas julio + fragmentos SICOIN: cargo y dependencia
 * no pueden quedar vacios en 011, 022, 029, 035, 183.
 */
class CargoDependenciaDocumentosTest {

    @Test
    void motorLlenaCargoYDependenciaDesdeDocumentosReales() throws Exception {
        List<FilaPlanilla> planillas = new ArrayList<>();
        planillas.addAll(ParserPlanillaPdf.parsearLineas(leer("/parser/planilla-011-deposito-julio.txt")));
        planillas.addAll(ParserPlanillaPdf.parsearLineas(leer("/parser/planilla-011-cheque-julio.txt")));
        planillas.addAll(ParserPlanillaPdf.parsearLineas(leer("/parser/planilla-022-julio.txt")));

        List<RegistroSicoin> sicoin = ParserSicoin.parsearLineas(List.of(
                "GARCIA,GONZALEZ,,MALVI,OSVALDO     NIT:  100192076",
                "19 02 001 002 000 029  209804343 IMPRESO Inversion Social  33749 ABC 45203 20/08/2026 "
                        + "2,500.00 Pago de POR SERVICIOS PRESTADOS A LA",
                "MUNICIPALIDAD DE GRANADOS COMO:",
                "31-0151-0003 00 TRABAJADOR OPERATIVO, SEGUN CONTRATO",
                "NO. 38-2026 CORRESPONDIENTE AL MES DE JULIO 2026",
                "Total por proveedor:  2,500.00",
                "RAMIREZ,GONZALEZ,,ELDER,RODOLFO     NIT:  100713238",
                "19 02 001 001 000 029  209802558 IMPRESO Inversion Social  33677 XYZ 45234 20/08/2026 "
                        + "3,000.00 Pago de POR SERVICIOS PRESTADOS A LA",
                "MUNICIPALIDAD DE GRNADOS BAJA VERPAZ",
                "22-0101-0001 00 COMO ASISTENTE UGAM SEGUN CONTRATO",
                "NO.051-2026 CORRESPONDIENTE AL MES DE JULIO 2026.",
                "Total por proveedor:  3,000.00",
                "GARCIA GARCIA,JARVIS EDUARDO     NIT:  555",
                "19 02 001 001 000 035  209812949 IMPRESO Inversion Social  2341 - - 45426 20/08/2026 "
                        + "2,600.00 PAGO DE LA PLANILLA PLANILLA DE PERSONAL",
                "CALLES GRANADOS BAJA VERAPAZ PARA EL EMPLEADO JARVIS",
                "035 QUE REALIZA TRABAJO DE LIMPIEZA DE",
                "CALLES",
                "Total por proveedor:  2,600.00",
                "AREVALO,REYES,,ELDER,ZOEL     NIT:  888",
                "01 00 000 001 000 183  209803338 IMPRESO Sin Proyecto  33646 DEF 45263 20/08/2026 "
                        + "30,000.00 Pago de POR SERVICIOS PROFESIONALES PRESTADOS A LA",
                "MUNICIPALIDAD DE GRANADOS BAJA VERAPAZ COMO: ASESOR LEGAL DEL CONSEJO MUNICIPAL, "
                        + "CORRESPONDIENTE AL MES DE JULIO 2026",
                "Total por proveedor:  30,000.00"));

        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                7, 2026, planillas, sicoin, List.of());

        FilaRemuneracion tereso = buscar(r, "LARIOS");
        assertTrue(tereso.getCargo().toUpperCase().contains("PUBLICOS"));
        assertTrue(tereso.getDependencia().toUpperCase().contains("CONCEJO"));

        FilaRemuneracion yefri = buscar(r, "PAREDES");
        assertTrue(yefri.getCargo().toUpperCase().contains("INVENTARIO"));
        assertFalse(yefri.getDependencia().isBlank());

        FilaRemuneracion malvi = buscar(r, "MALVI");
        assertTrue(malvi.getCargo().toUpperCase().contains("TRABAJADOR OPERATIVO"));
        assertFalse(malvi.getDependencia().isBlank());

        FilaRemuneracion elder = buscar(r, "RAMIREZ");
        assertTrue(elder.getCargo().toUpperCase().contains("UGAM"));
        assertTrue(elder.getDependencia().toUpperCase().contains("UGAM"));

        FilaRemuneracion jarvis = buscar(r, "JARVIS");
        assertTrue(jarvis.getCargo().toUpperCase().contains("LIMPIEZA"));
        assertFalse(jarvis.getDependencia().isBlank());

        FilaRemuneracion asesor = buscar(r, "AREVALO");
        assertTrue(asesor.getCargo().toUpperCase().contains("ASESOR"));
        assertTrue(asesor.getDependencia().toUpperCase().contains("CONSEJO"));

        assertTrue(r.getFilas().stream().noneMatch(f ->
                f.getCargo() == null || f.getCargo().isBlank()
                        || f.getDependencia() == null || f.getDependencia().isBlank()),
                "Nadie del oficio debe quedar sin cargo ni dependencia");
    }

    private static FilaRemuneracion buscar(ResultadoRemuneraciones r, String frag) {
        String u = frag.toUpperCase();
        return r.getFilas().stream()
                .filter(f -> f.getNombre().toUpperCase().contains(u))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No esta " + frag));
    }

    private static List<String> leer(String recurso) throws Exception {
        try (var in = CargoDependenciaDocumentosTest.class.getResourceAsStream(recurso)) {
            assertTrue(in != null, "Falta fixture " + recurso);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
