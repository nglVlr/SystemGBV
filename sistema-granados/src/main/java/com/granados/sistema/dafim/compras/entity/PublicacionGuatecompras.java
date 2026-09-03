package com.granados.sistema.dafim.compras.entity;

import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Una publicacion NPG de Guatecompras (catalogo completo, no solo las
 * pegadas a un cheque del mes).
 */
@Entity
@Table(name = "publicaciones_guatecompras", indexes = {
        @Index(name = "idx_pub_nit", columnList = "nit"),
        @Index(name = "idx_pub_anio_mes", columnList = "anio, mes")})
public class PublicacionGuatecompras {

    private static final DateTimeFormatter FECHA_GT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String npg;

    @Column(name = "fecha_texto", length = 40)
    private String fechaTexto;

    @Column(name = "fecha_pub")
    private LocalDate fechaPub;

    private Integer anio;
    private Integer mes;

    @Column(length = 40)
    private String modalidad;

    @Column(length = 800)
    private String descripcion;

    @Column(length = 20)
    private String nit;

    @Column(length = 200)
    private String nombre;

    @Column(precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(length = 20)
    private String origen;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    public static PublicacionGuatecompras de(RegistroGuatecompras r, String origen) {
        PublicacionGuatecompras p = new PublicacionGuatecompras();
        p.aplicar(r, origen);
        return p;
    }

    public void aplicar(RegistroGuatecompras r, String origen) {
        this.npg = nn(r.getNpg());
        this.fechaTexto = nn(r.getFecha());
        LocalDate d = parsearFecha(r.getFecha());
        this.fechaPub = d;
        this.anio = d == null ? null : d.getYear();
        this.mes = d == null ? null : d.getMonthValue();
        this.modalidad = nn(r.getModalidad());
        this.descripcion = TextoUtil.corta(nn(r.getDesc()), 800);
        this.nit = nn(r.getNit());
        this.nombre = TextoUtil.corta(nn(r.getProveedor()), 200);
        this.monto = BigDecimal.valueOf(r.getMonto()).setScale(2, RoundingMode.HALF_EVEN);
        if (origen != null && !origen.isBlank()) this.origen = origen;
    }

    static LocalDate parsearFecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.strip();
        int sp = t.indexOf(' ');
        if (sp > 0) t = t.substring(0, sp);
        try {
            return LocalDate.parse(t, FECHA_GT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    private static String nn(String s) {
        return s == null ? "" : s.trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNpg() { return npg; }
    public void setNpg(String npg) { this.npg = npg; }
    public String getFechaTexto() { return fechaTexto; }
    public void setFechaTexto(String fechaTexto) { this.fechaTexto = fechaTexto; }
    public LocalDate getFechaPub() { return fechaPub; }
    public void setFechaPub(LocalDate fechaPub) { this.fechaPub = fechaPub; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime f) { this.fechaActualizacion = f; }
}
