package com.granados.sistema.dafim.paquetes.web;

import com.granados.sistema.dafim.paquetes.entity.FacturaSat;
import com.granados.sistema.dafim.paquetes.entity.LineaPaquete;
import com.granados.sistema.dafim.paquetes.entity.PaqueteFacturas;
import com.granados.sistema.dafim.paquetes.service.PaquetesService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Modulo de paquetes de facturas SAT.
 *
 * Rutas bajo /dafim/paquetes:
 *   GET  ""                        meses procesados
 *   GET  /buscar                   busqueda global por DTE
 *   GET  /procesar                 formulario del mes
 *   POST /procesar                 procesa Excel + PDFs y guarda en BD
 *   GET  /{anio}/{mes}             detalle del mes
 *   GET  /paquete/{id}/imprimir    PDF unido del paquete, en orden
 *   GET  /factura/{id}/pdf         PDF individual de una factura
 *   POST /linea/{id}/asignar       asignacion manual
 *   POST /linea/{id}/quitar        quitar asignacion
 *   POST /{anio}/{mes}/eliminar    borrar el mes completo
 */
@Controller
@RequestMapping("/dafim/paquetes")
public class PaquetesFacturasController {

    private final PaquetesService servicio;

    public PaquetesFacturasController(PaquetesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String indice(Model model) {
        model.addAttribute("meses", servicio.mesesProcesados());
        return "dafim/paquetes/index";
    }

    @GetMapping("/buscar")
    public String buscar(@RequestParam(value = "dte", required = false) String dte, Model model) {
        model.addAttribute("dte", dte == null ? "" : dte);
        if (dte != null && !dte.isBlank()) {
            model.addAttribute("resultados", servicio.buscarPorDte(dte.trim()));
        }
        return "dafim/paquetes/buscar";
    }

    @GetMapping("/procesar")
    public String procesarForm(Model model) {
        LocalDate hoy = LocalDate.now();
        model.addAttribute("anioActual", hoy.getYear());
        model.addAttribute("mesActual", hoy.getMonthValue());
        return "dafim/paquetes/procesar";
    }

    @PostMapping("/procesar")
    public String procesar(@RequestParam int anio,
                           @RequestParam int mes,
                           @RequestParam("excel") MultipartFile excel,
                           @RequestParam("pdfs") List<MultipartFile> pdfs,
                           RedirectAttributes flash) {
        if (excel == null || excel.isEmpty()) {
            flash.addFlashAttribute("error", "Falta el Excel de paquetes.");
            return "redirect:/dafim/paquetes/procesar";
        }
        try {
            List<PaquetesService.PdfSubido> archivos = expandir(pdfs);
            if (archivos.isEmpty()) {
                flash.addFlashAttribute("error",
                        "No subiste ninguna factura PDF (puede ser un ZIP con todas).");
                return "redirect:/dafim/paquetes/procesar";
            }
            PaquetesService.ReporteProceso rep = servicio.procesarMes(
                    anio, mes, excel.getInputStream(), archivos);
            StringBuilder msj = new StringBuilder();
            msj.append("Mes procesado: ").append(rep.paquetes).append(" paquetes, ")
                    .append(rep.asignadas).append(" de ").append(rep.lineas)
                    .append(" lineas con factura.");
            if (rep.lineasPendientes > 0) {
                msj.append(' ').append(rep.lineasPendientes)
                        .append(" lineas quedaron pendientes.");
            }
            if (rep.sinPaquete > 0) {
                msj.append(' ').append(rep.sinPaquete)
                        .append(" facturas quedaron sin paquete.");
            }
            if (!rep.yaEnBd.isEmpty()) {
                msj.append(" Rechazadas por ya estar usadas: ")
                        .append(String.join(", ", rep.yaEnBd)).append('.');
            }
            if (!rep.repetidas.isEmpty()) {
                msj.append(" Repetidas en el lote: ")
                        .append(String.join(", ", rep.repetidas)).append('.');
            }
            if (!rep.conError.isEmpty()) {
                msj.append(" Con error de lectura: ")
                        .append(String.join("; ", rep.conError)).append('.');
            }
            flash.addFlashAttribute("exito", msj.toString());
            return "redirect:/dafim/paquetes/" + anio + "/" + mes;
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/dafim/paquetes/procesar";
        } catch (Exception e) {
            flash.addFlashAttribute("error",
                    "No se pudo procesar el mes. Verifica que el Excel y las facturas PDF sean los correctos.");
            return "redirect:/dafim/paquetes/procesar";
        }
    }

    /** PDFs sueltos o un ZIP con todos: se expande a la lista final. */
    private static List<PaquetesService.PdfSubido> expandir(
            List<MultipartFile> subidos) throws Exception {
        List<PaquetesService.PdfSubido> out = new ArrayList<>();
        if (subidos == null) return out;
        for (MultipartFile mf : subidos) {
            if (mf == null || mf.isEmpty()) continue;
            String nombre = mf.getOriginalFilename() == null
                    ? "archivo" : mf.getOriginalFilename();
            String bajo = nombre.toLowerCase(Locale.ROOT);
            if (bajo.endsWith(".zip")) {
                try (ZipInputStream zip = new ZipInputStream(mf.getInputStream())) {
                    ZipEntry entrada;
                    while ((entrada = zip.getNextEntry()) != null) {
                        if (entrada.isDirectory()) continue;
                        String n = entrada.getName();
                        int barra = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
                        if (barra >= 0) n = n.substring(barra + 1);
                        if (!n.toLowerCase(Locale.ROOT).endsWith(".pdf")
                                || n.startsWith(".")) continue;
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        zip.transferTo(bos);
                        out.add(new PaquetesService.PdfSubido(n, bos.toByteArray()));
                    }
                }
            } else if (bajo.endsWith(".pdf")) {
                out.add(new PaquetesService.PdfSubido(nombre, mf.getBytes()));
            }
        }
        return out;
    }

    @GetMapping("/{anio}/{mes}")
    public String mes(@PathVariable int anio, @PathVariable int mes, Model model,
                      RedirectAttributes flash) {
        List<PaqueteFacturas> ps = servicio.paquetesDelMes(anio, mes);
        if (ps.isEmpty()) {
            flash.addFlashAttribute("error",
                    "El mes " + mes + "/" + anio + " no esta procesado.");
            return "redirect:/dafim/paquetes";
        }
        Map<Long, List<LineaPaquete>> lineasPor = new LinkedHashMap<>();
        double totalGeneral = 0;
        Map<Long, Double> sumaFacturas = new LinkedHashMap<>();
        int pendientes = 0;
        for (PaqueteFacturas p : ps) {
            List<LineaPaquete> ls = servicio.lineasDe(p.getId());
            lineasPor.put(p.getId(), ls);
            double suma = 0;
            for (LineaPaquete l : ls) {
                if (l.getFactura() != null) suma += l.getFactura().getMonto();
                else pendientes++;
            }
            sumaFacturas.put(p.getId(), suma);
            totalGeneral += suma;
        }
        model.addAttribute("anio", anio);
        model.addAttribute("mes", mes);
        model.addAttribute("paquetes", ps);
        model.addAttribute("lineasPor", lineasPor);
        model.addAttribute("sumaFacturas", sumaFacturas);
        model.addAttribute("totalGeneral", totalGeneral);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("libres", servicio.facturasLibresDelMes(anio, mes));
        return "dafim/paquetes/mes";
    }

    @GetMapping("/paquete/{id}/imprimir")
    public Object imprimir(@PathVariable Long id, RedirectAttributes flash) {
        Optional<PaqueteFacturas> op = servicio.paquete(id);
        if (op.isEmpty()) {
            flash.addFlashAttribute("error", "Paquete no encontrado.");
            return "redirect:/dafim/paquetes";
        }
        try {
            byte[] pdf = servicio.imprimirPaquete(id);
            PaqueteFacturas p = op.get();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=paquete-" + p.getNumero() + "-"
                                    + p.getMes() + "-" + p.getAnio() + ".pdf")
                    .body(pdf);
        } catch (Exception e) {
            flash.addFlashAttribute("error",
                    "No se pudo armar el PDF del paquete: " + e.getMessage());
            PaqueteFacturas p = op.get();
            return "redirect:/dafim/paquetes/" + p.getAnio() + "/" + p.getMes();
        }
    }

    @GetMapping("/factura/{id}/pdf")
    public Object pdfFactura(@PathVariable Long id, RedirectAttributes flash) {
        Optional<byte[]> pdf = servicio.pdfDeFactura(id);
        if (pdf.isEmpty()) {
            flash.addFlashAttribute("error", "No se hallo el PDF de esa factura.");
            return "redirect:/dafim/paquetes";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=factura-" + id + ".pdf")
                .body(pdf.get());
    }

    @PostMapping("/linea/{id}/asignar")
    public String asignar(@PathVariable Long id, @RequestParam Long facturaId,
                          @RequestParam int anio, @RequestParam int mes,
                          RedirectAttributes flash) {
        String error = servicio.asignarManual(id, facturaId);
        if (error.isEmpty()) {
            flash.addFlashAttribute("exito", "Factura asignada a la linea.");
        } else {
            flash.addFlashAttribute("error", error);
        }
        return "redirect:/dafim/paquetes/" + anio + "/" + mes;
    }

    @PostMapping("/linea/{id}/quitar")
    public String quitar(@PathVariable Long id,
                         @RequestParam int anio, @RequestParam int mes,
                         RedirectAttributes flash) {
        servicio.quitarAsignacion(id);
        flash.addFlashAttribute("exito", "Asignacion quitada.");
        return "redirect:/dafim/paquetes/" + anio + "/" + mes;
    }

    @PostMapping("/{anio}/{mes}/eliminar")
    public String eliminar(@PathVariable int anio, @PathVariable int mes,
                           RedirectAttributes flash) {
        servicio.eliminarMes(anio, mes);
        flash.addFlashAttribute("exito", "Mes " + mes + "/" + anio
                + " eliminado: paquetes, lineas y facturas con sus PDFs.");
        return "redirect:/dafim/paquetes";
    }
}
