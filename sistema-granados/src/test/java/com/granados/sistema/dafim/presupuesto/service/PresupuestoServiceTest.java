package com.granados.sistema.dafim.presupuesto.service;

import com.granados.sistema.dafim.compras.entity.HistorialCompra;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria.TipoDineroCaja;
import com.granados.sistema.dafim.presupuesto.entity.CuentaMonetaria;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.BusquedaPago;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.DesgloseFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.DisponibilidadRenglon;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.FuenteResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.GrupoDesglose;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.LineaDesglose;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.LineaFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.RenglonResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.SimulacionTransferencia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresupuestoServiceTest {

    private static LineaPresupuesto linea(String renglon, String fuente, String descripcion,
                                          String vigente, String devengado, String pagado,
                                          String saldoDisponible) {
        LineaPresupuesto l = new LineaPresupuesto();
        l.setRenglon(renglon);
        l.setFuente(fuente);
        l.setDescripcion(descripcion);
        l.setVigente(new BigDecimal(vigente));
        l.setDevengado(new BigDecimal(devengado));
        l.setPagado(new BigDecimal(pagado));
        l.setSaldoDisponible(new BigDecimal(saldoDisponible));
        return l;
    }

    private static HistorialCompra pago(String renglon, int anio, int mes, String monto) {
        HistorialCompra h = new HistorialCompra();
        h.setRenglon(renglon);
        h.setAnio(anio);
        h.setMes(mes);
        h.setMonto(new BigDecimal(monto));
        return h;
    }

    /** Linea con id y estructura programatica, para desglose y simulacion de transferencias. */
    private static LineaPresupuesto lineaEstructura(Long id, String programa, String proyecto,
                                                    String vigente, String devengado,
                                                    String pagado, String saldoDisponible) {
        LineaPresupuesto l = linea("274", "11-0000-0000", "MATERIALES", vigente, devengado,
                pagado, saldoDisponible);
        l.setId(id);
        l.setPrograma(programa);
        l.setProyecto(proyecto);
        return l;
    }

    /** BigDecimal.equals compara escala; para importes interesa el valor. */
    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " pero fue " + actual);
    }

    // ---------------------------- agregarPorRenglon ----------------------------

    @Test
    void agregarPorRenglonSumaLineasYOrdenaPorVigenteDesc() {
        List<LineaPresupuesto> lineas = List.of(
                linea("029", "11-0000-0000", "PAPEL", "500", "100", "100", "400"),
                linea("274", "11-0000-0000", "CEMENTO", "1000", "500", "400", "500"),
                linea("274", "22-0000-0000", "CEMENTO GRIS", "2000", "1000", "800", "1000"));

        List<RenglonResumen> resumen = PresupuestoService.agregarPorRenglon(lineas, Map.of());

        assertEquals(2, resumen.size());
        RenglonResumen r274 = resumen.get(0); // vigente 3000 > 500
        assertEquals("274", r274.getRenglon());
        assertMonto("3000", r274.getVigente());
        assertMonto("1500", r274.getDevengado());
        assertMonto("1200", r274.getPagado());
        assertMonto("1500", r274.getSaldoDisponible());
        assertEquals(50.0, r274.getPctEjecucion(), 0.0001);
        RenglonResumen r029 = resumen.get(1);
        assertEquals("029", r029.getRenglon());
        assertMonto("500", r029.getVigente());
    }

    @Test
    void agregarPorRenglonCruzaPagosDelSistemaYCalculaDiferencia() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "500", "400", "500"),
                linea("274", "22-0000-0000", "CEMENTO", "2000", "1000", "800", "1000"),
                linea("029", "11-0000-0000", "PAPEL", "500", "100", "100", "400"));
        Map<String, BigDecimal> pagos = Map.of(
                "274", new BigDecimal("1150"),
                "029", new BigDecimal("100"));

        List<RenglonResumen> resumen = PresupuestoService.agregarPorRenglon(lineas, pagos);

        RenglonResumen r274 = resumen.get(0);
        assertMonto("1150", r274.getPagosSistema());
        assertMonto("50", r274.getDiferencia()); // 1200 - 1150
        RenglonResumen r029 = resumen.get(1);
        assertMonto("100", r029.getPagosSistema());
        assertMonto("0", r029.getDiferencia());
    }

    @Test
    void agregarPorRenglonSinPagosDelSistemaDejaDiferenciaIgualAlPagado() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "500", "400", "500"));

        RenglonResumen r = PresupuestoService.agregarPorRenglon(lineas, Map.of()).get(0);

        assertMonto("0", r.getPagosSistema());
        assertMonto("400", r.getDiferencia());
    }

    @Test
    void agregarPorRenglonUsaLaDescripcionMasFrecuente() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "100", "0", "0", "100"),
                linea("274", "22-0000-0000", "CEMENTO GRIS", "100", "0", "0", "100"),
                linea("274", "33-0000-0000", "CEMENTO", "100", "0", "0", "100"));

        RenglonResumen r = PresupuestoService.agregarPorRenglon(lineas, Map.of()).get(0);

        assertEquals("CEMENTO", r.getDescripcion());
    }

    @Test
    void agregarPorRenglonConVigenteCeroDaPctCeroSinExplotar() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "0", "0", "0", "0"));

        RenglonResumen r = PresupuestoService.agregarPorRenglon(lineas, Map.of()).get(0);

        assertEquals(0.0, r.getPctEjecucion(), 0.0001);
    }

    // ----------------------------- agregarPorFuente -----------------------------

    @Test
    void agregarPorFuenteSumaYUsaElNombreDelCatalogo() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "250", "200", "750"),
                linea("029", "11-0000-0000", "PAPEL", "500", "250", "100", "250"),
                linea("274", "22-0000-0000", "CEMENTO", "3000", "1500", "900", "1500"));
        Map<String, String> nombres = Map.of("11-0000-0000", "FONDO COMUN");

        List<FuenteResumen> resumen = PresupuestoService.agregarPorFuente(lineas, nombres);

        assertEquals(2, resumen.size());
        FuenteResumen f22 = resumen.get(0); // vigente 3000 > 1500
        assertEquals("22-0000-0000", f22.getCodigo());
        assertEquals("", f22.getNombre()); // sin nombre en el catalogo
        assertMonto("3000", f22.getVigente());
        assertMonto("1500", f22.getDevengado());
        assertMonto("900", f22.getPagado());
        assertMonto("1500", f22.getSaldoDisponible());
        assertEquals(50.0, f22.getPctEjecucion(), 0.0001);
        FuenteResumen f11 = resumen.get(1);
        assertEquals("11-0000-0000", f11.getCodigo());
        assertEquals("FONDO COMUN", f11.getNombre());
        assertMonto("1500", f11.getVigente());
        assertMonto("500", f11.getDevengado());
    }

    // ------------------------- calcularDisponibilidad -------------------------

    @Test
    void disponibilidadOkCuandoLaEjecucionProyectadaNoPasaDelUmbral() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "500", "400", "500"));
        List<HistorialCompra> pagosMes = List.of(pago("274", 2026, 8, "100"));

        List<DisponibilidadRenglon> d = PresupuestoService.calcularDisponibilidad(
                Set.of("274"), lineas, pagosMes);

        assertEquals(1, d.size());
        DisponibilidadRenglon r = d.get(0);
        assertEquals("274", r.getRenglon());
        assertEquals("CEMENTO", r.getDescripcion());
        assertMonto("100", r.getPagosMes());
        assertMonto("400", r.getSaldoProyectado()); // 500 - 100
        assertEquals(DisponibilidadRenglon.SEM_OK, r.getSemaforo()); // (500+100)/1000 = 0.60
    }

    @Test
    void disponibilidadPorAgotarseCuandoSuperaEl85PorCiento() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "800", "700", "200"));
        List<HistorialCompra> pagosMes = List.of(pago("274", 2026, 8, "100"));

        DisponibilidadRenglon r = PresupuestoService.calcularDisponibilidad(
                Set.of("274"), lineas, pagosMes).get(0);

        assertMonto("100", r.getSaldoProyectado()); // aun positivo
        assertEquals(DisponibilidadRenglon.SEM_POR_AGOTARSE, r.getSemaforo()); // 0.90 > 0.85
    }

    @Test
    void disponibilidadOkEnEl85ExactoPorqueLaReglaEsEstricta() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "750", "700", "250"));
        List<HistorialCompra> pagosMes = List.of(pago("274", 2026, 8, "100"));

        DisponibilidadRenglon r = PresupuestoService.calcularDisponibilidad(
                Set.of("274"), lineas, pagosMes).get(0);

        assertEquals(DisponibilidadRenglon.SEM_OK, r.getSemaforo()); // 0.85 no es > 0.85
    }

    @Test
    void disponibilidadAgotadoCuandoLosPagosDelMesSuperanElSaldo() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "900", "850", "100"));
        List<HistorialCompra> pagosMes = List.of(pago("274", 2026, 8, "150"));

        DisponibilidadRenglon r = PresupuestoService.calcularDisponibilidad(
                Set.of("274"), lineas, pagosMes).get(0);

        assertMonto("-50", r.getSaldoProyectado()); // 100 - 150
        assertEquals(DisponibilidadRenglon.SEM_AGOTADO, r.getSemaforo());
    }

    @Test
    void disponibilidadConVigenteCeroNoDividePorCero() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "0", "0", "0", "0"));

        DisponibilidadRenglon r = PresupuestoService.calcularDisponibilidad(
                Set.of("274"), lineas, List.of()).get(0);

        assertEquals(DisponibilidadRenglon.SEM_OK, r.getSemaforo());
    }

    @Test
    void disponibilidadSoloIncluyeRenglonesCdPresentesYOrdenaPorLoMasCritico() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO", "1000", "0", "0", "1000"),
                linea("999", "11-0000-0000", "NO ES CD", "5000", "0", "0", "5000"));
        List<HistorialCompra> pagosMes = List.of(
                pago("274", 2026, 8, "1200"), // proyectado -200
                pago("029", 2026, 8, "50"));  // solo en pagos: proyectado -50

        List<DisponibilidadRenglon> d = PresupuestoService.calcularDisponibilidad(
                Set.of("274", "029", "111"), lineas, pagosMes);

        // "999" no es CD y "111" no aparece ni en presupuesto ni en pagos: quedan 2
        assertEquals(2, d.size());
        assertEquals("274", d.get(0).getRenglon()); // -200 primero (mas critico)
        assertEquals(DisponibilidadRenglon.SEM_AGOTADO, d.get(0).getSemaforo());
        assertEquals("029", d.get(1).getRenglon());
        assertMonto("0", d.get(1).getSaldoDisponible());
        assertMonto("50", d.get(1).getPagosMes());
        assertMonto("-50", d.get(1).getSaldoProyectado());
    }

    // ------------------------------ ultimosPagos ------------------------------

    @Test
    void ultimosPagosFiltraOrdenaDelMasRecienteYLimita() {
        List<HistorialCompra> historial = List.of(
                pago("274", 2026, 6, "10"),
                pago("029", 2026, 8, "99"), // otro renglon: fuera
                pago("274", 2026, 8, "30"),
                pago("274", 2026, 7, "20"));

        List<HistorialCompra> ultimos = PresupuestoService.ultimosPagos(historial, "274", 50);

        assertEquals(3, ultimos.size());
        assertMonto("30", ultimos.get(0).getMonto());
        assertMonto("20", ultimos.get(1).getMonto());
        assertMonto("10", ultimos.get(2).getMonto());
        assertTrue(PresupuestoService.ultimosPagos(historial, "274", 2).size() == 2);
    }

    // ---------------------------- buscarDondePagar ----------------------------

    @Test
    void buscarConKeywordFleteEncuentraElRenglon142() {
        List<LineaPresupuesto> lineas = List.of(
                linea("142", "11-0000-0000", "TRANSPORTE Y FLETES DE MATERIAL",
                        "1000", "0", "0", "1000"),
                linea("274", "11-0000-0000", "CEMENTO GRIS", "2000", "0", "0", "2000"));

        List<BusquedaPago> r = PresupuestoService.buscarDondePagar(
                "camionadas con flete", null, lineas, Map.of());

        assertEquals(1, r.size());
        assertEquals("142", r.get(0).getRenglon());
    }

    @Test
    void buscarSoloCamionadasEntraPorElInicioDeLaFraseClave() {
        // "CAMIONADAS" no contiene la keyword "CAMIONADAS DE 10" ni aparece
        // en la descripcion "FLETES": entra por ser el inicio de la frase
        List<LineaPresupuesto> lineas = List.of(
                linea("142", "11-0000-0000", "FLETES", "1000", "0", "0", "1000"),
                linea("274", "11-0000-0000", "CEMENTO GRIS", "2000", "0", "0", "2000"));

        List<BusquedaPago> r = PresupuestoService.buscarDondePagar(
                "camionadas", null, lineas, Map.of());

        assertEquals(1, r.size());
        assertEquals("142", r.get(0).getRenglon());
    }

    @Test
    void buscarCamionadasDeMaterialEncuentra142PorDescripcionSinKeyword() {
        // "CAMIONADAS DE MATERIAL" no contiene ninguna keyword: llega por texto libre
        List<LineaPresupuesto> lineas = List.of(
                linea("142", "11-0000-0000", "CAMIONADAS DE MATERIAL DE CONSTRUCCION",
                        "1000", "0", "0", "1000"),
                linea("223", "11-0000-0000", "ARENA Y GRAVA", "500", "0", "0", "500"));

        List<BusquedaPago> r = PresupuestoService.buscarDondePagar(
                "camionadas de material", null, lineas, Map.of());

        assertEquals(1, r.size());
        assertEquals("142", r.get(0).getRenglon());
        assertEquals("CAMIONADAS DE MATERIAL DE CONSTRUCCION", r.get(0).getDescripcion());
    }

    @Test
    void buscarArrendamientoRetroexcavadoraEncuentra154SinDuplicar() {
        // 154 pega por keyword (RETROEXCAVADORA) Y por descripcion (ARRENDAMIENTO):
        // debe aparecer una sola vez con sus dos lineas
        List<LineaPresupuesto> lineas = List.of(
                linea("154", "11-0000-0000", "ARRENDAMIENTO DE MAQUINARIA Y EQUIPO DE CONSTRUCCION",
                        "3000", "0", "0", "3000"),
                linea("154", "22-0000-0000", "ARRENDAMIENTO DE MAQUINARIA Y EQUIPO DE CONSTRUCCION",
                        "1000", "500", "400", "500"));

        List<BusquedaPago> r = PresupuestoService.buscarDondePagar(
                "arrendamiento de retroexcavadora", null, lineas, Map.of());

        assertEquals(1, r.size());
        assertEquals("154", r.get(0).getRenglon());
        assertEquals(2, r.get(0).getLineas().size());
        assertMonto("3500", r.get(0).getTotalDisponible());
    }

    @Test
    void buscarPorCodigoExactoDeTresDigitos() {
        List<LineaPresupuesto> lineas = List.of(
                linea("154", "11-0000-0000", "ARRENDAMIENTO DE MAQUINARIA",
                        "1000", "0", "0", "1000"),
                linea("155", "11-0000-0000", "ARRENDAMIENTO DE EDIFICIOS",
                        "800", "0", "0", "800"));

        List<BusquedaPago> r = PresupuestoService.buscarDondePagar(
                "154", null, lineas, Map.of());

        assertEquals(1, r.size());
        assertEquals("154", r.get(0).getRenglon());
    }

    @Test
    void alcanzaComparaElSaldoContraElMontoPedido() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO GRIS", "12000", "2000", "1000", "10000"),
                linea("274", "22-0000-0000", "CEMENTO GRIS", "4000", "2000", "1000", "2000"));

        List<BusquedaPago> r = PresupuestoService.buscarDondePagar(
                "cemento", new BigDecimal("5000"), lineas, Map.of());

        assertEquals(1, r.size());
        BusquedaPago b = r.get(0);
        assertEquals(2, b.getLineas().size());
        // orden por saldo descendente: primero la de 10,000
        assertEquals("11-0000-0000", b.getLineas().get(0).getFuente());
        assertMonto("10000", b.getLineas().get(0).getSaldoDisponible());
        assertTrue(b.getLineas().get(0).isAlcanza());   // 10,000 >= 5,000
        assertEquals("22-0000-0000", b.getLineas().get(1).getFuente());
        assertFalse(b.getLineas().get(1).isAlcanza());  // 2,000 < 5,000
    }

    @Test
    void sinMontoAlcanzaCuandoElSaldoEsPositivoYLaAgotadaSeVe() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO GRIS", "1000", "500", "400", "500"),
                linea("274", "22-0000-0000", "CEMENTO GRIS", "1000", "1000", "1000", "0"));

        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "cemento", null, lineas, Map.of()).get(0);

        assertEquals(2, b.getLineas().size()); // la agotada NO se esconde
        assertTrue(b.getLineas().get(0).isAlcanza());  // saldo 500 > 0
        assertFalse(b.getLineas().get(1).isAlcanza()); // saldo 0
    }

    @Test
    void buscarSinCoincidenciasDevuelveListaVacia() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "11-0000-0000", "CEMENTO GRIS", "1000", "0", "0", "1000"));

        assertTrue(PresupuestoService.buscarDondePagar(
                "dietas para reuniones", null, lineas, Map.of()).isEmpty());
        // codigo de 3 digitos que no existe en la carga
        assertTrue(PresupuestoService.buscarDondePagar(
                "999", null, lineas, Map.of()).isEmpty());
        // consulta vacia
        assertTrue(PresupuestoService.buscarDondePagar(
                "   ", null, lineas, Map.of()).isEmpty());
    }

    @Test
    void ordenaRenglonesPorTotalDisponibleDescYPoneNombreDeFuente() {
        List<LineaPresupuesto> lineas = List.of(
                linea("142", "11-0000-0000", "CAMIONADAS DE MATERIAL",
                        "1000", "0", "0", "1000"),
                linea("274", "22-0000-0000", "CEMENTO PARA OBRA DE MATERIAL",
                        "5000", "0", "0", "5000"));
        Map<String, String> nombres = Map.of("22-0000-0000", "FONDO PROPIO");

        List<BusquedaPago> r = PresupuestoService.buscarDondePagar(
                "material", null, lineas, nombres);

        assertEquals(2, r.size());
        assertEquals("274", r.get(0).getRenglon()); // 5,000 > 1,000
        assertMonto("5000", r.get(0).getTotalDisponible());
        assertEquals("FONDO PROPIO", r.get(0).getLineas().get(0).getNombreFuente());
        assertEquals("142", r.get(1).getRenglon());
        assertEquals("", r.get(1).getLineas().get(0).getNombreFuente()); // sin nombre
    }

    // ------------------------------ armarDesglose ------------------------------

    @Test
    void armarDesgloseAgrupaPorProgramaYProyectoConSubtotalesAlCentavo() {
        List<LineaPresupuesto> lineas = List.of(
                lineaEstructura(1L, "01 CENTRAL", "001 PARQUE", "1000.25", "250.10", "100.05", "750.15"),
                lineaEstructura(2L, "01 CENTRAL", "001 PARQUE", "500.75", "100.05", "50.25", "400.70"),
                lineaEstructura(3L, "02 OBRAS", "002 PUENTE", "2000.00", "500.00", "300.00", "1500.00"));

        DesgloseFuente d = PresupuestoService.armarDesglose("11-0000-0000", "FONDO COMUN",
                null, lineas);

        assertEquals("11-0000-0000", d.getCodigo());
        assertEquals("FONDO COMUN", d.getNombre());
        assertEquals(2, d.getGrupos().size());
        // orden por subtotalSaldo desc: 1,500.00 (02 OBRAS) antes que 1,150.85 (01 CENTRAL)
        GrupoDesglose gObras = d.getGrupos().get(0);
        assertEquals("02 OBRAS · 002 PUENTE", gObras.getTitulo());
        assertMonto("2000.00", gObras.getSubtotalVigente());
        assertMonto("500.00", gObras.getSubtotalDevengado());
        assertMonto("1500.00", gObras.getSubtotalSaldo());
        GrupoDesglose gCentral = d.getGrupos().get(1);
        assertEquals("01 CENTRAL · 001 PARQUE", gCentral.getTitulo());
        assertMonto("1501.00", gCentral.getSubtotalVigente());  // 1,000.25 + 500.75
        assertMonto("350.15", gCentral.getSubtotalDevengado()); // 250.10 + 100.05
        assertMonto("1150.85", gCentral.getSubtotalSaldo());    // 750.15 + 400.70
        assertMonto("3501.00", d.getTotalVigente());
        assertMonto("850.15", d.getTotalDevengado());
        assertMonto("450.30", d.getTotalPagado());              // 100.05 + 50.25 + 300.00
        assertMonto("2650.85", d.getTotalSaldo());
    }

    @Test
    void armarDesgloseOrdenaLasLineasDelGrupoPorSaldoDesc() {
        List<LineaPresupuesto> lineas = List.of(
                lineaEstructura(1L, "01 CENTRAL", null, "200", "0", "0", "200"),
                lineaEstructura(2L, "01 CENTRAL", null, "900", "0", "0", "900"),
                lineaEstructura(3L, "01 CENTRAL", null, "500", "0", "0", "500"));

        GrupoDesglose g = PresupuestoService.armarDesglose("11-0000-0000", "", null, lineas)
                .getGrupos().get(0);

        assertEquals("01 CENTRAL", g.getTitulo()); // sin proyecto: solo el programa
        assertEquals(3, g.getLineas().size());
        assertMonto("900", g.getLineas().get(0).getLinea().getSaldoDisponible());
        assertMonto("500", g.getLineas().get(1).getLinea().getSaldoDisponible());
        assertMonto("200", g.getLineas().get(2).getLinea().getSaldoDisponible());
    }

    @Test
    void armarDesgloseMarcaAlcanzaContraElMontoPedido() {
        List<LineaPresupuesto> lineas = List.of(
                lineaEstructura(1L, "01 CENTRAL", null, "500", "0", "0", "500"),
                lineaEstructura(2L, "01 CENTRAL", null, "499.99", "0", "0", "499.99"));

        List<LineaDesglose> ls = PresupuestoService.armarDesglose("11-0000-0000", "",
                new BigDecimal("500"), lineas).getGrupos().get(0).getLineas();

        assertTrue(ls.get(0).isAlcanza());  // 500 >= 500
        assertFalse(ls.get(1).isAlcanza()); // 499.99 < 500
    }

    @Test
    void armarDesgloseSinMontoAlcanzaCuandoElSaldoEsPositivo() {
        List<LineaPresupuesto> lineas = List.of(
                lineaEstructura(1L, "01 CENTRAL", null, "0.01", "0", "0", "0.01"),
                lineaEstructura(2L, "01 CENTRAL", null, "0", "0", "0", "0"));

        List<LineaDesglose> ls = PresupuestoService.armarDesglose("11-0000-0000", "",
                null, lineas).getGrupos().get(0).getLineas();

        assertTrue(ls.get(0).isAlcanza());  // saldo 0.01 > 0
        assertFalse(ls.get(1).isAlcanza()); // saldo 0
    }

    @Test
    void armarDesgloseConProyectoSinProyectoUsaSoloElPrograma() {
        List<LineaPresupuesto> lineas = List.of(
                lineaEstructura(1L, "01 CENTRAL", "000 SIN PROYECTO", "100", "0", "0", "100"),
                lineaEstructura(2L, "", "000 SIN PROYECTO", "50", "0", "0", "50"));

        DesgloseFuente d = PresupuestoService.armarDesglose("11-0000-0000", "", null, lineas);

        assertEquals(2, d.getGrupos().size());
        assertEquals("01 CENTRAL", d.getGrupos().get(0).getTitulo()); // no agrega el proyecto
        assertEquals("SIN PROGRAMA", d.getGrupos().get(1).getTitulo()); // programa vacio
    }

    // -------------------------- calcularTransferencia --------------------------

    @Test
    void calcularTransferenciaValidaMueveSaldosSinTocarLasLineas() {
        LineaPresupuesto origen = lineaEstructura(1L, "01 CENTRAL", null, "1000", "0", "0", "1000");
        LineaPresupuesto destino = lineaEstructura(2L, "02 OBRAS", null, "100", "0", "0", "100");

        SimulacionTransferencia s = PresupuestoService.calcularTransferencia(
                origen, destino, new BigDecimal("250.50"));

        assertTrue(s.isValida());
        assertMonto("1000", s.getSaldoOrigenAntes());
        assertMonto("749.50", s.getSaldoOrigenDespues());
        assertMonto("100", s.getSaldoDestinoAntes());
        assertMonto("350.50", s.getSaldoDestinoDespues());
        assertEquals("Transferencia simulada: Q 250.50 de la linea 1 a la linea 2", s.getMensaje());
        // la simulacion NO muta las entidades: el cambio real se hace en SICOIN
        assertMonto("1000", origen.getSaldoDisponible());
        assertMonto("100", destino.getSaldoDisponible());
    }

    @Test
    void calcularTransferenciaInvalidaConMontoCeroNegativoONulo() {
        LineaPresupuesto origen = lineaEstructura(1L, "01", null, "1000", "0", "0", "1000");
        LineaPresupuesto destino = lineaEstructura(2L, "02", null, "100", "0", "0", "100");

        SimulacionTransferencia cero = PresupuestoService.calcularTransferencia(
                origen, destino, new BigDecimal("0"));
        assertFalse(cero.isValida());
        assertEquals("El monto debe ser mayor que cero.", cero.getMensaje());
        SimulacionTransferencia negativo = PresupuestoService.calcularTransferencia(
                origen, destino, new BigDecimal("-5"));
        assertFalse(negativo.isValida());
        assertEquals("El monto debe ser mayor que cero.", negativo.getMensaje());
        SimulacionTransferencia nulo = PresupuestoService.calcularTransferencia(
                origen, destino, null);
        assertFalse(nulo.isValida());
        assertEquals("El monto debe ser mayor que cero.", nulo.getMensaje());
        // en las invalidas los saldos "despues" son iguales a los "antes": nada se mueve
        assertMonto("1000", nulo.getSaldoOrigenDespues());
        assertMonto("100", nulo.getSaldoDestinoDespues());
    }

    @Test
    void calcularTransferenciaInvalidaCuandoElMontoSuperaElSaldoDelOrigen() {
        LineaPresupuesto origen = lineaEstructura(1L, "01", null, "1000", "0", "0", "1000");
        LineaPresupuesto destino = lineaEstructura(2L, "02", null, "100", "0", "0", "100");

        SimulacionTransferencia s = PresupuestoService.calcularTransferencia(
                origen, destino, new BigDecimal("1000.01"));

        assertFalse(s.isValida());
        assertEquals("El monto supera el saldo disponible de la linea origen", s.getMensaje());
    }

    @Test
    void calcularTransferenciaInvalidaCuandoOrigenYDestinoSonLaMismaLinea() {
        LineaPresupuesto origen = lineaEstructura(1L, "01", null, "1000", "0", "0", "1000");
        LineaPresupuesto misma = lineaEstructura(1L, "01", null, "1000", "0", "0", "1000");

        SimulacionTransferencia s = PresupuestoService.calcularTransferencia(
                origen, misma, new BigDecimal("100"));

        assertFalse(s.isValida());
        assertEquals("La linea origen y la linea destino deben ser distintas.", s.getMensaje());
    }

    // -------------------------- dinero real / caja --------------------------

    private static CuentaMonetaria cuenta(String codigo, String nuevoSaldo) {
        CuentaMonetaria c = new CuentaMonetaria();
        c.setCodigo(codigo);
        c.setNuevoSaldo(new BigDecimal(nuevoSaldo));
        c.setDescripcion(codigo);
        return c;
    }

    @Test
    void codigoFuenteDeCuentaTomaLosTresPrimerosSegmentos() {
        assertEquals("21-0101-0001",
                PresupuestoService.codigoFuenteDeCuenta("21-0101-0001-0-0-1"));
        assertEquals("31-0101-0004",
                PresupuestoService.codigoFuenteDeCuenta("31-0101-0004-329-1-2"));
        assertEquals("", PresupuestoService.codigoFuenteDeCuenta("118"));
    }

    @Test
    void dineroRealSumaSoloCuentasDeLaFuenteSinMezclarVecinas() {
        List<CuentaMonetaria> cuentas = cuentasIvaPazYVecinas();

        assertMonto("93045.91", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "21-0101-0001", "01 ACTIVIDADES CENTRALES", cuentas));
        assertMonto("6000.00", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "21-0101-0001", "19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS", cuentas));
        assertMonto("0", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "31-0101-0004", "01 ACTIVIDADES CENTRALES", cuentas));
        assertMonto("120000.00", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "31-0101-0004", "19 MOVILIDAD URBANA", cuentas));
        assertMonto("614.64", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "31-0101-0009", "01 ACTIVIDADES CENTRALES", cuentas));
        assertMonto("0", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "99-9999-9999", "01 ACTIVIDADES CENTRALES", cuentas));
        assertMonto("0", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "21-0101-0001", "", cuentas));

        Map<String, BigDecimal> porFuente = PresupuestoService.dineroRealPorFuente(cuentas);
        assertFalse(porFuente.containsKey("118"));
        assertFalse(porFuente.containsKey("21-0101-0002-0-0-1"));
        assertMonto("100.00", PresupuestoService.dineroRealDeFuenteParaPrograma(
                "21-0101-0002", "01 ACTIVIDADES CENTRALES", cuentas));
    }

    @Test
    void tipoDineroDePrograma01EsFuncionamientoYElRestoInversion() {
        assertEquals(TipoDineroCaja.FUNCIONAMIENTO,
                PresupuestoService.tipoDineroDePrograma("01 ACTIVIDADES CENTRALES"));
        assertEquals(TipoDineroCaja.INVERSION,
                PresupuestoService.tipoDineroDePrograma("19 MOVILIDAD URBANA"));
        assertEquals(TipoDineroCaja.INVERSION,
                PresupuestoService.tipoDineroDePrograma("32"));
        assertEquals(TipoDineroCaja.DESCONOCIDO,
                PresupuestoService.tipoDineroDePrograma(""));
        assertEquals(TipoDineroCaja.DESCONOCIDO,
                PresupuestoService.tipoDineroDePrograma(null));
    }

    @Test
    void clasificarTipoCuentaCajaSigueElOrdenDescripcionLuegoSegmento() {
        assertEquals(TipoDineroCaja.FUNCIONAMIENTO, PresupuestoService.clasificarTipoCuentaCaja(
                "21-0101-0001-0-0-1", "IVA-PAZ-FUNCIONAMIENTO"));
        assertEquals(TipoDineroCaja.INVERSION, PresupuestoService.clasificarTipoCuentaCaja(
                "21-0101-0001-0-0-2", "IVA-PAZ-INVERSION"));
        assertEquals(TipoDineroCaja.INVERSION, PresupuestoService.clasificarTipoCuentaCaja(
                "31-0101-0004-329-1-2", "CODEDE / INVERSION"));
        assertEquals(TipoDineroCaja.FUNCIONAMIENTO, PresupuestoService.clasificarTipoCuentaCaja(
                "21-0101-0001-0-0-1", ""));
        assertEquals(TipoDineroCaja.DESCONOCIDO, PresupuestoService.clasificarTipoCuentaCaja(
                "118", "PLAN DE PRESTACIONES"));
        assertEquals(TipoDineroCaja.DESCONOCIDO, PresupuestoService.clasificarTipoCuentaCaja(
                "201", "CUOTAS I.G.S.S."));
        assertEquals(TipoDineroCaja.DESCONOCIDO, PresupuestoService.clasificarTipoCuentaCaja(
                "301", "RETENCIONES"));
    }

    @Test
    void buscarDondePagarFiltraDineroRealPorProgramaDeLaLinea() {
        LineaPresupuesto p01 = linea("029", "21-0101-0001", "FLETES",
                "100000", "0", "0", "100000");
        p01.setPrograma("01 ACTIVIDADES CENTRALES");
        LineaPresupuesto p19 = linea("029", "21-0101-0001", "FLETES",
                "100000", "0", "0", "100000");
        p19.setPrograma("19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS");

        Map<String, BigDecimal> dinero = PresupuestoService.dineroRealPorFuenteYTipo(
                cuentasIvaPazYVecinas());
        BusquedaPago b = PresupuestoService.buscarDondePagar(
                "029", new BigDecimal("10000"), List.of(p01, p19), Map.of(), dinero).get(0);

        LineaFuente linea01 = b.getLineas().stream()
                .filter(l -> "01 ACTIVIDADES CENTRALES".equals(l.getPrograma()))
                .findFirst().orElseThrow();
        LineaFuente linea19 = b.getLineas().stream()
                .filter(l -> "19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS".equals(l.getPrograma()))
                .findFirst().orElseThrow();
        assertMonto("93045.91", linea01.getDineroReal());
        assertTrue(linea01.isAlcanzaBanco());
        assertMonto("6000.00", linea19.getDineroReal());
        assertFalse(linea19.isAlcanzaBanco());

        BusquedaPago mixto = PresupuestoService.buscarDondePagar(
                "029", new BigDecimal("10000"), List.of(p01, p19), Map.of(),
                Map.of("21-0101-0001", new BigDecimal("99045.91"))).get(0);
        assertMonto("0", porPrograma(mixto, "01 ACTIVIDADES CENTRALES").getDineroReal());
        assertMonto("0", porPrograma(mixto, "19 MOVILIDAD URBANA Y ESPACIOS PUBLICOS").getDineroReal());
    }

    private static LineaFuente porPrograma(BusquedaPago b, String programa) {
        return b.getLineas().stream()
                .filter(l -> programa.equals(l.getPrograma()))
                .findFirst().orElseThrow();
    }

    @Test
    void armarDesgloseDineroRealPorProgramaNoSumaFuncionamientoConInversion() {
        LineaPresupuesto p01 = lineaEstructura(1L, "01 ACTIVIDADES CENTRALES", null,
                "100000", "0", "0", "100000");
        p01.setFuente("21-0101-0001");
        LineaPresupuesto p19 = lineaEstructura(2L, "19 MOVILIDAD URBANA", null,
                "100000", "0", "0", "100000");
        p19.setFuente("21-0101-0001");
        List<CuentaMonetaria> cuentas = List.of(
                cuenta("21-0101-0001-0-0-1", "93045.91"),
                cuenta("21-0101-0001-0-0-2", "6000.00"));

        DesgloseFuente d = PresupuestoService.armarDesglose(
                "21-0101-0001", "", new BigDecimal("10000"), List.of(p01, p19),
                new BigDecimal("99045.91"), cuentas);

        LineaDesglose des01 = d.getGrupos().stream()
                .flatMap(g -> g.getLineas().stream())
                .filter(ld -> "01 ACTIVIDADES CENTRALES".equals(ld.getLinea().getPrograma()))
                .findFirst().orElseThrow();
        LineaDesglose des19 = d.getGrupos().stream()
                .flatMap(g -> g.getLineas().stream())
                .filter(ld -> "19 MOVILIDAD URBANA".equals(ld.getLinea().getPrograma()))
                .findFirst().orElseThrow();
        assertMonto("93045.91", des01.getDineroReal());
        assertMonto("6000.00", des19.getDineroReal());
        assertMonto("93045.91", d.getDineroRealFuncionamiento());
        assertMonto("6000.00", d.getDineroRealInversion());
    }

    private static List<CuentaMonetaria> cuentasIvaPazYVecinas() {
        return List.of(
                cuenta("21-0101-0001-0-0-1", "93045.91", "IVA-PAZ-FUNCIONAMIENTO"),
                cuenta("21-0101-0001-0-0-2", "6000.00", "IVA-PAZ-INVERSION"),
                cuenta("21-0101-0002-0-0-1", "100.00", "OTRA-FUNCIONAMIENTO"),
                cuenta("31-0101-0004-329-1-2", "120000.00", "CODEDE / INVERSION"),
                cuenta("31-0101-0009-0-0-1", "614.64", "PROPIOS-FUNCIONAMIENTO"),
                cuenta("118", "0.00", "PLAN DE PRESTACIONES"));
    }

    private static CuentaMonetaria cuenta(String codigo, String nuevoSaldo, String descripcion) {
        CuentaMonetaria c = cuenta(codigo, nuevoSaldo);
        c.setDescripcion(descripcion);
        return c;
    }

    @Test
    void agregarPorFuenteIncorporaDineroRealDelBoletin() {
        List<LineaPresupuesto> lineas = List.of(
                linea("274", "21-0101-0001", "CEMENTO", "1000", "100", "50", "900"));
        Map<String, BigDecimal> dinero = Map.of(
                "21-0101-0001", new BigDecimal("99045.91"));

        FuenteResumen f = PresupuestoService.agregarPorFuente(lineas, Map.of(), dinero).get(0);
        assertMonto("99045.91", f.getDineroReal());

        FuenteResumen tipado = PresupuestoService.agregarPorFuente(lineas, Map.of(),
                PresupuestoService.dineroRealConTipos(cuentasIvaPazYVecinas())).get(0);
        assertMonto("99045.91", tipado.getDineroReal());
        assertMonto("93045.91", tipado.getDineroRealFuncionamiento());
        assertMonto("6000.00", tipado.getDineroRealInversion());
    }

    @Test
    void armarDesgloseMarcaAlcanzaBancoContraElMonto() {
        List<LineaPresupuesto> lineas = List.of(
                lineaEstructura(1L, "01 PROG", null, "5000", "0", "0", "5000"));
        List<CuentaMonetaria> cuentas = List.of(cuenta("11-0000-0000-0-0-1", "3000"));

        DesgloseFuente d = PresupuestoService.armarDesglose(
                "11-0000-0000", "", new BigDecimal("2500"), lineas,
                new BigDecimal("3000"), cuentas);
        assertTrue(d.isAlcanzaBanco());
        assertMonto("3000", d.getDineroReal());
        assertEquals(1, d.getCuentasBanco().size());

        DesgloseFuente corto = PresupuestoService.armarDesglose(
                "11-0000-0000", "", new BigDecimal("4000"), lineas,
                new BigDecimal("3000"), cuentas);
        assertFalse(corto.isAlcanzaBanco());
    }
}
