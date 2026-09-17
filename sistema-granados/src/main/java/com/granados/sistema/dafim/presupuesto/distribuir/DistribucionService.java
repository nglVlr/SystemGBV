package com.granados.sistema.dafim.presupuesto.distribuir;

import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.paquetes.entity.FacturaSat;
import com.granados.sistema.dafim.paquetes.repository.FacturaSatRepository;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.BusquedaPago;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.ContextoPago;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.LineaFuente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Motor del asistente de distribucion de pagos (/dafim/presupuesto/distribuir):
 * carga las facturas SAT del mes, las clasifica (Gemini si hay key, si no las
 * reglas locales), apunta cada una a la mejor linea presupuestaria de la carga
 * activa validando saldos, calcula los totales por grupo y fuente del pie de la
 * hoja y, al "Aplicar", crea los apartados en lote.
 *
 * El mapeo de lineas reusa la busqueda de "donde pagar" sobre una foto
 * precargada (PresupuestoService.ContextoPago): con renglon clasificado se
 * toman solo las lineas de ESE renglon; sin renglon se busca texto libre con
 * la descripcion, igual que la consulta manual. Se prefiere la linea cuyo
 * saldo libre cubre el monto y, entre esas, la que ademas tiene dinero real
 * suficiente en caja; los empates se resuelven por mayor saldo. Si ninguna
 * alcanza, la fila queda apuntada a la de mayor saldo con
 * saldoSuficiente=false (sugerencia de mejor esfuerzo que el usuario corrige
 * en la hoja; la bandera es solo informativa y Aplicar revalida el saldo en
 * el servidor). Sin carga activa o sin candidatos: lineaId null y
 * saldoSuficiente=false.
 */
@Service
public class DistribucionService {

    private static final Logger log = LoggerFactory.getLogger(DistribucionService.class);

    private final FacturaSatRepository facturas;
    private final PresupuestoService presupuesto;
    private final GeminiService gemini;
    private final ClasificadorLocalService local;

    public DistribucionService(FacturaSatRepository facturas,
                               PresupuestoService presupuesto,
                               GeminiService gemini,
                               ClasificadorLocalService local) {
        this.facturas = facturas;
        this.presupuesto = presupuesto;
        this.gemini = gemini;
        this.local = local;
    }

    // ------------------------------ carga ------------------------------

    /**
     * Un par anio/mes que tiene facturas SAT cargadas, con su etiqueta para
     * el selector ("Septiembre 2026") y cuantas facturas tiene ese mes.
     * CONTRATO con la hoja (distribuir.js): los campos son mes, anio,
     * etiqueta y facturas.
     */
    public record MesAnio(int anio, int mes, String etiqueta, long facturas) { }

    /** Pares anio/mes con facturas y su conteo, del mas reciente al mas viejo. */
    public List<MesAnio> mesesConFacturas() {
        List<MesAnio> out = new ArrayList<>();
        for (Object[] fila : facturas.mesesConConteo()) {
            int anio = ((Number) fila[0]).intValue();
            int mes = ((Number) fila[1]).intValue();
            out.add(new MesAnio(anio, mes, etiquetaMes(anio, mes),
                    ((Number) fila[2]).longValue()));
        }
        return out;
    }

    /** "Septiembre 2026" a partir del nombre en mayusculas de Constantes.MESES_NOMBRE. */
    private static String etiquetaMes(int anio, int mes) {
        String nombre = Constantes.MESES_NOMBRE.getOrDefault(mes, "");
        if (nombre.isEmpty()) {
            return mes + "/" + anio;
        }
        return nombre.charAt(0) + nombre.substring(1).toLowerCase(Locale.ROOT) + " " + anio;
    }

    /** Filas iniciales del mes: una por factura, sin clasificar ni mapear todavia. */
    @Transactional(readOnly = true)
    public List<FilaDistribucion> cargarFilas(int mes, int anio) {
        List<FilaDistribucion> filas = new ArrayList<>();
        for (FacturaSat f : facturas.findByAnioAndMesOrderByIdAsc(anio, mes)) {
            filas.add(new FilaDistribucion(f.getId(), f.getDescripcion(),
                    f.getNombreEmisor(), BigDecimal.valueOf(f.getMonto())));
        }
        return filas;
    }

    // --------------------------- clasificacion ---------------------------

    /**
     * Pipeline completo del boton "Clasificar con IA": carga las facturas del
     * mes, las clasifica (Gemini o local) y las mapea a lineas con bandera de
     * saldo. ia=true solo si la clasificacion vino de Gemini.
     */
    @Transactional(readOnly = true)
    public ResultadoClasificacion clasificar(int mes, int anio) {
        List<FilaDistribucion> filas = cargarFilas(mes, anio);
        boolean ia = clasificarConIaOLocal(filas);
        mapearLineas(filas);
        return new ResultadoClasificacion(filas, ia, totales(filas));
    }

    /**
     * Orquesta Gemini -> local: si Gemini no esta disponible o falla (lista
     * vacia), TODAS las filas pasan por el clasificador local; si respondio,
     * solo las filas que dejo sin grupo se completan con el local. Devuelve
     * true si la clasificacion vino de Gemini.
     */
    public boolean clasificarConIaOLocal(List<FilaDistribucion> filas) {
        if (filas == null || filas.isEmpty()) {
            return false;
        }
        List<FilaDistribucion> viaGemini = gemini.estaDisponible()
                ? gemini.clasificar(filas) : List.of();
        if (viaGemini.isEmpty()) {
            clasificarSoloLocal(filas);
            return false;
        }
        for (FilaDistribucion f : filas) {
            if (f.getGrupo() == null || f.getGrupo().isBlank()) {
                local.clasificar(f);
            }
        }
        return true;
    }

    /** Clasifica todas las filas con las reglas locales, sin llamar a Gemini. */
    public void clasificarSoloLocal(List<FilaDistribucion> filas) {
        if (filas == null) return;
        for (FilaDistribucion f : filas) {
            local.clasificar(f);
        }
    }

    // ------------------------- mapeo a lineas -------------------------

    /**
     * Apunta cada fila a su mejor linea de la carga activa (ver la doc de la
     * clase para la regla de eleccion). Sin carga activa deja todas las filas
     * con lineaId null y saldoSuficiente=false.
     */
    public void mapearLineas(List<FilaDistribucion> filas) {
        if (filas == null || filas.isEmpty()) {
            return;
        }
        Optional<ContextoPago> ctx = presupuesto.contextoPago();
        if (ctx.isEmpty()) {
            for (FilaDistribucion f : filas) {
                f.setLineaId(null);
                f.setSaldoSuficiente(false);
            }
            return;
        }
        for (FilaDistribucion f : filas) {
            mapearLinea(ctx.get(), f);
        }
    }

    private static void mapearLinea(ContextoPago ctx, FilaDistribucion f) {
        if (f == null) {
            return;
        }
        f.setLineaId(null);
        f.setSaldoSuficiente(false);
        BigDecimal monto = f.getMonto();
        if (monto == null || monto.signum() <= 0) {
            return;
        }
        String renglon = f.getRenglon() == null ? "" : f.getRenglon().strip();
        List<LineaFuente> candidatos = new ArrayList<>();
        if (!renglon.isEmpty()) {
            // Renglon clasificado: solo lineas de ESE renglon (no se adivina
            // otro aunque la descripcion pegue; el usuario lo corrige a mano).
            for (BusquedaPago b : ctx.buscar(renglon, monto)) {
                if (renglon.equals(b.getRenglon())) {
                    candidatos.addAll(b.getLineas());
                }
            }
        } else if (f.getDescripcion() != null && !f.getDescripcion().isBlank()) {
            // Sin renglon: texto libre con la descripcion, como el "donde pagar".
            for (BusquedaPago b : ctx.buscar(f.getDescripcion(), monto)) {
                candidatos.addAll(b.getLineas());
            }
        }
        LineaFuente mejor = elegirMejor(candidatos);
        if (mejor == null) {
            return;
        }
        f.setLineaId(mejor.getId());
        f.setFuente(mejor.getFuente() == null ? "" : mejor.getFuente());
        f.setSaldoSuficiente(mejor.isAlcanza());
    }

    /**
     * La mejor linea candidata: primero las que alcanzan con saldo Y con caja
     * real, luego las que alcanzan solo con saldo y al final la de mayor saldo
     * (mejor esfuerzo con saldoSuficiente=false). null si no hay candidatos.
     */
    static LineaFuente elegirMejor(List<LineaFuente> candidatos) {
        if (candidatos == null || candidatos.isEmpty()) {
            return null;
        }
        Comparator<LineaFuente> porSaldo = Comparator.comparing(
                LineaFuente::getSaldoDisponible,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        return candidatos.stream()
                .filter(LineaFuente::isAlcanza)
                .filter(LineaFuente::isAlcanzaBanco)
                .max(porSaldo)
                .or(() -> candidatos.stream().filter(LineaFuente::isAlcanza).max(porSaldo))
                .or(() -> candidatos.stream().max(porSaldo))
                .orElse(null);
    }

    // ------------------------------ totales ------------------------------

    /**
     * Totales del pie de la hoja: suma de montos por grupo (las filas sin
     * grupo cuentan como OTROS) y por fuente (solo las filas ya mapeadas a
     * una), mas el total general. Las llaves conservan el orden de primera
     * aparicion en la hoja.
     */
    public static Totales totales(List<FilaDistribucion> filas) {
        Map<String, BigDecimal> porGrupo = new LinkedHashMap<>();
        Map<String, BigDecimal> porFuente = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FilaDistribucion f : filas == null ? List.<FilaDistribucion>of() : filas) {
            if (f == null) continue;
            BigDecimal monto = f.getMonto() == null ? BigDecimal.ZERO : f.getMonto();
            total = total.add(monto);
            String grupo = f.getGrupo() == null || f.getGrupo().isBlank()
                    ? ClasificadorLocalService.GRUPO_OTROS : f.getGrupo();
            porGrupo.merge(grupo, monto, BigDecimal::add);
            if (f.getFuente() != null && !f.getFuente().isBlank()) {
                porFuente.merge(f.getFuente(), monto, BigDecimal::add);
            }
        }
        return new Totales(porGrupo, porFuente, total);
    }

    // ------------------------------ aplicar ------------------------------

    /**
     * Crea los apartados del lote. Cada fila se aplica en SU PROPIA
     * transaccion (PresupuestoService.apartarFactura es @Transactional): si
     * una falla, solo lo suyo se revierte y se reporta el motivo, sin
     * arrastrar a las demas. Por eso este metodo NO es @Transactional: con
     * una transaccion unica, la primera fila mala marcaria todo el lote como
     * rollback-only aunque se capture la excepcion.
     *
     * El saldo SIEMPRE se revalida en el servidor dentro de apartarFactura
     * (contra la BD, con la linea bloqueada): la bandera saldoSuficiente que
     * manda la hoja es solo informativa y aqui no se usa para decidir. Asi,
     * dos filas apuntadas a la misma linea no se duplican: la segunda ve el
     * saldo ya comprometido por la primera. Solo se rechazan de entrada las
     * filas sin linea asignada o con monto invalido.
     */
    public ResultadoAplicar aplicar(List<FilaDistribucion> filas, String username) {
        List<Rechazo> rechazados = new ArrayList<>();
        int creados = 0;
        List<FilaDistribucion> lista = filas == null ? List.of() : filas;
        for (int i = 0; i < lista.size(); i++) {
            FilaDistribucion f = lista.get(i);
            int numero = i + 1; // la hoja numera las filas desde 1 (columna #)
            Long facturaId = f == null ? null : f.getFacturaId();
            String motivo = motivoRechazoPrevio(f);
            if (motivo != null) {
                rechazados.add(new Rechazo(numero, facturaId, motivo));
                continue;
            }
            try {
                Apartado a = presupuesto.apartarFactura(f.getLineaId(), f.getDescripcion(),
                        f.getMonto(), facturaId, f.getGrupo(), username);
                log.info("Apartado {} creado desde distribucion (factura {}, fila {})",
                        a.getId(), facturaId, numero);
                creados++;
            } catch (IllegalArgumentException e) {
                rechazados.add(new Rechazo(numero, facturaId, e.getMessage()));
            } catch (Exception e) {
                log.warn("Fila {} de distribucion fallo de forma inesperada", numero, e);
                rechazados.add(new Rechazo(numero, facturaId,
                        "Error inesperado al apartar; revisa el log del sistema."));
            }
        }
        return new ResultadoAplicar(creados, rechazados);
    }

    /** Razon para ni siquiera intentar apartar la fila; null si se puede intentar. */
    private static String motivoRechazoPrevio(FilaDistribucion f) {
        if (f == null) {
            return "La fila llego vacia.";
        }
        if (f.getLineaId() == null) {
            return "Sin linea presupuestaria asignada.";
        }
        if (f.getMonto() == null || f.getMonto().signum() <= 0) {
            return "El monto debe ser mayor que cero.";
        }
        return null;
    }

    // ------------------------- tipos de resultado -------------------------

    /** Totales por grupo y por fuente para el pie de la hoja, mas el general. */
    public record Totales(Map<String, BigDecimal> porGrupo,
                          Map<String, BigDecimal> porFuente,
                          BigDecimal total) { }

    /** Respuesta de clasificar: las filas listas para la hoja y si vinieron de la IA. */
    public record ResultadoClasificacion(List<FilaDistribucion> filas, boolean ia,
                                         Totales totales) { }

    /** Resultado del lote: cuantos apartados se crearon y por que se rechazo cada fila mala. */
    public record ResultadoAplicar(int creados, List<Rechazo> rechazados) { }

    /**
     * Una fila que no se pudo apartar: su numero visible en la hoja (columna
     * #, desde 1), la factura como respaldo para ubicarla y el motivo.
     */
    public record Rechazo(int fila, Long facturaId, String motivo) { }
}
