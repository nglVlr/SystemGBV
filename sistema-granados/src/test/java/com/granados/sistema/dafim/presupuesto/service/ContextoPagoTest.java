package com.granados.sistema.dafim.presupuesto.service;

import com.granados.sistema.config.ExclusiveJobs;
import com.granados.sistema.config.StorageService;
import com.granados.sistema.dafim.compras.repository.HistorialCompraRepository;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.entity.CargaCaja;
import com.granados.sistema.dafim.presupuesto.entity.CargaPresupuesto;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.repository.ApartadoRepository;
import com.granados.sistema.dafim.presupuesto.repository.CargaCajaRepository;
import com.granados.sistema.dafim.presupuesto.repository.CargaPresupuestoRepository;
import com.granados.sistema.dafim.presupuesto.repository.CuentaMonetariaRepository;
import com.granados.sistema.dafim.presupuesto.repository.FuenteFinanciamientoRepository;
import com.granados.sistema.dafim.presupuesto.repository.LineaPresupuestoRepository;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.BusquedaPago;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.ContextoPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guardia de la extraccion de contextoPago: dondePagar (instancia) ahora
 * delega en la foto precargada y debe comportarse igual que antes.
 */
@ExtendWith(MockitoExtension.class)
class ContextoPagoTest {

    @Mock CargaPresupuestoRepository cargas;
    @Mock LineaPresupuestoRepository lineas;
    @Mock FuenteFinanciamientoRepository fuentes;
    @Mock HistorialCompraRepository historial;
    @Mock CargaCajaRepository cargasCaja;
    @Mock CuentaMonetariaRepository cuentasCaja;
    @Mock ApartadoRepository apartados;
    @Mock StorageService storage;
    @Mock ExclusiveJobs jobs;
    @Mock PlatformTransactionManager txManager;

    PresupuestoService servicio;

    @BeforeEach
    void armar() {
        servicio = new PresupuestoService(cargas, lineas, fuentes, historial,
                cargasCaja, cuentasCaja, apartados, storage, jobs, txManager);
    }

    @Test
    void dondePagarSigueBuscandoIgualTrasLaExtraccion() {
        LineaPresupuesto linea = new LineaPresupuesto();
        linea.setId(10L);
        linea.setCargaId(1L);
        linea.setRenglon("142");
        linea.setFuente("11-0000-0000");
        linea.setDescripcion("FLETES");
        linea.setSaldoDisponible(new BigDecimal("50000"));
        escenarioCon(linea);

        List<BusquedaPago> r = servicio.dondePagar("142", new BigDecimal("20000"));

        assertEquals(1, r.size());
        assertEquals("142", r.get(0).getRenglon());
        assertMonto("50000", r.get(0).getTotalDisponible());
        assertTrue(r.get(0).getLineas().get(0).isAlcanza());
    }

    @Test
    void dondePagarConConsultaVaciaNoTocaLaBaseDeDatos() {
        assertTrue(servicio.dondePagar("   ", null).isEmpty());
        assertTrue(servicio.dondePagar(null, null).isEmpty());
        verifyNoInteractions(cargas, lineas, fuentes, apartados, cargasCaja, cuentasCaja);
    }

    @Test
    void dondePagarSinCargaActivaDevuelveVacio() {
        when(cargas.findTopByEstadoOrderByFechaCargaDesc(CargaPresupuesto.EST_ACTIVA))
                .thenReturn(Optional.empty());

        assertTrue(servicio.dondePagar("142", null).isEmpty());
        assertTrue(servicio.contextoPago().isEmpty());
    }

    @Test
    void contextoPagoPrecargadoBuscaVariasVecesSinRecargar() {
        LineaPresupuesto linea = new LineaPresupuesto();
        linea.setId(10L);
        linea.setCargaId(1L);
        linea.setRenglon("142");
        linea.setFuente("11-0000-0000");
        linea.setDescripcion("FLETES");
        linea.setSaldoDisponible(new BigDecimal("50000"));
        escenarioCon(linea);

        ContextoPago ctx = servicio.contextoPago().orElseThrow();

        assertEquals(1, ctx.getLineas().size());
        assertEquals(1, ctx.buscar("142", new BigDecimal("20000")).size());
        assertEquals(1, ctx.buscar("fletes", null).size());
        assertTrue(ctx.buscar("999", null).isEmpty());
    }

    /** Carga activa con la linea dada, sin apartados y sin boletin de caja. */
    private void escenarioCon(LineaPresupuesto linea) {
        CargaPresupuesto carga = new CargaPresupuesto();
        carga.setId(1L);
        carga.setAnio(2026);
        carga.setEstado(CargaPresupuesto.EST_ACTIVA);
        when(cargas.findTopByEstadoOrderByFechaCargaDesc(CargaPresupuesto.EST_ACTIVA))
                .thenReturn(Optional.of(carga));
        when(lineas.findByCargaId(1L)).thenReturn(List.of(linea));
        when(fuentes.findAllByOrderByCodigo()).thenReturn(List.of());
        when(apartados.findByEstadoOrderByFechaDesc(Apartado.EST_ACTIVO))
                .thenReturn(List.of());
        when(cargasCaja.findTopByEstadoOrderByFechaCargaDesc(CargaCaja.EST_ACTIVA))
                .thenReturn(Optional.empty());
    }

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " pero fue " + actual);
    }
}
