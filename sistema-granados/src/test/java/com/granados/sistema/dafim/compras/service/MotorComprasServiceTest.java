package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.dto.ContratoInfo;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.ProveedorInfo;
import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.dto.ResultadoConstruccion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caminos clave del motor. La paridad exhaustiva contra el Python original
 * se verifico con el arnes de /paridad (13 filas, 8 alertas, identico).
 */
class MotorComprasServiceTest {

    private static Cheque ch(String num, String nombre, double monto) {
        Cheque c = new Cheque();
        c.setCheque(num);
        c.setNombre(nombre);
        c.setMonto(monto);
        c.setFecha(LocalDate.of(2026, 6, 5));
        return c;
    }

    private static RegistroSicoin pdf(String cheque, String nit, String renglon,
                                      double monto, String desc, String contrato) {
        RegistroSicoin r = new RegistroSicoin();
        r.setCheque(cheque); r.setNit(nit); r.setRenglon(renglon);
        r.setMonto(monto); r.setDesc(desc); r.setContrato(contrato);
        r.setStatus("IMPRESO"); r.setFecha("05/06/2026"); r.setNombre("");
        return r;
    }

    private static RegistroGuatecompras txt(String npg, String nit, double monto,
                                            String modalidad) {
        return txt(npg, nit, monto, modalidad, "", "");
    }

    private static RegistroGuatecompras txt(String npg, String nit, double monto,
                                            String modalidad, String desc,
                                            String proveedor) {
        RegistroGuatecompras t = new RegistroGuatecompras();
        t.setNpg(npg); t.setNit(nit); t.setMonto(monto);
        t.setModalidad(modalidad); t.setFecha("04/06/2026");
        t.setDesc(desc); t.setProveedor(proveedor);
        return t;
    }

    @Test
    void matchDirectoTomaLaLineaDeMayorMonto() {
        List<Cheque> cheques = List.of(ch("41001", "LIBRERIA X", 1200.75));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41001", "2222", "291", 400.00, "papel bond", "N/A"),
                pdf("41001", "2222", "291", 800.75, "utiles de oficina", "N/A"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, List.of(), pdfs, Map.of(), Map.of(), Map.of());
        assertEquals(1, rc.getFilas().size());
        assertEquals("utiles de oficina", rc.getFilas().get(0).getDesc());
        assertEquals("291", rc.getFilas().get(0).getRenglon());
    }

    @Test
    void npgNoR029SaleDelTxtPorNitYMonto() {
        List<Cheque> cheques = List.of(ch("41001", "TALLER X", 3500.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41001", "1111", "165", 3500.00, "mantenimiento", "N/A"));
        List<RegistroGuatecompras> txts = List.of(
                txt("E568000001", "1111", 3500.00, "BAJA CUANTIA"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, txts, pdfs, Map.of(), Map.of(), Map.of());
        FilaCompra f = rc.getFilas().get(0);
        assertEquals("E568000001", f.getNpg());
        assertEquals("BAJA CUANTIA", f.getModalidad());
    }

    @Test
    void r029UsaNpgHistoricoSiElTxtNoTienePublicacionesDeEsaPersona() {
        List<Cheque> cheques = List.of(
                ch("41005", "PEDRO RAMIREZ COY ANTONIO", 2600.00),
                ch("41006", "PEDRO RAMIREZ COY ANTONIO", 2600.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41005", "7777", "029", 2600.00,
                        "servicios COMO: ALBANIL, SEGUN CONTRATO NUMERO 03-2026", "3-2026"),
                pdf("41006", "7777", "029", 2600.00,
                        "servicios COMO: ALBANIL, SEGUN CONTRATO NUMERO 03-2026", "3-2026"));
        Map<String, String> npgs = Map.of("7777", "E562222222");
        Map<String, ContratoInfo> bd = Map.of("7777",
                new ContratoInfo("RAMIREZ COY PEDRO ANTONIO", "3-2026", "ALBANIL",
                        "E562222222", 2026));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, List.of(), pdfs, Map.of(), bd, npgs);
        assertEquals("E562222222", rc.getFilas().get(0).getNpg());
        assertEquals("", rc.getFilas().get(1).getNpg());
        assertEquals("E562222222", rc.getNuevos029().get("7777").getNpg());
        assertEquals("CASO DE EXCEPCION", rc.getFilas().get(0).getModalidad());
    }

    @Test
    void r029TomaUnNpgDistintoPorChequeDelTxt() {
        List<Cheque> cheques = List.of(
                ch("41005", "PEDRO RAMIREZ COY ANTONIO", 2600.00),
                ch("41006", "PEDRO RAMIREZ COY ANTONIO", 2700.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41005", "7777", "029", 2600.00,
                        "servicios COMO: ALBANIL, SEGUN CONTRATO NUMERO 03-2026", "3-2026"),
                pdf("41006", "7777", "029", 2700.00,
                        "servicios COMO: ALBANIL, SEGUN CONTRATO NUMERO 03-2026", "3-2026"));
        List<RegistroGuatecompras> txts = List.of(
                txt("E568000101", "7777", 2600.00, "CASO DE EXCEPCION",
                        "servicios como albanil contrato 03-2026 primer pago",
                        "RAMIREZ,COY,,PEDRO,ANTONIO"),
                txt("E568000102", "7777", 2700.00, "CASO DE EXCEPCION",
                        "servicios como albanil contrato 03-2026 segundo pago",
                        "RAMIREZ,COY,,PEDRO,ANTONIO"));
        Map<String, String> npgs = Map.of("7777", "E562222222");
        Map<String, ContratoInfo> bd = Map.of("7777",
                new ContratoInfo("RAMIREZ COY PEDRO ANTONIO", "3-2026", "ALBANIL",
                        "E562222222", 2026));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, txts, pdfs, Map.of(), bd, npgs);
        assertEquals("E568000101", rc.getFilas().get(0).getNpg());
        assertEquals("E568000102", rc.getFilas().get(1).getNpg());
        assertTrue(rc.getNpgsSinCheque().isEmpty());
    }

    @Test
    void art44DeorsaAsignaNpgPorNitYDescripcion() {
        List<Cheque> cheques = List.of(ch("41030",
                "DISTRIBUIDORA DE ELECTRICIDAD DE ORIENTE SOCIEDAD ANONIMA",
                40681.13));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41030", "14946203", "262", 40681.13,
                        "pago de servicio de energia electrica mes de julio 2026",
                        "N/A"));
        List<RegistroGuatecompras> txts = List.of(
                txt("E588937274", "14946203", 40681.13, "CASO DE EXCEPCION",
                        "pago de servicio de energia electrica correspondiente al mes de julio 2026.",
                        "DISTRIBUIDORA DE ELECTRICIDAD DE ORIENTE SOCIEDAD ANONIMA"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, txts, pdfs, Map.of(), Map.of(), Map.of());
        assertEquals("E588937274", rc.getFilas().get(0).getNpg());
        assertEquals("CASO DE EXCEPCION", rc.getFilas().get(0).getModalidad());
        assertTrue(rc.getNpgsSinCheque().isEmpty());
    }

    @Test
    void npgGanaPorDescripcionAunqueElMontoNoCuadre() {
        List<Cheque> cheques = List.of(ch("41020", "JUANA PAOLA ORTIZ ROSALES", 8000.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41020", "40941442", "196", 8000.00,
                        "sonido y pantalla para eleccion de feria el guapinol", "N/A"));
        List<RegistroGuatecompras> txts = List.of(
                txt("E589000001", "40941442", 8000.00, "BAJA CUANTIA",
                        "horas de renta de maquinaria tipo patrol",
                        "ORTIZ,ROSALES,,JUANA,PAOLA"),
                txt("E590038931", "40941442", 9600.00, "BAJA CUANTIA",
                        "PAGO DE SONIDO Y PANTALA PARA ELECCION Y CORONACION DE FERIA EL GUAPINOL",
                        "ORTIZ,ROSALES,,JUANA,PAOLA"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, txts, pdfs, Map.of(), Map.of(), Map.of());
        assertEquals("E590038931", rc.getFilas().get(0).getNpg());
        assertEquals(1, rc.getNpgsSinCheque().size());
        assertEquals("E589000001", rc.getNpgsSinCheque().get(0).getNpg());
    }

    @Test
    void npgPorNombreCuandoElNitDelChequeNoEstaEnElTxt() {
        List<Cheque> cheques = List.of(ch("41021", "SENAIDA ORTIZ ORTIZ REYES MERIDA", 8000.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41021", "9999999", "165", 8000.00,
                        "trabajos de herreria en santa rosa", "N/A"));
        List<RegistroGuatecompras> txts = List.of(
                txt("E589747711", "206965826", 8000.00, "BAJA CUANTIA",
                        "por trabajos de herreria en la comunidad de santa rosa",
                        "ORTIZ,ORTIZ,REYES,MERIDA,SENAIDA"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, txts, pdfs, Map.of(), Map.of(), Map.of());
        assertEquals("E589747711", rc.getFilas().get(0).getNpg());
        assertTrue(rc.getNpgsSinCheque().isEmpty());
    }

    @Test
    void sinNpgDejaAlertaYNoInventaFilaExtra() {
        List<Cheque> cheques = List.of(ch("41022", "PROVEEDOR DESCONOCIDO SA", 1200.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41022", "1111", "299", 1200.00, "materiales varios", "N/A"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, List.of(), pdfs, Map.of(), Map.of(), Map.of());
        assertEquals(1, rc.getFilas().size());
        assertEquals("", rc.getFilas().get(0).getNpg());
        assertTrue(rc.getAlertas().stream().anyMatch(a -> a.startsWith("SIN NPG cheque 41022")));
    }

    @Test
    void sinPdfCaePorFuzzy029YLuegoKeywords() {
        Map<String, ContratoInfo> bd = new LinkedHashMap<>();
        bd.put("5050505", new ContratoInfo("PEREZ TZUL JUAN CARLOS", "8-2026",
                "", "", 2026));
        List<Cheque> cheques = List.of(
                ch("41012", "JUAN CARLOS PEREZ TZUL", 2400.00),
                ch("41010", "ALQUILER DE SILLAS Y MESAS LA FIESTA", 950.00));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, List.of(), List.of(), Map.of(), bd, Map.of());
        // orden final: no-029 primero
        FilaCompra alquiler = rc.getFilas().get(0);
        FilaCompra persona = rc.getFilas().get(1);
        assertEquals("196", alquiler.getRenglon());
        assertEquals("", alquiler.getNit());
        assertEquals("029", persona.getRenglon());
        assertEquals("5050505", persona.getNit());
        assertEquals("8-2026", persona.getContrato());
        assertTrue(rc.getAlertas().stream()
                .anyMatch(a -> a.startsWith("REVISAR cheque 41010")));
        assertTrue(rc.getAlertas().stream()
                .anyMatch(a -> a.startsWith("SIN NIT cheque 41010")));
    }

    @Test
    void nuevo029GeneraAlertaConEmoji() {
        List<Cheque> cheques = List.of(ch("41004", "LUIS CHAVEZ MO", 2800.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("41004", "6060606", "029", 2800.00,
                        "honorarios COMO: DIGITADOR, SEGUN CONTRATO No. 15-2026",
                        "15-2026"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, List.of(), pdfs, Map.of(), Map.of(), Map.of());
        assertTrue(rc.getAlertas().stream()
                .anyMatch(a -> a.startsWith("\uD83C\uDD95 R029 NUEVO")));
        assertEquals("DIGITADOR", rc.getNuevos029().get("6060606").getCargo());
    }

    @Test
    void sociedadAnonimaNoConfundeEmpresasDistintas() {
        assertFalse(MotorComprasService.nombreCoincide(
                "MULTIADITIVOS Y CONCRETOS DE GUATEMALA, SOCIEDAD ANONIMA",
                "DISTRIBUIDORA DE ELECTRICIDAD DE ORIENTE SOCIEDAD ANONIMA"));
    }

    @Test
    void noRobaNpgDeOtroNitPorSociedadAnonima() {
        List<Cheque> cheques = List.of(
                ch("44915", "MULTIADITIVOS Y CONCRETOS DE GUATEMALA, SOCIEDAD ANONIMA", 22995.00),
                ch("44916", "DISTRIBUIDORA DE ELECTRICIDAD DE ORIENTE SOCIEDAD ANONIMA", 40681.13));
        List<RegistroSicoin> pdfs = List.of(
                pdf("44915", "90711769", "274", 22995.00, "315 sacos de cemento 4060 PSI", "N/A"),
                pdf("44916", "14946203", "111", 40681.13,
                        "servicio de energia electrica correspondiente al mes de julio 2026", "N/A"));
        List<RegistroGuatecompras> txts = List.of(
                txt("E588937274", "14946203", 40681.13, "CASO DE EXCEPCION",
                        "pago de servicio de energia electrica correspondiente al mes de julio 2026.",
                        "DISTRIBUIDORA DE ELECTRICIDAD DE ORIENTE SOCIEDAD ANONIMA"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, txts, pdfs, Map.of(), Map.of(), Map.of());
        FilaCompra cemento = rc.getFilas().stream()
                .filter(f -> "44915".equals(f.getCheque())).findFirst().orElseThrow();
        FilaCompra deorsa = rc.getFilas().stream()
                .filter(f -> "44916".equals(f.getCheque())).findFirst().orElseThrow();
        assertEquals("", cemento.getNpg());
        assertEquals("E588937274", deorsa.getNpg());
    }

    @Test
    void catalogoAsignaNpgPorNitCuandoElTxtDelMesNoLoTrae() {
        List<Cheque> cheques = List.of(
                ch("44915", "MULTIADITIVOS Y CONCRETOS DE GUATEMALA, SOCIEDAD ANONIMA", 22995.00));
        List<RegistroSicoin> pdfs = List.of(
                pdf("44915", "90711769", "274", 22995.00, "315 sacos de cemento 4060 PSI", "N/A"));
        List<RegistroGuatecompras> catalogo = List.of(
                txt("E579255468", "90711769", 20790.00, "BAJA CUANTIA",
                        "Por pago de 315 sacos de cemento gris de 4060 PSI",
                        "MULTIADITIVOS Y CONCRETOS DE GUATEMALA, SOCIEDAD ANONIMA"));
        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, List.of(), pdfs, Map.of(), Map.of(), Map.of(), catalogo);
        assertEquals("E579255468", rc.getFilas().get(0).getNpg());
    }

    @Test
    void extraerParaBdConservaElPrimerNombreYCompleta() {
        FilaCompra a = fila("029", "9999", "PRIMER NOMBRE",
                "servicios varios", "N/A", "");
        FilaCompra b = fila("029", "9999", "SEGUNDO NOMBRE",
                "servicios COMO: COCINERA, SEGUN CONTRATO NO. 4-2026",
                "4-2026", "E560000001");
        FilaCompra c = fila("299", "1234", "PROVEEDOR X",
                "pago de materiales varios", "N/A", "");
        FilaCompra d = fila("184", "5678", "EXCLUIDO", "x", "N/A", "");
        ResultadoConstruccion ex = MotorComprasService.extraerParaBd(
                List.of(a, b, c, d));
        ContratoInfo info = ex.getNuevos029().get("9999");
        assertEquals("PRIMER NOMBRE", info.getNombre());
        assertEquals("4-2026", info.getContrato());
        assertEquals("COCINERA", info.getCargo());
        assertEquals("E560000001", info.getNpg());
        ProveedorInfo prov = ex.getNuevosProv().get("1234");
        assertEquals("materiales varios", prov.getDesc());
        assertEquals(1, ex.getNuevosProv().size());
    }

    private static FilaCompra fila(String renglon, String nit, String proveedor,
                                   String desc, String contrato, String npg) {
        FilaCompra f = new FilaCompra();
        f.setRenglon(renglon); f.setNit(nit); f.setProveedor(proveedor);
        f.setDesc(desc); f.setContrato(contrato); f.setNpg(npg);
        f.setPrecio(100.0); f.setCheque("1");
        f.setModalidad(""); f.setFechaPub(""); f.setFechaAdj("");
        f.setFechaCont("N/A");
        return f;
    }
}
