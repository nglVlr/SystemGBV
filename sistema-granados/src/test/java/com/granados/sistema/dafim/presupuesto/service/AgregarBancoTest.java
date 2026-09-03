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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgregarBancoTest {

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
    void agregarBancoSumaEfectivoAlActivoSinBanco() {
        Fixture f = escenarioActivoSinBanco();
        when(apartados.save(f.objetivo)).thenReturn(f.objetivo);

        Apartado r = servicio.agregarBanco(7L, new BigDecimal("2000"), "ana");

        assertMonto("2000", r.getMontoBanco());
        assertEquals(Apartado.EST_ACTIVO, r.getEstado());
        assertNotNull(r.getFechaCambio());
        verify(apartados).lockById(7L);
        verify(apartados).save(f.objetivo);
    }

    @Test
    void agregarBancoRechazaSiNoEstaActivo() {
        Apartado usado = apartado(7L, Apartado.EST_USADO, "20000", "0");
        when(apartados.lockById(7L)).thenReturn(Optional.of(usado));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.agregarBanco(7L, new BigDecimal("2000"), "ana"));
        assertTrue(e.getMessage().contains("ACTIVO"));
        verify(apartados, never()).save(any());
        assertMonto("0", usado.getMontoBanco());
    }

    @Test
    void agregarBancoRechazaSiYaTeniaBanco() {
        Apartado conBanco = apartado(7L, Apartado.EST_ACTIVO, "20000", "1500");
        when(apartados.lockById(7L)).thenReturn(Optional.of(conBanco));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.agregarBanco(7L, new BigDecimal("2000"), "ana"));
        assertTrue(e.getMessage().toLowerCase().contains("banco"));
        verify(apartados, never()).save(any());
        assertMonto("1500", conBanco.getMontoBanco());
    }

    @Test
    void agregarBancoRechazaSiSuperaElLibreDeLaFuente() {
        escenarioActivoSinBanco();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.agregarBanco(7L, new BigDecimal("2000.01"), "ana"));
        assertTrue(e.getMessage().contains("banco"));
        verify(apartados, never()).save(any());
    }

    @Test
    void agregarBancoNoAlteraElMontoDePresupuesto() {
        Fixture f = escenarioActivoSinBanco();
        when(apartados.save(f.objetivo)).thenReturn(f.objetivo);

        Apartado r = servicio.agregarBanco(7L, new BigDecimal("2000"), "ana");

        assertMonto("20000", r.getMontoPresupuesto());
        assertMonto("20000", f.objetivo.getMontoPresupuesto());
    }

    private Fixture escenarioActivoSinBanco() {
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

        Apartado objetivo = apartado(7L, Apartado.EST_ACTIVO, "20000", "0");
        objetivo.setLineaId(10L);
        objetivo.setRenglon(linea.getRenglon());
        objetivo.setFuente(linea.getFuente());
        objetivo.setActividadObra("");
        objetivo.setPrograma(linea.getPrograma());
        objetivo.setProyecto(linea.getProyecto());

        Apartado otro = apartado(8L, Apartado.EST_ACTIVO, "1000", "8000");
        otro.setLineaId(11L);
        otro.setRenglon("274");
        otro.setFuente("11-0000-0000");
        otro.setActividadObra("");
        otro.setPrograma("01 ACTIVIDADES CENTRALES");
        otro.setProyecto("000 SIN PROYECTO");

        CargaCaja caja = new CargaCaja();
        caja.setId(2L);
        caja.setEstado(CargaCaja.EST_ACTIVA);

        CuentaMonetaria cuenta = new CuentaMonetaria();
        cuenta.setCodigo("11-0000-0000");
        cuenta.setTipo("FUNCIONAMIENTO");
        cuenta.setNuevoSaldo(new BigDecimal("10000"));

        when(apartados.lockById(7L)).thenReturn(Optional.of(objetivo));
        when(cargas.findTopByEstadoOrderByFechaCargaDesc(CargaPresupuesto.EST_ACTIVA))
                .thenReturn(Optional.of(carga));
        when(lineas.findByCargaId(1L)).thenReturn(List.of(linea));
        when(apartados.findByEstadoOrderByFechaDesc(Apartado.EST_ACTIVO))
                .thenReturn(List.of(objetivo, otro));
        when(cargasCaja.findTopByEstadoOrderByFechaCargaDesc(CargaCaja.EST_ACTIVA))
                .thenReturn(Optional.of(caja));
        when(cuentasCaja.findByCargaId(2L)).thenReturn(List.of(cuenta));
        when(fuentes.findById("11-0000-0000")).thenReturn(Optional.empty());

        return new Fixture(objetivo);
    }

    private static Apartado apartado(Long id, String estado, String pres, String banco) {
        Apartado a = new Apartado();
        a.setId(id);
        a.setAnio(2026);
        a.setEstado(estado);
        a.setConcepto("fletes");
        a.setMontoPresupuesto(new BigDecimal(pres));
        a.setMontoBanco(new BigDecimal(banco));
        return a;
    }

    private static void assertMonto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                "se esperaba " + esperado + " pero fue " + actual);
    }

    private static final class Fixture {
        final Apartado objetivo;
        Fixture(Apartado objetivo) { this.objetivo = objetivo; }
    }
}
