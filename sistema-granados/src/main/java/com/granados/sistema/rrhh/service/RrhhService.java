package com.granados.sistema.rrhh.service;

import com.granados.sistema.rrhh.entity.Empleado;
import com.granados.sistema.rrhh.entity.Permiso;
import com.granados.sistema.rrhh.entity.PermisoAdjunto;
import com.granados.sistema.rrhh.repository.EmpleadoRepository;
import com.granados.sistema.rrhh.repository.PermisoAdjuntoRepository;
import com.granados.sistema.rrhh.repository.PermisoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Logica del modulo de Recursos Humanos (empleados y permisos). */
@Service
@Transactional(readOnly = true)
public class RrhhService {

    private final EmpleadoRepository empleados;
    private final PermisoRepository permisos;
    private final PermisoAdjuntoRepository adjuntos;

    public RrhhService(EmpleadoRepository empleados, PermisoRepository permisos,
                       PermisoAdjuntoRepository adjuntos) {
        this.empleados = empleados;
        this.permisos = permisos;
        this.adjuntos = adjuntos;
    }

    // ------------------------------ empleados ------------------------------

    public List<Empleado> todosLosEmpleados() {
        return empleados.findAllByOrderByActivoDescNombreAsc();
    }

    public List<Empleado> empleadosActivos() {
        return empleados.findByActivoTrueOrderByNombreAsc();
    }

    public Optional<Empleado> empleado(Long id) {
        return empleados.findById(id);
    }

    @Transactional
    public Empleado guardarEmpleado(Empleado e) {
        return empleados.save(e);
    }

    @Transactional
    public void alternarActivo(Long id) {
        empleados.findById(id).ifPresent(e -> e.setActivo(!e.isActivo()));
    }

    // ------------------------------- permisos ------------------------------

    public List<Permiso> permisos(String estado) {
        if (estado == null || estado.isBlank() || "TODOS".equals(estado)) {
            return permisos.findAllByOrderBySolicitadoEnDesc();
        }
        return permisos.findByEstadoOrderBySolicitadoEnDesc(estado);
    }

    public Optional<Permiso> permiso(Long id) {
        return permisos.findById(id);
    }

    @Transactional
    public Permiso solicitar(Permiso p) {
        if (p.getEmpleado() == null || p.getEmpleado().getId() == null) {
            throw new IllegalArgumentException(
                    "Solo se puede registrar permiso de personal activo.");
        }
        Empleado emp = empleados.lockById(p.getEmpleado().getId()).orElseThrow(
                () -> new IllegalArgumentException("No se encontro el empleado."));
        if (!emp.isActivo()) {
            throw new IllegalArgumentException(
                    "Solo se puede registrar permiso de personal activo.");
        }
        p.setEmpleado(emp);
        if (!Permiso.TIPOS.contains(p.getTipo())) {
            throw new IllegalArgumentException("Tipo de permiso no reconocido.");
        }
        List<Permiso> choque = permisos.solapes(
                emp.getId(), p.getFechaInicio(), p.getFechaFin());
        if (!choque.isEmpty()) {
            Permiso otro = choque.get(0);
            throw new IllegalArgumentException(
                    "Ese empleado ya tiene un permiso " + otro.getEstado().toLowerCase()
                    + " del " + otro.getFechaInicio() + " al " + otro.getFechaFin()
                    + ". Revisa la bandeja antes de duplicar.");
        }
        p.setEstado(Permiso.EST_SOLICITADO);
        p.setSolicitadoEn(LocalDateTime.now());
        return permisos.save(p);
    }

    @Transactional
    public boolean resolver(Long id, boolean aprobar, String username, String observaciones) {
        Optional<Permiso> op = permisos.lockById(id);
        if (op.isEmpty()) return false;
        Permiso p = op.get();
        if (!Permiso.EST_SOLICITADO.equals(p.getEstado())) {
            throw new IllegalArgumentException(
                    "Ese permiso ya esta " + p.getEstado().toLowerCase()
                    + ". Otro usuario pudo haberlo resuelto.");
        }
        if (!aprobar && (observaciones == null || observaciones.isBlank())) {
            throw new IllegalArgumentException(
                    "Para rechazar indica el motivo en observaciones.");
        }
        p.setEstado(aprobar ? Permiso.EST_APROBADO : Permiso.EST_RECHAZADO);
        p.setResueltoPor(username == null ? "" : username);
        p.setResueltoEn(LocalDateTime.now());
        if (observaciones != null && !observaciones.isBlank()) {
            p.setObservaciones(observaciones.strip());
        }
        return true;
    }

    /** Dias APROBADOS del empleado en el anio (informativo, no bloquea). */
    public long diasAprobadosEnAnio(Long empleadoId, int anio) {
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);
        return permisos.findByEmpleadoIdAndEstadoAndFechaInicioBetween(
                        empleadoId, Permiso.EST_APROBADO, desde, hasta)
                .stream().mapToLong(Permiso::getDias).sum();
    }

    // ------------------------------ dashboard ------------------------------

    public long totalEmpleadosActivos() { return empleados.countByActivoTrue(); }

    public long pendientes() { return permisos.countByEstado(Permiso.EST_SOLICITADO); }

    /**
     * Bandeja con filtros combinables: estado, empleado, tipo y rango de
     * fechas de inicio. Cualquier filtro vacio se ignora.
     */
    public List<Permiso> permisosFiltrados(String estado, Long empleadoId,
                                           String tipo, LocalDate desde,
                                           LocalDate hasta) {
        String est = (estado == null || estado.isBlank() || "TODOS".equals(estado))
                ? null : estado;
        String tip = (tipo == null || tipo.isBlank() || "TODOS".equals(tipo))
                ? null : tipo;
        return permisos.filtrar(est, empleadoId, tip, desde, hasta);
    }

    /** Personal filtrado por texto (nombre, cargo o dependencia). */
    public List<Empleado> empleadosFiltrados(String q) {
        List<Empleado> todos = todosLosEmpleados();
        if (q == null || q.isBlank()) return todos;
        String t = q.strip().toLowerCase();
        return todos.stream().filter(e ->
                e.getNombre().toLowerCase().contains(t)
                        || e.getCargo().toLowerCase().contains(t)
                        || e.getDependencia().toLowerCase().contains(t)
                        || e.getRenglon().toLowerCase().contains(t)).toList();
    }

    // --------------------- permiso escaneado (adjunto) ---------------------

    @Transactional
    public void guardarAdjunto(Long permisoId, String nombre, String tipo,
                               byte[] datos) {
        PermisoAdjunto a = adjuntos.findFirstByPermisoId(permisoId)
                .orElseGet(PermisoAdjunto::new);
        a.setPermisoId(permisoId);
        a.setNombre(nombre == null || nombre.isBlank() ? "permiso-escaneado" : nombre);
        a.setTipo(tipo == null || tipo.isBlank() ? "application/pdf" : tipo);
        a.setDatos(datos);
        adjuntos.save(a);
    }

    public Optional<PermisoAdjunto> adjuntoDe(Long permisoId) {
        return adjuntos.findFirstByPermisoId(permisoId);
    }

    public java.util.Set<Long> permisosConAdjunto() {
        return new java.util.HashSet<>(adjuntos.idsDePermisosConAdjunto());
    }

    public long aprobadosDelMes() {
        LocalDate hoy = LocalDate.now();
        return permisos.countByEstadoAndFechaInicioBetween(Permiso.EST_APROBADO,
                hoy.withDayOfMonth(1), hoy.withDayOfMonth(hoy.lengthOfMonth()));
    }
}
