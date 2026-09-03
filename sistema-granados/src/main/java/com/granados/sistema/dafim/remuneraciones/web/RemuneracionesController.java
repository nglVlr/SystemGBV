package com.granados.sistema.dafim.remuneraciones.web;

import com.granados.sistema.config.ExclusiveJobs;
import com.granados.sistema.config.StorageService;
import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.parser.ParserSicoin;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.remuneraciones.dto.FilaPlanilla;
import com.granados.sistema.dafim.remuneraciones.dto.PersonaRrhh;
import com.granados.sistema.dafim.remuneraciones.dto.ResultadoRemuneraciones;
import com.granados.sistema.dafim.remuneraciones.parser.ParserPlanillaPdf;
import com.granados.sistema.dafim.remuneraciones.service.ExcelGeneradorRemuneraciones;
import com.granados.sistema.dafim.remuneraciones.service.MotorRemuneracionesService;
import com.granados.sistema.rrhh.entity.Empleado;
import com.granados.sistema.rrhh.service.RrhhService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/dafim/remuneraciones")
public class RemuneracionesController {

    static final String SES_RESULTADO = "resultadoRemuneraciones";
    private static final Logger log = LoggerFactory.getLogger(RemuneracionesController.class);

    private final StorageService storage;
    private final ExclusiveJobs jobs;
    private final RrhhService rrhh;

    public RemuneracionesController(StorageService storage, ExclusiveJobs jobs,
                                    RrhhService rrhh) {
        this.storage = storage;
        this.jobs = jobs;
        this.rrhh = rrhh;
    }

    @GetMapping
    public String index() {
        return "redirect:/dafim/remuneraciones/procesar";
    }

    @GetMapping("/procesar")
    public String form(Model model, HttpSession sesion) {
        model.addAttribute("meses", Constantes.MESES_NOMBRE);
        model.addAttribute("anioActual", Year.now().getValue());
        model.addAttribute("resultado", sesion.getAttribute(SES_RESULTADO));
        return "dafim/remuneraciones/procesar";
    }

    @PostMapping("/procesar/nuevo")
    public String nuevo(HttpSession sesion) {
        sesion.removeAttribute(SES_RESULTADO);
        return "redirect:/dafim/remuneraciones/procesar";
    }

    @PostMapping("/procesar")
    public String procesar(@RequestParam int mes,
                           @RequestParam int anio,
                           @RequestParam("archivoSicoin") MultipartFile archivoSicoin,
                           @RequestParam(value = "planilla011Deposito", required = false)
                           MultipartFile planilla011Deposito,
                           @RequestParam(value = "planilla011Cheque", required = false)
                           MultipartFile planilla011Cheque,
                           @RequestParam(value = "planilla022", required = false)
                           MultipartFile planilla022,
                           HttpSession sesion,
                           RedirectAttributes flash) {
        if (mes < 1 || mes > 12) {
            flash.addFlashAttribute("error", "El mes debe estar entre 1 y 12.");
            return "redirect:/dafim/remuneraciones/procesar";
        }
        if (vacio(archivoSicoin)) {
            flash.addFlashAttribute("error",
                    "Falta el PDF de SICOIN (el mismo detalle de presupuesto ejecutado).");
            return "redirect:/dafim/remuneraciones/procesar";
        }
        try (AutoCloseable ignored = jobs.hold("remuneraciones-mes-" + anio + "-" + mes)) {
            String prefijo = String.format(Locale.US, "%04d%02d", anio, mes);
            storage.guardarSubida(archivoSicoin, prefijo + "_sicoin_rem");
            if (!vacio(planilla011Deposito)) {
                storage.guardarSubida(planilla011Deposito, prefijo + "_planilla011_deposito");
            }
            if (!vacio(planilla011Cheque)) {
                storage.guardarSubida(planilla011Cheque, prefijo + "_planilla011_cheque");
            }
            if (!vacio(planilla022)) {
                storage.guardarSubida(planilla022, prefijo + "_planilla022");
            }

            List<RegistroSicoin> sicoin;
            try {
                sicoin = ParserSicoin.parsear(archivoSicoin.getInputStream());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "No se pudo leer el PDF de SICOIN. Usa el detalle de gastos por proveedor.");
            }
            if (sicoin.isEmpty()) {
                throw new IllegalArgumentException(
                        "El PDF de SICOIN no tiene registros reconocibles.");
            }

            List<FilaPlanilla> planillas = new ArrayList<>();
            agregarPlanilla(planillas, planilla011Deposito, "011 deposito");
            agregarPlanilla(planillas, planilla011Cheque, "011 cheque");
            agregarPlanilla(planillas, planilla022, "022");

            List<PersonaRrhh> personas = new ArrayList<>();
            for (Empleado e : rrhh.empleadosActivos()) {
                PersonaRrhh p = new PersonaRrhh();
                p.setNombre(e.getNombre() == null ? "" : e.getNombre());
                p.setCargo(e.getCargo() == null ? "" : e.getCargo());
                p.setDependencia(e.getDependencia() == null ? "" : e.getDependencia());
                p.setRenglon(e.getRenglon() == null ? "" : e.getRenglon());
                personas.add(p);
            }

            ResultadoRemuneraciones res = MotorRemuneracionesService.construir(
                    mes, anio, planillas, sicoin, personas);
            String nombreExcel = "REMUNERACIONES_"
                    + Constantes.MESES_NOMBRE.get(mes) + "_" + anio + ".xlsx";
            Path ruta = storage.rutaGenerado(nombreExcel);
            ExcelGeneradorRemuneraciones.generar(res.getFilas(), mes, anio, ruta);
            res.setNombreExcel(nombreExcel);
            sesion.setAttribute(SES_RESULTADO, res);
            return "redirect:/dafim/remuneraciones/procesar";
        } catch (IllegalStateException | IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/dafim/remuneraciones/procesar";
        } catch (Exception e) {
            log.error("Error procesando remuneraciones {}/{}", mes, anio, e);
            flash.addFlashAttribute("error",
                    "No se pudo armar el reporte. Verifica que cada PDF sea el correcto.");
            return "redirect:/dafim/remuneraciones/procesar";
        }
    }

    @GetMapping("/procesar/excel")
    public ResponseEntity<Resource> descargar(HttpSession sesion) {
        ResultadoRemuneraciones rp =
                (ResultadoRemuneraciones) sesion.getAttribute(SES_RESULTADO);
        if (rp == null || rp.getNombreExcel() == null || rp.getNombreExcel().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path ruta = storage.rutaGenerado(rp.getNombreExcel());
        if (!Files.exists(ruta)) return ResponseEntity.notFound().build();
        FileSystemResource recurso = new FileSystemResource(ruta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + rp.getNombreExcel() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(recurso);
    }

    private static void agregarPlanilla(List<FilaPlanilla> dest, MultipartFile archivo,
                                        String etiqueta) {
        if (vacio(archivo)) return;
        try {
            dest.addAll(ParserPlanillaPdf.parsear(archivo.getInputStream()));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "No se pudo leer la planilla " + etiqueta + ". Debe ser el PDF de SICOIN GL.");
        }
    }

    private static boolean vacio(MultipartFile f) {
        return f == null || f.isEmpty();
    }
}
