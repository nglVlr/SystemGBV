package com.granados.sistema.dafim.presupuesto.distribuir;

import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.MesAnio;
import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.ResultadoAplicar;
import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.ResultadoClasificacion;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.repository.FuenteFinanciamientoRepository;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capa web del asistente de distribucion de pagos: una hoja de calculo con
 * las facturas del mes que se clasifican (IA o reglas locales), se revisan y
 * se convierten en apartados en lote. Permiso: el de siempre de
 * /dafim/presupuesto/** (rol PRESUPUESTO).
 *
 * Rutas:
 *   GET  /dafim/presupuesto/distribuir             la hoja (mes, anio opcionales)
 *   POST /dafim/presupuesto/distribuir/clasificar  JSON {mes, anio} -> {filas, ia, totales}
 *   POST /dafim/presupuesto/distribuir/aplicar     JSON {filas} -> {creados, rechazados}
 *
 * Los POST son JSON (@RequestBody/@ResponseBody); la hoja manda el token CSRF
 * en el header, como cualquier formulario del sistema. El GET ya entrega las
 * filas pre-clasificadas con las reglas LOCALES y mapeadas a lineas (rapido,
 * sin llamar a Gemini); el boton "Clasificar con IA" repite el pipeline con
 * Gemini y reemplaza la propuesta.
 */
@Controller
@RequestMapping("/dafim/presupuesto/distribuir")
public class DistribucionController {

    private static final Logger log = LoggerFactory.getLogger(DistribucionController.class);

    private final DistribucionService distribucion;
    private final PresupuestoService presupuesto;
    private final FuenteFinanciamientoRepository fuentes;
    private final GeminiService gemini;

    public DistribucionController(DistribucionService distribucion,
                                  PresupuestoService presupuesto,
                                  FuenteFinanciamientoRepository fuentes,
                                  GeminiService gemini) {
        this.distribucion = distribucion;
        this.presupuesto = presupuesto;
        this.fuentes = fuentes;
        this.gemini = gemini;
    }

    @GetMapping
    public String distribuir(@RequestParam(required = false) Integer mes,
                             @RequestParam(required = false) Integer anio,
                             Model model) {
        List<MesAnio> meses = distribucion.mesesConFacturas();
        // Sin parametros validos: el mes mas reciente que tenga facturas;
        // sin facturas en el sistema, el mes actual (la hoja sale vacia).
        MesAnio porDefecto = meses.isEmpty() ? null : meses.get(0);
        LocalDate hoy = LocalDate.now();
        int m = mesValido(mes) ? mes
                : (porDefecto != null ? porDefecto.mes() : hoy.getMonthValue());
        int a = anioValido(anio) ? anio
                : (porDefecto != null ? porDefecto.anio() : hoy.getYear());

        List<FilaDistribucion> filas = distribucion.cargarFilas(m, a);
        distribucion.clasificarSoloLocal(filas);
        distribucion.mapearLineas(filas);

        model.addAttribute("filas", filas);
        model.addAttribute("meses", meses);
        model.addAttribute("fuentes", fuentes.findAllByOrderByCodigo());
        model.addAttribute("renglones", renglonesActivos(presupuesto.destinosPosibles()));
        model.addAttribute("grupos", ClasificadorLocalService.GRUPOS);
        model.addAttribute("iaActiva", gemini.estaDisponible());
        model.addAttribute("mes", m);
        model.addAttribute("anio", a);
        return "dafim/presupuesto/distribuir";
    }

    /**
     * Clasifica el mes con el pipeline completo (Gemini si hay key, si no
     * reglas locales) y devuelve las filas listas para la hoja. mes/anio
     * fuera de rango caen al mes actual, como en disponibilidad.
     */
    @PostMapping("/clasificar")
    @ResponseBody
    public ResultadoClasificacion clasificar(
            @RequestBody(required = false) PeticionClasificar peticion) {
        Integer mes = peticion == null ? null : peticion.getMes();
        Integer anio = peticion == null ? null : peticion.getAnio();
        LocalDate hoy = LocalDate.now();
        int m = mesValido(mes) ? mes : hoy.getMonthValue();
        int a = anioValido(anio) ? anio : hoy.getYear();
        ResultadoClasificacion r = distribucion.clasificar(m, a);
        log.info("Distribucion {}/{} clasificada ({} filas, ia={})", m, a,
                r.filas().size(), r.ia());
        return r;
    }

    /**
     * Crea los apartados de las filas tal como las ve el usuario en la hoja.
     * Cada fila se aplica en su propia transaccion: las malas se reportan en
     * rechazados con su numero de fila (desde 1) sin arrastrar al lote.
     */
    @PostMapping("/aplicar")
    @ResponseBody
    public ResultadoAplicar aplicar(@RequestBody(required = false) PeticionAplicar peticion,
                                    Authentication auth) {
        List<FilaDistribucion> filas = peticion == null || peticion.getFilas() == null
                ? List.of() : peticion.getFilas();
        String usuario = auth == null ? "" : auth.getName();
        ResultadoAplicar r = distribucion.aplicar(filas, usuario);
        log.info("Distribucion aplicada por {}: {} creados, {} rechazados",
                usuario, r.creados(), r.rechazados().size());
        return r;
    }

    // ============================== helpers ==============================

    /**
     * Renglones distintos de la carga activa para el dropdown de la hoja,
     * ordenados por codigo; la descripcion es la no-vacia mas frecuente del
     * renglon (en empate gana la primera vista, igual que en "donde pagar").
     */
    private static List<RenglonOpcion> renglonesActivos(List<LineaPresupuesto> lineas) {
        Map<String, Map<String, Integer>> conteo = new LinkedHashMap<>();
        for (LineaPresupuesto l : lineas) {
            String codigo = l.getRenglon() == null ? "" : l.getRenglon().strip();
            if (codigo.isEmpty()) continue;
            String desc = l.getDescripcion() == null ? "" : l.getDescripcion().strip();
            Map<String, Integer> porDesc = conteo.computeIfAbsent(codigo,
                    k -> new LinkedHashMap<>());
            if (!desc.isEmpty()) {
                porDesc.merge(desc, 1, Integer::sum);
            }
        }
        List<RenglonOpcion> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : conteo.entrySet()) {
            // max estable: en empate de veces se queda la primera descripcion
            String mejor = e.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
            out.add(new RenglonOpcion(e.getKey(), mejor));
        }
        out.sort(Comparator.comparing(RenglonOpcion::codigo));
        return out;
    }

    private static boolean mesValido(Integer mes) {
        return mes != null && mes >= 1 && mes <= 12;
    }

    private static boolean anioValido(Integer anio) {
        return anio != null && anio >= 2020 && anio <= 2100;
    }

    /** Una opcion del dropdown de renglon en la hoja: codigo SICOIN y su descripcion. */
    public record RenglonOpcion(String codigo, String descripcion) { }

    /** Peticion JSON de /clasificar: el mes y anio a clasificar. */
    public static class PeticionClasificar {
        private Integer mes;
        private Integer anio;

        public Integer getMes() { return mes; }
        public void setMes(Integer mes) { this.mes = mes; }
        public Integer getAnio() { return anio; }
        public void setAnio(Integer anio) { this.anio = anio; }
    }

    /** Peticion JSON de /aplicar: las filas de la hoja tal como las ve el usuario. */
    public static class PeticionAplicar {
        private List<FilaDistribucion> filas = List.of();

        public List<FilaDistribucion> getFilas() { return filas; }
        public void setFilas(List<FilaDistribucion> filas) { this.filas = filas; }
    }
}
