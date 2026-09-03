package com.granados.sistema.dafim.presupuesto.service;

import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria.TipoDineroCaja;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.BusquedaPago;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApartadoCalculoTest {

    private static LineaPresupuesto linea(String renglon, String fuente, String saldo) {
        LineaPresupuesto l = new LineaPresupuesto();
        l.setRenglon(renglon);
        l.setFuente(fuente);
        l.setActividadObra("");
        l.setDescripcion("FLETES");
        l.setVigente(new BigDecimal(saldo));
        l.setDevengado(BigDecimal.ZERO);
        l.setPagado(BigDecimal.ZERO);
        l.setSaldoDisponible(new BigDecimal(saldo));
        return l;
    }

    private static Apartado activo(String renglon, String fuente, String pres, String banco) {
        Apartado a = new Apartado();
        a.setRenglon(renglon);
        a.setFuente(fuente);
        a.setActividadObra("");
        a.setEstado(Apartado.EST_ACTIVO);
        a.setMontoPresupuesto(new BigDecimal(pres));
        a.setMontoBanco(new BigDecimal(banco));
        return a;
    }

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " pero fue " + actual);
    }

    @Test
    void claveLineaNormalizaNulos() {
        assertEquals("|||||142|11-0000-0000",
                PresupuestoService.claveLinea("142", "11-0000-0000", null));
        assertEquals("||||001|142|11-0000-0000",
                PresupuestoService.claveLinea("142", "11-0000-0000", "001"));
        assertEquals("19||001|||029|31-0151-0003",
                PresupuestoService.claveLinea("029", "31-0151-0003", "",
                        "19 MOVILIDAD URBANA", "001 SERVICIOS PUBLICOS"));
        assertEquals(
                PresupuestoService.claveLinea("029", "31-0151-0003", "", "19 MOVILIDAD", "001 X"),
                PresupuestoService.claveLinea("029", "31-0151-0003", "", "19", "001"));
    }

    @Test
    void claveLineaDistingueActividadesDelMismoProyecto() {
        LineaPresupuesto calle = gemela19(563L, "367500");
        calle.setSubprograma("02 MEJORA DE LA GESTION MUNICIPAL");
        calle.setActividad("001 APOYO CALLE AREA URBANA Y OTRAS COMUNIDADES");
        LineaPresupuesto servicios = gemela19(568L, "34968");
        servicios.setSubprograma("02 MEJORA DE LA GESTION MUNICIPAL");
        servicios.setActividad("002 APOYO SERVICIOS PUBLICOS TODAS LAS COMUNIDADES");

        assertNotEquals(PresupuestoService.claveLinea(calle),
                PresupuestoService.claveLinea(servicios));
        assertEquals("19|02|001|001|000|029|22-0101-0001",
                PresupuestoService.claveLinea(calle));
        assertEquals("19|02|001|002|000|029|22-0101-0001",
                PresupuestoService.claveLinea(servicios));
    }

    @Test
    void saldoLibreRestaSinIrANegativo() {
        assertMonto("30000", PresupuestoService.saldoLibre(
                new BigDecimal("50000"), new BigDecimal("20000")));
        assertMonto("0", PresupuestoService.saldoLibre(
                new BigDecimal("100"), new BigDecimal("150")));
        assertMonto("100", PresupuestoService.saldoLibre(new BigDecimal("100"), null));
    }

    @Test
    void sumaApartadoPresupuestoSoloActivosDeEsaLinea() {
        List<Apartado> lista = List.of(
                activo("142", "11-0000-0000", "20000", "0"),
                activo("142", "11-0000-0000", "5000", "1000"),
                activo("142", "22-0000-0000", "9000", "0"),
                activo("274", "11-0000-0000", "1000", "0"));
        lista.get(2).setEstado(Apartado.EST_LIBERADO);

        Map<String, BigDecimal> porClave = PresupuestoService.apartadoPresupuestoPorClave(lista);
        assertMonto("25000", porClave.get(PresupuestoService.claveLinea("142", "11-0000-0000", "")));
        assertFalse(porClave.containsKey(PresupuestoService.claveLinea("142", "22-0000-0000", "")));
    }

    @Test
    void sumaApartadoBancoPorFuenteIndependienteDelRenglon() {
        List<Apartado> lista = List.of(
                activo("142", "11-0000-0000", "20000", "8000"),
                activo("274", "11-0000-0000", "1000", "2000"),
                activo("142", "22-0000-0000", "500", "500"));

        Map<String, BigDecimal> porFuente = PresupuestoService.apartadoBancoPorFuente(lista);
        assertMonto("10000", porFuente.get("11-0000-0000"));
        assertMonto("500", porFuente.get("22-0000-0000"));
    }

    @Test
    void validarPermitePresupuestoSinDineroDeBanco() {
        PresupuestoService.validarApartado(
                new BigDecimal("20000"), BigDecimal.ZERO,
                new BigDecimal("50000"), BigDecimal.ZERO);
    }

    @Test
    void validarRechazaPresupuestoCeroONegativo() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PresupuestoService.validarApartado(
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("100"), new BigDecimal("100")));
        assertTrue(e.getMessage().contains("presupuesto"));
    }

    @Test
    void validarRechazaSiElPresupuestoSuperaElLibre() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PresupuestoService.validarApartado(
                        new BigDecimal("20000.01"), BigDecimal.ZERO,
                        new BigDecimal("20000"), new BigDecimal("50000")));
        assertTrue(e.getMessage().contains("presupuesto"));
    }

    @Test
    void validarRechazaSiElBancoSuperaElLibreAunqueElPresupuestoAlcance() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PresupuestoService.validarApartado(
                        new BigDecimal("1000"), new BigDecimal("5000"),
                        new BigDecimal("10000"), new BigDecimal("4999.99")));
        assertTrue(e.getMessage().contains("banco"));
    }

    @Test
    void validarAceptaApartarPresupuestoYBancoCuandoAmbosAlcanzan() {
        PresupuestoService.validarApartado(
                new BigDecimal("20000"), new BigDecimal("15000"),
                new BigDecimal("20000"), new BigDecimal("15000"));
    }

    @Test
    void buscarDondePagarUsaSaldoLibreTrasApartados() {
        List<LineaPresupuesto> lineas = List.of(
                linea("142", "11-0000-0000", "50000"));
        Map<String, BigDecimal> pres = Map.of(
                PresupuestoService.claveLinea("142", "11-0000-0000", ""),
                new BigDecimal("20000"));
        Map<String, BigDecimal> banco = Map.of("11-0000-0000", new BigDecimal("3000"));
        Map<String, BigDecimal> dinero = Map.of("11-0000-0000", new BigDecimal("10000"));

        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "142", new BigDecimal("20000"), lineas, Map.of(),
                dinero, pres, banco).get(0);

        assertMonto("30000", b.getTotalDisponible());
        assertMonto("30000", b.getLineas().get(0).getSaldoDisponible());
        assertTrue(b.getLineas().get(0).isAlcanza());
        assertMonto("7000", b.getLineas().get(0).getDineroReal());
        assertFalse(b.getLineas().get(0).isAlcanzaBanco()); // 7000 < 20000
        assertMonto("20000", b.getLineas().get(0).getApartadoPresupuesto());
    }

    @Test
    void buscarDondePagarSinOverlayNoCambiaLosSaldos() {
        List<LineaPresupuesto> lineas = List.of(
                linea("142", "11-0000-0000", "50000"));

        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "142", new BigDecimal("20000"), lineas, Map.of()).get(0);

        assertMonto("50000", b.getTotalDisponible());
        assertTrue(b.getLineas().get(0).isAlcanza());
        assertMonto("0", b.getLineas().get(0).getApartadoPresupuesto());
    }

    /**
     * Misma fuente, mismo renglon y el mismo saldo de banco no pueden
     * hacer que un apartado del programa 19 agote tambien el 01.
     */
    @Test
    void buscarDondePagarNoCruzaApartadoEntreProgramasDeLaMismaFuente() {
        LineaPresupuesto p01 = linea("029", "31-0151-0003", "100000");
        p01.setPrograma("01 ACTIVIDADES CENTRALES");
        p01.setProyecto("000 SIN PROYECTO");
        LineaPresupuesto p19 = linea("029", "31-0151-0003", "100000");
        p19.setPrograma("19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");
        p19.setProyecto("001 SERVICIOS PUBLICOS MUNICIPALES");

        Apartado apartado19 = activo("029", "31-0151-0003", "100000", "0");
        apartado19.setPrograma(p19.getPrograma());
        apartado19.setProyecto(p19.getProyecto());

        Map<String, BigDecimal> pres = PresupuestoService.apartadoPresupuestoPorClave(
                List.of(apartado19));
        Map<String, BigDecimal> banco = Map.of("31-0151-0003", new BigDecimal("29712.61"));

        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "029", new BigDecimal("100000"), List.of(p01, p19), Map.of(),
                Map.of("31-0151-0003", new BigDecimal("29712.61")),
                pres, banco).get(0);

        PresupuestoService.LineaFuente linea01 = porPrograma(b, "01 ACTIVIDADES CENTRALES");
        PresupuestoService.LineaFuente linea19 = porPrograma(b, "19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");

        assertMonto("0", linea19.getSaldoDisponible());
        assertMonto("100000", linea19.getApartadoPresupuesto());
        assertMonto("100000", linea01.getSaldoDisponible());
        assertMonto("0", linea01.getApartadoPresupuesto());
        assertTrue(linea01.isAlcanza());
        assertFalse(linea19.isAlcanza());
    }

    @Test
    void armarDesgloseNoCruzaApartadoEntreProgramasDeLaMismaFuente() {
        LineaPresupuesto p01 = linea("029", "31-0151-0003", "100000");
        p01.setPrograma("01 ACTIVIDADES CENTRALES");
        p01.setProyecto("000 SIN PROYECTO");
        LineaPresupuesto p19 = linea("029", "31-0151-0003", "100000");
        p19.setPrograma("19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");
        p19.setProyecto("001 SERVICIOS PUBLICOS MUNICIPALES");

        Apartado apartado19 = activo("029", "31-0151-0003", "100000", "0");
        apartado19.setPrograma(p19.getPrograma());
        apartado19.setProyecto(p19.getProyecto());

        Map<String, BigDecimal> pres = PresupuestoService.apartadoPresupuestoPorClave(
                List.of(apartado19));
        PresupuestoService.DesgloseFuente d = PresupuestoService.armarDesglose(
                "31-0151-0003", "", new BigDecimal("100000"),
                List.of(p01, p19), BigDecimal.ZERO, List.of(), pres);

        PresupuestoService.LineaDesglose des01 = d.getGrupos().stream()
                .flatMap(g -> g.getLineas().stream())
                .filter(ld -> "01 ACTIVIDADES CENTRALES".equals(ld.getLinea().getPrograma()))
                .findFirst().orElseThrow();
        PresupuestoService.LineaDesglose des19 = d.getGrupos().stream()
                .flatMap(g -> g.getLineas().stream())
                .filter(ld -> "19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS".equals(ld.getLinea().getPrograma()))
                .findFirst().orElseThrow();

        assertMonto("100000", des01.getSaldoLibre());
        assertMonto("0", des01.getApartadoPresupuesto());
        assertMonto("0", des19.getSaldoLibre());
        assertMonto("100000", des19.getApartadoPresupuesto());
    }

    @Test
    void elegirLineaUsaProgramaYNoElMayorSaldo() {
        LineaPresupuesto p01 = linea("029", "31-0151-0003", "200000");
        p01.setPrograma("01 ACTIVIDADES CENTRALES");
        p01.setProyecto("000 SIN PROYECTO");
        LineaPresupuesto p19 = linea("029", "31-0151-0003", "100000");
        p19.setPrograma("19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");
        p19.setProyecto("001 SERVICIOS PUBLICOS MUNICIPALES");

        LineaPresupuesto elegida = PresupuestoService.elegirLinea(
                List.of(p01, p19), "31-0151-0003", "",
                "19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS",
                "001 SERVICIOS PUBLICOS MUNICIPALES");

        assertEquals(p19, elegida);
    }

    @Test
    void elegirLineaAmbiguoSinProgramaNoAdivina() {
        LineaPresupuesto p01 = linea("029", "31-0151-0003", "200000");
        p01.setPrograma("01 ACTIVIDADES CENTRALES");
        LineaPresupuesto p19 = linea("029", "31-0151-0003", "100000");
        p19.setPrograma("19 MOVILIDAD URBANA");

        assertEquals(null, PresupuestoService.elegirLinea(
                List.of(p01, p19), "31-0151-0003", "", null, null));
    }

    /**
     * Programa 19 tiene dos lineas 029 / 22-0101-0001 con el mismo
     * proyecto y distinta disponibilidad: el apartado de una no puede
     * agotar la otra ni impedir abrir el formulario.
     */
    @Test
    void apartadoPorLineaIdNoCruzaGemelasDelMismoPrograma() {
        LineaPresupuesto gorda = gemela19(563L, "367500");
        LineaPresupuesto flaca = gemela19(568L, "34968");
        Apartado ap = activo("029", "22-0101-0001", "100000", "0");
        ap.setPrograma(gorda.getPrograma());
        ap.setProyecto(gorda.getProyecto());
        ap.setLineaId(563L);

        Map<Long, BigDecimal> porId = PresupuestoService.apartadoPresupuestoPorLinea(
                List.of(gorda, flaca), List.of(ap));

        assertMonto("100000", porId.get(563L));
        assertFalse(porId.containsKey(568L));
    }

    @Test
    void buscarDondePagarConLineaIdSoloRestaLaGemelaElegida() {
        LineaPresupuesto gorda = gemela19(563L, "367500");
        LineaPresupuesto flaca = gemela19(568L, "34968");
        Map<Long, BigDecimal> porId = Map.of(563L, new BigDecimal("100000"));

        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "029", new BigDecimal("100000"), List.of(gorda, flaca), Map.of(),
                Map.of(), Map.of(), Map.of(), porId).get(0);

        PresupuestoService.LineaFuente deGorda = porIdLinea(b, 563L);
        PresupuestoService.LineaFuente deFlaca = porIdLinea(b, 568L);
        assertMonto("267500", deGorda.getSaldoDisponible());
        assertMonto("100000", deGorda.getApartadoPresupuesto());
        assertMonto("34968", deFlaca.getSaldoDisponible());
        assertMonto("0", deFlaca.getApartadoPresupuesto());
        assertEquals(563L, deGorda.getId());
        assertEquals(568L, deFlaca.getId());
    }

    @Test
    void elegirLineaGemelasDelMismoProgramaNoAdivina() {
        LineaPresupuesto gorda = gemela19(563L, "367500");
        LineaPresupuesto flaca = gemela19(568L, "34968");
        assertEquals(null, PresupuestoService.elegirLinea(
                List.of(gorda, flaca), "22-0101-0001", "000",
                gorda.getPrograma(), gorda.getProyecto()));
    }

    @Test
    void elegirLineaGemelasDeDistintaActividadEligeLaCorrecta() {
        LineaPresupuesto gorda = gemela19(563L, "367500");
        gorda.setActividadObra("001");
        LineaPresupuesto flaca = gemela19(568L, "34968");
        flaca.setActividadObra("002");
        assertEquals(gorda, PresupuestoService.elegirLinea(
                List.of(gorda, flaca), "22-0101-0001", "001",
                gorda.getPrograma(), gorda.getProyecto()));
        assertEquals(flaca, PresupuestoService.elegirLinea(
                List.of(gorda, flaca), "22-0101-0001", "002",
                flaca.getPrograma(), flaca.getProyecto()));
    }

    /**
     * Tras reimportar el PDF los ids cambian. Si las gemelas siguen
     * compartiendo la clave (misma actividad), el overlay no puede
     * adivinar: no se rocia el apartado a las dos.
     */
    @Test
    void overlayTrasReimportNoPisaGemelasSiClaveEsAmbiguo() {
        LineaPresupuesto gorda = gemela19(901L, "367500");
        LineaPresupuesto flaca = gemela19(902L, "34968");
        Apartado ap = apartadoSobre(gemela19(563L, "367500"), "100000");

        List<LineaPresupuesto> nuevas = List.of(gorda, flaca);
        List<Apartado> activos = List.of(ap);
        Map<String, BigDecimal> porClave = PresupuestoService.apartadoPresupuestoPorClave(activos);
        Map<Long, BigDecimal> porId = PresupuestoService.apartadoPresupuestoPorLinea(nuevas, activos);

        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "029", new BigDecimal("100000"), nuevas, Map.of(),
                Map.of(), porClave, Map.of(), porId).get(0);

        PresupuestoService.LineaFuente deGorda = porIdLinea(b, 901L);
        PresupuestoService.LineaFuente deFlaca = porIdLinea(b, 902L);
        assertMonto("367500", deGorda.getSaldoDisponible());
        assertMonto("0", deGorda.getApartadoPresupuesto());
        assertMonto("34968", deFlaca.getSaldoDisponible());
        assertMonto("0", deFlaca.getApartadoPresupuesto());
        assertFalse(porId.containsKey(901L));
        assertFalse(porId.containsKey(902L));
        assertEquals(null, PresupuestoService.lineaIdVigente(ap, nuevas));
        assertFalse(PresupuestoService.reasignarLineaId(ap, nuevas));
        assertEquals(563L, ap.getLineaId());
    }

    /**
     * Si Agent A distingue las gemelas por actividad, la clave completa
     * sobrevive al reimport: el apartado sigue en la misma linea aunque
     * el id haya cambiado.
     */
    @Test
    void overlayTrasReimportConservaPorClaveCompletaCuandoHayActividad() {
        LineaPresupuesto gordaVieja = gemela19(563L, "367500");
        gordaVieja.setActividadObra("001");
        LineaPresupuesto gorda = gemela19(901L, "367500");
        gorda.setActividadObra("001");
        LineaPresupuesto flaca = gemela19(902L, "34968");
        flaca.setActividadObra("002");
        Apartado ap = apartadoSobre(gordaVieja, "100000");

        List<LineaPresupuesto> nuevas = List.of(gorda, flaca);
        List<Apartado> activos = List.of(ap);
        Map<String, BigDecimal> porClave = PresupuestoService.apartadoPresupuestoPorClave(activos);
        Map<Long, BigDecimal> porId = PresupuestoService.apartadoPresupuestoPorLinea(nuevas, activos);

        assertMonto("100000", porId.get(901L));
        assertFalse(porId.containsKey(902L));
        assertEquals(901L, PresupuestoService.lineaIdVigente(ap, nuevas));
        assertTrue(PresupuestoService.reasignarLineaId(ap, nuevas));
        assertEquals(901L, ap.getLineaId());

        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "029", new BigDecimal("100000"), nuevas, Map.of(),
                Map.of(), porClave, Map.of(), porId).get(0);

        PresupuestoService.LineaFuente deGorda = porIdLinea(b, 901L);
        PresupuestoService.LineaFuente deFlaca = porIdLinea(b, 902L);
        assertMonto("267500", deGorda.getSaldoDisponible());
        assertMonto("100000", deGorda.getApartadoPresupuesto());
        assertMonto("34968", deFlaca.getSaldoDisponible());
        assertMonto("0", deFlaca.getApartadoPresupuesto());
    }

    @Test
    void overlayTrasReimportConservaPorActividadDeJerarquia() {
        LineaPresupuesto calleVieja = gemela19(563L, "367500");
        calleVieja.setSubprograma("02 MEJORA DE LA GESTION MUNICIPAL");
        calleVieja.setActividad("001 APOYO CALLE AREA URBANA Y OTRAS COMUNIDADES");
        LineaPresupuesto calle = gemela19(901L, "367500");
        calle.setSubprograma("02 MEJORA DE LA GESTION MUNICIPAL");
        calle.setActividad("001 APOYO CALLE AREA URBANA Y OTRAS COMUNIDADES");
        LineaPresupuesto servicios = gemela19(902L, "34968");
        servicios.setSubprograma("02 MEJORA DE LA GESTION MUNICIPAL");
        servicios.setActividad("002 APOYO SERVICIOS PUBLICOS TODAS LAS COMUNIDADES");
        Apartado ap = apartadoSobre(calleVieja, "100000");

        List<LineaPresupuesto> nuevas = List.of(calle, servicios);
        Map<Long, BigDecimal> porId = PresupuestoService.apartadoPresupuestoPorLinea(
                nuevas, List.of(ap));

        assertMonto("100000", porId.get(901L));
        assertFalse(porId.containsKey(902L));
        assertEquals(901L, PresupuestoService.lineaIdVigente(ap, nuevas));
        assertTrue(PresupuestoService.reasignarLineaId(ap, nuevas));
        assertEquals(901L, ap.getLineaId());
    }

    @Test
    void armarDesgloseTrasReimportNoPisaGemelasSiClaveEsAmbiguo() {
        LineaPresupuesto gorda = gemela19(901L, "367500");
        LineaPresupuesto flaca = gemela19(902L, "34968");
        Apartado ap = apartadoSobre(gemela19(563L, "367500"), "100000");

        List<LineaPresupuesto> nuevas = List.of(gorda, flaca);
        List<Apartado> activos = List.of(ap);
        PresupuestoService.DesgloseFuente d = PresupuestoService.armarDesglose(
                "22-0101-0001", "", new BigDecimal("100000"), nuevas,
                BigDecimal.ZERO, List.of(),
                PresupuestoService.apartadoPresupuestoPorClave(activos),
                PresupuestoService.apartadoPresupuestoPorLinea(nuevas, activos));

        PresupuestoService.LineaDesglose desGorda = d.getGrupos().stream()
                .flatMap(g -> g.getLineas().stream())
                .filter(ld -> Long.valueOf(901L).equals(ld.getLinea().getId()))
                .findFirst().orElseThrow();
        PresupuestoService.LineaDesglose desFlaca = d.getGrupos().stream()
                .flatMap(g -> g.getLineas().stream())
                .filter(ld -> Long.valueOf(902L).equals(ld.getLinea().getId()))
                .findFirst().orElseThrow();

        assertMonto("367500", desGorda.getSaldoLibre());
        assertMonto("0", desGorda.getApartadoPresupuesto());
        assertMonto("34968", desFlaca.getSaldoLibre());
        assertMonto("0", desFlaca.getApartadoPresupuesto());
    }

    @Test
    void dosApartadosEnGemelasNoSePisan() {
        LineaPresupuesto gorda = gemela19(563L, "367500");
        LineaPresupuesto flaca = gemela19(568L, "34968");
        Map<Long, BigDecimal> porId = PresupuestoService.apartadoPresupuestoPorLinea(
                List.of(gorda, flaca),
                List.of(apartadoSobre(gorda, "100000"), apartadoSobre(flaca, "5000")));

        assertMonto("100000", porId.get(563L));
        assertMonto("5000", porId.get(568L));
    }

    /**
     * Un apartado de banco del programa 19 no puede agotar el dinero real
     * de funcionamiento del programa 01: son pozos distintos.
     */
    @Test
    void apartadoBancoDel19NoDebeAgotarDineroRealDel01() {
        LineaPresupuesto p01 = linea("029", "31-0151-0003", "100000");
        p01.setId(1L);
        p01.setPrograma("01 ACTIVIDADES CENTRALES");
        p01.setProyecto("000 SIN PROYECTO");
        LineaPresupuesto p19 = linea("029", "31-0151-0003", "100000");
        p19.setId(2L);
        p19.setPrograma("19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");
        p19.setProyecto("001 SERVICIOS PUBLICOS MUNICIPALES");

        Apartado apartado19 = apartadoSobre(p19, "100000");
        apartado19.setMontoBanco(new BigDecimal("29712.61"));

        Map<String, BigDecimal> banco = PresupuestoService.apartadoBancoPorFuenteYTipo(
                List.of(apartado19));
        Map<String, BigDecimal> dinero = Map.of(
                PresupuestoService.claveDineroCaja("31-0151-0003", TipoDineroCaja.FUNCIONAMIENTO),
                new BigDecimal("29712.61"),
                PresupuestoService.claveDineroCaja("31-0151-0003", TipoDineroCaja.INVERSION),
                BigDecimal.ZERO);
        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "029", new BigDecimal("100000"), List.of(p01, p19), Map.of(),
                dinero, Map.of(), banco, Map.of()).get(0);

        PresupuestoService.LineaFuente linea01 = porPrograma(b, "01 ACTIVIDADES CENTRALES");
        PresupuestoService.LineaFuente linea19 = porPrograma(b, "19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");
        assertMonto("29712.61", linea01.getDineroReal());
        assertFalse(linea01.isAlcanzaBanco());
        assertMonto("0", linea19.getDineroReal());
        assertFalse(linea19.isAlcanzaBanco());
    }

    private static Apartado apartadoSobre(LineaPresupuesto l, String pres) {
        Apartado a = activo(l.getRenglon(), l.getFuente(), pres, "0");
        a.setPrograma(l.getPrograma());
        a.setSubprograma(l.getSubprograma());
        a.setProyecto(l.getProyecto());
        a.setActividad(l.getActividad());
        a.setActividadObra(l.getActividadObra());
        a.setLineaId(l.getId());
        return a;
    }

    private static LineaPresupuesto gemela19(Long id, String saldo) {
        LineaPresupuesto l = linea("029", "22-0101-0001", saldo);
        l.setId(id);
        l.setPrograma("19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");
        l.setProyecto("001 SERVICIOS PUBLICOS MUNICIPALES");
        l.setActividadObra("000");
        return l;
    }

    private static PresupuestoService.LineaFuente porIdLinea(BusquedaPago b, Long id) {
        return b.getLineas().stream()
                .filter(l -> id.equals(l.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no estaba la linea " + id));
    }

    private static PresupuestoService.LineaFuente porPrograma(BusquedaPago b, String programa) {
        return b.getLineas().stream()
                .filter(l -> programa.equals(l.getPrograma()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no estaba el programa " + programa));
    }
}
