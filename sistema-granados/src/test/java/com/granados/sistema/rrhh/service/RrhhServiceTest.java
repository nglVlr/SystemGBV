package com.granados.sistema.rrhh.service;

import com.granados.sistema.rrhh.entity.Empleado;
import com.granados.sistema.rrhh.entity.Permiso;
import com.granados.sistema.rrhh.repository.EmpleadoRepository;
import com.granados.sistema.rrhh.repository.PermisoAdjuntoRepository;
import com.granados.sistema.rrhh.repository.PermisoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RrhhServiceTest {

    @Mock EmpleadoRepository empleados;
    @Mock PermisoRepository permisos;
    @Mock PermisoAdjuntoRepository adjuntos;

    RrhhService rrhh;

    @BeforeEach
    void armar() {
        rrhh = new RrhhService(empleados, permisos, adjuntos);
    }

    @Test
    void noRegistraPermisoDeBaja() {
        Empleado e = empleado(1L, false);
        when(empleados.lockById(1L)).thenReturn(Optional.of(e));
        Permiso p = permiso(e);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rrhh.solicitar(p));
        assertTrue(ex.getMessage().contains("activo"));
        verify(permisos, never()).save(any());
    }

    @Test
    void noRegistraSiHaySolapePendienteOAprobado() {
        Empleado e = empleado(1L, true);
        when(empleados.lockById(1L)).thenReturn(Optional.of(e));
        Permiso previo = new Permiso();
        previo.setEstado(Permiso.EST_SOLICITADO);
        previo.setFechaInicio(LocalDate.of(2026, 8, 10));
        previo.setFechaFin(LocalDate.of(2026, 8, 12));
        when(permisos.solapes(1L, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 11)))
                .thenReturn(List.of(previo));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rrhh.solicitar(permiso(e)));
        assertTrue(ex.getMessage().contains("ya tiene un permiso"));
        verify(permisos, never()).save(any());
    }

    @Test
    void rechazoSinMotivoFallaYNoCambiaEstado() {
        Permiso p = new Permiso();
        p.setEstado(Permiso.EST_SOLICITADO);
        when(permisos.lockById(9L)).thenReturn(Optional.of(p));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rrhh.resolver(9L, false, "rrhh", "  "));
        assertTrue(ex.getMessage().toLowerCase().contains("motivo"));
        assertEquals(Permiso.EST_SOLICITADO, p.getEstado());
    }

    @Test
    void noSeResuelveDosVeces() {
        Permiso p = new Permiso();
        p.setEstado(Permiso.EST_APROBADO);
        when(permisos.lockById(9L)).thenReturn(Optional.of(p));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> rrhh.resolver(9L, true, "rrhh", null));
        assertTrue(ex.getMessage().contains("ya esta"));
    }

    private static Empleado empleado(long id, boolean activo) {
        Empleado e = new Empleado();
        e.setId(id);
        e.setNombre("Ana");
        e.setActivo(activo);
        return e;
    }

    private static Permiso permiso(Empleado e) {
        Permiso p = new Permiso();
        p.setEmpleado(e);
        p.setTipo("PERSONAL");
        p.setFechaInicio(LocalDate.of(2026, 8, 11));
        p.setFechaFin(LocalDate.of(2026, 8, 11));
        return p;
    }
}
