package com.granados.sistema.dafim.presupuesto.service;

import com.granados.sistema.config.ExclusiveJobs;
import com.granados.sistema.config.StorageService;
import com.granados.sistema.dafim.compras.entity.HistorialCompra;
import com.granados.sistema.dafim.compras.repository.HistorialCompraRepository;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import com.granados.sistema.dafim.presupuesto.dto.BoletinParseado;
import com.granados.sistema.dafim.presupuesto.dto.EjecucionParseada;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria.TipoDineroCaja;
import com.granados.sistema.dafim.presupuesto.dto.LineaEjecucion;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.entity.CargaCaja;
import com.granados.sistema.dafim.presupuesto.entity.CargaPresupuesto;
import com.granados.sistema.dafim.presupuesto.entity.CuentaMonetaria;
import com.granados.sistema.dafim.presupuesto.entity.FuenteFinanciamiento;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.parser.ParserBoletinCaja;
import com.granados.sistema.dafim.presupuesto.parser.ParserEjecucionEgresos;
import com.granados.sistema.dafim.presupuesto.repository.ApartadoRepository;
import com.granados.sistema.dafim.presupuesto.repository.CargaCajaRepository;
import com.granados.sistema.dafim.presupuesto.repository.CargaPresupuestoRepository;
import com.granados.sistema.dafim.presupuesto.repository.CuentaMonetariaRepository;
import com.granados.sistema.dafim.presupuesto.repository.FuenteFinanciamientoRepository;
import com.granados.sistema.dafim.presupuesto.repository.LineaPresupuestoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logica del modulo de presupuesto: importa el PDF SICOIN de ejecucion de
 * egresos (una sola carga ACTIVA a la vez, las anteriores quedan
 * REEMPLAZADAS) y cruza esa foto oficial contra los pagos reales del
 * sistema (historial_compras) para responder la pregunta del municipio:
 * "cuanto queda disponible en cada renglon antes de aprobar un cheque".
 *
 * Las agregaciones se hacen EN MEMORIA con streams (convencion del
 * proyecto: las tablas son pequenas y asi las consultas se pueden probar
 * sin Spring): los metodos estaticos puros reciben listas/mapas y los
 * metodos de instancia solo orquestan repositorios + esos helpers.
 */
@Service
public class PresupuestoService {

    private static final Logger log = LoggerFactory.getLogger(PresupuestoService.class);

    /** Umbral de ejecucion (devengado + pagos del mes)/vigente para el semaforo. */
    private static final double UMBRAL_POR_AGOTARSE = 0.85;

    /** Maximo de pagos del sistema que se muestran en el detalle de un renglon. */
    private static final int MAX_PAGOS_DETALLE = 50;

    /** Valor con el que el PDF SICOIN marca las lineas que no tienen proyecto real. */
    private static final String SIN_PROYECTO = "000 SIN PROYECTO";

    /** Titulo de grupo para las lineas sin programa (o con programa en blanco). */
    private static final String SIN_PROGRAMA = "SIN PROGRAMA";

    private final CargaPresupuestoRepository cargas;
    private final LineaPresupuestoRepository lineas;
    private final FuenteFinanciamientoRepository fuentes;
    private final HistorialCompraRepository historial;
    private final CargaCajaRepository cargasCaja;
    private final CuentaMonetariaRepository cuentasCaja;
    private final ApartadoRepository apartados;
    private final StorageService storage;
    private final ExclusiveJobs jobs;
    private final TransactionTemplate tx;

    public PresupuestoService(CargaPresupuestoRepository cargas,
                              LineaPresupuestoRepository lineas,
                              FuenteFinanciamientoRepository fuentes,
                              HistorialCompraRepository historial,
                              CargaCajaRepository cargasCaja,
                              CuentaMonetariaRepository cuentasCaja,
                              ApartadoRepository apartados,
                              StorageService storage,
                              ExclusiveJobs jobs,
                              PlatformTransactionManager txManager) {
        this.cargas = cargas;
        this.lineas = lineas;
        this.fuentes = fuentes;
        this.historial = historial;
        this.cargasCaja = cargasCaja;
        this.cuentasCaja = cuentasCaja;
        this.apartados = apartados;
        this.storage = storage;
        this.jobs = jobs;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------ escrituras ------------------------------

    /**
     * Importa el PDF SICOIN de ejecucion de egresos: la carga nueva queda
     * ACTIVA y todas las anteriores pasan a REEMPLAZADA. El parseo corre
     * SIN conexion a BD para no ocupar el pool mientras se lee el PDF.
     */
    public CargaPresupuesto importarPdf(MultipartFile archivo, String username) throws IOException {
        EjecucionParseada ejecucion = ParserEjecucionEgresos.parsear(archivo.getInputStream());
        if (ejecucion.getLineas().isEmpty()) {
            throw new IllegalArgumentException(
                    "El PDF no contiene lineas de ejecucion reconocibles. "
                            + "Verifica que sea el PDF de ejecucion de egresos.");
        }
        return jobs.run("presupuesto-egresos", () -> tx.execute(status ->
                persistirEgresos(ejecucion, archivo, username)));
    }

    private CargaPresupuesto persistirEgresos(EjecucionParseada ejecucion,
                                              MultipartFile archivo, String username) {
        for (CargaPresupuesto anterior : cargas.findAll()) {
            if (CargaPresupuesto.EST_ACTIVA.equals(anterior.getEstado())) {
                anterior.setEstado(CargaPresupuesto.EST_REEMPLAZADA);
                cargas.save(anterior);
            }
        }

        CargaPresupuesto carga = new CargaPresupuesto();
        carga.setAnio(ejecucion.getAnio());
        carga.setPeriodoDesde(ejecucion.getPeriodoDesde());
        carga.setPeriodoHasta(ejecucion.getPeriodoHasta());
        String nombreOriginal = archivo.getOriginalFilename();
        carga.setNombreArchivo(nombreOriginal == null ? "ejecucion.pdf" : nombreOriginal);
        carga.setFechaCarga(LocalDateTime.now());
        carga.setUsuario(username);
        carga.setTotalLineas(ejecucion.getLineas().size());
        carga.setTotalVigente(ejecucion.getTotalVigente());
        carga.setTotalDevengado(ejecucion.getTotalDevengado());
        carga.setTotalPagado(ejecucion.getTotalPagado());
        carga.setEstado(CargaPresupuesto.EST_ACTIVA);
        carga = cargas.save(carga);

        List<LineaPresupuesto> entidades = new ArrayList<>();
        for (LineaEjecucion le : ejecucion.getLineas()) {
            entidades.add(mapearLinea(le, carga.getId()));
        }
        lineas.saveAll(entidades);

        sembrarFuentes(ejecucion.getLineas());

        try {
            storage.guardarSubida(archivo, "presupuesto_");
        } catch (Exception ex) {
            // el respaldo en disco es best-effort: no aborta la importacion
            log.warn("No se pudo guardar la copia de respaldo del PDF de presupuesto: {}",
                    ex.getMessage());
        }
        return carga;
    }

    /**
     * Importa el PDF SICOIN del Boletin de Caja. Parseo fuera de la TX.
     */
    public CargaCaja importarBoletinCaja(MultipartFile archivo, String username) throws IOException {
        BoletinParseado boletin = ParserBoletinCaja.parsear(archivo.getInputStream());
        if (boletin.getCuentas().isEmpty()) {
            throw new IllegalArgumentException(
                    "El PDF no contiene cuentas reconocibles. "
                            + "Verifica que sea el PDF del boletin de caja.");
        }
        return jobs.run("presupuesto-caja", () -> tx.execute(status ->
                persistirCaja(boletin, archivo, username)));
    }

    private CargaCaja persistirCaja(BoletinParseado boletin, MultipartFile archivo,
                                    String username) {
        for (CargaCaja anterior : cargasCaja.findAll()) {
            if (CargaCaja.EST_ACTIVA.equals(anterior.getEstado())) {
                anterior.setEstado(CargaCaja.EST_REEMPLAZADA);
                cargasCaja.save(anterior);
            }
        }

        CargaCaja carga = new CargaCaja();
        carga.setAnio(boletin.getAnio() == 0 ? LocalDateTime.now().getYear() : boletin.getAnio());
        carga.setFechaCorte(boletin.getFechaCorte());
        String nombreOriginal = archivo.getOriginalFilename();
        carga.setNombreArchivo(nombreOriginal == null ? "boletin-caja.pdf" : nombreOriginal);
        carga.setFechaCarga(LocalDateTime.now());
        carga.setUsuario(username);
        carga.setTotalCuentas(boletin.getCuentas().size());
        carga.setTotalNuevoSaldo(boletin.getTotalNuevoSaldo());
        carga.setEstado(CargaCaja.EST_ACTIVA);
        carga = cargasCaja.save(carga);

        List<CuentaMonetaria> entidades = new ArrayList<>();
        for (LineaCuentaMonetaria lc : boletin.getCuentas()) {
            entidades.add(mapearCuenta(lc, carga.getId()));
        }
        cuentasCaja.saveAll(entidades);

        try {
            storage.guardarSubida(archivo, "caja_");
        } catch (Exception ex) {
            log.warn("No se pudo guardar la copia de respaldo del PDF de caja: {}", ex.getMessage());
        }
        return carga;
    }

    /**
     * Borra una carga con todas sus lineas. Si la eliminada era la ACTIVA,
     * la mas reciente de las restantes pasa a ACTIVA para que el modulo no
     * se quede sin presupuesto vigente.
     */
    @Transactional
    public void eliminarCarga(Long id) {
        CargaPresupuesto carga = cargas.findById(id).orElseThrow(
                () -> new IllegalArgumentException("La carga de presupuesto no existe."));
        boolean eraActiva = CargaPresupuesto.EST_ACTIVA.equals(carga.getEstado());
        lineas.deleteByCargaId(id);
        cargas.delete(carga);
        if (eraActiva) {
            List<CargaPresupuesto> restantes = cargas.findAllByOrderByFechaCargaDesc();
            if (!restantes.isEmpty()) {
                CargaPresupuesto reciente = restantes.get(0);
                reciente.setEstado(CargaPresupuesto.EST_ACTIVA);
                cargas.save(reciente);
            }
        }
    }

    /**
     * Borra una carga de boletin de caja con sus cuentas. Si era la ACTIVA,
     * la mas reciente restante pasa a ACTIVA.
     */
    @Transactional
    public void eliminarCargaCaja(Long id) {
        CargaCaja carga = cargasCaja.findById(id).orElseThrow(
                () -> new IllegalArgumentException("La carga de boletin de caja no existe."));
        boolean eraActiva = CargaCaja.EST_ACTIVA.equals(carga.getEstado());
        cuentasCaja.deleteByCargaId(id);
        cargasCaja.delete(carga);
        if (eraActiva) {
            List<CargaCaja> restantes = cargasCaja.findAllByOrderByFechaCargaDesc();
            if (!restantes.isEmpty()) {
                CargaCaja reciente = restantes.get(0);
                reciente.setEstado(CargaCaja.EST_ACTIVA);
                cargasCaja.save(reciente);
            }
        }
    }

    /** Edita el nombre visible de una fuente del catalogo (el codigo no cambia). */
    @Transactional
    public FuenteFinanciamiento renombrarFuente(String codigo, String nombre) {
        FuenteFinanciamiento fuente = fuentes.findById(codigo).orElseThrow(
                () -> new IllegalArgumentException(
                        "La fuente de financiamiento no existe: " + codigo));
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() > 160) {
            throw new IllegalArgumentException(
                    "El nombre de la fuente no puede superar 160 caracteres.");
        }
        fuente.setNombre(n);
        return fuentes.save(fuente);
    }

    // ------------------------------- consultas ------------------------------

    /** La carga ACTIVA mas reciente, si existe. */
    @Transactional(readOnly = true)
    public Optional<CargaPresupuesto> cargaActiva() {
        return cargas.findTopByEstadoOrderByFechaCargaDesc(CargaPresupuesto.EST_ACTIVA);
    }

    /** Todas las cargas (activas y reemplazadas), de la mas nueva a la mas vieja. */
    @Transactional(readOnly = true)
    public List<CargaPresupuesto> historialCargas() {
        return cargas.findAllByOrderByFechaCargaDesc();
    }

    /** La carga ACTIVA del boletin de caja, si existe. */
    @Transactional(readOnly = true)
    public Optional<CargaCaja> cargaCajaActiva() {
        return cargasCaja.findTopByEstadoOrderByFechaCargaDesc(CargaCaja.EST_ACTIVA);
    }

    /** Historial de boletin de caja, de la mas nueva a la mas vieja. */
    @Transactional(readOnly = true)
    public List<CargaCaja> historialCargasCaja() {
        return cargasCaja.findAllByOrderByFechaCargaDesc();
    }

    /**
     * Bitacora general: renglones de la carga activa que coinciden con q
     * (codigo, descripcion, fuente, programa o proyecto).
     */
    @Transactional(readOnly = true)
    public List<LineaPresupuesto> buscarLineas(String q) {
        if (q == null || q.isBlank()) return List.of();
        String n = TextoUtil.norm(q);
        return cargaActiva()
                .map(c -> lineas.findByCargaId(c.getId()).stream()
                        .filter(l -> coincide(n, l.getRenglon(), l.getDescripcion(),
                                l.getFuente(), l.getPrograma(), l.getProyecto(),
                                l.getActividadObra()))
                        .limit(60)
                        .toList())
                .orElse(List.of());
    }

    /** Catalogo de fuentes cuyo codigo o nombre contiene q. */
    @Transactional(readOnly = true)
    public List<FuenteFinanciamiento> buscarFuentes(String q) {
        if (q == null || q.isBlank()) return List.of();
        String n = TextoUtil.norm(q);
        return fuentes.findAllByOrderByCodigo().stream()
                .filter(f -> coincide(n, f.getCodigo(), f.getNombre()))
                .limit(40)
                .toList();
    }

    /** Apartados (reservas) cuyo concepto, renglon, fuente o usuario coincide. */
    @Transactional(readOnly = true)
    public List<Apartado> buscarApartados(String q) {
        if (q == null || q.isBlank()) return List.of();
        String n = TextoUtil.norm(q);
        return apartados.findAllByOrderByFechaDesc().stream()
                .filter(a -> coincide(n, a.getConcepto(), a.getRenglon(), a.getFuente(),
                        a.getDescripcion(), a.getUsuario(), a.getEstado()))
                .limit(40)
                .toList();
    }

    private static boolean coincide(String qNorm, String... campos) {
        for (String c : campos) {
            if (c != null && !c.isBlank() && TextoUtil.norm(c).contains(qNorm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Totales de la carga activa. saldoDisponible = vigente - devengado (lo
     * que aun se puede devengar), consistente con la disponibilidad que el
     * municipio revisa antes de aprobar pagos. Vacio si no hay carga activa.
     */
    @Transactional(readOnly = true)
    public Optional<ResumenGeneral> resumenGeneral() {
        return cargaActiva().map(c -> {
            BigDecimal vigente = nz(c.getTotalVigente());
            BigDecimal devengado = nz(c.getTotalDevengado());
            BigDecimal pagado = nz(c.getTotalPagado());
            return new ResumenGeneral(vigente, devengado, pagado,
                    vigente.subtract(devengado), pctEjecucion(devengado, vigente));
        });
    }

    /**
     * Ejecucion de la carga activa agrupada por renglon, cruzada con los
     * pagos del sistema (historial_compras, todos los meses): la diferencia
     * pagado - pagosSistema muestra que tan al dia esta SICOIN contra lo
     * que el sistema tiene confirmado. Orden: vigente descendente.
     */
    @Transactional(readOnly = true)
    public List<RenglonResumen> porRenglon() {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty()) return List.of();
        List<LineaPresupuesto> ls = lineas.findByCargaId(activa.get().getId());
        return agregarPorRenglon(ls, sumarPagosPorRenglon(historial.findAll()));
    }

    /**
     * Ejecucion de la carga activa agrupada por fuente de financiamiento,
     * con el nombre editable del catalogo y el dinero real del boletin de
     * caja (suma de nuevo saldo de cuentas cuyo codigo empieza por la fuente).
     * Orden: vigente descendente.
     */
    @Transactional(readOnly = true)
    public List<FuenteResumen> porFuente() {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty()) return List.of();
        List<LineaPresupuesto> ls = lineas.findByCargaId(activa.get().getId());
        Map<String, String> nombres = new LinkedHashMap<>();
        for (FuenteFinanciamiento f : fuentes.findAllByOrderByCodigo()) {
            nombres.put(f.getCodigo(), f.getNombre() == null ? "" : f.getNombre());
        }
        return agregarPorFuente(ls, nombres, dineroRealConTipos(cuentasCajaActivas()));
    }

    /**
     * Detalle de un renglon de la carga activa: sus lineas (ordenadas por
     * fuente) y los ultimos pagos del sistema de ese renglon (anio/mes
     * descendente, maximo 50). Vacio si no hay carga activa.
     */
    @Transactional(readOnly = true)
    public Optional<RenglonDetalle> detalleRenglon(String renglon) {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty()) return Optional.empty();
        List<LineaPresupuesto> ls = new ArrayList<>(
                lineas.findByCargaIdAndRenglon(activa.get().getId(), renglon));
        ls.sort(Comparator.comparing(l -> l.getFuente() == null ? "" : l.getFuente()));
        return Optional.of(new RenglonDetalle(ls,
                ultimosPagos(historial.findAll(), renglon, MAX_PAGOS_DETALLE)));
    }

    /**
     * Disponibilidad de los renglones de compra directa (Constantes
     * .RENGLONES_CD) para un mes: lo que queda segun SICOIN menos lo ya
     * pagado en el sistema ese mes, con semaforo. Los mas criticos primero.
     */
    @Transactional(readOnly = true)
    public List<DisponibilidadRenglon> disponibilidad(int mes, int anio) {
        List<LineaPresupuesto> ls = cargaActiva()
                .map(c -> lineas.findByCargaId(c.getId()))
                .orElse(List.of());
        return calcularDisponibilidad(Constantes.RENGLONES_CD, ls,
                historial.findByAnioAndMes(anio, mes));
    }

    /**
     * Responde la pregunta "con que presupuesto pago esto": la consulta
     * libre (tipo de gasto: camionadas, arrendamiento, cemento...) se
     * traduce a renglones candidatos y para cada uno se listan las lineas
     * de la carga activa por fuente, marcando si el saldo alcanza para el
     * monto pedido. Lista vacia si no hay carga activa o consulta.
     */
    @Transactional(readOnly = true)
    public List<BusquedaPago> dondePagar(String consulta, BigDecimal monto) {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty() || consulta == null || consulta.isBlank()) {
            return List.of();
        }
        List<LineaPresupuesto> ls = lineas.findByCargaId(activa.get().getId());
        Map<String, String> nombres = new LinkedHashMap<>();
        for (FuenteFinanciamiento f : fuentes.findAllByOrderByCodigo()) {
            nombres.put(f.getCodigo(), f.getNombre() == null ? "" : f.getNombre());
        }
        List<Apartado> activos = apartadosActivos();
        return buscarDondePagar(consulta, monto, ls, nombres,
                dineroRealPorFuenteYTipo(cuentasCajaActivas()),
                apartadoPresupuestoPorClave(activos),
                apartadoBancoPorFuenteYTipo(activos),
                apartadoPresupuestoPorLinea(ls, activos));
    }

    /**
     * Desglose de una fuente de financiamiento dentro de la carga activa,
     * agrupado por programa (y por proyecto cuando la linea tiene uno
     * real): es la pantalla de analisis para decidir de que lineas sacar
     * fondos antes de tramitar una transferencia. El modulo es
     * CONSULTIVO: aqui no se mueve ni se guarda nada; la modificacion
     * presupuestaria real se hace en SICOIN. Si se pasa un monto, cada
     * linea se marca con alcanza segun si su saldo lo cubre. Vacio si no
     * hay carga activa o la fuente no tiene lineas en ella.
     */
    @Transactional(readOnly = true)
    public Optional<DesgloseFuente> desgloseFuente(String codigo, BigDecimal monto) {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty() || codigo == null) return Optional.empty();
        List<LineaPresupuesto> ls = new ArrayList<>();
        for (LineaPresupuesto l : lineas.findByCargaId(activa.get().getId())) {
            if (codigo.equals(l.getFuente())) ls.add(l);
        }
        if (ls.isEmpty()) return Optional.empty();
        String nombre = fuentes.findById(codigo)
                .map(f -> f.getNombre() == null ? "" : f.getNombre())
                .orElse("");
        List<CuentaMonetaria> cuentas = cuentasDeFuente(codigo, cuentasCajaActivas());
        List<Apartado> activos = apartadosActivos();
        Map<String, BigDecimal> apartadoBanco = apartadoBancoPorFuenteYTipo(activos);
        BigDecimal func = saldoLibre(
                dineroRealDeFuenteParaTipo(codigo, TipoDineroCaja.FUNCIONAMIENTO, cuentas),
                nz(apartadoBanco.get(claveDineroCaja(codigo, TipoDineroCaja.FUNCIONAMIENTO))));
        BigDecimal inv = saldoLibre(
                dineroRealDeFuenteParaTipo(codigo, TipoDineroCaja.INVERSION, cuentas),
                nz(apartadoBanco.get(claveDineroCaja(codigo, TipoDineroCaja.INVERSION))));
        return Optional.of(armarDesglose(codigo, nombre, monto, ls, func.add(inv), cuentas,
                apartadoPresupuestoPorClave(activos),
                apartadoPresupuestoPorLinea(ls, activos),
                apartadoBanco));
    }

    /** Una linea de presupuesto por su id (para precargar origen/destino de la simulacion). */
    @Transactional(readOnly = true)
    public Optional<LineaPresupuesto> lineaPorId(Long id) {
        return id == null ? Optional.empty() : lineas.findById(id);
    }

    /**
     * Todas las lineas de la carga activa: candidatas a recibir fondos en
     * la simulacion de transferencia. Lista vacia si no hay carga activa.
     */
    @Transactional(readOnly = true)
    public List<LineaPresupuesto> destinosPosibles() {
        return cargaActiva()
                .map(c -> lineas.findByCargaId(c.getId()))
                .orElse(List.of());
    }

    /**
     * Simula una transferencia de saldo entre dos lineas (origen ->
     * destino) SIN tocar la base de datos: es la herramienta de analisis
     * previo a la modificacion presupuestaria que luego se tramita en
     * SICOIN, por eso NADA se persiste aqui. Si alguna linea no existe,
     * la simulacion sale invalida y el campo correspondiente
     * (origen/destino) queda en null dentro del resultado.
     */
    @Transactional(readOnly = true)
    public SimulacionTransferencia simularTransferencia(Long origenId, Long destinoId,
                                                        BigDecimal monto) {
        LineaPresupuesto origen = origenId == null ? null : lineas.findById(origenId).orElse(null);
        LineaPresupuesto destino = destinoId == null ? null : lineas.findById(destinoId).orElse(null);
        return calcularTransferencia(origen, destino, monto);
    }

    // ------------------------------ apartados ------------------------------

    /** Reservas ACTIVO: restan del saldo libre hasta que se liberen o se marquen usadas. */
    @Transactional(readOnly = true)
    public List<Apartado> apartadosActivos() {
        return apartados.findByEstadoOrderByFechaDesc(Apartado.EST_ACTIVO);
    }

    @Transactional(readOnly = true)
    public List<Apartado> listarApartados(String estado) {
        if (estado == null || estado.isBlank() || "TODOS".equalsIgnoreCase(estado)) {
            return apartados.findAllByOrderByFechaDesc();
        }
        return apartados.findByEstadoOrderByFechaDesc(estado.strip().toUpperCase());
    }

    @Transactional(readOnly = true)
    public ResumenApartados resumenApartados() {
        List<Apartado> activos = apartadosActivos();
        BigDecimal pres = BigDecimal.ZERO;
        BigDecimal banco = BigDecimal.ZERO;
        for (Apartado a : activos) {
            pres = pres.add(nz(a.getMontoPresupuesto()));
            banco = banco.add(nz(a.getMontoBanco()));
        }
        return new ResumenApartados(activos.size(), pres, banco);
    }

    /**
     * Datos para el formulario de apartar: origen, saldo SICOIN, ya
     * apartado y lo que queda libre (presupuesto y banco por separado).
     */
    @Transactional(readOnly = true)
    public Optional<VistaApartar> vistaApartar(Long lineaId) {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty() || lineaId == null) return Optional.empty();
        LineaPresupuesto linea = lineas.findById(lineaId).orElse(null);
        if (linea == null || !activa.get().getId().equals(linea.getCargaId())) {
            return Optional.empty();
        }
        return armarVistaApartar(activa.get(), linea);
    }

    @Transactional(readOnly = true)
    public Optional<VistaApartar> vistaApartar(String renglon, String fuente,
                                               String actividadObra) {
        return vistaApartar(renglon, fuente, actividadObra, null, null);
    }

    @Transactional(readOnly = true)
    public Optional<VistaApartar> vistaApartar(String renglon, String fuente,
                                               String actividadObra,
                                               String programa, String proyecto) {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty() || renglon == null || fuente == null) {
            return Optional.empty();
        }
        LineaPresupuesto linea = encontrarLinea(activa.get().getId(),
                renglon, fuente, actividadObra, programa, proyecto);
        if (linea == null) return Optional.empty();
        return armarVistaApartar(activa.get(), linea);
    }

    private Optional<VistaApartar> armarVistaApartar(CargaPresupuesto activa,
                                                     LineaPresupuesto linea) {
        return armarVistaApartar(activa, linea, null);
    }

    private Optional<VistaApartar> armarVistaApartar(CargaPresupuesto activa,
                                                     LineaPresupuesto linea,
                                                     Long excluirApartadoId) {
        List<Apartado> activos = sinApartado(apartadosActivos(), excluirApartadoId);
        List<LineaPresupuesto> ls = lineas.findByCargaId(activa.getId());
        BigDecimal apartadoP = apartadoDeLinea(linea,
                apartadoPresupuestoPorClave(activos),
                apartadoPresupuestoPorLinea(ls, activos));
        BigDecimal sicoin = nz(linea.getSaldoDisponible());
        BigDecimal dinero = dineroRealDeFuenteParaPrograma(
                linea.getFuente(), linea.getPrograma(), cuentasCajaActivas());
        BigDecimal apartadoB = valorPorFuenteYTipo(
                apartadoBancoPorFuenteYTipo(activos), linea.getFuente(), linea.getPrograma());
        String nombreFuente = fuentes.findById(linea.getFuente())
                .map(f -> f.getNombre() == null ? "" : f.getNombre())
                .orElse("");
        return Optional.of(new VistaApartar(linea, nombreFuente, activa.getAnio(),
                sicoin, apartadoP, saldoLibre(sicoin, apartadoP),
                dinero, apartadoB, saldoLibre(dinero, apartadoB)));
    }

    /**
     * Crea un apartado ACTIVO. El presupuesto es obligatorio y no puede
     * superar el saldo libre de la linea. El banco es independiente y
     * puede ser 0 (se aparta presupuesto aunque no haya efectivo).
     * lineaId distingue renglones gemelos del mismo programa.
     */
    @Transactional
    public Apartado apartar(Long lineaId, String concepto, BigDecimal montoPresupuesto,
                            BigDecimal montoBanco, String username) {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay presupuesto cargado. Sube el PDF de SICOIN en Cargas.");
        }
        LineaPresupuesto linea = lineas.findById(lineaId == null ? -1L : lineaId).orElse(null);
        if (linea == null || !activa.get().getId().equals(linea.getCargaId())) {
            throw new IllegalArgumentException(
                    "No se encontro la linea en la carga activa. Elige de nuevo el programa.");
        }
        return persistirApartado(activa.get(), linea, concepto, montoPresupuesto,
                montoBanco, username);
    }

    @Transactional
    public Apartado apartar(String renglon, String fuente, String actividadObra,
                            String concepto, BigDecimal montoPresupuesto,
                            BigDecimal montoBanco, String username) {
        return apartar(renglon, fuente, actividadObra, null, null, concepto,
                montoPresupuesto, montoBanco, username);
    }

    @Transactional
    public Apartado apartar(String renglon, String fuente, String actividadObra,
                            String programa, String proyecto, String concepto,
                            BigDecimal montoPresupuesto, BigDecimal montoBanco,
                            String username) {
        Optional<CargaPresupuesto> activa = cargaActiva();
        if (activa.isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay presupuesto cargado. Sube el PDF de SICOIN en Cargas.");
        }
        LineaPresupuesto linea = encontrarLinea(activa.get().getId(),
                renglon, fuente, actividadObra, programa, proyecto);
        if (linea == null) {
            throw new IllegalArgumentException(
                    "No se encontro la linea " + renglon + " / " + fuente
                            + " en la carga activa. Elige de nuevo el programa.");
        }
        return persistirApartado(activa.get(), linea, concepto, montoPresupuesto,
                montoBanco, username);
    }

    private Apartado persistirApartado(CargaPresupuesto activa, LineaPresupuesto linea,
                                       String concepto, BigDecimal montoPresupuesto,
                                       BigDecimal montoBanco, String username) {
        lineas.lockById(linea.getId()).orElseThrow(() -> new IllegalArgumentException(
                "No se encontro la linea " + linea.getRenglon() + " / " + linea.getFuente()
                        + " en la carga activa."));
        VistaApartar vista = armarVistaApartar(activa, linea)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se pudo calcular el saldo libre de la linea."));
        validarApartado(montoPresupuesto, montoBanco,
                vista.getPresupuestoLibre(), vista.getBancoLibre());

        String conceptoLimpio = concepto == null ? "" : concepto.strip();
        if (conceptoLimpio.isEmpty()) {
            conceptoLimpio = linea.getDescripcion() == null ? "Pago" : linea.getDescripcion();
        }

        Apartado a = new Apartado();
        a.setAnio(activa.getAnio());
        a.setRenglon(linea.getRenglon());
        a.setFuente(linea.getFuente());
        a.setActividadObra(linea.getActividadObra() == null ? "" : linea.getActividadObra());
        a.setDescripcion(linea.getDescripcion());
        a.setPrograma(linea.getPrograma());
        a.setSubprograma(linea.getSubprograma());
        a.setProyecto(linea.getProyecto());
        a.setActividad(linea.getActividad());
        a.setLineaId(linea.getId());
        a.setConcepto(conceptoLimpio.length() > 200
                ? conceptoLimpio.substring(0, 200) : conceptoLimpio);
        a.setMontoPresupuesto(montoPresupuesto.setScale(2, RoundingMode.HALF_UP));
        a.setMontoBanco(nz(montoBanco).setScale(2, RoundingMode.HALF_UP));
        a.setEstado(Apartado.EST_ACTIVO);
        a.setUsuario(username == null ? "" : username);
        a.setFecha(LocalDateTime.now());
        a.setFechaCambio(a.getFecha());
        return apartados.save(a);
    }

    @Transactional
    public Apartado liberarApartado(Long id) {
        return cambiarEstado(id, Apartado.EST_LIBERADO);
    }

    @Transactional
    public Apartado marcarApartadoUsado(Long id) {
        return cambiarEstado(id, Apartado.EST_USADO);
    }

    private Apartado cambiarEstado(Long id, String nuevo) {
        Apartado a = apartados.lockById(id).orElseThrow(() ->
                new IllegalArgumentException("No se encontro el apartado."));
        if (!Apartado.EST_ACTIVO.equals(a.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede cambiar un apartado ACTIVO (este esta "
                            + a.getEstado() + ").");
        }
        a.setEstado(nuevo);
        a.setFechaCambio(LocalDateTime.now());
        return apartados.save(a);
    }

    /**
     * Suma banco a un ACTIVO que se dejo en 0. No edita ni aumenta un
     * monto ya reservado. El presupuesto no se mueve.
     */
    @Transactional
    public Apartado agregarBanco(Long id, BigDecimal monto, String username) {
        Apartado a = apartados.lockById(id).orElseThrow(() ->
                new IllegalArgumentException("No se encontro el apartado."));
        exigirActivoSinBanco(a);
        BigDecimal banco = nz(monto);
        if (banco.signum() <= 0) {
            throw new IllegalArgumentException(
                    "El monto de banco a apartar debe ser mayor que cero.");
        }
        CargaPresupuesto activa = cargaActiva().orElseThrow(() ->
                new IllegalArgumentException(
                        "No hay presupuesto cargado. Sube el PDF de SICOIN en Cargas."));
        LineaPresupuesto linea = lineaDeApartado(activa, a);
        VistaApartar vista = armarVistaApartar(activa, linea, a.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se pudo calcular el saldo libre de la fuente."));
        validarApartado(a.getMontoPresupuesto(), banco,
                vista.getPresupuestoLibre(), vista.getBancoLibre());
        a.setMontoBanco(banco.setScale(2, RoundingMode.HALF_UP));
        a.setFechaCambio(LocalDateTime.now());
        return apartados.save(a);
    }

    /** Foto para el formulario corto de agregar banco. */
    @Transactional(readOnly = true)
    public FormularioAgregarBanco formularioAgregarBanco(Long id) {
        Apartado a = apartados.findById(id).orElseThrow(() ->
                new IllegalArgumentException("No se encontro el apartado."));
        exigirActivoSinBanco(a);
        CargaPresupuesto activa = cargaActiva().orElseThrow(() ->
                new IllegalArgumentException(
                        "No hay presupuesto cargado. Sube el PDF de SICOIN en Cargas."));
        LineaPresupuesto linea = lineaDeApartado(activa, a);
        VistaApartar vista = armarVistaApartar(activa, linea, a.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se pudo calcular el saldo libre de la fuente."));
        return new FormularioAgregarBanco(a, vista);
    }

    private LineaPresupuesto lineaDeApartado(CargaPresupuesto activa, Apartado a) {
        LineaPresupuesto linea = resolverLineaDeApartado(a,
                lineas.findByCargaId(activa.getId()));
        if (linea == null) {
            throw new IllegalArgumentException(
                    "No se encontro la linea " + a.getRenglon() + " / " + a.getFuente()
                            + " en la carga activa. Elige de nuevo el programa.");
        }
        return linea;
    }

    private static void exigirActivoSinBanco(Apartado a) {
        if (!Apartado.EST_ACTIVO.equals(a.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede agregar banco a un apartado ACTIVO (este esta "
                            + a.getEstado() + ").");
        }
        if (nz(a.getMontoBanco()).signum() > 0) {
            throw new IllegalArgumentException(
                    "Este apartado ya tiene banco. No se puede aumentar ni editar el monto.");
        }
    }

    private static List<Apartado> sinApartado(List<Apartado> lista, Long excluirId) {
        if (lista == null || lista.isEmpty() || excluirId == null) {
            return lista == null ? List.of() : lista;
        }
        List<Apartado> filtrados = new ArrayList<>();
        for (Apartado x : lista) {
            if (x != null && !excluirId.equals(x.getId())) {
                filtrados.add(x);
            }
        }
        return filtrados;
    }

    private LineaPresupuesto encontrarLinea(Long cargaId, String renglon,
                                            String fuente, String actividadObra,
                                            String programa, String proyecto) {
        return elegirLinea(lineas.findByCargaIdAndRenglon(cargaId, renglon),
                fuente, actividadObra, programa, proyecto);
    }

    /**
     * Elige la linea por fuente + actividad + programa + proyecto.
     * Si faltan programa/proyecto y hay varias candidatas, no adivina
     * (antes se iba por el mayor saldo y cruzaba programas).
     */
    public static LineaPresupuesto elegirLinea(List<LineaPresupuesto> candidatas,
                                               String fuente, String actividadObra,
                                               String programa, String proyecto) {
        if (candidatas == null || fuente == null) return null;
        String act = actividadObra == null ? "" : actividadObra.strip();
        String fu = fuente.strip();
        String prog = codigoPrograma(programa);
        String proy = codigoProyecto(proyecto);
        LineaPresupuesto unica = null;
        for (LineaPresupuesto l : candidatas) {
            if (l == null) continue;
            String lf = l.getFuente() == null ? "" : l.getFuente().strip();
            if (!fu.equals(lf)) continue;
            String la = l.getActividadObra() == null ? "" : l.getActividadObra().strip();
            if (!act.equals(la)) continue;
            if (!prog.isEmpty() && !prog.equals(codigoPrograma(l.getPrograma()))) continue;
            if (!proy.isEmpty() && !proy.equals(codigoProyecto(l.getProyecto()))) continue;
            if (unica != null) return null;
            unica = l;
        }
        return unica;
    }

    // -------------------- agregacion pura (sin repositorios) --------------------

    /**
     * Suma las lineas por renglon y las cruza con los pagos del sistema.
     * Solo aparecen renglones presentes en las lineas; los pagos de
     * renglones sin presupuesto no se muestran aqui (van en disponibilidad).
     */
    public static List<RenglonResumen> agregarPorRenglon(List<LineaPresupuesto> lineas,
                                                         Map<String, BigDecimal> pagosPorRenglon) {
        List<RenglonResumen> resumen = new ArrayList<>();
        for (Map.Entry<String, List<LineaPresupuesto>> e : agruparPorRenglon(lineas).entrySet()) {
            List<LineaPresupuesto> ls = e.getValue();
            BigDecimal vigente = sumar(ls, LineaPresupuesto::getVigente);
            BigDecimal devengado = sumar(ls, LineaPresupuesto::getDevengado);
            BigDecimal pagado = sumar(ls, LineaPresupuesto::getPagado);
            BigDecimal saldo = sumar(ls, LineaPresupuesto::getSaldoDisponible);
            BigDecimal pagosSistema = pagosPorRenglon.getOrDefault(e.getKey(), BigDecimal.ZERO);
            resumen.add(new RenglonResumen(e.getKey(), descripcionFrecuente(ls),
                    vigente, devengado, pagado, saldo, pctEjecucion(devengado, vigente),
                    pagosSistema, pagado.subtract(pagosSistema)));
        }
        resumen.sort(Comparator.comparing(RenglonResumen::getVigente).reversed());
        return resumen;
    }

    /** Suma las lineas por codigo de fuente; el nombre sale del catalogo ("" si no tiene). */
    public static List<FuenteResumen> agregarPorFuente(List<LineaPresupuesto> lineas,
                                                       Map<String, String> nombresPorCodigo) {
        return agregarPorFuente(lineas, nombresPorCodigo, Map.of());
    }

    /**
     * Suma las lineas por fuente e incorpora el dinero real del boletin
     * (mapa fuente -> suma de nuevo saldo). Sin mapa, dineroReal = 0.
     */
    public static List<FuenteResumen> agregarPorFuente(List<LineaPresupuesto> lineas,
                                                       Map<String, String> nombresPorCodigo,
                                                       Map<String, BigDecimal> dineroRealPorCodigo) {
        Map<String, List<LineaPresupuesto>> agrupadas = new LinkedHashMap<>();
        for (LineaPresupuesto l : lineas) {
            String codigo = l.getFuente() == null ? "" : l.getFuente();
            agrupadas.computeIfAbsent(codigo, k -> new ArrayList<>()).add(l);
        }
        List<FuenteResumen> resumen = new ArrayList<>();
        for (Map.Entry<String, List<LineaPresupuesto>> e : agrupadas.entrySet()) {
            List<LineaPresupuesto> ls = e.getValue();
            BigDecimal vigente = sumar(ls, LineaPresupuesto::getVigente);
            BigDecimal devengado = sumar(ls, LineaPresupuesto::getDevengado);
            resumen.add(new FuenteResumen(e.getKey(),
                    nombresPorCodigo.getOrDefault(e.getKey(), ""),
                    vigente, devengado, sumar(ls, LineaPresupuesto::getPagado),
                    sumar(ls, LineaPresupuesto::getSaldoDisponible),
                    pctEjecucion(devengado, vigente),
                    dineroRealDeMapa(dineroRealPorCodigo, e.getKey()),
                    nz(dineroRealPorCodigo.get(claveDineroCaja(e.getKey(),
                            TipoDineroCaja.FUNCIONAMIENTO))),
                    nz(dineroRealPorCodigo.get(claveDineroCaja(e.getKey(),
                            TipoDineroCaja.INVERSION)))));
        }
        resumen.sort(Comparator.comparing(FuenteResumen::getVigente).reversed());
        return resumen;
    }

    /**
     * Para cada renglon de compra directa presente en el presupuesto o en
     * los pagos del mes: saldoDisponible (SICOIN), pagosMes (sistema) y
     * saldoProyectado = saldoDisponible - pagosMes, con semaforo AGOTADO /
     * POR_AGOTARSE / OK. Orden: saldoProyectado ascendente.
     */
    public static List<DisponibilidadRenglon> calcularDisponibilidad(
            Collection<String> renglonesCd, List<LineaPresupuesto> lineas,
            List<HistorialCompra> pagosDelMes) {
        Map<String, List<LineaPresupuesto>> porRenglon = agruparPorRenglon(lineas);
        Map<String, BigDecimal> pagosPorRenglon = sumarPagosPorRenglon(pagosDelMes);
        List<DisponibilidadRenglon> disponibilidad = new ArrayList<>();
        for (String renglon : renglonesCd) {
            List<LineaPresupuesto> ls = porRenglon.get(renglon);
            BigDecimal pagosMes = pagosPorRenglon.get(renglon);
            if (ls == null && pagosMes == null) continue; // no aparece ni en uno ni en otro
            BigDecimal vigente = BigDecimal.ZERO, devengado = BigDecimal.ZERO, saldo = BigDecimal.ZERO;
            String descripcion = "";
            if (ls != null) {
                vigente = sumar(ls, LineaPresupuesto::getVigente);
                devengado = sumar(ls, LineaPresupuesto::getDevengado);
                saldo = sumar(ls, LineaPresupuesto::getSaldoDisponible);
                descripcion = descripcionFrecuente(ls);
            }
            if (pagosMes == null) pagosMes = BigDecimal.ZERO;
            BigDecimal proyectado = saldo.subtract(pagosMes);
            disponibilidad.add(new DisponibilidadRenglon(renglon, descripcion,
                    vigente, devengado, saldo, pagosMes, proyectado,
                    semaforo(vigente, devengado, pagosMes, proyectado)));
        }
        disponibilidad.sort(Comparator.comparing(DisponibilidadRenglon::getSaldoProyectado));
        return disponibilidad;
    }

    /** Suma de montos del historial por renglon (ignora filas sin renglon). */
    public static Map<String, BigDecimal> sumarPagosPorRenglon(List<HistorialCompra> historialCompras) {
        Map<String, BigDecimal> suma = new LinkedHashMap<>();
        for (HistorialCompra h : historialCompras) {
            if (h.getRenglon() == null) continue;
            suma.merge(h.getRenglon(), nz(h.getMonto()), BigDecimal::add);
        }
        return suma;
    }

    /** Pagos del sistema de un renglon, del mas reciente al mas viejo (anio/mes), tope maximo. */
    public static List<HistorialCompra> ultimosPagos(List<HistorialCompra> historialCompras,
                                                     String renglon, int maximo) {
        return historialCompras.stream()
                .filter(h -> renglon.equals(h.getRenglon()))
                .sorted(Comparator
                        .comparing(HistorialCompra::getAnio,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(HistorialCompra::getMes,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maximo)
                .toList();
    }

    /**
     * Busca en que renglones/fuentes cabe un pago descrito en lenguaje
     * libre. Para cada renglon candidato presente en las lineas arma sus
     * LineaFuente (con el nombre de la fuente del catalogo), incluidas las
     * agotadas (saldo <= 0) para que el usuario vea que ya no queda. Orden:
     * lineas por saldo descendente, renglones por totalDisponible
     * descendente. Consulta vacia o sin candidatos: lista vacia.
     */
    public static List<BusquedaPago> buscarDondePagar(String consulta, BigDecimal monto,
                                                      List<LineaPresupuesto> lineas,
                                                      Map<String, String> nombresPorFuente) {
        return buscarDondePagar(consulta, monto, lineas, nombresPorFuente, Map.of(),
                Map.of(), Map.of(), Map.of());
    }

    /**
     * Igual que {@link #buscarDondePagar(String, BigDecimal, List, Map)} y
     * ademas marca alcanzaBanco con el dinero real por fuente del boletin.
     */
    public static List<BusquedaPago> buscarDondePagar(String consulta, BigDecimal monto,
                                                      List<LineaPresupuesto> lineas,
                                                      Map<String, String> nombresPorFuente,
                                                      Map<String, BigDecimal> dineroRealPorCodigo) {
        return buscarDondePagar(consulta, monto, lineas, nombresPorFuente,
                dineroRealPorCodigo, Map.of(), Map.of(), Map.of());
    }

    /**
     * Busqueda con overlay de apartados activos: el saldo y el dinero real
     * que se muestran (y contra los que se marca "alcanza") ya tienen
     * restado lo reservado. Los mapas vacios dejan el comportamiento original.
     */
    public static List<BusquedaPago> buscarDondePagar(String consulta, BigDecimal monto,
                                                      List<LineaPresupuesto> lineas,
                                                      Map<String, String> nombresPorFuente,
                                                      Map<String, BigDecimal> dineroRealPorCodigo,
                                                      Map<String, BigDecimal> apartadoPresPorClave,
                                                      Map<String, BigDecimal> apartadoBancoPorFuente) {
        return buscarDondePagar(consulta, monto, lineas, nombresPorFuente,
                dineroRealPorCodigo, apartadoPresPorClave, apartadoBancoPorFuente, null);
    }

    /**
     * Igual y con overlay por id de linea: distingue renglones gemelos
     * (mismo programa, fuente y renglon; distinto saldo).
     */
    public static List<BusquedaPago> buscarDondePagar(String consulta, BigDecimal monto,
                                                      List<LineaPresupuesto> lineas,
                                                      Map<String, String> nombresPorFuente,
                                                      Map<String, BigDecimal> dineroRealPorCodigo,
                                                      Map<String, BigDecimal> apartadoPresPorClave,
                                                      Map<String, BigDecimal> apartadoBancoPorFuente,
                                                      Map<Long, BigDecimal> apartadoPresPorLineaId) {
        String consultaNorm = TextoUtil.norm(consulta == null ? "" : consulta);
        if (consultaNorm.isEmpty()) return List.of();
        Map<String, List<LineaPresupuesto>> porRenglon = agruparPorRenglon(lineas);
        List<BusquedaPago> resultados = new ArrayList<>();
        for (String renglon : renglonesCandidatos(consultaNorm, porRenglon)) {
            List<LineaPresupuesto> ls = porRenglon.get(renglon);
            List<LineaFuente> detalle = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (LineaPresupuesto l : ls) {
                BigDecimal saldoSicoin = nz(l.getSaldoDisponible());
                String codigoFuente = l.getFuente() == null ? "" : l.getFuente();
                BigDecimal apartadoP = apartadoDeLinea(l, apartadoPresPorClave,
                        apartadoPresPorLineaId);
                BigDecimal saldo = saldoLibre(saldoSicoin, apartadoP);
                total = total.add(saldo);
                boolean alcanza = monto == null
                        ? saldo.signum() > 0
                        : saldo.compareTo(monto) >= 0;
                BigDecimal dineroSicoin = valorPorFuenteYTipo(dineroRealPorCodigo,
                        codigoFuente, l.getPrograma());
                BigDecimal apartadoB = valorPorFuenteYTipo(apartadoBancoPorFuente,
                        codigoFuente, l.getPrograma());
                BigDecimal dineroReal = saldoLibre(dineroSicoin, apartadoB);
                boolean alcanzaBanco = monto == null
                        ? dineroReal.signum() > 0
                        : dineroReal.compareTo(monto) >= 0;
                detalle.add(new LineaFuente(l.getId(), codigoFuente,
                        nombresPorFuente.getOrDefault(codigoFuente, ""),
                        l.getPrograma() == null ? "" : l.getPrograma(),
                        l.getProyecto() == null ? "" : l.getProyecto(),
                        l.getActividadObra() == null ? "" : l.getActividadObra(),
                        nz(l.getVigente()), nz(l.getDevengado()), saldo, alcanza,
                        dineroReal, alcanzaBanco, apartadoP, apartadoB));
            }
            detalle.sort(Comparator.comparing(LineaFuente::getSaldoDisponible).reversed());
            resultados.add(new BusquedaPago(renglon, descripcionFrecuente(ls), total, detalle));
        }
        resultados.sort(Comparator.comparing(BusquedaPago::getTotalDisponible).reversed());
        return resultados;
    }

    /**
     * Arma el desglose de una fuente agrupando sus lineas por programa,
     * y dentro del programa por proyecto solo cuando la linea tiene uno
     * real (no vacio ni "000 SIN PROYECTO"): el titulo del grupo es
     * "programa · proyecto" o solo el programa (vacio -> "SIN PROGRAMA").
     * Cada linea se envuelve con alcanza (con monto: saldo >= monto; sin
     * monto: saldo > 0). Orden: grupos por subtotalSaldo descendente y
     * lineas por saldoDisponible descendente, para que las donantes con
     * mas holgura queden arriba. Los nulos de la entidad cuentan como 0.
     */
    public static DesgloseFuente armarDesglose(String codigo, String nombre, BigDecimal monto,
                                               List<LineaPresupuesto> lineas) {
        return armarDesglose(codigo, nombre, monto, lineas, BigDecimal.ZERO, List.of());
    }

    /**
     * Desglose de fuente con dinero real del boletin y las cuentas
     * monetarias que lo componen. alcanzaBanco: con monto, dineroReal >= monto;
     * sin monto, dineroReal > 0.
     */
    public static DesgloseFuente armarDesglose(String codigo, String nombre, BigDecimal monto,
                                               List<LineaPresupuesto> lineas,
                                               BigDecimal dineroReal,
                                               List<CuentaMonetaria> cuentasBanco) {
        return armarDesglose(codigo, nombre, monto, lineas, dineroReal, cuentasBanco, Map.of(),
                Map.of());
    }

    /**
     * Desglose restando apartados activos de presupuesto por clave de linea.
     * El dineroReal que llega ya debe venir libre (boletin menos apartado banco).
     */
    public static DesgloseFuente armarDesglose(String codigo, String nombre, BigDecimal monto,
                                               List<LineaPresupuesto> lineas,
                                               BigDecimal dineroReal,
                                               List<CuentaMonetaria> cuentasBanco,
                                               Map<String, BigDecimal> apartadoPresPorClave) {
        return armarDesglose(codigo, nombre, monto, lineas, dineroReal, cuentasBanco,
                apartadoPresPorClave, null);
    }

    /**
     * Desglose restando apartados por id de linea cuando existe, si no por clave.
     */
    public static DesgloseFuente armarDesglose(String codigo, String nombre, BigDecimal monto,
                                               List<LineaPresupuesto> lineas,
                                               BigDecimal dineroReal,
                                               List<CuentaMonetaria> cuentasBanco,
                                               Map<String, BigDecimal> apartadoPresPorClave,
                                               Map<Long, BigDecimal> apartadoPresPorLineaId) {
        return armarDesglose(codigo, nombre, monto, lineas, dineroReal, cuentasBanco,
                apartadoPresPorClave, apartadoPresPorLineaId, Map.of());
    }

    /**
     * Desglose restando tambien el apartado de banco del pozo
     * funcionamiento o inversion que le toca al programa de cada linea.
     */
    public static DesgloseFuente armarDesglose(String codigo, String nombre, BigDecimal monto,
                                               List<LineaPresupuesto> lineas,
                                               BigDecimal dineroReal,
                                               List<CuentaMonetaria> cuentasBanco,
                                               Map<String, BigDecimal> apartadoPresPorClave,
                                               Map<Long, BigDecimal> apartadoPresPorLineaId,
                                               Map<String, BigDecimal> apartadoBancoPorFuente) {
        Map<String, List<LineaPresupuesto>> porTitulo = new LinkedHashMap<>();
        for (LineaPresupuesto l : lineas) {
            porTitulo.computeIfAbsent(tituloGrupo(l), k -> new ArrayList<>()).add(l);
        }
        List<GrupoDesglose> grupos = new ArrayList<>();
        BigDecimal totalSaldoLibre = BigDecimal.ZERO;
        for (Map.Entry<String, List<LineaPresupuesto>> e : porTitulo.entrySet()) {
            List<LineaPresupuesto> ls = new ArrayList<>(e.getValue());
            ls.sort(Comparator.comparing(
                    (LineaPresupuesto l) -> saldoLibre(nz(l.getSaldoDisponible()),
                            apartadoDeLinea(l, apartadoPresPorClave, apartadoPresPorLineaId)))
                    .reversed());
            List<LineaDesglose> envueltas = new ArrayList<>();
            BigDecimal subSaldo = BigDecimal.ZERO;
            for (LineaPresupuesto l : ls) {
                BigDecimal apartadoP = apartadoDeLinea(l, apartadoPresPorClave,
                        apartadoPresPorLineaId);
                BigDecimal saldo = saldoLibre(nz(l.getSaldoDisponible()), apartadoP);
                subSaldo = subSaldo.add(saldo);
                boolean alcanza = monto == null
                        ? saldo.signum() > 0
                        : saldo.compareTo(monto) >= 0;
                BigDecimal dineroLinea = saldoLibre(
                        dineroRealDeFuenteParaPrograma(codigo, l.getPrograma(), cuentasBanco),
                        valorPorFuenteYTipo(apartadoBancoPorFuente, codigo, l.getPrograma()));
                envueltas.add(new LineaDesglose(l, alcanza, saldo, apartadoP, dineroLinea));
            }
            totalSaldoLibre = totalSaldoLibre.add(subSaldo);
            grupos.add(new GrupoDesglose(e.getKey(),
                    sumar(ls, LineaPresupuesto::getVigente),
                    sumar(ls, LineaPresupuesto::getDevengado),
                    subSaldo,
                    envueltas));
        }
        grupos.sort(Comparator.comparing(GrupoDesglose::getSubtotalSaldo).reversed());
        BigDecimal real = nz(dineroReal);
        boolean alcanzaBanco = monto == null
                ? real.signum() > 0
                : real.compareTo(monto) >= 0;
        BigDecimal func = saldoLibre(
                dineroRealDeFuenteParaTipo(codigo, TipoDineroCaja.FUNCIONAMIENTO, cuentasBanco),
                nz(apartadoBancoPorFuente == null ? null
                        : apartadoBancoPorFuente.get(claveDineroCaja(codigo,
                        TipoDineroCaja.FUNCIONAMIENTO))));
        BigDecimal inv = saldoLibre(
                dineroRealDeFuenteParaTipo(codigo, TipoDineroCaja.INVERSION, cuentasBanco),
                nz(apartadoBancoPorFuente == null ? null
                        : apartadoBancoPorFuente.get(claveDineroCaja(codigo,
                        TipoDineroCaja.INVERSION))));
        return new DesgloseFuente(codigo, nombre,
                sumar(lineas, LineaPresupuesto::getVigente),
                sumar(lineas, LineaPresupuesto::getDevengado),
                sumar(lineas, LineaPresupuesto::getPagado),
                totalSaldoLibre,
                real, alcanzaBanco,
                cuentasBanco == null ? List.of() : List.copyOf(cuentasBanco),
                grupos, func, inv);
    }

    /**
     * Dinero real de una fuente = suma de nuevoSaldo de cuentas cuyo codigo
     * es igual a la fuente o empieza con fuente + "-".
     */
    public static BigDecimal dineroRealDeFuente(String codigoFuente,
                                                List<CuentaMonetaria> cuentas) {
        return sumarNuevoSaldo(cuentasDeFuente(codigoFuente, cuentas));
    }

    /**
     * Mapa fuente-presupuesto -> dinero real. Solo se agregan claves cuando
     * se pide explicitamente por codigo de fuente (no inventa fuentes).
     * Para enriquecer un listado, se consulta por cada codigo de fuente.
     */
    public static Map<String, BigDecimal> dineroRealPorFuente(List<CuentaMonetaria> cuentas) {
        Map<String, BigDecimal> porPrefijo = new LinkedHashMap<>();
        if (cuentas == null) return porPrefijo;
        for (CuentaMonetaria c : cuentas) {
            String codigo = c.getCodigo() == null ? "" : c.getCodigo();
            if (codigo.isEmpty()) continue;
            String fuente = codigoFuenteDeCuenta(codigo);
            if (fuente.isEmpty()) continue;
            porPrefijo.merge(fuente, nz(c.getNuevoSaldo()), BigDecimal::add);
        }
        return porPrefijo;
    }

    /** Cuentas monetarias que pertenecen a una fuente presupuestaria. */
    public static List<CuentaMonetaria> cuentasDeFuente(String codigoFuente,
                                                       List<CuentaMonetaria> cuentas) {
        if (codigoFuente == null || codigoFuente.isBlank() || cuentas == null) {
            return List.of();
        }
        String prefijo = codigoFuente + "-";
        List<CuentaMonetaria> match = new ArrayList<>();
        for (CuentaMonetaria c : cuentas) {
            String codigo = c.getCodigo() == null ? "" : c.getCodigo();
            if (codigo.equals(codigoFuente) || codigo.startsWith(prefijo)) {
                match.add(c);
            }
        }
        return match;
    }

    /**
     * Prefijo de fuente presupuestaria de un codigo de cuenta monetaria
     * (los primeros 3 segmentos XX-XXXX-XXXX). Cuentas cortas (118) -> "".
     */
    public static String codigoFuenteDeCuenta(String codigoCuenta) {
        if (codigoCuenta == null) return "";
        Matcher m = Pattern.compile("^(\\d{2}-\\d{4}-\\d{4})").matcher(codigoCuenta);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Calcula una simulacion de transferencia origen -> destino. Es pura
     * y CONSULTIVA: no muta las entidades ni guarda nada; la
     * modificacion real se tramita despues en SICOIN. Invalida cuando:
     * alguna linea no existe (origen/destino null), ambas son la misma
     * (mismo id), el monto es nulo o no positivo, o supera el saldo
     * disponible del origen. En las invalidas los saldos "despues" son
     * iguales a los "antes" (nada se mueve). En las validas: origen
     * -monto, destino +monto.
     */
    public static SimulacionTransferencia calcularTransferencia(LineaPresupuesto origen,
                                                                LineaPresupuesto destino,
                                                                BigDecimal monto) {
        if (origen == null) {
            return simulacionInvalida(null, destino, monto, "La linea origen no existe.");
        }
        if (destino == null) {
            return simulacionInvalida(origen, null, monto, "La linea destino no existe.");
        }
        BigDecimal saldoOrigen = nz(origen.getSaldoDisponible());
        BigDecimal saldoDestino = nz(destino.getSaldoDisponible());
        if (Objects.equals(origen.getId(), destino.getId())) {
            return simulacionInvalida(origen, destino, monto,
                    "La linea origen y la linea destino deben ser distintas.");
        }
        if (monto == null || monto.signum() <= 0) {
            return simulacionInvalida(origen, destino, monto, "El monto debe ser mayor que cero.");
        }
        if (monto.compareTo(saldoOrigen) > 0) {
            return simulacionInvalida(origen, destino, monto,
                    "El monto supera el saldo disponible de la linea origen");
        }
        return new SimulacionTransferencia(origen, destino, monto,
                saldoOrigen, saldoOrigen.subtract(monto),
                saldoDestino, saldoDestino.add(monto),
                true, "Transferencia simulada: Q " + monto.toPlainString()
                        + " de la linea " + origen.getId() + " a la linea " + destino.getId());
    }

    // --------------------------- helpers privados ---------------------------

    private static LineaPresupuesto mapearLinea(LineaEjecucion le, Long cargaId) {
        LineaPresupuesto l = new LineaPresupuesto();
        l.setCargaId(cargaId);
        l.setPrograma(le.getPrograma());
        l.setSubprograma(le.getSubprograma());
        l.setProyecto(le.getProyecto());
        l.setActividad(le.getActividad());
        l.setActividadObra(le.getActividadObra());
        l.setRenglon(le.getRenglon());
        l.setDescripcion(le.getDescripcion());
        l.setFuente(le.getFuente());
        l.setAsignado(le.getAsignado());
        l.setModificado(le.getModificado());
        l.setVigente(le.getVigente());
        l.setPreCompromiso(le.getPreCompromiso());
        l.setCompromiso(le.getCompromiso());
        l.setDevengado(le.getDevengado());
        l.setPagado(le.getPagado());
        l.setExtraPresupuestario(le.getExtraPresupuestario());
        l.setSaldoDisponible(le.getSaldoDisponible());
        l.setSaldoPorDevengar(le.getSaldoPorDevengar());
        l.setSaldoPorPagar(le.getSaldoPorPagar());
        return l;
    }

    private static CuentaMonetaria mapearCuenta(LineaCuentaMonetaria lc, Long cargaId) {
        CuentaMonetaria c = new CuentaMonetaria();
        c.setCargaId(cargaId);
        c.setCodigo(lc.getCodigo());
        c.setDescripcion(lc.getDescripcion());
        c.setSaldoAnterior(lc.getSaldoAnterior());
        c.setMontoCredito(lc.getMontoCredito());
        c.setMontoDebito(lc.getMontoDebito());
        c.setNuevoSaldo(lc.getNuevoSaldo());
        TipoDineroCaja tipo = lc.getTipo();
        if (tipo == null || tipo == TipoDineroCaja.DESCONOCIDO) {
            tipo = ParserBoletinCaja.clasificarTipo(lc.getCodigo(), lc.getDescripcion());
        }
        c.setTipo(tipo.id());
        return c;
    }

    private List<CuentaMonetaria> cuentasCajaActivas() {
        return cargaCajaActiva()
                .map(c -> cuentasCaja.findByCargaId(c.getId()))
                .orElse(List.of());
    }

    private static BigDecimal sumarNuevoSaldo(List<CuentaMonetaria> cuentas) {
        BigDecimal total = BigDecimal.ZERO;
        for (CuentaMonetaria c : cuentas) {
            total = total.add(nz(c.getNuevoSaldo()));
        }
        return total;
    }

    /** Inserta las fuentes que no existan en el catalogo (nombre vacio); las ya editadas no se tocan. */
    private void sembrarFuentes(List<LineaEjecucion> lineasEjecucion) {
        Map<String, FuenteFinanciamiento> existentes = new LinkedHashMap<>();
        for (FuenteFinanciamiento f : fuentes.findAll()) {
            existentes.put(f.getCodigo(), f);
        }
        List<FuenteFinanciamiento> nuevas = new ArrayList<>();
        for (LineaEjecucion le : lineasEjecucion) {
            String codigo = le.getFuente();
            if (codigo == null || codigo.isBlank() || existentes.containsKey(codigo)) continue;
            FuenteFinanciamiento f = new FuenteFinanciamiento();
            f.setCodigo(codigo);
            f.setNombre("");
            nuevas.add(f);
            existentes.put(codigo, f); // evita duplicados dentro del mismo PDF
        }
        fuentes.saveAll(nuevas);
    }

    private static Map<String, List<LineaPresupuesto>> agruparPorRenglon(List<LineaPresupuesto> lineas) {
        Map<String, List<LineaPresupuesto>> agrupadas = new LinkedHashMap<>();
        for (LineaPresupuesto l : lineas) {
            String renglon = l.getRenglon() == null ? "" : l.getRenglon();
            agrupadas.computeIfAbsent(renglon, k -> new ArrayList<>()).add(l);
        }
        return agrupadas;
    }

    /**
     * Renglones candidatos de una consulta ya normalizada, sin duplicados
     * y solo entre los presentes en las lineas:
     * a) la consulta ES un codigo de renglon de 3 digitos;
     * b) la consulta CONTIENE alguna palabra clave del dominio
     *    (Constantes.KEYWORDS, se prueban primero las mas largas) o ES el
     *    inicio de una frase clave ("camionadas" -> "CAMIONADAS DE 10");
     * c) algun token de la consulta (3+ caracteres) aparece en la
     *    descripcion normalizada de una linea.
     */
    private static Set<String> renglonesCandidatos(
            String consultaNorm, Map<String, List<LineaPresupuesto>> porRenglon) {
        Set<String> candidatos = new LinkedHashSet<>();
        if (consultaNorm.matches("\\d{3}") && porRenglon.containsKey(consultaNorm)) {
            candidatos.add(consultaNorm);
        }
        for (String kw : Constantes.KEYWORDS_ORDENADAS) {
            // Doble direccion: la consulta contiene la frase clave completa
            // ("pago de VIAJES DE MATERIAL") o la consulta es el inicio de
            // la frase clave ("camionadas" inicia "CAMIONADAS DE 10").
            if (consultaNorm.contains(kw)
                    || (consultaNorm.length() >= 4 && kw.startsWith(consultaNorm))) {
                String renglon = Constantes.KEYWORDS.get(kw);
                if (porRenglon.containsKey(renglon)) {
                    candidatos.add(renglon);
                }
            }
        }
        List<String> tokens = new ArrayList<>();
        for (String t : consultaNorm.split(" ")) {
            if (t.length() >= 3) tokens.add(t);
        }
        if (!tokens.isEmpty()) {
            for (Map.Entry<String, List<LineaPresupuesto>> e : porRenglon.entrySet()) {
                for (LineaPresupuesto l : e.getValue()) {
                    String desc = TextoUtil.norm(
                            l.getDescripcion() == null ? "" : l.getDescripcion());
                    for (String t : tokens) {
                        if (desc.contains(t)) {
                            candidatos.add(e.getKey());
                            break;
                        }
                    }
                    if (candidatos.contains(e.getKey())) break;
                }
            }
        }
        return candidatos;
    }

    /** La descripcion que mas se repite dentro del grupo; en empate gana la primera. */
    private static String descripcionFrecuente(List<LineaPresupuesto> lineas) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (LineaPresupuesto l : lineas) {
            String d = l.getDescripcion() == null ? "" : l.getDescripcion();
            conteo.merge(d, 1, Integer::sum);
        }
        return conteo.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static BigDecimal sumar(List<LineaPresupuesto> lineas,
                                    Function<LineaPresupuesto, BigDecimal> campo) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineaPresupuesto l : lineas) {
            total = total.add(nz(campo.apply(l)));
        }
        return total;
    }

    /** devengado/vigente*100 con 1 decimal; vigente en cero da 0.0 (sin division por cero). */
    private static double pctEjecucion(BigDecimal devengado, BigDecimal vigente) {
        if (vigente.signum() == 0) return 0.0;
        double pct = devengado.divide(vigente, 6, RoundingMode.HALF_UP).doubleValue() * 100.0;
        return Math.round(pct * 10.0) / 10.0;
    }

    private static String semaforo(BigDecimal vigente, BigDecimal devengado,
                                   BigDecimal pagosMes, BigDecimal saldoProyectado) {
        if (saldoProyectado.signum() < 0) return DisponibilidadRenglon.SEM_AGOTADO;
        BigDecimal comprometido = devengado.add(pagosMes);
        if (vigente.signum() == 0) {
            // sin presupuesto vigente no hay razon calculable: cualquier gasto prende la alerta
            return comprometido.signum() > 0
                    ? DisponibilidadRenglon.SEM_POR_AGOTARSE : DisponibilidadRenglon.SEM_OK;
        }
        double razon = comprometido.divide(vigente, 6, RoundingMode.HALF_UP).doubleValue();
        return razon > UMBRAL_POR_AGOTARSE
                ? DisponibilidadRenglon.SEM_POR_AGOTARSE : DisponibilidadRenglon.SEM_OK;
    }

    private static BigDecimal nz(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /** Clave estable de una linea: sobrevive al reimportar el PDF. */
    public static String claveLinea(String renglon, String fuente, String actividadObra) {
        return claveLinea(renglon, fuente, actividadObra, "", "", "", "");
    }

    public static String claveLinea(LineaPresupuesto l) {
        if (l == null) return claveLinea("", "", "", "", "", "", "");
        return claveLinea(l.getRenglon(), l.getFuente(), l.getActividadObra(),
                l.getPrograma(), l.getProyecto(), l.getSubprograma(), l.getActividad());
    }

    public static String claveLinea(Apartado a) {
        if (a == null) return claveLinea("", "", "", "", "", "", "");
        return claveLinea(a.getRenglon(), a.getFuente(), a.getActividadObra(),
                a.getPrograma(), a.getProyecto(), a.getSubprograma(), a.getActividad());
    }

    /**
     * Identidad de linea: programa + subprograma + proyecto + actividad +
     * actividadObra + renglon + fuente. Sin actividad, dos renglones del
     * mismo proyecto (p.ej. 029 / 22-0101-0001) se mezclaban al apartar.
     */
    public static String claveLinea(String renglon, String fuente, String actividadObra,
                                    String programa, String proyecto) {
        return claveLinea(renglon, fuente, actividadObra, programa, proyecto, "", "");
    }

    public static String claveLinea(String renglon, String fuente, String actividadObra,
                                    String programa, String proyecto,
                                    String subprograma, String actividad) {
        return codigoPrograma(programa)
                + "|" + codigoPrograma(subprograma)
                + "|" + codigoProyecto(proyecto)
                + "|" + codigoProyecto(actividad)
                + "|" + (actividadObra == null ? "" : actividadObra.strip())
                + "|" + (renglon == null ? "" : renglon.strip())
                + "|" + (fuente == null ? "" : fuente.strip());
    }

    /** Codigo de 2 digitos al inicio ("19 MOVILIDAD..." -> "19"). */
    public static String codigoPrograma(String programa) {
        String s = programa == null ? "" : programa.strip();
        if (s.length() >= 2 && Character.isDigit(s.charAt(0)) && Character.isDigit(s.charAt(1))) {
            return s.substring(0, 2);
        }
        return s;
    }

    /** Codigo de 3 digitos al inicio ("001 SERVICIOS..." -> "001"). */
    public static String codigoProyecto(String proyecto) {
        String s = proyecto == null ? "" : proyecto.strip();
        if (s.length() >= 3
                && Character.isDigit(s.charAt(0))
                && Character.isDigit(s.charAt(1))
                && Character.isDigit(s.charAt(2))) {
            return s.substring(0, 3);
        }
        return s;
    }

    /** Saldo SICOIN menos lo apartado; nunca negativo. */
    public static BigDecimal saldoLibre(BigDecimal sicoin, BigDecimal apartado) {
        BigDecimal libre = nz(sicoin).subtract(nz(apartado));
        return libre.signum() < 0 ? BigDecimal.ZERO : libre;
    }

    public static Map<String, BigDecimal> apartadoPresupuestoPorClave(List<Apartado> lista) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        if (lista == null) return mapa;
        for (Apartado a : lista) {
            if (!Apartado.EST_ACTIVO.equals(a.getEstado())) continue;
            if (nz(a.getMontoPresupuesto()).signum() <= 0) continue;
            mapa.merge(claveLinea(a), nz(a.getMontoPresupuesto()), BigDecimal::add);
        }
        return mapa;
    }

    /**
     * Overlay por id de linea. Si el apartado trae lineaId y esa linea
     * sigue en la carga, va ahi. Si no, se aplica por clave solo cuando
     * la clave es unica (no se rocian gemelas).
     */
    public static Map<Long, BigDecimal> apartadoPresupuestoPorLinea(
            List<LineaPresupuesto> lineas, List<Apartado> lista) {
        Map<Long, BigDecimal> resultado = new LinkedHashMap<>();
        if (lineas == null || lista == null) return resultado;
        Map<Long, LineaPresupuesto> porId = new LinkedHashMap<>();
        Map<String, List<LineaPresupuesto>> porClave = new LinkedHashMap<>();
        for (LineaPresupuesto l : lineas) {
            if (l == null) continue;
            if (l.getId() != null) porId.put(l.getId(), l);
            porClave.computeIfAbsent(claveLinea(l), k -> new ArrayList<>()).add(l);
        }
        for (Apartado a : lista) {
            if (a == null || !Apartado.EST_ACTIVO.equals(a.getEstado())) continue;
            if (nz(a.getMontoPresupuesto()).signum() <= 0) continue;
            LineaPresupuesto destino = resolverLineaDeApartado(a, porId, porClave);
            if (destino != null && destino.getId() != null) {
                resultado.merge(destino.getId(), nz(a.getMontoPresupuesto()), BigDecimal::add);
            }
        }
        return resultado;
    }

    /**
     * Matching tras reimport: primero el lineaId si sigue existiendo;
     * si el id cambio, la clave completa solo cuando es unica.
     */
    public static LineaPresupuesto resolverLineaDeApartado(Apartado a,
                                                          List<LineaPresupuesto> lineas) {
        if (a == null || lineas == null) return null;
        Map<Long, LineaPresupuesto> porId = new LinkedHashMap<>();
        Map<String, List<LineaPresupuesto>> porClave = new LinkedHashMap<>();
        for (LineaPresupuesto l : lineas) {
            if (l == null) continue;
            if (l.getId() != null) porId.put(l.getId(), l);
            porClave.computeIfAbsent(claveLinea(l), k -> new ArrayList<>()).add(l);
        }
        return resolverLineaDeApartado(a, porId, porClave);
    }

    public static LineaPresupuesto resolverLineaDeApartado(Apartado a,
                                                          Map<Long, LineaPresupuesto> porId,
                                                          Map<String, List<LineaPresupuesto>> porClave) {
        if (a == null) return null;
        LineaPresupuesto destino = (a.getLineaId() == null || porId == null)
                ? null : porId.get(a.getLineaId());
        if (destino != null) return destino;
        if (porClave == null) return null;
        List<LineaPresupuesto> cands = porClave.get(claveLinea(a));
        if (cands != null && cands.size() == 1) return cands.get(0);
        return null;
    }

    /** Id actual de la linea del apartado, o null si la clave es ambigua. */
    public static Long lineaIdVigente(Apartado a, List<LineaPresupuesto> lineas) {
        LineaPresupuesto destino = resolverLineaDeApartado(a, lineas);
        return destino == null ? null : destino.getId();
    }

    /**
     * Si el id viejo ya no esta, apunta al de la clave unica. No adivina
     * gemelas. Devuelve true si cambio el id (para persistir).
     */
    public static boolean reasignarLineaId(Apartado a, List<LineaPresupuesto> lineas) {
        Long vigente = lineaIdVigente(a, lineas);
        if (vigente == null || vigente.equals(a.getLineaId())) return false;
        a.setLineaId(vigente);
        return true;
    }

    public static BigDecimal apartadoDeLinea(LineaPresupuesto l,
                                            Map<String, BigDecimal> porClave,
                                            Map<Long, BigDecimal> porId) {
        if (l != null && l.getId() != null && porId != null) {
            return nz(porId.get(l.getId()));
        }
        if (l == null || porClave == null) return BigDecimal.ZERO;
        return nz(porClave.get(claveLinea(l)));
    }

    public static Map<String, BigDecimal> apartadoBancoPorFuente(List<Apartado> lista) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        if (lista == null) return mapa;
        for (Apartado a : lista) {
            if (!Apartado.EST_ACTIVO.equals(a.getEstado())) continue;
            if (nz(a.getMontoBanco()).signum() <= 0) continue;
            String fuente = a.getFuente() == null ? "" : a.getFuente();
            mapa.merge(fuente, nz(a.getMontoBanco()), BigDecimal::add);
        }
        return mapa;
    }

    /**
     * Presupuesto obligatorio y &gt; 0, no mayor al libre de la linea.
     * Banco independiente: 0 es valido (apartar sin efectivo); si es &gt; 0
     * no puede superar el dinero real libre de la fuente.
     */
    public static void validarApartado(BigDecimal montoPresupuesto, BigDecimal montoBanco,
                                       BigDecimal presupuestoLibre, BigDecimal bancoLibre) {
        BigDecimal pres = nz(montoPresupuesto);
        BigDecimal banco = nz(montoBanco);
        if (pres.signum() <= 0) {
            throw new IllegalArgumentException(
                    "El monto de presupuesto a apartar debe ser mayor que cero.");
        }
        if (banco.signum() < 0) {
            throw new IllegalArgumentException(
                    "El monto de banco no puede ser negativo. Dejalo en 0 si no hay efectivo.");
        }
        if (pres.compareTo(nz(presupuestoLibre)) > 0) {
            throw new IllegalArgumentException(
                    "El monto de presupuesto supera el saldo libre de la linea.");
        }
        if (banco.compareTo(nz(bancoLibre)) > 0) {
            throw new IllegalArgumentException(
                    "El monto de banco supera el dinero real libre de la fuente.");
        }
    }

    /**
     * Etiqueta del grupo del desglose: el programa, mas " · proyecto" solo
     * si el proyecto es real (no vacio ni "000 SIN PROYECTO"); programa
     * vacio o nulo -> "SIN PROGRAMA".
     */
    private static String tituloGrupo(LineaPresupuesto l) {
        String programa = l.getPrograma() == null || l.getPrograma().isBlank()
                ? SIN_PROGRAMA : l.getPrograma().trim();
        String proyecto = l.getProyecto() == null ? "" : l.getProyecto().trim();
        if (!proyecto.isEmpty() && !SIN_PROYECTO.equals(proyecto)) {
            return programa + " · " + proyecto;
        }
        return programa;
    }

    /**
     * Simulacion rechazada: los saldos "despues" quedan iguales a los
     * "antes" (nada se mueve) y el monto nulo se reporta como 0, para que
     * la interfaz nunca reciba nulos en los importes.
     */
    private static SimulacionTransferencia simulacionInvalida(LineaPresupuesto origen,
                                                              LineaPresupuesto destino,
                                                              BigDecimal monto, String mensaje) {
        BigDecimal saldoOrigen = origen == null ? BigDecimal.ZERO : nz(origen.getSaldoDisponible());
        BigDecimal saldoDestino = destino == null ? BigDecimal.ZERO : nz(destino.getSaldoDisponible());
        return new SimulacionTransferencia(origen, destino, nz(monto),
                saldoOrigen, saldoOrigen, saldoDestino, saldoDestino, false, mensaje);
    }

    // --------------------------- tipos de resultado ---------------------------

    /** Totales de la carga activa con el porcentaje de ejecucion (devengado/vigente). */
    public static class ResumenGeneral {
        private final BigDecimal totalVigente;
        private final BigDecimal totalDevengado;
        private final BigDecimal totalPagado;
        private final BigDecimal saldoDisponible;
        private final double pctEjecucion;

        public ResumenGeneral(BigDecimal totalVigente, BigDecimal totalDevengado,
                              BigDecimal totalPagado, BigDecimal saldoDisponible,
                              double pctEjecucion) {
            this.totalVigente = totalVigente;
            this.totalDevengado = totalDevengado;
            this.totalPagado = totalPagado;
            this.saldoDisponible = saldoDisponible;
            this.pctEjecucion = pctEjecucion;
        }

        public BigDecimal getTotalVigente() { return totalVigente; }
        public BigDecimal getTotalDevengado() { return totalDevengado; }
        public BigDecimal getTotalPagado() { return totalPagado; }
        public BigDecimal getSaldoDisponible() { return saldoDisponible; }
        public double getPctEjecucion() { return pctEjecucion; }
    }

    /**
     * Un renglon de la carga activa con sus sumas y el cruce contra los
     * pagos del sistema: diferencia = pagado (SICOIN) - pagosSistema.
     */
    public static class RenglonResumen {
        private final String renglon;
        private final String descripcion;
        private final BigDecimal vigente;
        private final BigDecimal devengado;
        private final BigDecimal pagado;
        private final BigDecimal saldoDisponible;
        private final double pctEjecucion;
        private final BigDecimal pagosSistema;
        private final BigDecimal diferencia;

        public RenglonResumen(String renglon, String descripcion, BigDecimal vigente,
                              BigDecimal devengado, BigDecimal pagado, BigDecimal saldoDisponible,
                              double pctEjecucion, BigDecimal pagosSistema, BigDecimal diferencia) {
            this.renglon = renglon;
            this.descripcion = descripcion;
            this.vigente = vigente;
            this.devengado = devengado;
            this.pagado = pagado;
            this.saldoDisponible = saldoDisponible;
            this.pctEjecucion = pctEjecucion;
            this.pagosSistema = pagosSistema;
            this.diferencia = diferencia;
        }

        public String getRenglon() { return renglon; }
        public String getDescripcion() { return descripcion; }
        public BigDecimal getVigente() { return vigente; }
        public BigDecimal getDevengado() { return devengado; }
        public BigDecimal getPagado() { return pagado; }
        public BigDecimal getSaldoDisponible() { return saldoDisponible; }
        public double getPctEjecucion() { return pctEjecucion; }
        public BigDecimal getPagosSistema() { return pagosSistema; }
        public BigDecimal getDiferencia() { return diferencia; }
    }

    /** Una fuente de financiamiento de la carga activa con sus sumas, nombre y dinero real. */
    public static class FuenteResumen {
        private final String codigo;
        private final String nombre;
        private final BigDecimal vigente;
        private final BigDecimal devengado;
        private final BigDecimal pagado;
        private final BigDecimal saldoDisponible;
        private final double pctEjecucion;
        private final BigDecimal dineroReal;
        private final BigDecimal dineroRealFuncionamiento;
        private final BigDecimal dineroRealInversion;

        public FuenteResumen(String codigo, String nombre, BigDecimal vigente,
                             BigDecimal devengado, BigDecimal pagado,
                             BigDecimal saldoDisponible, double pctEjecucion) {
            this(codigo, nombre, vigente, devengado, pagado, saldoDisponible,
                    pctEjecucion, BigDecimal.ZERO);
        }

        public FuenteResumen(String codigo, String nombre, BigDecimal vigente,
                             BigDecimal devengado, BigDecimal pagado,
                             BigDecimal saldoDisponible, double pctEjecucion,
                             BigDecimal dineroReal) {
            this(codigo, nombre, vigente, devengado, pagado, saldoDisponible,
                    pctEjecucion, dineroReal, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public FuenteResumen(String codigo, String nombre, BigDecimal vigente,
                             BigDecimal devengado, BigDecimal pagado,
                             BigDecimal saldoDisponible, double pctEjecucion,
                             BigDecimal dineroReal,
                             BigDecimal dineroRealFuncionamiento,
                             BigDecimal dineroRealInversion) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.vigente = vigente;
            this.devengado = devengado;
            this.pagado = pagado;
            this.saldoDisponible = saldoDisponible;
            this.pctEjecucion = pctEjecucion;
            this.dineroReal = dineroReal == null ? BigDecimal.ZERO : dineroReal;
            this.dineroRealFuncionamiento = dineroRealFuncionamiento == null
                    ? BigDecimal.ZERO : dineroRealFuncionamiento;
            this.dineroRealInversion = dineroRealInversion == null
                    ? BigDecimal.ZERO : dineroRealInversion;
        }

        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
        public BigDecimal getVigente() { return vigente; }
        public BigDecimal getDevengado() { return devengado; }
        public BigDecimal getPagado() { return pagado; }
        public BigDecimal getSaldoDisponible() { return saldoDisponible; }
        public double getPctEjecucion() { return pctEjecucion; }
        public BigDecimal getDineroReal() { return dineroReal; }
        public BigDecimal getDineroRealFuncionamiento() { return dineroRealFuncionamiento; }
        public BigDecimal getDineroRealInversion() { return dineroRealInversion; }
    }

    /** Detalle de un renglon: sus lineas de la carga activa y los ultimos pagos del sistema. */
    public static class RenglonDetalle {
        private final List<LineaPresupuesto> lineas;
        private final List<HistorialCompra> pagos;

        public RenglonDetalle(List<LineaPresupuesto> lineas, List<HistorialCompra> pagos) {
            this.lineas = lineas;
            this.pagos = pagos;
        }

        public List<LineaPresupuesto> getLineas() { return lineas; }
        public List<HistorialCompra> getPagos() { return pagos; }
    }

    /**
     * Un renglon de compra directa con su saldo SICOIN, lo pagado en el
     * sistema durante el mes y el saldo proyectado; el semaforo resume el
     * riesgo de quedarse sin presupuesto antes de aprobar mas cheques.
     */
    public static class DisponibilidadRenglon {
        public static final String SEM_OK = "OK";
        public static final String SEM_POR_AGOTARSE = "POR_AGOTARSE";
        public static final String SEM_AGOTADO = "AGOTADO";

        private final String renglon;
        private final String descripcion;
        private final BigDecimal vigente;
        private final BigDecimal devengado;
        private final BigDecimal saldoDisponible;
        private final BigDecimal pagosMes;
        private final BigDecimal saldoProyectado;
        private final String semaforo;

        public DisponibilidadRenglon(String renglon, String descripcion, BigDecimal vigente,
                                     BigDecimal devengado, BigDecimal saldoDisponible,
                                     BigDecimal pagosMes, BigDecimal saldoProyectado,
                                     String semaforo) {
            this.renglon = renglon;
            this.descripcion = descripcion;
            this.vigente = vigente;
            this.devengado = devengado;
            this.saldoDisponible = saldoDisponible;
            this.pagosMes = pagosMes;
            this.saldoProyectado = saldoProyectado;
            this.semaforo = semaforo;
        }

        public String getRenglon() { return renglon; }
        public String getDescripcion() { return descripcion; }
        public BigDecimal getVigente() { return vigente; }
        public BigDecimal getDevengado() { return devengado; }
        public BigDecimal getSaldoDisponible() { return saldoDisponible; }
        public BigDecimal getPagosMes() { return pagosMes; }
        public BigDecimal getSaldoProyectado() { return saldoProyectado; }
        public String getSemaforo() { return semaforo; }
    }

    /**
     * Una linea de un renglon candidato en la busqueda "donde pagar": su
     * fuente (con el nombre del catalogo, "" si no tiene), la estructura
     * programatica y los saldos. alcanza dice si el saldo cubre el monto
     * pedido; sin monto pedido, basta con que el saldo sea positivo.
     */
    public static class LineaFuente {
        private final Long id;
        private final String fuente;
        private final String nombreFuente;
        private final String programa;
        private final String proyecto;
        private final String actividadObra;
        private final BigDecimal vigente;
        private final BigDecimal devengado;
        private final BigDecimal saldoDisponible;
        private final boolean alcanza;
        private final BigDecimal dineroReal;
        private final boolean alcanzaBanco;
        private final BigDecimal apartadoPresupuesto;
        private final BigDecimal apartadoBanco;

        public LineaFuente(String fuente, String nombreFuente, String programa,
                           String proyecto, String actividadObra, BigDecimal vigente,
                           BigDecimal devengado, BigDecimal saldoDisponible, boolean alcanza) {
            this(fuente, nombreFuente, programa, proyecto, actividadObra, vigente,
                    devengado, saldoDisponible, alcanza, BigDecimal.ZERO, false);
        }

        public LineaFuente(String fuente, String nombreFuente, String programa,
                           String proyecto, String actividadObra, BigDecimal vigente,
                           BigDecimal devengado, BigDecimal saldoDisponible, boolean alcanza,
                           BigDecimal dineroReal, boolean alcanzaBanco) {
            this(null, fuente, nombreFuente, programa, proyecto, actividadObra, vigente,
                    devengado, saldoDisponible, alcanza, dineroReal, alcanzaBanco,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public LineaFuente(String fuente, String nombreFuente, String programa,
                           String proyecto, String actividadObra, BigDecimal vigente,
                           BigDecimal devengado, BigDecimal saldoDisponible, boolean alcanza,
                           BigDecimal dineroReal, boolean alcanzaBanco,
                           BigDecimal apartadoPresupuesto, BigDecimal apartadoBanco) {
            this(null, fuente, nombreFuente, programa, proyecto, actividadObra, vigente,
                    devengado, saldoDisponible, alcanza, dineroReal, alcanzaBanco,
                    apartadoPresupuesto, apartadoBanco);
        }

        public LineaFuente(Long id, String fuente, String nombreFuente, String programa,
                           String proyecto, String actividadObra, BigDecimal vigente,
                           BigDecimal devengado, BigDecimal saldoDisponible, boolean alcanza,
                           BigDecimal dineroReal, boolean alcanzaBanco,
                           BigDecimal apartadoPresupuesto, BigDecimal apartadoBanco) {
            this.id = id;
            this.fuente = fuente;
            this.nombreFuente = nombreFuente;
            this.programa = programa;
            this.proyecto = proyecto;
            this.actividadObra = actividadObra;
            this.vigente = vigente;
            this.devengado = devengado;
            this.saldoDisponible = saldoDisponible;
            this.alcanza = alcanza;
            this.dineroReal = dineroReal == null ? BigDecimal.ZERO : dineroReal;
            this.alcanzaBanco = alcanzaBanco;
            this.apartadoPresupuesto = apartadoPresupuesto == null ? BigDecimal.ZERO : apartadoPresupuesto;
            this.apartadoBanco = apartadoBanco == null ? BigDecimal.ZERO : apartadoBanco;
        }

        public Long getId() { return id; }
        public String getFuente() { return fuente; }
        public String getNombreFuente() { return nombreFuente; }
        public String getPrograma() { return programa; }
        public String getProyecto() { return proyecto; }
        public String getActividadObra() { return actividadObra; }
        public BigDecimal getVigente() { return vigente; }
        public BigDecimal getDevengado() { return devengado; }
        public BigDecimal getSaldoDisponible() { return saldoDisponible; }
        public boolean isAlcanza() { return alcanza; }
        public BigDecimal getDineroReal() { return dineroReal; }
        public boolean isAlcanzaBanco() { return alcanzaBanco; }
        public BigDecimal getApartadoPresupuesto() { return apartadoPresupuesto; }
        public BigDecimal getApartadoBanco() { return apartadoBanco; }

        public String getEtiquetaTipoDinero() {
            TipoDineroCaja t = tipoDineroDePrograma(programa);
            if (t == TipoDineroCaja.FUNCIONAMIENTO) return "funcionamiento";
            if (t == TipoDineroCaja.INVERSION) return "inversión";
            return "";
        }
    }

    /**
     * Un renglon candidato para el gasto consultado: su descripcion, el
     * total disponible (suma de saldos de sus lineas) y las lineas por
     * fuente, ordenadas de mayor a menor saldo.
     */
    public static class BusquedaPago {
        private final String renglon;
        private final String descripcion;
        private final BigDecimal totalDisponible;
        private final List<LineaFuente> lineas;

        public BusquedaPago(String renglon, String descripcion,
                            BigDecimal totalDisponible, List<LineaFuente> lineas) {
            this.renglon = renglon;
            this.descripcion = descripcion;
            this.totalDisponible = totalDisponible;
            this.lineas = lineas;
        }

        public String getRenglon() { return renglon; }
        public String getDescripcion() { return descripcion; }
        public BigDecimal getTotalDisponible() { return totalDisponible; }
        public List<LineaFuente> getLineas() { return lineas; }
    }

    /**
     * Una linea dentro del desglose de una fuente: la entidad completa y
     * la marca alcanza (su saldo cubre el monto pedido; sin monto, que
     * tenga saldo positivo).
     */
    public static class LineaDesglose {
        private final LineaPresupuesto linea;
        private final boolean alcanza;
        private final BigDecimal saldoLibre;
        private final BigDecimal apartadoPresupuesto;
        private final BigDecimal dineroReal;

        public LineaDesglose(LineaPresupuesto linea, boolean alcanza) {
            this(linea, alcanza, linea == null ? BigDecimal.ZERO : nz(linea.getSaldoDisponible()),
                    BigDecimal.ZERO);
        }

        public LineaDesglose(LineaPresupuesto linea, boolean alcanza,
                             BigDecimal saldoLibre, BigDecimal apartadoPresupuesto) {
            this(linea, alcanza, saldoLibre, apartadoPresupuesto, BigDecimal.ZERO);
        }

        public LineaDesglose(LineaPresupuesto linea, boolean alcanza,
                             BigDecimal saldoLibre, BigDecimal apartadoPresupuesto,
                             BigDecimal dineroReal) {
            this.linea = linea;
            this.alcanza = alcanza;
            this.saldoLibre = saldoLibre == null ? BigDecimal.ZERO : saldoLibre;
            this.apartadoPresupuesto = apartadoPresupuesto == null ? BigDecimal.ZERO : apartadoPresupuesto;
            this.dineroReal = dineroReal == null ? BigDecimal.ZERO : dineroReal;
        }

        public LineaPresupuesto getLinea() { return linea; }
        public boolean isAlcanza() { return alcanza; }
        public BigDecimal getSaldoLibre() { return saldoLibre; }
        public BigDecimal getApartadoPresupuesto() { return apartadoPresupuesto; }
        public BigDecimal getDineroReal() { return dineroReal; }
    }

    /**
     * Un grupo del desglose de una fuente (un programa, o un programa +
     * proyecto) con sus subtotales y sus lineas ordenadas de mayor a
     * menor saldo: las donantes con mas holgura quedan arriba.
     */
    public static class GrupoDesglose {
        private final String titulo;
        private final BigDecimal subtotalVigente;
        private final BigDecimal subtotalDevengado;
        private final BigDecimal subtotalSaldo;
        private final List<LineaDesglose> lineas;

        public GrupoDesglose(String titulo, BigDecimal subtotalVigente,
                             BigDecimal subtotalDevengado, BigDecimal subtotalSaldo,
                             List<LineaDesglose> lineas) {
            this.titulo = titulo;
            this.subtotalVigente = subtotalVigente;
            this.subtotalDevengado = subtotalDevengado;
            this.subtotalSaldo = subtotalSaldo;
            this.lineas = lineas;
        }

        public String getTitulo() { return titulo; }
        public BigDecimal getSubtotalVigente() { return subtotalVigente; }
        public BigDecimal getSubtotalDevengado() { return subtotalDevengado; }
        public BigDecimal getSubtotalSaldo() { return subtotalSaldo; }
        public List<LineaDesglose> getLineas() { return lineas; }
    }

    /**
     * Desglose de una fuente de financiamiento de la carga activa: sus
     * totales y los grupos por programa/proyecto. Herramienta de analisis
     * previo a una transferencia; nada de esto modifica el presupuesto.
     */
    public static class DesgloseFuente {
        private final String codigo;
        private final String nombre;
        private final BigDecimal totalVigente;
        private final BigDecimal totalDevengado;
        private final BigDecimal totalPagado;
        private final BigDecimal totalSaldo;
        private final BigDecimal dineroReal;
        private final boolean alcanzaBanco;
        private final List<CuentaMonetaria> cuentasBanco;
        private final List<GrupoDesglose> grupos;
        private final BigDecimal dineroRealFuncionamiento;
        private final BigDecimal dineroRealInversion;

        public DesgloseFuente(String codigo, String nombre, BigDecimal totalVigente,
                              BigDecimal totalDevengado, BigDecimal totalPagado,
                              BigDecimal totalSaldo, List<GrupoDesglose> grupos) {
            this(codigo, nombre, totalVigente, totalDevengado, totalPagado, totalSaldo,
                    BigDecimal.ZERO, false, List.of(), grupos);
        }

        public DesgloseFuente(String codigo, String nombre, BigDecimal totalVigente,
                              BigDecimal totalDevengado, BigDecimal totalPagado,
                              BigDecimal totalSaldo, BigDecimal dineroReal,
                              boolean alcanzaBanco, List<CuentaMonetaria> cuentasBanco,
                              List<GrupoDesglose> grupos) {
            this(codigo, nombre, totalVigente, totalDevengado, totalPagado, totalSaldo,
                    dineroReal, alcanzaBanco, cuentasBanco, grupos,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public DesgloseFuente(String codigo, String nombre, BigDecimal totalVigente,
                              BigDecimal totalDevengado, BigDecimal totalPagado,
                              BigDecimal totalSaldo, BigDecimal dineroReal,
                              boolean alcanzaBanco, List<CuentaMonetaria> cuentasBanco,
                              List<GrupoDesglose> grupos,
                              BigDecimal dineroRealFuncionamiento,
                              BigDecimal dineroRealInversion) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.totalVigente = totalVigente;
            this.totalDevengado = totalDevengado;
            this.totalPagado = totalPagado;
            this.totalSaldo = totalSaldo;
            this.dineroReal = dineroReal == null ? BigDecimal.ZERO : dineroReal;
            this.alcanzaBanco = alcanzaBanco;
            this.cuentasBanco = cuentasBanco == null ? List.of() : cuentasBanco;
            this.grupos = grupos;
            this.dineroRealFuncionamiento = dineroRealFuncionamiento == null
                    ? BigDecimal.ZERO : dineroRealFuncionamiento;
            this.dineroRealInversion = dineroRealInversion == null
                    ? BigDecimal.ZERO : dineroRealInversion;
        }

        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
        public BigDecimal getTotalVigente() { return totalVigente; }
        public BigDecimal getTotalDevengado() { return totalDevengado; }
        public BigDecimal getTotalPagado() { return totalPagado; }
        public BigDecimal getTotalSaldo() { return totalSaldo; }
        public BigDecimal getDineroReal() { return dineroReal; }
        public boolean isAlcanzaBanco() { return alcanzaBanco; }
        public List<CuentaMonetaria> getCuentasBanco() { return cuentasBanco; }
        public List<GrupoDesglose> getGrupos() { return grupos; }
        public BigDecimal getDineroRealFuncionamiento() { return dineroRealFuncionamiento; }
        public BigDecimal getDineroRealInversion() { return dineroRealInversion; }
    }

    /**
     * Resultado de simular una transferencia de saldo entre dos lineas.
     * Es solo un calculo en memoria (la modificacion real se hace en
     * SICOIN): valida dice si la operacion se podria tramitar y mensaje
     * lo explica. Si alguna linea no existe, origen/destino pueden ser
     * null; en las invalidas los saldos "despues" son iguales a los
     * "antes".
     */
    public static class SimulacionTransferencia {
        private final LineaPresupuesto origen;
        private final LineaPresupuesto destino;
        private final BigDecimal monto;
        private final BigDecimal saldoOrigenAntes;
        private final BigDecimal saldoOrigenDespues;
        private final BigDecimal saldoDestinoAntes;
        private final BigDecimal saldoDestinoDespues;
        private final boolean valida;
        private final String mensaje;

        public SimulacionTransferencia(LineaPresupuesto origen, LineaPresupuesto destino,
                                       BigDecimal monto, BigDecimal saldoOrigenAntes,
                                       BigDecimal saldoOrigenDespues, BigDecimal saldoDestinoAntes,
                                       BigDecimal saldoDestinoDespues, boolean valida,
                                       String mensaje) {
            this.origen = origen;
            this.destino = destino;
            this.monto = monto;
            this.saldoOrigenAntes = saldoOrigenAntes;
            this.saldoOrigenDespues = saldoOrigenDespues;
            this.saldoDestinoAntes = saldoDestinoAntes;
            this.saldoDestinoDespues = saldoDestinoDespues;
            this.valida = valida;
            this.mensaje = mensaje;
        }

        public LineaPresupuesto getOrigen() { return origen; }
        public LineaPresupuesto getDestino() { return destino; }
        public BigDecimal getMonto() { return monto; }
        public BigDecimal getSaldoOrigenAntes() { return saldoOrigenAntes; }
        public BigDecimal getSaldoOrigenDespues() { return saldoOrigenDespues; }
        public BigDecimal getSaldoDestinoAntes() { return saldoDestinoAntes; }
        public BigDecimal getSaldoDestinoDespues() { return saldoDestinoDespues; }
        public boolean isValida() { return valida; }
        public String getMensaje() { return mensaje; }
    }

    /** Totales de reservas ACTIVO para el encabezado de la vista. */
    public static class ResumenApartados {
        private final int cantidad;
        private final BigDecimal totalPresupuesto;
        private final BigDecimal totalBanco;

        public ResumenApartados(int cantidad, BigDecimal totalPresupuesto,
                                BigDecimal totalBanco) {
            this.cantidad = cantidad;
            this.totalPresupuesto = totalPresupuesto == null ? BigDecimal.ZERO : totalPresupuesto;
            this.totalBanco = totalBanco == null ? BigDecimal.ZERO : totalBanco;
        }

        public int getCantidad() { return cantidad; }
        public BigDecimal getTotalPresupuesto() { return totalPresupuesto; }
        public BigDecimal getTotalBanco() { return totalBanco; }
    }

    /** Foto de una linea al momento de apartar: SICOIN, ya reservado y libre. */
    public static class VistaApartar {
        private final LineaPresupuesto linea;
        private final String nombreFuente;
        private final int anio;
        private final BigDecimal presupuestoSicoin;
        private final BigDecimal presupuestoApartado;
        private final BigDecimal presupuestoLibre;
        private final BigDecimal bancoSicoin;
        private final BigDecimal bancoApartado;
        private final BigDecimal bancoLibre;

        public VistaApartar(LineaPresupuesto linea, String nombreFuente, int anio,
                            BigDecimal presupuestoSicoin, BigDecimal presupuestoApartado,
                            BigDecimal presupuestoLibre, BigDecimal bancoSicoin,
                            BigDecimal bancoApartado, BigDecimal bancoLibre) {
            this.linea = linea;
            this.nombreFuente = nombreFuente == null ? "" : nombreFuente;
            this.anio = anio;
            this.presupuestoSicoin = presupuestoSicoin;
            this.presupuestoApartado = presupuestoApartado;
            this.presupuestoLibre = presupuestoLibre;
            this.bancoSicoin = bancoSicoin;
            this.bancoApartado = bancoApartado;
            this.bancoLibre = bancoLibre;
        }

        public LineaPresupuesto getLinea() { return linea; }
        public String getNombreFuente() { return nombreFuente; }
        public int getAnio() { return anio; }
        public BigDecimal getPresupuestoSicoin() { return presupuestoSicoin; }
        public BigDecimal getPresupuestoApartado() { return presupuestoApartado; }
        public BigDecimal getPresupuestoLibre() { return presupuestoLibre; }
        public BigDecimal getBancoSicoin() { return bancoSicoin; }
        public BigDecimal getBancoApartado() { return bancoApartado; }
        public BigDecimal getBancoLibre() { return bancoLibre; }

        /** "funcionamiento", "inversión" o vacio si el programa no se clasifica. */
        public String getEtiquetaTipoDinero() {
            TipoDineroCaja t = tipoDineroDePrograma(linea == null ? null : linea.getPrograma());
            if (t == TipoDineroCaja.FUNCIONAMIENTO) return "funcionamiento";
            if (t == TipoDineroCaja.INVERSION) return "inversión";
            return "";
        }
    }

    /** Apartado ACTIVO sin banco + saldos libres de la fuente (sin contarlo). */
    public static class FormularioAgregarBanco {
        private final Apartado apartado;
        private final VistaApartar vista;

        public FormularioAgregarBanco(Apartado apartado, VistaApartar vista) {
            this.apartado = apartado;
            this.vista = vista;
        }

        public Apartado getApartado() { return apartado; }
        public VistaApartar getVista() { return vista; }
    }

    // -------------------- dinero real funcionamiento / inversion --------------------

    public static TipoDineroCaja clasificarTipoCuentaCaja(String codigo, String descripcion) {
        return ParserBoletinCaja.clasificarTipo(codigo, descripcion);
    }

    public static TipoDineroCaja tipoDineroDePrograma(String programa) {
        String cod = codigoPrograma(programa);
        if (cod.isEmpty()) return TipoDineroCaja.DESCONOCIDO;
        if ("01".equals(cod)) return TipoDineroCaja.FUNCIONAMIENTO;
        return TipoDineroCaja.INVERSION;
    }

    public static String claveDineroCaja(String fuente, TipoDineroCaja tipo) {
        if (fuente == null || fuente.isBlank() || tipo == null
                || tipo == TipoDineroCaja.DESCONOCIDO) {
            return "";
        }
        return fuente.strip() + "|" + tipo.id();
    }

    public static TipoDineroCaja tipoDeCuentaCaja(CuentaMonetaria cuenta) {
        if (cuenta == null) return TipoDineroCaja.DESCONOCIDO;
        String persistido = cuenta.getTipo();
        if (persistido != null && !persistido.isBlank()) {
            try {
                return TipoDineroCaja.valueOf(persistido.strip().toUpperCase());
            } catch (IllegalArgumentException ignorado) {
                // se reclasifica por codigo y descripcion
            }
        }
        return clasificarTipoCuentaCaja(cuenta.getCodigo(), cuenta.getDescripcion());
    }

    /**
     * Dinero real de la fuente filtrado por el tipo que le toca al programa
     * (01 = funcionamiento; el resto = inversion). Programa vacio o cuenta
     * desconocida: 0, no se mezcla.
     */
    public static BigDecimal dineroRealDeFuenteParaPrograma(String codigoFuente, String programa,
                                                            List<CuentaMonetaria> cuentas) {
        return dineroRealDeFuenteParaTipo(codigoFuente, tipoDineroDePrograma(programa), cuentas);
    }

    public static BigDecimal dineroRealDeFuenteParaTipo(String codigoFuente, TipoDineroCaja tipo,
                                                        List<CuentaMonetaria> cuentas) {
        if (tipo == null || tipo == TipoDineroCaja.DESCONOCIDO) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (CuentaMonetaria c : cuentasDeFuente(codigoFuente, cuentas)) {
            if (tipoDeCuentaCaja(c) == tipo) {
                total = total.add(nz(c.getNuevoSaldo()));
            }
        }
        return total;
    }

    /**
     * Mapa "fuente|funcionamiento" / "fuente|inversion" -> nuevo saldo.
     * Cuentas cortas y desconocidas no entran.
     */
    public static Map<String, BigDecimal> dineroRealPorFuenteYTipo(List<CuentaMonetaria> cuentas) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        if (cuentas == null) return mapa;
        for (CuentaMonetaria c : cuentas) {
            String fuente = codigoFuenteDeCuenta(c.getCodigo());
            if (fuente.isEmpty()) continue;
            String clave = claveDineroCaja(fuente, tipoDeCuentaCaja(c));
            if (clave.isEmpty()) continue;
            mapa.merge(clave, nz(c.getNuevoSaldo()), BigDecimal::add);
        }
        return mapa;
    }

    /** Mixto por fuente mas pozos por tipo, para el listado de fuentes. */
    public static Map<String, BigDecimal> dineroRealConTipos(List<CuentaMonetaria> cuentas) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>(dineroRealPorFuente(cuentas));
        mapa.putAll(dineroRealPorFuenteYTipo(cuentas));
        return mapa;
    }

    /**
     * Apartado de banco agrupado por fuente y tipo del programa de la
     * reserva. No cruza funcionamiento con inversion.
     */
    public static Map<String, BigDecimal> apartadoBancoPorFuenteYTipo(List<Apartado> lista) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        if (lista == null) return mapa;
        for (Apartado a : lista) {
            if (a == null || !Apartado.EST_ACTIVO.equals(a.getEstado())) continue;
            if (nz(a.getMontoBanco()).signum() <= 0) continue;
            String fuente = a.getFuente() == null ? "" : a.getFuente();
            String clave = claveDineroCaja(fuente, tipoDineroDePrograma(a.getPrograma()));
            if (clave.isEmpty()) continue;
            mapa.merge(clave, nz(a.getMontoBanco()), BigDecimal::add);
        }
        return mapa;
    }

    /**
     * Lee el pozo tipado de una linea. Programa 01/19/32/etc. nunca cae
     * al total mixto de la fuente (eso pintaba 93045.91+6000 en todas
     * las lineas de ¿con que pago?). El listado de fuentes usa
     * {@link #dineroRealDeMapa}. Programa sin tipo: 0 si hay claves
     * tipadas de esa fuente; si no, la clave mixta (mapas viejos).
     */
    public static BigDecimal valorPorFuenteYTipo(Map<String, BigDecimal> mapa,
                                                 String fuente, String programa) {
        if (mapa == null || mapa.isEmpty()) return BigDecimal.ZERO;
        String fu = fuente == null ? "" : fuente;
        TipoDineroCaja tipo = tipoDineroDePrograma(programa);
        boolean hayTipadas = mapa.containsKey(claveDineroCaja(fu, TipoDineroCaja.FUNCIONAMIENTO))
                || mapa.containsKey(claveDineroCaja(fu, TipoDineroCaja.INVERSION));
        if (tipo == TipoDineroCaja.FUNCIONAMIENTO || tipo == TipoDineroCaja.INVERSION) {
            return nz(mapa.get(claveDineroCaja(fu, tipo)));
        }
        if (hayTipadas) return BigDecimal.ZERO;
        return nz(mapa.get(fu));
    }

    private static BigDecimal dineroRealDeMapa(Map<String, BigDecimal> mapa, String fuente) {
        BigDecimal mixed = nz(mapa == null ? null : mapa.get(fuente));
        if (mixed.signum() != 0) return mixed;
        return nz(mapa == null ? null : mapa.get(claveDineroCaja(fuente,
                TipoDineroCaja.FUNCIONAMIENTO)))
                .add(nz(mapa == null ? null : mapa.get(claveDineroCaja(fuente,
                        TipoDineroCaja.INVERSION))));
    }
}
