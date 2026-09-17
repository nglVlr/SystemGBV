package com.granados.sistema.dafim.presupuesto.service;

import com.granados.sistema.config.ExclusiveJobs;
import com.granados.sistema.config.StorageService;
import com.granados.sistema.dafim.compras.repository.HistorialCompraRepository;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.entity.CargaCaja;
import com.granados.sistema.dafim.presupuesto.entity.CargaPresupuesto;
import com.granados.sistema.dafim.presupuesto.entity.CuentaMonetaria;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.repository.ApartadoRepository;
import com.granados.sistema.dafim.presupuesto.repository.CargaCajaRepository;
import com.granados.sistema.dafim.presupuesto.repository.CargaPresupuestoRepository;
import com.granados.sistema.dafim.presupuesto.repository.CuentaMonetariaRepository;
import com.granados.sistema.dafim.presupuesto.repository.FuenteFinanciamientoRepository;
import com.granados.sistema.dafim.presupuesto.repository.LineaPresupuestoRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de apartarFactura (el camino del asistente de distribucion) con
 * los repositorios mockeados, siguiendo el patron de AgregarBancoTest:
 * misma validacion que el apartado manual y, ademas, la trazabilidad de la
 * factura SAT y el grupo de la clasificacion.
 */
@ExtendWith(MockitoExtension.class)
class ApartarFacturaTest {

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
    void guardaTrazabilidadDeFacturaYGrupoConBancoEnCero() {
        escenarioBasico();
        when(apartados.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Apartado a = servicio.apartarFactura(10L, "  Pago de fletes del mercado  ",
                new BigDecimal("5000"), 777L, "INFRAESTRUCTURA", "ana");

        assertEquals(777L, a.getFacturaId());
        assertEquals("INFRAESTRUCTURA", a.getGrupo());
        assertEquals("Pago de fletes del mercado", a.getConcepto()); // limpio y recortado
        assertMonto("5000.00", a.getMontoPresupuesto());
        assertMonto("0", a.getMontoBanco()); // la distribucion no aparta efectivo
        assertEquals(Apartado.EST_ACTIVO, a.getEstado());
        assertEquals("ana", a.getUsuario());
        assertEquals(2026, a.getAnio());
        assertEquals(10L, a.getLineaId());
        assertEquals("142", a.getRenglon());
        assertEquals("11-0000-0000", a.getFuente());
        verify(lineas).lockById(10L);
        verify(apartados).save(any());
    }

    @Test
    void rechazaCuandoElMontoSuperaElSaldoLibre() {
        escenarioBasico();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.apartarFactura(10L, "x", new BigDecimal("20000.01"),
                        1L, "OTROS", "ana"));
        assertTrue(e.getMessage().contains("presupuesto"));
        verify(apartados, never()).save(any());
    }

    @Test
    void rechazaCuandoNoHayCargaActiva() {
        when(cargas.findTopByEstadoOrderByFechaCargaDesc(CargaPresupuesto.EST_ACTIVA))
                .thenReturn(Optional.empty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.apartarFactura(10L, "x", new BigDecimal("100"),
                        1L, "OTROS", "ana"));
        assertTrue(e.getMessage().contains("No hay presupuesto"));
        verify(apartados, never()).save(any());
    }

    @Test
    void rechazaUnaLineaQueNoEsDeLaCargaActiva() {
        CargaPresupuesto carga = new CargaPresupuesto();
        carga.setId(1L);
        carga.setAnio(2026);
        carga.setEstado(CargaPresupuesto.EST_ACTIVA);
        LineaPresupuesto ajena = new LineaPresupuesto();
        ajena.setId(10L);
        ajena.setCargaId(99L);
        when(cargas.findTopByEstadoOrderByFechaCargaDesc(CargaPresupuesto.EST_ACTIVA))
                .thenReturn(Optional.of(carga));
        when(lineas.findById(10L)).thenReturn(Optional.of(ajena));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.apartarFactura(10L, "x", new BigDecimal("100"),
                        1L, "OTROS", "ana"));
        assertTrue(e.getMessage().contains("No se encontro la linea"));
        verify(apartados, never()).save(any());
    }

    @Test
    void facturaYGrupoSonOpcionalesYQuedanNulos() {
        escenarioBasico();
        when(apartados.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Apartado a = servicio.apartarFactura(10L, "Fletes", new BigDecimal("100"),
                null, null, "ana");

        assertNull(a.getFacturaId());
        assertNull(a.getGrupo());
        assertEquals(Apartado.EST_ACTIVO, a.getEstado());
    }

    /** Carga activa con una linea 142 de saldo 20,000 y boletin con 10,000 de caja. */
    private LineaPresupuesto escenarioBasico() {
        CargaPresupuesto carga = new CargaPresupuesto();
        carga.setId(1L);
        carga.setAnio(2026);
        carga.setEstado(CargaPresupuesto.EST_ACTIVA);

        LineaPresupuesto linea = new LineaPresupuesto();
        linea.setId(10L);
        linea.setCargaId(1L);
        linea.setRenglon("142");
        linea.setFuente("11-0000-0000");
        linea.setActividadObra("");
        linea.setPrograma("01 ACTIVIDADES CENTRALES");
        linea.setProyecto("000 SIN PROYECTO");
        linea.setDescripcion("FLETES");
        linea.setSaldoDisponible(new BigDecimal("20000"));

        CargaCaja caja = new CargaCaja();
        caja.setId(2L);
        caja.setEstado(CargaCaja.EST_ACTIVA);

        CuentaMonetaria cuenta = new CuentaMonetaria();
        cuenta.setCodigo("11-0000-0000");
        cuenta.setTipo("FUNCIONAMIENTO");
        cuenta.setNuevoSaldo(new BigDecimal("10000"));

        when(cargas.findTopByEstadoOrderByFechaCargaDesc(CargaPresupuesto.EST_ACTIVA))
                .thenReturn(Optional.of(carga));
        when(lineas.findById(10L)).thenReturn(Optional.of(linea));
        when(lineas.lockById(10L)).thenReturn(Optional.of(linea));
        when(lineas.findByCargaId(1L)).thenReturn(List.of(linea));
        when(apartados.findByEstadoOrderByFechaDesc(Apartado.EST_ACTIVO))
                .thenReturn(List.of());
        when(cargasCaja.findTopByEstadoOrderByFechaCargaDesc(CargaCaja.EST_ACTIVA))
                .thenReturn(Optional.of(caja));
        when(cuentasCaja.findByCargaId(2L)).thenReturn(List.of(cuenta));
        when(fuentes.findById("11-0000-0000")).thenReturn(Optional.empty());
        return linea;
    }

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " pero fue " + actual);
    }
}
