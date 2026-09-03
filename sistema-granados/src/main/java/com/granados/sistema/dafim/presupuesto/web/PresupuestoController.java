package com.granados.sistema.dafim.presupuesto.web;

import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.presupuesto.entity.CargaCaja;
import com.granados.sistema.dafim.presupuesto.entity.CargaPresupuesto;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoFiltros;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.DesgloseFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.FuenteResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.RenglonDetalle;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.RenglonResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.VistaApartar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Capa web del modulo de presupuesto DAFIM: la direccion financiera revisa
 * aqui la ejecucion oficial de SICOIN (vigente, devengado, pagado) cruzada
 * con los pagos reales del sistema, para decidir con cuanto presupuesto
 * cuenta cada renglon antes de aprobar un cheque. El PDF de ejecucion se
 * sube una vez por corte y reemplaza a la carga anterior.
 *
 * Rutas:
 *   /dafim/presupuesto                     resumen general de la carga activa
 *   /dafim/presupuesto/cargar              subir PDFs SICOIN (GET; POST egresos /cargar, POST caja /cargar-caja)
 *   /dafim/presupuesto/renglones           ejecucion por renglon (filtro q)
 *   /dafim/presupuesto/renglones/{codigo}  detalle de un renglon
 *   /dafim/presupuesto/fuentes             ejecucion por fuente + renombrar
 *   /dafim/presupuesto/fuentes/ir          buscador: va directo al desglose (q)
 *   /dafim/presupuesto/fuentes/{codigo}    desglose de la fuente (monto opcional)
 *   /dafim/presupuesto/transferencias      simulador origen->destino (consultivo)
 *   /dafim/presupuesto/disponibilidad      semaforo del mes (compra directa)
 *   /dafim/presupuesto/donde-pagar         con que fuente pagar un gasto (q, monto)
 *   /dafim/presupuesto/apartar             formulario para reservar un pago
 *   /dafim/presupuesto/apartados           reservas activas (armar pagos)
 *   /dafim/presupuesto/cargas              historial de cargas (+ eliminar)
 */
@Controller
@RequestMapping("/dafim/presupuesto")
public class PresupuestoController {

    private static final Logger log = LoggerFactory.getLogger(PresupuestoController.class);

    private static final DateTimeFormatter F_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ES_GT = new Locale("es", "GT");

    /** Cuantas cargas se muestran como historial breve en el formulario de subida. */
    private static final int MAX_CARGAS_RECIENTES = 5;

    /** Renglones "mas cerca de agotarse" que se destacan en el resumen. */
    private static final int MAX_CRITICOS = 5;

    private final PresupuestoService presupuesto;

    public PresupuestoController(PresupuestoService presupuesto) {
        this.presupuesto = presupuesto;
    }

    // ============================== RESUMEN ==============================

    @GetMapping
    public String inicio(Model model) {
        Optional<CargaPresupuesto> carga = presupuesto.cargaActiva();
        model.addAttribute("carga", carga.orElse(null));
        model.addAttribute("resumen", presupuesto.resumenGeneral().orElse(null));
        model.addAttribute("cargaCaja", presupuesto.cargaCajaActiva().orElse(null));
        if (carga.isPresent()) {
            model.addAttribute("criticos", presupuesto.porRenglon().stream()
                    .filter(r -> r.getVigente().signum() > 0)
                    .sorted(Comparator
                            .comparingDouble(RenglonResumen::getPctEjecucion).reversed())
                    .limit(MAX_CRITICOS)
                    .toList());
        }
        return "dafim/presupuesto/index";
    }

    // ============================ CARGAR PDF =============================

    @GetMapping("/cargar")
    public String cargarForm(Model model) {
        List<CargaPresupuesto> historial = presupuesto.historialCargas();
        model.addAttribute("cargasRecientes", historial.size() > MAX_CARGAS_RECIENTES
                ? historial.subList(0, MAX_CARGAS_RECIENTES) : historial);
        List<CargaCaja> historialCaja = presupuesto.historialCargasCaja();
        model.addAttribute("cargasCajaRecientes", historialCaja.size() > MAX_CARGAS_RECIENTES
                ? historialCaja.subList(0, MAX_CARGAS_RECIENTES) : historialCaja);
        model.addAttribute("carga", presupuesto.cargaActiva().orElse(null));
        model.addAttribute("cargaCaja", presupuesto.cargaCajaActiva().orElse(null));
        return "dafim/presupuesto/cargar";
    }

    @PostMapping("/cargar")
    public String cargar(@RequestParam("archivo") MultipartFile archivo,
                         Authentication auth, RedirectAttributes flash) {
        if (archivo == null || archivo.isEmpty()) {
            flash.addFlashAttribute("error", MensajesCarga.EGRESOS_VACIO);
            return "redirect:/dafim/presupuesto/cargar";
        }
        if (MensajesCarga.noEsPdf(archivo)) {
            flash.addFlashAttribute("error", MensajesCarga.EGRESOS_NO_PDF);
            return "redirect:/dafim/presupuesto/cargar";
        }
        try {
            CargaPresupuesto carga = presupuesto.importarPdf(archivo,
                    auth == null ? "" : auth.getName());
            flash.addFlashAttribute("exito", "Ejecucion de egresos importada: "
                    + carga.getTotalLineas() + " lineas, periodo "
                    + fecha(carga.getPeriodoDesde()) + " a " + fecha(carga.getPeriodoHasta())
                    + ". Quedo como carga activa (la anterior pasa a historial). "
                    + "Vigente " + quetzales(carga.getTotalVigente())
                    + ", devengado " + quetzales(carga.getTotalDevengado())
                    + ", pagado " + quetzales(carga.getTotalPagado()) + ".");
            return "redirect:/dafim/presupuesto";
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", MensajesCarga.errorEgresos(e));
            return "redirect:/dafim/presupuesto/cargar";
        } catch (Exception e) {
            log.error("Error importando ejecucion de egresos", e);
            flash.addFlashAttribute("error", MensajesCarga.errorEgresos(e));
            return "redirect:/dafim/presupuesto/cargar";
        }
    }

    @PostMapping("/cargar-caja")
    public String cargarCaja(@RequestParam("archivo") MultipartFile archivo,
                             Authentication auth, RedirectAttributes flash) {
        if (archivo == null || archivo.isEmpty()) {
            flash.addFlashAttribute("error", MensajesCarga.CAJA_VACIO);
            return "redirect:/dafim/presupuesto/cargar";
        }
        if (MensajesCarga.noEsPdf(archivo)) {
            flash.addFlashAttribute("error", MensajesCarga.CAJA_NO_PDF);
            return "redirect:/dafim/presupuesto/cargar";
        }
        try {
            CargaCaja carga = presupuesto.importarBoletinCaja(archivo,
                    auth == null ? "" : auth.getName());
            flash.addFlashAttribute("exito", "Boletin de caja importado: "
                    + carga.getTotalCuentas() + " cuentas"
                    + (carga.getFechaCorte() == null ? ""
                    : ", corte " + fecha(carga.getFechaCorte()))
                    + ". Quedo como carga activa (la anterior pasa a historial). "
                    + "Dinero real total " + quetzales(carga.getTotalNuevoSaldo()) + ".");
            return "redirect:/dafim/presupuesto/fuentes";
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", MensajesCarga.errorCaja(e));
            return "redirect:/dafim/presupuesto/cargar";
        } catch (Exception e) {
            log.error("Error importando boletin de caja", e);
            flash.addFlashAttribute("error", MensajesCarga.errorCaja(e));
            return "redirect:/dafim/presupuesto/cargar";
        }
    }

    // ============================== RENGLONES ============================

    @GetMapping("/renglones")
    public String renglones(@RequestParam(required = false) String q,
                            @RequestParam(required = false) String saldoMin,
                            @RequestParam(required = false) String saldoMax,
                            @RequestParam(required = false) String ejecucion,
                            @RequestParam(required = false) String saldo,
                            @RequestParam(required = false) String orden,
                            Model model) {
        List<RenglonResumen> lista = presupuesto.porRenglon();
        BigDecimal min = parsearMonto(saldoMin == null ? "" : saldoMin.strip(), null);
        BigDecimal max = parsearMonto(saldoMax == null ? "" : saldoMax.strip(), null);
        lista = PresupuestoFiltros.filtrarRenglones(lista, q, min, max, ejecucion, saldo, orden);
        model.addAttribute("renglones", lista);
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("saldoMin", saldoMin == null ? "" : saldoMin.strip());
        model.addAttribute("saldoMax", saldoMax == null ? "" : saldoMax.strip());
        model.addAttribute("ejecucion", ejecucion == null || ejecucion.isBlank()
                ? "todos" : ejecucion.strip());
        model.addAttribute("saldo", saldo == null ? "" : saldo.strip());
        model.addAttribute("orden", PresupuestoFiltros.ordenRenglones(orden));
        model.addAttribute("hayCarga", presupuesto.cargaActiva().isPresent());
        model.addAttribute("hayBoletin", presupuesto.cargaCajaActiva().isPresent());
        model.addAttribute("saldoFiltrado", PresupuestoFiltros.sumarSaldoRenglones(lista));
        return "dafim/presupuesto/renglones";
    }

    @GetMapping("/renglones/{codigo}")
    public String detalleRenglon(@PathVariable String codigo,
                                 @RequestParam(required = false) String q,
                                 Model model, RedirectAttributes flash) {
        Optional<RenglonDetalle> detalle = presupuesto.detalleRenglon(codigo);
        if (detalle.isEmpty() || detalle.get().getLineas().isEmpty()) {
            flash.addFlashAttribute("error",
                    "No se encontro el renglon " + codigo + " en la carga activa.");
            return "redirect:/dafim/presupuesto/renglones"
                    + (q == null || q.isBlank() ? "" : "?q=" + q.strip());
        }
        model.addAttribute("codigo", codigo);
        model.addAttribute("detalle", detalle.get());
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("meses", Constantes.MESES_NOMBRE);
        return "dafim/presupuesto/renglon-detalle";
    }

    // =============================== FUENTES =============================

    @GetMapping("/fuentes")
    public String fuentes(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String saldo,
                          @RequestParam(required = false) String dinero,
                          @RequestParam(required = false) String orden,
                          Model model) {
        List<FuenteResumen> lista = PresupuestoFiltros.filtrarFuentes(
                presupuesto.porFuente(), q, saldo, dinero, orden);
        model.addAttribute("fuentes", lista);
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("saldo", saldo == null ? "" : saldo.strip());
        model.addAttribute("dinero", dinero == null ? "" : dinero.strip());
        model.addAttribute("orden", PresupuestoFiltros.ordenFuentes(orden));
        model.addAttribute("cargaCaja", presupuesto.cargaCajaActiva().orElse(null));
        model.addAttribute("hayBoletin", presupuesto.cargaCajaActiva().isPresent());
        model.addAttribute("hayCarga", presupuesto.cargaActiva().isPresent());
        return "dafim/presupuesto/fuentes";
    }

    @PostMapping("/fuentes/nombre")
    public String renombrarFuente(@RequestParam String codigo,
                                  @RequestParam(required = false) String nombre,
                                  RedirectAttributes flash) {
        try {
            presupuesto.renombrarFuente(codigo, nombre);
            flash.addFlashAttribute("exito",
                    "Nombre de la fuente " + codigo + " actualizado.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dafim/presupuesto/fuentes";
    }

    /**
     * Atajo del buscador de la lista de fuentes: lleva directo al desglose
     * de la fuente cuyo numero se escribio. q se normaliza (sin espacios,
     * en mayusculas); primero se intenta el codigo exacto y si no, los
     * codigos que lo contienen. Con un solo candidato se va al desglose;
     * con varios o ninguno se avisa y se regresa a la lista para elegir
     * el codigo completo a mano. Spring resuelve este literal antes que
     * /fuentes/{codigo}, asi que no chocan.
     */
    @GetMapping("/fuentes/ir")
    public String irAFuente(@RequestParam(required = false) String q,
                            RedirectAttributes flash) {
        String buscado = q == null ? "" : q.strip().toUpperCase(Locale.ROOT);
        if (buscado.isEmpty()) {
            return "redirect:/dafim/presupuesto/fuentes";
        }
        List<FuenteResumen> fuentes = presupuesto.porFuente();
        Optional<FuenteResumen> exacta = fuentes.stream()
                .filter(f -> f.getCodigo().toUpperCase(Locale.ROOT).equals(buscado))
                .findFirst();
        if (exacta.isPresent()) {
            return "redirect:/dafim/presupuesto/fuentes/" + exacta.get().getCodigo();
        }
        List<FuenteResumen> parciales = fuentes.stream()
                .filter(f -> f.getCodigo().toUpperCase(Locale.ROOT).contains(buscado))
                .toList();
        if (parciales.size() == 1) {
            return "redirect:/dafim/presupuesto/fuentes/" + parciales.get(0).getCodigo();
        }
        if (parciales.size() > 1) {
            flash.addFlashAttribute("error", "La busqueda " + buscado
                    + " coincide con " + parciales.size()
                    + " fuentes: elige el codigo completo de la lista.");
        } else {
            flash.addFlashAttribute("error",
                    "No se encontro la fuente " + buscado + ".");
        }
        return "redirect:/dafim/presupuesto/fuentes";
    }

    /**
     * Desglose de una fuente de la carga activa: totales y lineas agrupadas
     * por programa/proyecto, para decidir de donde sacar fondos. El monto
     * (opcional, texto libre) marca como "donante posible" las lineas cuyo
     * saldo lo cubre; si no se puede leer o es negativo se avisa y se
     * muestra todo sin marcar. Sin carga activa o sin lineas en la fuente:
     * flash de error y regreso a la lista de fuentes.
     */
    @GetMapping("/fuentes/{codigo}")
    public String detalleFuente(@PathVariable String codigo,
                                @RequestParam(required = false) String monto,
                                @RequestParam(required = false) String q,
                                Model model, RedirectAttributes flash) {
        String montoTexto = monto == null ? "" : monto.strip();
        BigDecimal montoPago = parsearMonto(montoTexto, model);
        Optional<DesgloseFuente> desglose = presupuesto.desgloseFuente(codigo, montoPago);
        if (desglose.isEmpty()) {
            flash.addFlashAttribute("error",
                    "No se encontro la fuente " + codigo + " en la carga activa.");
            return "redirect:/dafim/presupuesto/fuentes";
        }
        model.addAttribute("codigo", codigo);
        model.addAttribute("desglose", desglose.get());
        model.addAttribute("monto", montoTexto);
        model.addAttribute("montoPago", montoPago);
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("cargaCaja", presupuesto.cargaCajaActiva().orElse(null));
        return "dafim/presupuesto/fuente-detalle";
    }

    // =========================== TRANSFERENCIAS ==========================

    /**
     * Simulador de transferencias entre lineas de la carga activa: origen,
     * destino y monto son opcionales; con los tres validos se muestra la
     * simulacion (como quedarian los saldos) y sin ellos el formulario
     * limpio. Es CONSULTIVO: no guarda nada; la modificacion real se
     * tramita en SICOIN. Monto invalido: aviso y no se simula.
     */
    @GetMapping("/transferencias")
    public String transferencias(@RequestParam(required = false) Long origen,
                                 @RequestParam(required = false) Long destino,
                                 @RequestParam(required = false) String monto,
                                 Model model) {
        boolean hayCarga = presupuesto.cargaActiva().isPresent();
        String montoTexto = monto == null ? "" : monto.strip();
        BigDecimal montoSimular = parsearMonto(montoTexto, model);
        if (hayCarga) {
            model.addAttribute("lineas", presupuesto.destinosPosibles());
            if (origen != null && destino != null && montoSimular != null) {
                model.addAttribute("simulacion",
                        presupuesto.simularTransferencia(origen, destino, montoSimular));
            }
        }
        model.addAttribute("hayCarga", hayCarga);
        model.addAttribute("origen", origen);
        model.addAttribute("destino", destino);
        model.addAttribute("monto", montoTexto);
        return "dafim/presupuesto/transferencias";
    }

    // ============================ DISPONIBILIDAD =========================

    @GetMapping("/disponibilidad")
    public String disponibilidad(@RequestParam(required = false) Integer mes,
                                 @RequestParam(required = false) Integer anio,
                                 Model model) {
        LocalDate hoy = LocalDate.now();
        int m = (mes == null || mes < 1 || mes > 12) ? hoy.getMonthValue() : mes;
        int a = (anio == null || anio < 2020 || anio > 2100) ? hoy.getYear() : anio;
        List<Integer> anios = new ArrayList<>();
        for (int i = hoy.getYear() - 2; i <= hoy.getYear() + 1; i++) {
            anios.add(i);
        }
        model.addAttribute("mes", m);
        model.addAttribute("anio", a);
        model.addAttribute("anios", anios);
        model.addAttribute("meses", Constantes.MESES_NOMBRE);
        // Nombre ya resuelto: ${meses[entero]} no indexa Map<Integer,..> en Thymeleaf
        model.addAttribute("nombreMes", Constantes.MESES_NOMBRE.get(m));
        model.addAttribute("disponibilidad", presupuesto.disponibilidad(m, a));
        return "dafim/presupuesto/disponibilidad";
    }

    // ============================ DONDE PAGAR ============================

    /**
     * Buscador "con que presupuesto pago esto": q es el tipo de gasto en
     * lenguaje libre (o el codigo del renglon) y monto es opcional; si el
     * monto no se puede leer o es negativo se avisa y se busca sin el.
     */
    @GetMapping("/donde-pagar")
    public String dondePagar(@RequestParam(required = false) String q,
                             @RequestParam(required = false) String monto,
                             @RequestParam(required = false) String soloPres,
                             @RequestParam(required = false) String soloBanco,
                             @RequestParam(required = false) String saldo,
                             @RequestParam(required = false) String banco,
                             Model model) {
        String consulta = q == null ? "" : q.strip();
        String montoTexto = monto == null ? "" : monto.strip();
        BigDecimal montoPago = null;
        if (!montoTexto.isEmpty()) {
            try {
                montoPago = new BigDecimal(montoTexto
                        .replace("Q", "").replace("q", "")
                        .replace(",", "").trim());
                if (montoPago.signum() < 0) {
                    model.addAttribute("errorMonto",
                            "El monto a pagar no puede ser negativo; se ignoro en la busqueda.");
                    montoPago = null;
                }
            } catch (NumberFormatException e) {
                model.addAttribute("errorMonto", "'" + montoTexto
                        + "' no es un monto valido y se ignoro. "
                        + "Escribe solo numeros, por ejemplo 5000 o 12,500.50.");
            }
        }
        boolean hayCarga = presupuesto.cargaActiva().isPresent();
        boolean hayBoletin = presupuesto.cargaCajaActiva().isPresent();
        boolean filtraPres = verdad(soloPres);
        boolean filtraBanco = verdad(soloBanco);
        model.addAttribute("hayCarga", hayCarga);
        model.addAttribute("cargaCaja", presupuesto.cargaCajaActiva().orElse(null));
        model.addAttribute("hayBoletin", hayBoletin);
        model.addAttribute("q", consulta);
        model.addAttribute("monto", montoTexto);
        model.addAttribute("montoPago", montoPago);
        model.addAttribute("soloPres", filtraPres);
        model.addAttribute("soloBanco", filtraBanco);
        model.addAttribute("saldo", saldo == null ? "" : saldo.strip());
        model.addAttribute("banco", banco == null ? "" : banco.strip());
        if (hayCarga) {
            model.addAttribute("atajos",
                    PresupuestoFiltros.atajosRenglon(presupuesto.porRenglon()));
        }
        if (hayCarga && !consulta.isEmpty()) {
            model.addAttribute("resultados", PresupuestoFiltros.aplicarVistaDondePagar(
                    presupuesto.dondePagar(consulta, montoPago),
                    filtraPres, filtraBanco, saldo, banco));
        }
        return "dafim/presupuesto/donde-pagar";
    }

    // ============================== APARTADOS ============================

    /**
     * Formulario para reservar un pago: presupuesto de la linea (obligatorio)
     * y dinero real de la fuente (opcional, puede ir en 0).
     */
    @GetMapping("/apartar")
    public String apartarForm(@RequestParam(required = false) Long lineaId,
                              @RequestParam(required = false) String renglon,
                              @RequestParam(required = false) String fuente,
                              @RequestParam(required = false) String actividadObra,
                              @RequestParam(required = false) String programa,
                              @RequestParam(required = false) String proyecto,
                              @RequestParam(required = false) String monto,
                              @RequestParam(required = false) String concepto,
                              @RequestParam(required = false) String q,
                              Model model, RedirectAttributes flash) {
        var vista = lineaId != null
                ? presupuesto.vistaApartar(lineaId)
                : (renglon == null || fuente == null
                ? Optional.<VistaApartar>empty()
                : presupuesto.vistaApartar(renglon, fuente, actividadObra, programa, proyecto));
        if (vista.isEmpty()) {
            flash.addFlashAttribute("error",
                    "No se encontro la linea"
                            + (renglon != null && fuente != null ? " " + renglon + " / " + fuente : "")
                            + " en la carga activa. Elige de nuevo el programa.");
            return "redirect:/dafim/presupuesto/donde-pagar" + queryDondePagar(q, monto);
        }
        String montoTexto = monto == null ? "" : monto.strip();
        BigDecimal sugerido = parsearMonto(montoTexto, model);
        if (sugerido != null && sugerido.compareTo(vista.get().getPresupuestoLibre()) > 0) {
            sugerido = vista.get().getPresupuestoLibre();
        }
        BigDecimal sugeridoBanco = BigDecimal.ZERO;
        if (sugerido != null && sugerido.compareTo(vista.get().getBancoLibre()) <= 0) {
            sugeridoBanco = sugerido;
        }
        model.addAttribute("vista", vista.get());
        model.addAttribute("concepto", concepto == null ? "" : concepto.strip());
        model.addAttribute("montoPresupuesto", sugerido == null ? montoTexto
                : sugerido.toPlainString());
        model.addAttribute("montoBanco", sugeridoBanco.signum() == 0 ? "0"
                : sugeridoBanco.toPlainString());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("monto", montoTexto);
        return "dafim/presupuesto/apartar";
    }

    @PostMapping("/apartar")
    public String apartar(@RequestParam(required = false) Long lineaId,
                          @RequestParam(required = false) String renglon,
                          @RequestParam(required = false) String fuente,
                          @RequestParam(required = false) String actividadObra,
                          @RequestParam(required = false) String programa,
                          @RequestParam(required = false) String proyecto,
                          @RequestParam(required = false) String concepto,
                          @RequestParam(required = false) String montoPresupuesto,
                          @RequestParam(required = false) String montoBanco,
                          @RequestParam(required = false) String q,
                          Authentication auth, RedirectAttributes flash) {
        BigDecimal pres = parsearMonto(montoPresupuesto == null ? "" : montoPresupuesto.strip(),
                null);
        BigDecimal banco = parsearMonto(montoBanco == null ? "" : montoBanco.strip(), null);
        if (pres == null) {
            flash.addFlashAttribute("error",
                    "Escribe el monto de presupuesto a apartar (solo numeros).");
            recordarLinea(flash, lineaId, renglon, fuente, actividadObra, programa, proyecto);
            recordarBusqueda(flash, q, montoPresupuesto);
            return "redirect:/dafim/presupuesto/apartar";
        }
        if (banco == null) banco = BigDecimal.ZERO;
        try {
            Apartado a = lineaId != null
                    ? presupuesto.apartar(lineaId, concepto, pres, banco,
                    auth == null ? "" : auth.getName())
                    : presupuesto.apartar(renglon, fuente, actividadObra,
                    programa, proyecto, concepto,
                    pres, banco, auth == null ? "" : auth.getName());
            flash.addFlashAttribute("exito",
                    "Apartado: " + a.getConcepto() + " · presupuesto "
                            + quetzales(a.getMontoPresupuesto())
                            + (a.getMontoBanco().signum() > 0
                            ? " y banco " + quetzales(a.getMontoBanco())
                            : " (sin efectivo)") + ".");
            return "redirect:/dafim/presupuesto/apartados";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            recordarLinea(flash, lineaId, renglon, fuente, actividadObra, programa, proyecto);
            recordarBusqueda(flash, q, montoPresupuesto);
            if (concepto != null && !concepto.isBlank()) {
                flash.addAttribute("concepto", concepto);
            }
            return "redirect:/dafim/presupuesto/apartar";
        }
    }

    private static void recordarLinea(RedirectAttributes flash, Long lineaId,
                                      String renglon, String fuente,
                                      String actividadObra, String programa, String proyecto) {
        if (lineaId != null) {
            flash.addAttribute("lineaId", lineaId);
        }
        if (renglon != null && !renglon.isBlank()) {
            flash.addAttribute("renglon", renglon);
        }
        if (fuente != null && !fuente.isBlank()) {
            flash.addAttribute("fuente", fuente);
        }
        if (actividadObra != null && !actividadObra.isBlank()) {
            flash.addAttribute("actividadObra", actividadObra);
        }
        if (programa != null && !programa.isBlank()) {
            flash.addAttribute("programa", programa);
        }
        if (proyecto != null && !proyecto.isBlank()) {
            flash.addAttribute("proyecto", proyecto);
        }
    }

    private static void recordarBusqueda(RedirectAttributes flash, String q, String monto) {
        if (q != null && !q.isBlank()) {
            flash.addAttribute("q", q.strip());
        }
        if (monto != null && !monto.isBlank()) {
            flash.addAttribute("monto", monto.strip());
        }
    }

    private static String queryDondePagar(String q, String monto) {
        StringBuilder sb = new StringBuilder();
        if (q != null && !q.isBlank()) {
            sb.append(sb.isEmpty() ? '?' : '&').append("q=").append(q.strip());
        }
        if (monto != null && !monto.isBlank()) {
            sb.append(sb.isEmpty() ? '?' : '&').append("monto=").append(monto.strip());
        }
        return sb.toString();
    }

    @GetMapping("/apartados")
    public String apartados(@RequestParam(required = false) String estado,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String tipo,
                            @RequestParam(required = false) String fuente,
                            @RequestParam(required = false) String desde,
                            @RequestParam(required = false) String hasta,
                            @RequestParam(required = false) String grupo,
                            Model model) {
        String filtro = (estado == null || estado.isBlank()) ? Apartado.EST_ACTIVO : estado.strip();
        List<Apartado> lista = presupuesto.listarApartados(filtro);
        List<Apartado> filtrados = PresupuestoFiltros.filtrarApartados(
                lista, q, tipo, fuente, parsearFecha(desde), parsearFecha(hasta));
        String agrupacion = PresupuestoFiltros.grupoApartados(grupo);
        model.addAttribute("estado", filtro);
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("tipo", tipo == null ? "" : tipo.strip());
        model.addAttribute("fuente", fuente == null ? "" : fuente.strip());
        model.addAttribute("desde", desde == null ? "" : desde.strip());
        model.addAttribute("hasta", hasta == null ? "" : hasta.strip());
        model.addAttribute("grupo", agrupacion);
        model.addAttribute("fuentesOpciones", PresupuestoFiltros.fuentesEnApartados(lista));
        model.addAttribute("apartados", filtrados);
        model.addAttribute("gruposApartados",
                PresupuestoFiltros.agruparApartados(filtrados, agrupacion));
        model.addAttribute("resumen", presupuesto.resumenApartados());
        return "dafim/presupuesto/apartados";
    }

    @PostMapping("/apartados/{id}/liberar")
    public String liberarApartado(@PathVariable Long id, RedirectAttributes flash) {
        try {
            presupuesto.liberarApartado(id);
            flash.addFlashAttribute("exito",
                    "Apartado liberado: el dinero volvio a estar libre.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dafim/presupuesto/apartados";
    }

    @PostMapping("/apartados/{id}/usar")
    public String usarApartado(@PathVariable Long id, RedirectAttributes flash) {
        try {
            presupuesto.marcarApartadoUsado(id);
            flash.addFlashAttribute("exito",
                    "Apartado marcado como pagado. Ya no resta del saldo libre.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dafim/presupuesto/apartados";
    }

    @GetMapping("/apartados/{id}/banco")
    public String agregarBancoForm(@PathVariable Long id, Model model,
                                   RedirectAttributes flash) {
        try {
            var form = presupuesto.formularioAgregarBanco(id);
            model.addAttribute("apartado", form.getApartado());
            model.addAttribute("vista", form.getVista());
            return "dafim/presupuesto/apartado-banco";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/dafim/presupuesto/apartados";
        }
    }

    @PostMapping("/apartados/{id}/banco")
    public String agregarBanco(@PathVariable Long id,
                               @RequestParam(required = false) String montoBanco,
                               Authentication auth, RedirectAttributes flash) {
        BigDecimal banco = parsearMonto(montoBanco == null ? "" : montoBanco.strip(), null);
        if (banco == null || banco.signum() <= 0) {
            flash.addFlashAttribute("error",
                    "Escribe el monto de banco a apartar (solo numeros, mayor que cero).");
            return "redirect:/dafim/presupuesto/apartados/" + id + "/banco";
        }
        try {
            Apartado a = presupuesto.agregarBanco(id, banco,
                    auth == null ? "" : auth.getName());
            flash.addFlashAttribute("exito",
                    "Se aparto " + quetzales(a.getMontoBanco())
                            + " de banco en la fuente " + a.getFuente() + ".");
            return "redirect:/dafim/presupuesto/apartados";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/dafim/presupuesto/apartados/" + id + "/banco";
        }
    }

    // =============================== CARGAS ==============================

    @GetMapping("/cargas")
    public String cargas(Model model) {
        model.addAttribute("cargas", presupuesto.historialCargas());
        model.addAttribute("cargasCaja", presupuesto.historialCargasCaja());
        return "dafim/presupuesto/cargas";
    }

    @PostMapping("/cargas/{id}/eliminar")
    public String eliminarCarga(@PathVariable Long id, RedirectAttributes flash) {
        try {
            presupuesto.eliminarCarga(id);
            flash.addFlashAttribute("exito",
                    "Carga de presupuesto eliminada. Si era la activa, ahora se usa la mas reciente.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dafim/presupuesto/cargas";
    }

    @PostMapping("/cargas-caja/{id}/eliminar")
    public String eliminarCargaCaja(@PathVariable Long id, RedirectAttributes flash) {
        try {
            presupuesto.eliminarCargaCaja(id);
            flash.addFlashAttribute("exito",
                    "Carga de boletin de caja eliminada. Si era la activa, ahora se usa la mas reciente.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dafim/presupuesto/cargas";
    }

    // ============================== helpers ==============================

    private static boolean verdad(String valor) {
        if (valor == null || valor.isBlank()) return false;
        String s = valor.strip();
        return "1".equals(s) || "on".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
    }

    private static LocalDate parsearFecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Lee un monto escrito a mano (admite "Q" y comas de miles). Devuelve
     * null cuando el texto esta vacio, no se puede leer o es negativo; en
     * los dos ultimos casos deja el aviso en el modelo como "errorMonto".
     */
    private static BigDecimal parsearMonto(String montoTexto, Model model) {
        if (montoTexto == null || montoTexto.isEmpty()) return null;
        try {
            BigDecimal valor = new BigDecimal(montoTexto
                    .replace("Q", "").replace("q", "")
                    .replace(",", "").trim());
            if (valor.signum() < 0) {
                if (model != null) {
                    model.addAttribute("errorMonto",
                            "El monto no puede ser negativo; se ignoro.");
                }
                return null;
            }
            return valor;
        } catch (NumberFormatException e) {
            if (model != null) {
                model.addAttribute("errorMonto", "'" + montoTexto
                        + "' no es un monto valido y se ignoro. "
                        + "Escribe solo numeros, por ejemplo 5000 o 12,500.50.");
            }
            return null;
        }
    }

    private static String fecha(LocalDate f) {
        return f == null ? "?" : F_FECHA.format(f);
    }

    private static String quetzales(BigDecimal v) {
        NumberFormat nf = NumberFormat.getNumberInstance(ES_GT);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "Q" + nf.format(v == null ? BigDecimal.ZERO : v);
    }
}
