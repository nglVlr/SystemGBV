package com.granados.sistema.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Permiso o licencia de un empleado. Puede ser por dias (fechaInicio a
 * fechaFin) o por horas dentro de un mismo dia (horaInicio a horaFin).
 *
 * Tipos usados en la practica municipal:
 *   IGSS        cita o suspension del IGSS
 *   PERSONAL    asunto personal
 *   VACACIONES  goce de vacaciones
 *   LUTO        fallecimiento de familiar
 *   MATERNIDAD  pre y post natal / paternidad
 *   ESTUDIO     examenes o diligencias academicas
 *   COMISION    comision oficial (no descuenta)
 *   OTRO        cualquier otro caso
 */
@Entity
@Table(name = "rrhh_permisos", indexes = {
        @Index(name = "idx_permisos_estado", columnList = "estado"),
        @Index(name = "idx_permisos_empleado", columnList = "empleado_id"),
        @Index(name = "idx_permisos_fechas", columnList = "empleado_id, fecha_inicio, fecha_fin")})
public class Permiso {

    public static final String EST_SOLICITADO = "SOLICITADO";
    public static final String EST_APROBADO = "APROBADO";
    public static final String EST_RECHAZADO = "RECHAZADO";

    public static final java.util.Set<String> TIPOS = java.util.Set.of(
            "IGSS", "PERSONAL", "VACACIONES", "LUTO",
            "MATERNIDAD", "ESTUDIO", "COMISION", "OTRO");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    @Column(nullable = false, length = 15)
    private String tipo = "PERSONAL";

    /** true = con goce de salario, false = sin goce. */
    @Column(name = "con_goce", nullable = false)
    private boolean conGoce = true;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    /** Solo para permisos por horas (mismo dia). */
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(length = 300)
    private String motivo = "";

    @Column(nullable = false, length = 12)
    private String estado = EST_SOLICITADO;

    @Column(length = 300)
    private String observaciones = "";

    @Column(name = "solicitado_en", nullable = false)
    private LocalDateTime solicitadoEn = LocalDateTime.now();

    /** Username de quien aprobo o rechazo. */
    @Column(name = "resuelto_por", length = 50)
    private String resueltoPor = "";

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    /** Dias calendario que abarca (inclusive). Para horas devuelve 1. */
    public long getDias() {
        if (fechaInicio == null || fechaFin == null) return 0;
        return ChronoUnit.DAYS.between(fechaInicio, fechaFin) + 1;
    }

    public boolean isPorHoras() {
        return horaInicio != null && horaFin != null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public boolean isConGoce() { return conGoce; }
    public void setConGoce(boolean conGoce) { this.conGoce = conGoce; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public LocalDateTime getSolicitadoEn() { return solicitadoEn; }
    public void setSolicitadoEn(LocalDateTime solicitadoEn) { this.solicitadoEn = solicitadoEn; }
    public String getResueltoPor() { return resueltoPor; }
    public void setResueltoPor(String resueltoPor) { this.resueltoPor = resueltoPor; }
    public LocalDateTime getResueltoEn() { return resueltoEn; }
    public void setResueltoEn(LocalDateTime resueltoEn) { this.resueltoEn = resueltoEn; }
}
