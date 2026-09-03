package com.granados.sistema.dafim.remuneraciones.service;

import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.remuneraciones.dto.FilaPlanilla;
import com.granados.sistema.dafim.remuneraciones.dto.FilaRemuneracion;
import com.granados.sistema.dafim.remuneraciones.dto.PersonaRrhh;
import com.granados.sistema.dafim.remuneraciones.dto.ResultadoRemuneraciones;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotorRemuneracionesServiceTest {

    @Test
    void une011SinDuplicarYCalculaDescuentosYLiquido() {
        FilaPlanilla a = planilla("011", "LARIOS MARROQUIN, TERESO", "ASISTENTE",
                3120, 150.69, 218.40, 250, 3000.91);
        FilaPlanilla dup = planilla("011", "TERESO LARIOS MARROQUIN", "ASISTENTE",
                3120, 150.69, 218.40, 250, 3000.91);
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                7, 2026, List.of(a, dup), List.of(), List.of());
        List<FilaRemuneracion> s011 = deRenglon(r, "011");
        assertEquals(1, s011.size());
        FilaRemuneracion f = s011.get(0);
        assertEquals(3370.00, f.getTotalIngresos(), 0.001);
        assertEquals(3120.00, f.getSueldoBase(), 0.001);
        assertEquals(250.00, f.getBonifIncentivo(), 0.001);
        assertEquals(369.09, f.getDescuentos(), 0.001);
        assertEquals(3000.91, f.getLiquido(), 0.001);
        assertFalse(f.isIncompleta());
    }

    @Test
    void planilla022YSicoin029y035() {
        FilaPlanilla p022 = planilla("022", "PAREDES, YEFRI EDEY", "ENCARGADO DE INVENTARIO",
                2895.10, 0, 0, 0, 2895.10);
        RegistroSicoin r029 = sicoin("029", "MALVI OSWALDO GARCIA GONZALEZ", 2500,
                "POR SERVICIOS PRESTADOS COMO: TRABAJADOR OPERATIVO, SEGUN CONTRATO.");
        RegistroSicoin r035 = sicoin("035", "GARCIA GARCIA, JARVIS EDUARDO", 3600,
                "PARA EL EMPLEADO GARCIA GARCIA JARVIS PAGO DE LIMPIEZA");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                7, 2026, List.of(p022), List.of(r029, r035), List.of());
        assertEquals(1, deRenglon(r, "022").size());
        assertEquals(2895.10, deRenglon(r, "022").get(0).getTotalIngresos(), 0.001);
        assertEquals(0.0, deRenglon(r, "022").get(0).getDescuentos(), 0.001);
        FilaRemuneracion f029 = deRenglon(r, "029").get(0);
        assertEquals(2500.00, f029.getSueldoBase(), 0.001);
        assertEquals(2500.00, f029.getLiquido(), 0.001);
        assertTrue(f029.getCargo().toUpperCase().contains("TRABAJADOR"));
        assertEquals(3600.00, deRenglon(r, "035").get(0).getSueldoBase(), 0.001);
    }

    @Test
    void concejalSicoinMantieneBrutoYDescuento1760() {
        RegistroSicoin c = sicoin("064", "HILARIO PEREZ CAMAJA", 11000, "PAGO DE DIETAS CONCEJAL III");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                7, 2026, List.of(), List.of(c), List.of());
        FilaRemuneracion f = deRenglon(r, "064").get(0);
        assertEquals(11000.00, f.getDietas(), 0.001);
        assertEquals(11000.00, f.getTotalIngresos(), 0.001);
        assertEquals(1760.00, f.getDescuentos(), 0.001);
        assertEquals(9240.00, f.getLiquido(), 0.001);
    }

    @Test
    void rrhhSinMontoQuedaIncompletoYAlerta() {
        PersonaRrhh hueco = new PersonaRrhh("MARVIN OVIDIO CANAHUI", "ALCALDE MUNICIPAL",
                "CONCEJO", "011");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                7, 2026, List.of(), List.of(), List.of(hueco));
        FilaRemuneracion f = deRenglon(r, "011").get(0);
        assertTrue(f.isIncompleta());
        assertEquals(0.0, f.getTotalIngresos(), 0.001);
        assertTrue(r.getAlertas().stream().anyMatch(a -> a.toUpperCase().contains("CANAHUI")));
    }

    @Test
    void ordenDeSeccionesYNumeracionPorBloque() {
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                8, 2026,
                List.of(planilla("011", "UNO, EMPLEADO", "SECRETARIO", 1000, 0, 0, 250, 1250),
                        planilla("022", "DOS, EMPLEADO", "INVENTARIO", 2000, 0, 0, 0, 2000)),
                List.of(
                        sicoin("064", "CONCEJAL UNO", 11000, "DIETAS"),
                        sicoin("029", "CONTRATO UNO", 2500, "COMO: ALBANIL, SEGUN."),
                        sicoin("188", "SUPERVISOR UNO", 7000, "SUPERVISION DE OBRAS"),
                        sicoin("183", "ASESOR UNO", 30000, "ASESOR LEGAL"),
                        sicoin("035", "LIMPIEZA UNO", 3600, "LIMPIEZA")),
                List.of());
        List<String> orden = r.getFilas().stream().map(FilaRemuneracion::getRenglon).toList();
        assertEquals(List.of("064", "011", "022", "029", "188", "183", "035"), orden);
        assertEquals(1, r.getFilas().get(0).getNumero());
        assertEquals(1, deRenglon(r, "011").get(0).getNumero());
    }

    @Test
    void dietaSicoinSumaAFila011() {
        FilaPlanilla p = planilla("011", "SANTIAGO DE JESUS ZULETA", "SECRETARIO",
                16000, 0, 0, 250, 16250);
        RegistroSicoin dieta = sicoin("011", "SANTIAGO DE JESUS ZULETA REYES", 11000,
                "PAGO DE DIETAS DEL SECRETARIO MUNICIPAL");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                7, 2026, List.of(p), List.of(dieta), List.of());
        FilaRemuneracion f = deRenglon(r, "011").get(0);
        assertEquals(11000.00, f.getDietas(), 0.001);
        assertEquals(27250.00, f.getTotalIngresos(), 0.001);
    }

    @Test
    void sicoin029SinPrefijoComoIgualLlenaCargo() {
        RegistroSicoin r029 = sicoin("029", "GARCIA,GONZALEZ,,MALVI,OSVALDO", 2500,
                "POR SERVICIOS PRESTADOS A LA TRABAJADOR OPERATIVO, SEGUN CONTRATO NO. 38-2026");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                8, 2026, List.of(), List.of(r029), List.of());
        FilaRemuneracion f = deRenglon(r, "029").get(0);
        assertTrue(f.getCargo().toUpperCase().contains("TRABAJADOR OPERATIVO"));
        assertFalse(f.getDependencia().isBlank());
    }

    @Test
    void sicoin029ComoLargoY035LimpiezaY183Asesor() {
        RegistroSicoin maestra = sicoin("029", "GARCIA,GARCIA,,JENIFER,HAYDEE", 1700,
                "POR SERVICIOS TECNICOS PRESTADOS A LA MUNICIPALIDAD DE GRANADOS, COMO MAESTRA DE EDUCACION "
                        + "INFANTIL DE LA ESCUELA OFICIAL DE PARVULOS ALDEA EL GUAPINOL, SEGUN CONTRATO NO. 55-2026");
        RegistroSicoin ugam = sicoin("029", "RAMIREZ,GONZALEZ,,ELDER,RODOLFO", 3000,
                "POR SERVICIOS PRESTADOS A LA COMO ASISTENTE UGAM SEGUN CONTRATO NO.051-2026");
        RegistroSicoin lim = sicoin("035", "GARCIA GARCIA,JARVIS EDUARDO", 3600,
                "PLANILLA DE PERSONAL CALLES GRANADOS PARA EL EMPLEADO JARVIS QUE REALIZA TRABAJO DE LIMPIEZA DE CALLES");
        RegistroSicoin asesor = sicoin("183", "AREVALO,REYES,,ELDER,ZOEL", 30000,
                "POR SERVICIOS PROFESIONALES PRESTADOS COMO: ASESOR LEGAL DEL CONSEJO MUNICIPAL, CORRESPONDIENTE AL MES");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                8, 2026, List.of(), List.of(maestra, ugam, lim, asesor), List.of());
        FilaRemuneracion f029m = deRenglon(r, "029").stream()
                .filter(x -> x.getNombre().toUpperCase().contains("JENIFER")).findFirst().orElseThrow();
        assertTrue(f029m.getCargo().toUpperCase().contains("MAESTRA"));
        FilaRemuneracion fUgam = deRenglon(r, "029").stream()
                .filter(x -> x.getNombre().toUpperCase().contains("ELDER")).findFirst().orElseThrow();
        assertTrue(fUgam.getCargo().toUpperCase().contains("UGAM"));
        assertEquals("UGAM", fUgam.getDependencia());
        FilaRemuneracion f035 = deRenglon(r, "035").get(0);
        assertTrue(f035.getCargo().toUpperCase().contains("LIMPIEZA"));
        assertFalse(f035.getDependencia().isBlank());
        FilaRemuneracion f183 = deRenglon(r, "183").get(0);
        assertTrue(f183.getCargo().toUpperCase().contains("ASESOR LEGAL"));
        assertEquals("CONSEJO MUNICIPAL", f183.getDependencia());
    }

    @Test
    void rrhhNoBorraCargoNiDependenciaDelDocumento() {
        FilaPlanilla p = planilla("011", "LARIOS MARROQUIN, TERESO", "ASISTENTE DE SERVICIOS PUBLICOS",
                3120, 150.69, 218.40, 250, 3000.91);
        p.setDependencia("CONCEJO Y ALCALDIA");
        PersonaRrhh rrhh = new PersonaRrhh("TERESO LARIOS MARROQUIN", "OTRO CARGO",
                "OTRA DEP", "011");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                7, 2026, List.of(p), List.of(), List.of(rrhh));
        FilaRemuneracion f = deRenglon(r, "011").get(0);
        assertTrue(f.getCargo().contains("ASISTENTE"));
        assertTrue(f.getDependencia().contains("CONCEJO"));
    }

    @Test
    void rrhhRellenaSoloSiElPdfNoTraeCargo() {
        RegistroSicoin r188 = sicoin("188", "VENTURA,SANCHEZ,,JOSE,DANIEL", 7000,
                "PAGO CORRESPONDIENTE AL MES DE JULIO 2026 SEGUN DOCUMENTO INTERNO");
        PersonaRrhh rrhh = new PersonaRrhh("JOSE DANIEL VENTURA SANCHEZ", "SUPERVISOR DE OBRAS",
                "DIRECCION MUNICIPAL DE PLANIFICACION", "188");
        ResultadoRemuneraciones r = MotorRemuneracionesService.construir(
                8, 2026, List.of(), List.of(r188), List.of(rrhh));
        FilaRemuneracion f = deRenglon(r, "188").get(0);
        assertEquals("SUPERVISOR DE OBRAS", f.getCargo());
        assertTrue(f.getDependencia().toUpperCase().contains("PLANIFICACION")
                || f.getDependencia().equals("DMP"));
    }

    private static List<FilaRemuneracion> deRenglon(ResultadoRemuneraciones r, String codigo) {
        return r.getFilas().stream().filter(f -> codigo.equals(f.getRenglon())).toList();
    }

    private static FilaPlanilla planilla(String renglon, String nombre, String cargo,
                                         double dev, double igss, double otras,
                                         double boni, double recibir) {
        FilaPlanilla f = new FilaPlanilla();
        f.setRenglon(renglon);
        f.setNombre(nombre);
        f.setCargo(cargo);
        f.setTotalDevengado(dev);
        f.setIgss(igss);
        f.setOtrasDeducciones(otras);
        f.setBoniLey(boni);
        f.setTotalRecibir(recibir);
        return f;
    }

    private static RegistroSicoin sicoin(String renglon, String nombre, double monto, String desc) {
        RegistroSicoin r = new RegistroSicoin();
        r.setRenglon(renglon);
        r.setNombre(nombre);
        r.setMonto(monto);
        r.setDesc(desc);
        r.setStatus("IMPRESO");
        r.setCheque("45000");
        return r;
    }
}
