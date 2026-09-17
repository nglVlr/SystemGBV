package com.granados.sistema.dafim.presupuesto.distribuir;

import com.granados.sistema.dafim.paquetes.entity.FacturaSat;
import com.granados.sistema.dafim.paquetes.repository.FacturaSatRepository;
import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.MesAnio;
import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.ResultadoAplicar;
import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.ResultadoClasificacion;
import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.Totales;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria.TipoDineroCaja;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.ContextoPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas del motor de distribucion con los repositorios y servicios
 * externos mockeados: el mapeo a lineas corre contra la busqueda REAL de
 * "donde pagar" (ContextoPago se construye a mano, como en
 * PresupuestoServiceTest) y el clasificador local es el real.
 */
@ExtendWith(MockitoExtension.class)
class DistribucionServiceTest {

    @Mock FacturaSatRepository facturas;
    @Mock PresupuestoService presupuesto;
    @Mock GeminiService gemini;

    DistribucionService servicio;

    @BeforeEach
    void armar() {
        servicio = new DistribucionService(facturas, presupuesto, gemini,
                new ClasificadorLocalService());
    }

    // ------------------------------ fixtures ------------------------------

    private static FacturaSat factura(long id, String descripcion, String emisor, double monto) {
        FacturaSat f = new FacturaSat();
        f.setId(id);
        f.setDescripcion(descripcion);
        f.setNombreEmisor(emisor);
        f.setMonto(monto);
        f.setMes(9);
        f.setAnio(2026);
        return f;
    }

    private static LineaPresupuesto linea(Long id, String renglon, String fuente, String saldo) {
        LineaPresupuesto l = new LineaPresupuesto();
        l.setId(id);
        l.setCargaId(1L);
        l.setRenglon(renglon);
        l.setFuente(fuente);
        l.setPrograma("01 ACTIVIDADES CENTRALES");
        l.setDescripcion("DESC " + renglon);
        l.setVigente(new BigDecimal(saldo));
        l.setDevengado(BigDecimal.ZERO);
        l.setPagado(BigDecimal.ZERO);
        l.setSaldoDisponible(new BigDecimal(saldo));
        return l;
    }

    private static ContextoPago contexto(List<LineaPresupuesto> lineas,
                                         Map<String, BigDecimal> dinero) {
        return new ContextoPago(lineas, Map.of(), dinero, Map.of(), Map.of(), Map.of());
    }

    private static FilaDistribucion fila(long facturaId, String descripcion, String monto) {
        return new FilaDistribucion(facturaId, descripcion, "EMISOR " + facturaId,
                new BigDecimal(monto));
    }

    /** Fila lista para aplicar: con linea, monto y la bandera de saldo en true. */
    private static FilaDistribucion filaAplicable(long facturaId, Long lineaId, String monto) {
        FilaDistribucion f = fila(facturaId, "desc " + facturaId, monto);
        f.setLineaId(lineaId);
        f.setSaldoSuficiente(true);
        f.setGrupo("OTROS");
        return f;
    }

    /** BigDecimal.equals compara escala; para importes interesa el valor. */
    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " pero fue " + actual);
    }

    // ------------------------------- carga --------------------------------

    @Test
    void cargarFilasConvierteCadaFacturaEnFila() {
        when(facturas.findByAnioAndMesOrderByIdAsc(2026, 9)).thenReturn(List.of(
                factura(5L, "Sacos de cemento", "FERRETERIA EL CLAVO", 1234.56),
                factura(6L, "Utiles escolares", "LIBRERIA LA CENTRAL", 250.00)));

        List<FilaDistribucion> filas = servicio.cargarFilas(9, 2026);

        assertEquals(2, filas.size());
        assertEquals(5L, filas.get(0).getFacturaId());
        assertEquals("Sacos de cemento", filas.get(0).getDescripcion());
        assertEquals("FERRETERIA EL CLAVO", filas.get(0).getEmisor());
        assertMonto("1234.56", filas.get(0).getMonto());
        assertEquals(6L, filas.get(1).getFacturaId());
    }

    @Test
    void mesesConFacturasTraenEtiquetaYConteo() {
        when(facturas.mesesConConteo()).thenReturn(List.of(
                new Object[]{2026, 9, 3L},
                new Object[]{2026, 8, 1L}));

        List<MesAnio> meses = servicio.mesesConFacturas();

        assertEquals(2, meses.size());
        assertEquals(2026, meses.get(0).anio());
        assertEquals(9, meses.get(0).mes());
        assertEquals("Septiembre 2026", meses.get(0).etiqueta());
        assertEquals(3, meses.get(0).facturas());
        assertEquals("Agosto 2026", meses.get(1).etiqueta());
        assertEquals(1, meses.get(1).facturas());
    }

    // --------------------------- clasificacion ----------------------------

    @Test
    void sinGeminiTodasLasFilasPasanPorElLocal() {
        when(gemini.estaDisponible()).thenReturn(false);
        List<FilaDistribucion> filas = List.of(fila(1L, "Pago de sacos de cemento", "100"));

        boolean ia = servicio.clasificarConIaOLocal(filas);

        assertFalse(ia);
        assertEquals("INFRAESTRUCTURA", filas.get(0).getGrupo());
        assertEquals("274", filas.get(0).getRenglon());
        verify(gemini, never()).clasificar(any());
    }

    @Test
    void conGeminiLasFilasSinGrupoSeCompletanConElLocal() {
        when(gemini.estaDisponible()).thenReturn(true);
        when(gemini.clasificar(any())).thenAnswer(inv -> {
            List<FilaDistribucion> fs = inv.getArgument(0);
            fs.get(0).setGrupo("SALUD");
            fs.get(0).setRenglon("263");
            fs.get(0).setExplicacion("Medicinas");
            return fs; // la fila 2 queda sin clasificar
        });
        List<FilaDistribucion> filas = List.of(
                fila(1L, "Medicamentos para el centro de salud", "100"),
                fila(2L, "Pago de sacos de cemento", "200"));

        boolean ia = servicio.clasificarConIaOLocal(filas);

        assertTrue(ia);
        assertEquals("SALUD", filas.get(0).getGrupo()); // de Gemini, no se toca
        assertEquals("263", filas.get(0).getRenglon());
        assertEquals("INFRAESTRUCTURA", filas.get(1).getGrupo()); // rellenada por el local
        assertEquals("274", filas.get(1).getRenglon());
    }

    @Test
    void cuandoGeminiFallaTodasLasFilasCaenAlLocal() {
        when(gemini.estaDisponible()).thenReturn(true);
        when(gemini.clasificar(any())).thenReturn(List.of());
        List<FilaDistribucion> filas = List.of(fila(1L, "Pago de sacos de cemento", "100"));

        boolean ia = servicio.clasificarConIaOLocal(filas);

        assertFalse(ia);
        assertEquals("INFRAESTRUCTURA", filas.get(0).getGrupo());
        assertEquals("274", filas.get(0).getRenglon());
    }

    // ------------------------------ mapeo ---------------------------------

    @Test
    void mapearPrefiereLaLineaConCajaRealAunqueTengaMenosSaldo() {
        LineaPresupuesto conMasSaldo = linea(1L, "274", "11-0000-0000", "10000");
        LineaPresupuesto conCaja = linea(2L, "274", "22-0000-0000", "5000");
        Map<String, BigDecimal> dinero = Map.of(
                PresupuestoService.claveDineroCaja("11-0000-0000", TipoDineroCaja.FUNCIONAMIENTO),
                BigDecimal.ZERO,
                PresupuestoService.claveDineroCaja("22-0000-0000", TipoDineroCaja.FUNCIONAMIENTO),
                new BigDecimal("9000"));
        when(presupuesto.contextoPago())
                .thenReturn(Optional.of(contexto(List.of(conMasSaldo, conCaja), dinero)));
        FilaDistribucion f = fila(1L, "sacos de cemento", "4000");
        f.setRenglon("274");

        servicio.mapearLineas(List.of(f));

        // las dos alcanzan con saldo; gana la que ademas tiene caja real
        assertEquals(2L, f.getLineaId());
        assertEquals("22-0000-0000", f.getFuente());
        assertTrue(f.isSaldoSuficiente());
    }

    @Test
    void mapearSinSaldoSuficienteApuntaALaDeMayorSaldoConBanderaEnFalso() {
        when(presupuesto.contextoPago()).thenReturn(Optional.of(
                contexto(List.of(linea(1L, "274", "11-0000-0000", "100")), Map.of())));
        FilaDistribucion f = fila(1L, "sacos de cemento", "500");
        f.setRenglon("274");

        servicio.mapearLineas(List.of(f));

        // mejor esfuerzo: queda apuntada para que el usuario la corrija
        assertEquals(1L, f.getLineaId());
        assertEquals("11-0000-0000", f.getFuente());
        assertFalse(f.isSaldoSuficiente());
    }

    @Test
    void mapearSinRenglonBuscaTextoLibrePorLaDescripcion() {
        when(presupuesto.contextoPago()).thenReturn(Optional.of(
                contexto(List.of(linea(7L, "274", "11-0000-0000", "1000")), Map.of())));
        FilaDistribucion f = fila(1L, "Pago de sacos de cemento", "100"); // renglon ""

        servicio.mapearLineas(List.of(f));

        assertEquals(7L, f.getLineaId());
        assertEquals("11-0000-0000", f.getFuente());
        assertTrue(f.isSaldoSuficiente());
    }

    @Test
    void mapearConRenglonAusenteDeLaCargaNoAdivinaOtro() {
        // aunque la descripcion pegaria con 274, un renglon clasificado que no
        // existe en la carga deja la fila sin mapear (el usuario elige a mano)
        when(presupuesto.contextoPago()).thenReturn(Optional.of(
                contexto(List.of(linea(7L, "274", "11-0000-0000", "1000")), Map.of())));
        FilaDistribucion f = fila(1L, "Pago de sacos de cemento", "100");
        f.setRenglon("999");

        servicio.mapearLineas(List.of(f));

        assertNull(f.getLineaId());
        assertEquals("", f.getFuente());
        assertFalse(f.isSaldoSuficiente());
    }

    @Test
    void mapearSinCargaActivaDejaLasFilasSinMapear() {
        when(presupuesto.contextoPago()).thenReturn(Optional.empty());
        FilaDistribucion f = fila(1L, "sacos de cemento", "100");
        f.setRenglon("274");

        servicio.mapearLineas(List.of(f));

        assertNull(f.getLineaId());
        assertFalse(f.isSaldoSuficiente());
    }

    // ------------------------------ totales -------------------------------

    @Test
    void totalesSumanPorGrupoYFuenteConOtrosParaLasSinGrupo() {
        FilaDistribucion a = fila(1L, "x", "100");
        a.setGrupo("EDUCACION");
        a.setFuente("11-0000-0000");
        FilaDistribucion b = fila(2L, "y", "50.25");
        b.setGrupo("EDUCACION");
        b.setFuente("11-0000-0000");
        FilaDistribucion c = fila(3L, "z", "200");
        c.setGrupo("SALUD");
        c.setFuente("22-0000-0000");
        FilaDistribucion d = fila(4L, "w", "25"); // sin grupo ni fuente

        Totales t = DistribucionService.totales(List.of(a, b, c, d));

        assertMonto("375.25", t.total());
        assertMonto("150.25", t.porGrupo().get("EDUCACION"));
        assertMonto("200", t.porGrupo().get("SALUD"));
        assertMonto("25", t.porGrupo().get("OTROS")); // la sin grupo cae en OTROS
        assertEquals(2, t.porFuente().size()); // la sin fuente no entra al mapa
        assertMonto("150.25", t.porFuente().get("11-0000-0000"));
        assertMonto("200", t.porFuente().get("22-0000-0000"));
    }

    @Test
    void totalesConListaNulaDaCeros() {
        Totales t = DistribucionService.totales(null);

        assertMonto("0", t.total());
        assertTrue(t.porGrupo().isEmpty());
        assertTrue(t.porFuente().isEmpty());
    }

    // ------------------------------ aplicar -------------------------------

    @Test
    void aplicarCreaLasValidasYReportaCadaRechazoSinFrenarElLote() {
        FilaDistribucion ok1 = filaAplicable(1L, 10L, "100");
        FilaDistribucion sinLinea = fila(2L, "sin linea", "100"); // lineaId null
        FilaDistribucion montoCero = filaAplicable(3L, 10L, "0");
        FilaDistribucion falla = filaAplicable(4L, 11L, "200");
        FilaDistribucion ok2 = filaAplicable(5L, 12L, "300");
        Apartado creado = new Apartado();
        creado.setId(1000L);
        when(presupuesto.apartarFactura(eq(10L), any(), any(), any(), any(), any()))
                .thenReturn(creado);
        when(presupuesto.apartarFactura(eq(11L), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException(
                        "El monto de presupuesto supera el saldo libre de la linea."));
        when(presupuesto.apartarFactura(eq(12L), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        ResultadoAplicar r = servicio.aplicar(List.of(ok1, sinLinea, montoCero, falla, ok2), "ana");

        assertEquals(2, r.creados());
        assertEquals(3, r.rechazados().size());
        // numeros de fila visibles (columna #, desde 1) y factura de respaldo
        assertEquals(2, r.rechazados().get(0).fila());
        assertEquals(2L, r.rechazados().get(0).facturaId());
        assertTrue(r.rechazados().get(0).motivo().contains("linea"));
        assertEquals(3, r.rechazados().get(1).fila());
        assertTrue(r.rechazados().get(1).motivo().contains("monto"));
        assertEquals(4, r.rechazados().get(2).fila());
        assertEquals(4L, r.rechazados().get(2).facturaId());
        assertTrue(r.rechazados().get(2).motivo().contains("saldo libre"));
        // el lote siguio despues del error de la fila 4
        verify(presupuesto).apartarFactura(eq(12L), any(), any(), eq(5L), any(), eq("ana"));
    }

    @Test
    void aplicarNoConfiaEnLaBanderaDeSaldoYRevalidaEnElServidor() {
        // la hoja la ve insuficiente, pero el servidor revalida y SI alcanza
        FilaDistribucion f = filaAplicable(9L, 10L, "100");
        f.setSaldoSuficiente(false);
        Apartado creado = new Apartado();
        creado.setId(1L);
        when(presupuesto.apartarFactura(eq(10L), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        ResultadoAplicar r = servicio.aplicar(List.of(f), "ana");

        assertEquals(1, r.creados());
        assertTrue(r.rechazados().isEmpty());
        verify(presupuesto).apartarFactura(eq(10L), any(), any(), eq(9L), any(), eq("ana"));
    }

    @Test
    void aplicarPasaFacturaYGrupoAlApartado() {
        FilaDistribucion f = filaAplicable(77L, 10L, "150.50");
        f.setDescripcion("  Pago de fletes del mercado  ");
        f.setGrupo("INFRAESTRUCTURA");
        Apartado creado = new Apartado();
        creado.setId(5L);
        when(presupuesto.apartarFactura(any(), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        ResultadoAplicar r = servicio.aplicar(List.of(f), "ana");

        assertEquals(1, r.creados());
        verify(presupuesto).apartarFactura(eq(10L), eq("  Pago de fletes del mercado  "),
                eq(new BigDecimal("150.50")), eq(77L), eq("INFRAESTRUCTURA"), eq("ana"));
    }

    @Test
    void aplicarConListaVaciaONulaNoTocaPresupuesto() {
        assertEquals(0, servicio.aplicar(null, "ana").creados());
        ResultadoAplicar r = servicio.aplicar(List.of(), "ana");
        assertEquals(0, r.creados());
        assertTrue(r.rechazados().isEmpty());
        verifyNoInteractions(presupuesto);
    }

    // ------------------------- pipeline completo --------------------------

    @Test
    void clasificarMesArmaFilasClasificadasMapeadasYTotales() {
        when(facturas.findByAnioAndMesOrderByIdAsc(2026, 9)).thenReturn(List.of(
                factura(1L, "Pago de sacos de cemento", "FERRETERIA", 400.0),
                factura(2L, "Utiles escolares", "LIBRERIA", 100.0)));
        when(gemini.estaDisponible()).thenReturn(false);
        when(presupuesto.contextoPago()).thenReturn(Optional.of(contexto(List.of(
                linea(7L, "274", "11-0000-0000", "1000"),
                linea(8L, "291", "11-0000-0000", "50")), Map.of())));

        ResultadoClasificacion r = servicio.clasificar(9, 2026);

        assertFalse(r.ia());
        assertEquals(2, r.filas().size());
        FilaDistribucion cemento = r.filas().get(0);
        assertEquals("INFRAESTRUCTURA", cemento.getGrupo());
        assertEquals("274", cemento.getRenglon());
        assertEquals(7L, cemento.getLineaId());
        assertTrue(cemento.isSaldoSuficiente()); // 1000 >= 400
        FilaDistribucion utiles = r.filas().get(1);
        assertEquals("EDUCACION", utiles.getGrupo());
        assertEquals("291", utiles.getRenglon());
        assertEquals(8L, utiles.getLineaId()); // mejor esfuerzo
        assertFalse(utiles.isSaldoSuficiente()); // 50 < 100
        assertMonto("500", r.totales().total());
        assertMonto("400", r.totales().porGrupo().get("INFRAESTRUCTURA"));
        assertMonto("100", r.totales().porGrupo().get("EDUCACION"));
        assertMonto("500", r.totales().porFuente().get("11-0000-0000"));
    }
}
