package com.granados.sistema.dafim.compras.web;

import com.granados.sistema.config.ExclusiveJobs;
import com.granados.sistema.config.StorageService;
import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.dto.DatosBd;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.MachotePendiente;
import com.granados.sistema.dafim.compras.dto.NpgConfirmacion;
import com.granados.sistema.dafim.compras.dto.PersonaRemuneracion;
import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.dto.ResultadoConstruccion;
import com.granados.sistema.dafim.compras.dto.ResultadoProcesamiento;
import com.granados.sistema.dafim.compras.dto.ResultadoValidacion;
import com.granados.sistema.dafim.compras.parser.ParserCheques;
import com.granados.sistema.dafim.compras.parser.ParserGuatecompras;
import com.granados.sistema.dafim.compras.parser.ParserMachote;
import com.granados.sistema.dafim.compras.parser.ParserNpgConfirmacion;
import com.granados.sistema.dafim.compras.parser.ParserRemuneraciones;
import com.granados.sistema.dafim.compras.parser.ParserSicoin;
import com.granados.sistema.dafim.compras.service.ComparadorMensualService;
import com.granados.sistema.dafim.compras.service.ExcelGeneradorService;
import com.granados.sistema.dafim.compras.service.GestionBaseDatosComprasService;
import com.granados.sistema.dafim.compras.service.MotorComprasService;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.usuarios.entity.Usuario;
import com.granados.sistema.usuarios.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Modulo DAFIM: Compras Directas.
 *
 * Flujo principal (igual que las celdas 6 y 7 del Colab):
 *   1. POST /procesar : sube cheques (.xls), TXT de Guatecompras y PDF de
 *      SICOIN (remuneraciones opcional) -> arma las filas, valida, compara
 *      con el mes anterior, genera el Excel y deja TODO en la sesion.
 *   2. El usuario revisa la vista previa y descarga el Excel.
 *   3. POST /procesar/confirmar : guarda el historial y fusiona la BD.
 *
 * Flujos de carga historica (celdas 3 y 4 del Colab):
 *   - /cargar-npgs    : PDFs de confirmacion de Guatecompras (o un ZIP).
 *   - /cargar-machote : Excel de un mes ya trabajado -> historial + BD.
 */
@Controller
@RequestMapping("/dafim/compras")
public class ComprasDirectasController {

    private static final Logger log =
            LoggerFactory.getLogger(ComprasDirectasController.class);

    static final String SES_RESULTADO = "resultadoProcesamiento";
    static final String SES_MACHOTE = "machotePendiente";

    private final GestionBaseDatosComprasService gestionBd;
    private final StorageService storage;
    private final UsuarioService usuarioService;
    private final ExclusiveJobs jobs;

    public ComprasDirectasController(GestionBaseDatosComprasService gestionBd,
                                     StorageService storage,
                                     UsuarioService usuarioService,
                                     ExclusiveJobs jobs) {
        this.gestionBd = gestionBd;
        this.storage = storage;
        this.usuarioService = usuarioService;
        this.jobs = jobs;
    }

    private Long idUsuario(Authentication auth) {
        return usuarioService.porUsername(auth.getName())
                .map(Usuario::getId).orElse(null);
    }

    // ============================= DASHBOARD =============================

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", gestionBd.estadisticasDashboard());
        return "dafim/compras/index";
    }

    // ========================== PROCESAR EL MES ==========================

    @GetMapping("/procesar")
    public String procesarForm(Model model, HttpSession sesion) {
        model.addAttribute("meses", Constantes.MESES_NOMBRE);
        model.addAttribute("anioActual", Year.now().getValue());
        model.addAttribute("resultado", sesion.getAttribute(SES_RESULTADO));
        return "dafim/compras/procesar";
    }

    @PostMapping("/procesar")
    public String procesar(@RequestParam int mes,
                           @RequestParam int anio,
                           @RequestParam("archivoCheques") MultipartFile archivoCheques,
                           @RequestParam("archivoTxt") MultipartFile archivoTxt,
                           @RequestParam("archivoPdf") MultipartFile archivoPdf,
                           @RequestParam(value = "archivoRemuneraciones", required = false)
                           MultipartFile archivoRem,
                           HttpSession sesion,
                           Authentication auth,
                           RedirectAttributes flash) {
        if (mes < 1 || mes > 12) {
            flash.addFlashAttribute("error", "El mes debe estar entre 1 y 12.");
            return "redirect:/dafim/compras/procesar";
        }
        if (vacio(archivoCheques) || vacio(archivoTxt) || vacio(archivoPdf)) {
            flash.addFlashAttribute("error",
                    "Faltan archivos: se necesitan el reporte de cheques, el TXT "
                    + "de Guatecompras y el PDF de SICOIN.");
            return "redirect:/dafim/compras/procesar";
        }
        try (AutoCloseable ignored = jobs.hold("compras-mes-" + anio + "-" + mes)) {
            // guardar copia de lo subido (respaldo del mes)
            String prefijo = String.format(Locale.US, "%04d%02d", anio, mes);
            storage.guardarSubida(archivoCheques, prefijo + "_cheques");
            storage.guardarSubida(archivoTxt, prefijo + "_guatecompras");
            storage.guardarSubida(archivoPdf, prefijo + "_sicoin");
            if (!vacio(archivoRem)) {
                storage.guardarSubida(archivoRem, prefijo + "_remuneraciones");
            }

            // 1) parsear las tres fuentes (+ remuneraciones opcional)
            List<Cheque> cheques;
            try {
                cheques = ParserCheques.parsear(archivoCheques.getInputStream());
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "No se pudo leer el reporte de cheques. "
                        + "Debe ser un Excel (.xls o .xlsx) del banco.");
            }

            String contenidoTxt = new String(archivoTxt.getBytes(), StandardCharsets.UTF_8);
            List<RegistroGuatecompras> txtRegs = ParserGuatecompras.parsear(contenidoTxt);
            if (txtRegs.isEmpty()) {
                throw new IllegalArgumentException(
                        "El TXT de Guatecompras no tiene publicaciones reconocibles "
                        + "(NPG que empiece con E y 8 a 10 digitos). "
                        + "Verifica que sea la exportacion del mes y no un archivo vacio.");
            }
            gestionBd.upsertPublicaciones(txtRegs, "TXT_MES");

            List<RegistroSicoin> pdfRegs;
            try {
                pdfRegs = ParserSicoin.parsear(archivoPdf.getInputStream());
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "No se pudo leer el PDF de SICOIN. "
                        + "Usa el detalle de gastos por proveedor, no otro reporte.");
            }
            if (pdfRegs.isEmpty()) {
                throw new IllegalArgumentException(
                        "El PDF de SICOIN no tiene registros reconocibles. "
                        + "Usa el detalle de transacciones por proveedor, no otro reporte.");
            }

            List<PersonaRemuneracion> personasRem = List.of();
            if (!vacio(archivoRem)) {
                try {
                    personasRem = ParserRemuneraciones.parsear(archivoRem.getInputStream());
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "No se pudo leer el archivo de remuneraciones. "
                            + "Debe ser la planilla Excel del renglón 029.");
                }
            }

            // 2) motor: armar filas con la BD historica
            DatosBd bd = gestionBd.cargarBd();
            List<RegistroGuatecompras> catalogo = gestionBd.publicacionesComoRegistros();
            ResultadoConstruccion rc = MotorComprasService.construirFilas(
                    cheques, txtRegs, pdfRegs,
                    bd.getProveedores(), bd.getContratos(), bd.getNpgs029(), catalogo);

            if (!vacio(archivoRem) && personasRem.isEmpty()) {
                rc.getAlertas().add(0,
                        "ADVERTENCIA: el archivo de remuneraciones no produjo personas R029. "
                        + "Verifica que sea la planilla correcta.");
            }

            // 3) validar, cruzar remuneraciones y comparar con el mes anterior
            ResultadoValidacion rv = ValidadorComprasService.validar(rc.getFilas(), cheques);
            List<String> obsRem = ValidadorComprasService.validarRemuneraciones(
                    rc.getFilas(), personasRem);
            List<String> obsComp = ComparadorMensualService.comparar(
                    rc.getFilas(), gestionBd.cargarHistorial(), anio, mes);

            // 4) generar el Excel legal
            String nombreExcel = "COMPRAS_DIRECTAS_"
                    + Constantes.MESES_NOMBRE.get(mes) + "_" + anio + ".xlsx";
            Path rutaExcel = storage.rutaGenerado(nombreExcel);
            ExcelGeneradorService.generarExcel(rc.getFilas(), mes, anio, rutaExcel);

            // 5) armar el resultado y dejarlo en la sesion para confirmar
            ResultadoProcesamiento rp = new ResultadoProcesamiento();
            rp.setAnio(anio);
            rp.setMes(mes);
            rp.setFilas(rc.getFilas());
            rp.setAlertas(rc.getAlertas());
            rp.setReporteValidacion(rv.getReporte());
            rp.setValidacionOk(rv.isOk());
            rp.setObsRemuneraciones(obsRem);
            rp.setObsComparacion(obsComp);
            rp.setNuevos029(rc.getNuevos029());
            rp.setNuevosProv(rc.getNuevosProv());
            rp.setNpgsSinCheque(rc.getNpgsSinCheque());
            rp.setNombreExcel(nombreExcel);
            rp.setTotalCheques(cheques.size());
            double total = 0;
            for (FilaCompra f : rc.getFilas()) total += f.getPrecio();
            rp.setTotalMonto(ValidadorComprasService.round2(total));
            rp.setMesYaGuardado(gestionBd.existeMesGuardado(anio, mes));
            sesion.setAttribute(SES_RESULTADO, rp);

            gestionBd.registrarProceso(anio, mes, idUsuario(auth), rc.getFilas().size(),
                    rp.getTotalMonto(), rc.getAlertas(), rutaExcel.toString(),
                    "PROCESADO");
            return "redirect:/dafim/compras/procesar";
        } catch (IllegalStateException | IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/dafim/compras/procesar";
        } catch (Exception e) {
            log.error("Error procesando el mes {}/{}", mes, anio, e);
            flash.addFlashAttribute("error",
                    "No se pudo procesar el mes. Verifica que cada archivo sea el correcto.");
            return "redirect:/dafim/compras/procesar";
        }
    }

    @GetMapping("/procesar/excel")
    public ResponseEntity<Resource> descargarExcel(HttpSession sesion) {
        ResultadoProcesamiento rp =
                (ResultadoProcesamiento) sesion.getAttribute(SES_RESULTADO);
        if (rp == null || rp.getNombreExcel().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path ruta = storage.rutaGenerado(rp.getNombreExcel());
        if (!Files.exists(ruta)) return ResponseEntity.notFound().build();
        return descargaXlsx(ruta, rp.getNombreExcel());
    }

    @PostMapping("/procesar/confirmar")
    public String confirmar(HttpSession sesion, Authentication auth,
                            RedirectAttributes flash) {
        ResultadoProcesamiento rp =
                (ResultadoProcesamiento) sesion.getAttribute(SES_RESULTADO);
        if (rp == null) {
            flash.addFlashAttribute("error",
                    "No hay un mes procesado pendiente. Procesa primero.");
            return "redirect:/dafim/compras/procesar";
        }
        long reemplazadas = 0;
        try (AutoCloseable ignored = jobs.hold(
                "compras-mes-" + rp.getAnio() + "-" + rp.getMes())) {
            reemplazadas = gestionBd.guardarHistorial(
                    rp.getFilas(), rp.getAnio(), rp.getMes(), idUsuario(auth));
            gestionBd.fusionarBd(rp.getNuevos029(), rp.getNuevosProv(), rp.getAnio());
            gestionBd.registrarProceso(rp.getAnio(), rp.getMes(), idUsuario(auth),
                    rp.getFilas().size(), rp.getTotalMonto(), rp.getAlertas(),
                    storage.rutaGenerado(rp.getNombreExcel()).toString(), "GUARDADO");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/dafim/compras/procesar";
        } catch (Exception e) {
            flash.addFlashAttribute("error",
                    "No se pudo guardar el mes. Intenta de nuevo.");
            return "redirect:/dafim/compras/procesar";
        }
        sesion.removeAttribute(SES_RESULTADO);
        String msj = "Mes " + rp.getMesNombre() + " " + rp.getAnio()
                + " guardado en la base de datos ("
                + rp.getFilas().size() + " registros";
        if (reemplazadas > 0) msj += ", " + reemplazadas + " reemplazados";
        flash.addFlashAttribute("exito", msj + ").");
        return "redirect:/dafim/compras";
    }

    @PostMapping("/procesar/descartar")
    public String descartar(HttpSession sesion, RedirectAttributes flash) {
        sesion.removeAttribute(SES_RESULTADO);
        flash.addFlashAttribute("exito", "Vista previa descartada. Nada se guardo.");
        return "redirect:/dafim/compras/procesar";
    }

    // ===================== CARGAR NPGs HISTORICOS ========================

    @GetMapping("/cargar-npgs")
    public String cargarNpgsForm() {
        return "dafim/compras/cargar-npgs";
    }

    @PostMapping("/cargar-npgs")
    public String cargarNpgs(@RequestParam("archivos") List<MultipartFile> archivos,
                             Model model, RedirectAttributes flash) {
        List<NpgConfirmacion> ok = new ArrayList<>();
        List<NpgConfirmacion> conError = new ArrayList<>();
        List<String> sinDatos = new ArrayList<>();
        int totalPdfs = 0;
        try {
            for (MultipartFile archivo : archivos) {
                if (vacio(archivo)) continue;
                String nombre = archivo.getOriginalFilename() == null
                        ? "archivo" : archivo.getOriginalFilename();
                if (nombre.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    try (ZipInputStream zis =
                                 new ZipInputStream(archivo.getInputStream())) {
                        ZipEntry entrada;
                        while ((entrada = zis.getNextEntry()) != null) {
                            if (entrada.isDirectory()) continue;
                            String en = entrada.getName();
                            if (!en.toLowerCase(Locale.ROOT).endsWith(".pdf")) continue;
                            totalPdfs++;
                            byte[] bytes = zis.readAllBytes();
                            clasificar(ParserNpgConfirmacion.parsear(
                                            new ByteArrayInputStream(bytes), en),
                                    ok, conError, sinDatos);
                        }
                    }
                } else if (nombre.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    totalPdfs++;
                    clasificar(ParserNpgConfirmacion.parsear(
                                    archivo.getInputStream(), nombre),
                            ok, conError, sinDatos);
                }
            }
        } catch (IOException e) {
            log.error("Error leyendo archivos de NPG", e);
            flash.addFlashAttribute("error",
                    "No se pudieron leer los archivos. Sube PDFs de confirmacion o un ZIP.");
            return "redirect:/dafim/compras/cargar-npgs";
        }
        if (totalPdfs == 0) {
            flash.addFlashAttribute("error",
                    "No se encontro ningun PDF. Sube PDFs de confirmacion o un ZIP.");
            return "redirect:/dafim/compras/cargar-npgs";
        }
        int[] res = gestionBd.aplicarNpgs(ok, Year.now().getValue());
        model.addAttribute("procesados", totalPdfs);
        model.addAttribute("resultados", ok);
        model.addAttribute("errores", conError);
        model.addAttribute("sinDatos", sinDatos);
        model.addAttribute("actualizados", res[0]);
        model.addAttribute("nuevos", res[1]);
        return "dafim/compras/cargar-npgs";
    }

    @PostMapping("/cargar-npgs/txt")
    public String cargarNpgsTxt(@RequestParam("archivoTxt") MultipartFile archivoTxt,
                                RedirectAttributes flash) {
        if (vacio(archivoTxt)) {
            flash.addFlashAttribute("error",
                    "Selecciona el TXT de publicaciones de Guatecompras.");
            return "redirect:/dafim/compras/cargar-npgs";
        }
        try {
            String contenido = new String(archivoTxt.getBytes(), StandardCharsets.UTF_8);
            List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(contenido);
            if (regs.isEmpty()) {
                flash.addFlashAttribute("error",
                        "El TXT no tiene publicaciones reconocibles (NPG que empiece con E).");
                return "redirect:/dafim/compras/cargar-npgs";
            }
            int[] res = gestionBd.upsertPublicaciones(regs, "CATALOGO");
            flash.addFlashAttribute("exito",
                    regs.size() + " publicaciones guardadas ("
                    + res[0] + " nuevas, " + res[1] + " actualizadas).");
            return "redirect:/dafim/compras/cargar-npgs";
        } catch (Exception e) {
            log.error("Error leyendo catalogo NPG", e);
            flash.addFlashAttribute("error",
                    "No se pudo leer el TXT de Guatecompras. Verifica que sea la exportacion.");
            return "redirect:/dafim/compras/cargar-npgs";
        }
    }

    /**
     * Alta MANUAL de un NPG: para cuando no se tiene el PDF de confirmacion
     * o el PDF viene en un formato que el parser no reconoce.
     */
    @PostMapping("/cargar-npgs/manual")
    public String cargarNpgManual(@RequestParam("npg") String npg,
                                  @RequestParam("nit") String nit,
                                  @RequestParam("nombre") String nombre,
                                  @RequestParam(value = "contrato", required = false) String contrato,
                                  @RequestParam(value = "descManual", required = false) String descManual,
                                  RedirectAttributes flash) {
        npg = npg == null ? "" : npg.trim().toUpperCase(Locale.ROOT);
        nit = nit == null ? "" : nit.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        nombre = nombre == null ? "" : nombre.trim();
        contrato = contrato == null ? "" : contrato.trim();
        descManual = descManual == null ? "" : descManual.trim();

        if (!npg.matches("E\\d{8,10}")) {
            flash.addFlashAttribute("error",
                    "NPG invalido: debe ser una E seguida de 8 a 10 digitos (ej. E581998146).");
            return "redirect:/dafim/compras/cargar-npgs";
        }
        if (!nit.matches("\\d{4,12}K?")) {
            flash.addFlashAttribute("error",
                    "NIT invalido: solo digitos (y K final si aplica), sin guiones.");
            return "redirect:/dafim/compras/cargar-npgs";
        }
        if (nombre.length() < 3) {
            flash.addFlashAttribute("error", "Escribe el nombre del proveedor o contratista.");
            return "redirect:/dafim/compras/cargar-npgs";
        }
        if (!contrato.isEmpty() && !contrato.matches("\\d{1,3}-\\d{4}")) {
            flash.addFlashAttribute("error",
                    "Contrato invalido: usa el formato numero-anio (ej. 98-2026) o dejalo vacio.");
            return "redirect:/dafim/compras/cargar-npgs";
        }

        NpgConfirmacion r = new NpgConfirmacion();
        r.setArchivo("(ingreso manual)");
        r.setNpg(npg);
        r.setNit(nit.replace("K", ""));
        r.setNombre(nombre);
        r.setContrato(contrato);
        r.setDesc(descManual.length() > 200 ? descManual.substring(0, 200) : descManual);
        r.setError("");

        int[] res = gestionBd.aplicarNpgs(List.of(r), Year.now().getValue());
        flash.addFlashAttribute("exito", "NPG " + npg + " guardado para " + nombre
                + (res[1] > 0 ? " (persona nueva en la BD)." : " (registro actualizado)."));
        return "redirect:/dafim/compras/cargar-npgs";
    }

    private static void clasificar(NpgConfirmacion r, List<NpgConfirmacion> ok,
                                   List<NpgConfirmacion> conError,
                                   List<String> sinDatos) {
        if (r.getError() != null && !r.getError().isEmpty()) {
            conError.add(r);
        } else if (r.getNpg().isEmpty() || r.getNit().isEmpty()) {
            sinDatos.add(r.getArchivo());
        } else {
            ok.add(r);
        }
    }

    // ======================= CARGAR MACHOTE ==============================

    @GetMapping("/cargar-machote")
    public String cargarMachoteForm(Model model, HttpSession sesion) {
        model.addAttribute("meses", Constantes.MESES_NOMBRE);
        model.addAttribute("anioActual", Year.now().getValue());
        model.addAttribute("pendiente", sesion.getAttribute(SES_MACHOTE));
        return "dafim/compras/cargar-machote";
    }

    @PostMapping("/cargar-machote")
    public String cargarMachote(@RequestParam int mes,
                                @RequestParam int anio,
                                @RequestParam("archivoMachote") MultipartFile archivo,
                                HttpSession sesion,
                                RedirectAttributes flash) {
        if (mes < 1 || mes > 12) {
            flash.addFlashAttribute("error", "El mes debe estar entre 1 y 12.");
            return "redirect:/dafim/compras/cargar-machote";
        }
        if (vacio(archivo)) {
            flash.addFlashAttribute("error", "Selecciona el Excel del mes a cargar.");
            return "redirect:/dafim/compras/cargar-machote";
        }
        try {
            List<FilaCompra> filas = ParserMachote.parsear(archivo.getInputStream());
            if (filas.isEmpty()) {
                flash.addFlashAttribute("error",
                        "El archivo no tiene filas de datos reconocibles.");
                return "redirect:/dafim/compras/cargar-machote";
            }
            MachotePendiente mp = new MachotePendiente();
            mp.setAnio(anio);
            mp.setMes(mes);
            mp.setNombreArchivo(archivo.getOriginalFilename() == null
                    ? "machote.xlsx" : archivo.getOriginalFilename());
            mp.setFilas(filas);
            mp.setMesYaGuardado(gestionBd.existeMesGuardado(anio, mes));
            sesion.setAttribute(SES_MACHOTE, mp);
            return "redirect:/dafim/compras/cargar-machote";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/dafim/compras/cargar-machote";
        } catch (Exception e) {
            log.error("Error leyendo machote", e);
            flash.addFlashAttribute("error",
                    "No se pudo leer el Excel. Verifica que sea el informe de compras directas.");
            return "redirect:/dafim/compras/cargar-machote";
        }
    }

    @PostMapping("/cargar-machote/confirmar")
    public String confirmarMachote(HttpSession sesion, Authentication auth,
                                   RedirectAttributes flash) {
        MachotePendiente mp = (MachotePendiente) sesion.getAttribute(SES_MACHOTE);
        if (mp == null) {
            flash.addFlashAttribute("error", "No hay un machote pendiente.");
            return "redirect:/dafim/compras/cargar-machote";
        }
        // misma secuencia que la celda 4 del Colab:
        ResultadoConstruccion paraBd = MotorComprasService.extraerParaBd(mp.getFilas());
        gestionBd.fusionarBd(paraBd.getNuevos029(), paraBd.getNuevosProv(), mp.getAnio());
        long reemplazadas = gestionBd.guardarHistorial(
                mp.getFilas(), mp.getAnio(), mp.getMes(), idUsuario(auth));
        gestionBd.registrarProceso(mp.getAnio(), mp.getMes(), idUsuario(auth),
                mp.getFilas().size(), mp.getTotalMonto(), List.of(),
                mp.getNombreArchivo(), "MACHOTE");
        sesion.removeAttribute(SES_MACHOTE);
        String msj = "Machote de " + mp.getMesNombre() + " " + mp.getAnio()
                + " cargado (" + mp.getFilas().size() + " registros";
        if (reemplazadas > 0) msj += ", " + reemplazadas + " reemplazados";
        flash.addFlashAttribute("exito", msj + ").");
        return "redirect:/dafim/compras";
    }

    @PostMapping("/cargar-machote/descartar")
    public String descartarMachote(HttpSession sesion, RedirectAttributes flash) {
        sesion.removeAttribute(SES_MACHOTE);
        flash.addFlashAttribute("exito", "Carga descartada. Nada se guardo.");
        return "redirect:/dafim/compras/cargar-machote";
    }

    // ===================== BUSCAR / RESUMEN / EXPORTAR ===================

    @GetMapping("/buscar")
    public String buscar(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String tipo,
                         @RequestParam(required = false) String renglon,
                         @RequestParam(required = false) Integer anio,
                         @RequestParam(required = false) Integer mes,
                         @RequestParam(required = false) Boolean sinPago) {
        UriComponentsBuilder dest = UriComponentsBuilder.fromPath("/dafim/bitacora");
        if (q != null && !q.isBlank()) dest.queryParam("q", q);
        if (tipo != null && !tipo.isBlank()) dest.queryParam("tipo", tipo);
        if (renglon != null && !renglon.isBlank()) dest.queryParam("renglon", renglon);
        if (anio != null) dest.queryParam("anio", anio);
        if (mes != null) dest.queryParam("mes", mes);
        if (Boolean.TRUE.equals(sinPago)) dest.queryParam("sinPago", true);
        return "redirect:" + dest.build().encode().toUriString();
    }

    @GetMapping("/resumen")
    public String resumen(Model model) {
        model.addAttribute("r", gestionBd.resumen());
        return "dafim/compras/resumen";
    }

    @GetMapping("/exportar-bd")
    public ResponseEntity<Resource> exportarBd() throws IOException {
        String nombre = "BD_COMPRAS_GRANADOS.xlsx";
        Path ruta = storage.rutaGenerado(nombre);
        ExcelGeneradorService.generarExcelBd(
                gestionBd.contratosComoMapa(),
                gestionBd.proveedoresComoMapa(),
                gestionBd.cargarHistorial(),
                ruta);
        return descargaXlsx(ruta, nombre);
    }

    // ============================= helpers ===============================

    private static boolean vacio(MultipartFile f) {
        return f == null || f.isEmpty();
    }

    private static ResponseEntity<Resource> descargaXlsx(Path ruta, String nombre) {
        FileSystemResource recurso = new FileSystemResource(ruta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument"
                        + ".spreadsheetml.sheet"))
                .body(recurso);
    }
}
