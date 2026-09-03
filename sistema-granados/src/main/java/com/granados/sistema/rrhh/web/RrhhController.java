package com.granados.sistema.rrhh.web;

import com.granados.sistema.rrhh.entity.Empleado;
import com.granados.sistema.rrhh.entity.Permiso;
import com.granados.sistema.rrhh.service.RrhhService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.util.Optional;

/**
 * Modulo de Recursos Humanos.
 *
 * Rutas:
 *   /rrhh                           dashboard
 *   /rrhh/empleados                 lista + alta de personal
 *   /rrhh/permisos                  bandeja de permisos (filtro por estado)
 *   /rrhh/permisos/nuevo            solicitar permiso
 *   /rrhh/permisos/{id}/aprobar     aprobar
 *   /rrhh/permisos/{id}/rechazar    rechazar
 *   /rrhh/permisos/{id}/constancia  constancia imprimible
 */
@Controller
@RequestMapping("/rrhh")
public class RrhhController {

    private final RrhhService rrhh;

    /** Quien firma las constancias; se configura en application.properties. */
    @Value("${rrhh.encargada.nombre:}")
    private String encargadaNombre;

    @Value("${rrhh.encargada.cargo:Encargada de Recursos Humanos}")
    private String encargadaCargo;

    public RrhhController(RrhhService rrhh) {
        this.rrhh = rrhh;
    }

    @GetMapping
    public String inicio(Model model) {
        model.addAttribute("empleadosActivos", rrhh.totalEmpleadosActivos());
        model.addAttribute("pendientes", rrhh.pendientes());
        model.addAttribute("aprobadosMes", rrhh.aprobadosDelMes());
        return "rrhh/index";
    }

    // ============================= EMPLEADOS =============================

    @GetMapping("/empleados")
    public String empleados(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("empleados", rrhh.empleadosFiltrados(q));
        model.addAttribute("q", q == null ? "" : q);
        return "rrhh/empleados";
    }

    @PostMapping("/empleados/guardar")
    public String guardarEmpleado(@RequestParam String nombre,
                                  @RequestParam(required = false) String cargo,
                                  @RequestParam(required = false) String dependencia,
                                  @RequestParam(required = false) String renglon,
                                  @RequestParam(required = false) String dpi,
                                  @RequestParam(required = false) String fechaIngreso,
                                  RedirectAttributes flash) {
        nombre = nombre == null ? "" : nombre.strip();
        if (nombre.length() < 3) {
            flash.addFlashAttribute("error", "Escribe el nombre del empleado.");
            return "redirect:/rrhh/empleados";
        }
        Empleado e = new Empleado();
        e.setNombre(nombre);
        e.setCargo(limpio(cargo));
        e.setDependencia(limpio(dependencia));
        e.setRenglon(limpio(renglon));
        e.setDpi(limpio(dpi));
        if (fechaIngreso != null && !fechaIngreso.isBlank()) {
            try {
                e.setFechaIngreso(LocalDate.parse(fechaIngreso));
            } catch (Exception ignorada) {
                // fecha opcional: si viene mal formada se guarda sin fecha
            }
        }
        rrhh.guardarEmpleado(e);
        flash.addFlashAttribute("exito", "Empleado " + nombre + " agregado.");
        return "redirect:/rrhh/empleados";
    }

    @PostMapping("/empleados/{id}/toggle")
    public String alternar(@PathVariable Long id, RedirectAttributes flash) {
        rrhh.alternarActivo(id);
        flash.addFlashAttribute("exito", "Estado del empleado actualizado.");
        return "redirect:/rrhh/empleados";
    }

    // ============================== PERMISOS =============================

    @GetMapping("/permisos")
    public String permisos(@RequestParam(defaultValue = "TODOS") String estado,
                           @RequestParam(required = false) Long empleadoId,
                           @RequestParam(required = false) String tipo,
                           @RequestParam(required = false) String desde,
                           @RequestParam(required = false) String hasta,
                           Model model) {
        LocalDate d = fechaONulo(desde);
        LocalDate h = fechaONulo(hasta);
        model.addAttribute("permisos",
                rrhh.permisosFiltrados(estado, empleadoId, tipo, d, h));
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("tipoFiltro", tipo == null ? "TODOS" : tipo);
        model.addAttribute("empleadoFiltro", empleadoId);
        model.addAttribute("desde", desde == null ? "" : desde);
        model.addAttribute("hasta", hasta == null ? "" : hasta);
        model.addAttribute("empleados", rrhh.empleadosActivos());
        model.addAttribute("pendientes", rrhh.pendientes());
        model.addAttribute("conAdjunto", rrhh.permisosConAdjunto());
        return "rrhh/permisos";
    }

    private static LocalDate fechaONulo(String s) {
        try {
            return (s == null || s.isBlank()) ? null : LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/permisos/nuevo")
    public String permisoForm(Model model) {
        model.addAttribute("empleados", rrhh.empleadosActivos());
        model.addAttribute("hoy", LocalDate.now());
        model.addAttribute("sinPersonal", rrhh.totalEmpleadosActivos() == 0);
        return "rrhh/permiso-form";
    }

    @PostMapping("/permisos/guardar")
    public String guardarPermiso(@RequestParam Long empleadoId,
                                 @RequestParam String tipo,
                                 @RequestParam(defaultValue = "true") boolean conGoce,
                                 @RequestParam String fechaInicio,
                                 @RequestParam(required = false) String fechaFin,
                                 @RequestParam(required = false) String horaInicio,
                                 @RequestParam(required = false) String horaFin,
                                 @RequestParam(required = false) String motivo,
                                 @RequestParam(value = "escaneado", required = false)
                                 MultipartFile escaneado,
                                 RedirectAttributes flash) {
        Optional<Empleado> oe = rrhh.empleado(empleadoId);
        if (oe.isEmpty()) {
            flash.addFlashAttribute("error", "Selecciona un empleado valido.");
            return "redirect:/rrhh/permisos/nuevo";
        }
        if (!oe.get().isActivo()) {
            flash.addFlashAttribute("error",
                    "Esa persona ya no esta activa. Reactivala en Personal o elige otra.");
            return "redirect:/rrhh/permisos/nuevo";
        }
        String tipoNorm = limpio(tipo).toUpperCase();
        if (!Permiso.TIPOS.contains(tipoNorm)) {
            flash.addFlashAttribute("error", "Tipo de permiso no reconocido.");
            return "redirect:/rrhh/permisos/nuevo";
        }
        LocalDate ini;
        LocalDate fin;
        try {
            ini = LocalDate.parse(fechaInicio);
            fin = (fechaFin == null || fechaFin.isBlank())
                    ? ini : LocalDate.parse(fechaFin);
        } catch (Exception ex) {
            flash.addFlashAttribute("error", "Fechas invalidas.");
            return "redirect:/rrhh/permisos/nuevo";
        }
        if (fin.isBefore(ini)) {
            flash.addFlashAttribute("error",
                    "La fecha final no puede ser antes de la inicial.");
            return "redirect:/rrhh/permisos/nuevo";
        }
        LocalTime hi = null;
        LocalTime hf = null;
        boolean hayHoraIni = horaInicio != null && !horaInicio.isBlank();
        boolean hayHoraFin = horaFin != null && !horaFin.isBlank();
        if (hayHoraIni ^ hayHoraFin) {
            flash.addFlashAttribute("error",
                    "Si el permiso es por horas, indica hora de inicio y de fin.");
            return "redirect:/rrhh/permisos/nuevo";
        }
        if (hayHoraIni) {
            try {
                hi = LocalTime.parse(horaInicio);
                hf = LocalTime.parse(horaFin);
            } catch (Exception ignorada) {
                flash.addFlashAttribute("error", "Horas invalidas.");
                return "redirect:/rrhh/permisos/nuevo";
            }
            if (!hf.isAfter(hi)) {
                flash.addFlashAttribute("error",
                        "La hora fin debe ser despues de la hora inicio.");
                return "redirect:/rrhh/permisos/nuevo";
            }
            fin = ini;
        }
        if (escaneado != null && !escaneado.isEmpty()
                && escaneado.getSize() > 8L * 1024 * 1024) {
            flash.addFlashAttribute("error",
                    "El escaneado pesa mas de 8 MB. Reduce el PDF o la foto.");
            return "redirect:/rrhh/permisos/nuevo";
        }
        Permiso p = new Permiso();
        p.setEmpleado(oe.get());
        p.setTipo(tipoNorm);
        p.setConGoce(conGoce);
        p.setFechaInicio(ini);
        p.setFechaFin(fin);
        p.setHoraInicio(hi);
        p.setHoraFin(hf);
        p.setMotivo(limpio(motivo));
        try {
            p = rrhh.solicitar(p);
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/rrhh/permisos/nuevo";
        }
        String nota = "";
        if (escaneado != null && !escaneado.isEmpty()) {
            try {
                rrhh.guardarAdjunto(p.getId(), escaneado.getOriginalFilename(),
                        escaneado.getContentType(), escaneado.getBytes());
                nota = " con su permiso escaneado adjunto";
            } catch (Exception e) {
                nota = " (el escaneado no se pudo guardar: " + e.getMessage() + ")";
            }
        }
        flash.addFlashAttribute("exito", "Permiso registrado para "
                + oe.get().getNombre() + nota + " (queda pendiente de aprobación).");
        return "redirect:/rrhh/permisos?estado=SOLICITADO";
    }

    @PostMapping("/permisos/{id}/aprobar")
    public String aprobar(@PathVariable Long id,
                          @RequestParam(required = false) String observaciones,
                          Authentication auth, RedirectAttributes flash) {
        try {
            boolean ok = rrhh.resolver(id, true,
                    auth == null ? "" : auth.getName(), observaciones);
            flash.addFlashAttribute(ok ? "exito" : "error",
                    ok ? "Permiso aprobado. Ya puedes imprimir la constancia."
                            : "No se encontro el permiso.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rrhh/permisos?estado=SOLICITADO";
    }

    @PostMapping("/permisos/{id}/rechazar")
    public String rechazar(@PathVariable Long id,
                           @RequestParam(required = false) String observaciones,
                           Authentication auth, RedirectAttributes flash) {
        try {
            boolean ok = rrhh.resolver(id, false,
                    auth == null ? "" : auth.getName(), observaciones);
            flash.addFlashAttribute(ok ? "exito" : "error",
                    ok ? "Permiso rechazado." : "No se encontro el permiso.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rrhh/permisos?estado=SOLICITADO";
    }

    @GetMapping("/permisos/{id}/constancia")
    public String constancia(@PathVariable Long id, Model model,
                             RedirectAttributes flash) {
        Optional<Permiso> op = rrhh.permiso(id);
        if (op.isEmpty()) {
            flash.addFlashAttribute("error", "No se encontro el permiso.");
            return "redirect:/rrhh/permisos";
        }
        Permiso p = op.get();
        if (!Permiso.EST_APROBADO.equals(p.getEstado())) {
            flash.addFlashAttribute("error",
                    "La constancia solo se imprime de un permiso ya aprobado.");
            return "redirect:/rrhh/permisos";
        }
        model.addAttribute("p", p);
        model.addAttribute("diasEnAnio", rrhh.diasAprobadosEnAnio(
                p.getEmpleado().getId(), Year.now().getValue()));
        model.addAttribute("encargadaNombre", encargadaNombre == null ? "" : encargadaNombre.strip());
        model.addAttribute("encargadaCargo", encargadaCargo);
        model.addAttribute("tieneAdjunto", rrhh.adjuntoDe(id).isPresent());
        return "rrhh/constancia";
    }

    /** Descarga o muestra el permiso fisico escaneado (PDF o imagen). */
    @GetMapping("/permisos/{id}/adjunto")
    public Object adjunto(@PathVariable Long id, RedirectAttributes flash) {
        var oa = rrhh.adjuntoDe(id);
        if (oa.isEmpty()) {
            flash.addFlashAttribute("error", "Ese permiso no tiene escaneado adjunto.");
            return "redirect:/rrhh/permisos";
        }
        var a = oa.get();
        MediaType mt;
        try {
            mt = MediaType.parseMediaType(a.getTipo());
        } catch (Exception e) {
            mt = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=permiso-" + id + "-escaneado")
                .body(a.getDatos());
    }

    /** Adjuntar o reemplazar el escaneado de un permiso ya registrado. */
    @PostMapping("/permisos/{id}/adjunto")
    public String subirAdjunto(@PathVariable Long id,
                               @RequestParam("escaneado") MultipartFile escaneado,
                               RedirectAttributes flash) {
        if (rrhh.permiso(id).isEmpty()) {
            flash.addFlashAttribute("error", "No se encontró el permiso.");
            return "redirect:/rrhh/permisos";
        }
        if (escaneado == null || escaneado.isEmpty()) {
            flash.addFlashAttribute("error", "Selecciona el archivo escaneado.");
            return "redirect:/rrhh/permisos";
        }
        if (escaneado.getSize() > 8L * 1024 * 1024) {
            flash.addFlashAttribute("error",
                    "El escaneado pesa mas de 8 MB. Reduce el PDF o la foto.");
            return "redirect:/rrhh/permisos";
        }
        try {
            rrhh.guardarAdjunto(id, escaneado.getOriginalFilename(),
                    escaneado.getContentType(), escaneado.getBytes());
            flash.addFlashAttribute("exito", "Permiso escaneado guardado.");
        } catch (Exception e) {
            flash.addFlashAttribute("error",
                    "No se pudo guardar el escaneado: " + e.getMessage());
        }
        return "redirect:/rrhh/permisos";
    }

    private static String limpio(String s) {
        return s == null ? "" : s.strip();
    }
}
