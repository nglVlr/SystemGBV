package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.ContratoInfo;
import com.granados.sistema.dafim.compras.dto.DatosBd;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.ProveedorInfo;
import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import com.granados.sistema.dafim.compras.dto.RegistroHistorial;
import com.granados.sistema.dafim.compras.entity.ContratoPersonal029;
import com.granados.sistema.dafim.compras.entity.HistorialCompra;
import com.granados.sistema.dafim.compras.entity.ProcesoMensual;
import com.granados.sistema.dafim.compras.entity.Proveedor;
import com.granados.sistema.dafim.compras.entity.PublicacionGuatecompras;
import com.granados.sistema.dafim.compras.repository.PublicacionGuatecomprasRepository;
import com.granados.sistema.dafim.compras.repository.ContratoPersonal029Repository;
import com.granados.sistema.dafim.compras.repository.HistorialCompraRepository;
import com.granados.sistema.dafim.compras.repository.ProcesoMensualRepository;
import com.granados.sistema.dafim.compras.repository.ProveedorRepository;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Capa de persistencia del modulo de Compras Directas.
 *
 * Traduce las operaciones que la version Colab hacia contra Google Sheets a
 * operaciones JPA sobre MySQL, conservando el comportamiento:
 *
 *  - cargarBd()        -> lee contratos_029 y proveedores a mapas por NIT
 *                          (mismo shape que cargar_bd() del Python).
 *  - fusionarBd()      -> merge de lo detectado en el mes contra lo ya
 *                          guardado, con UPSERT por NIT (en Sheets se borraba
 *                          y reescribia toda la hoja; aqui no).
 *  - guardarHistorial()-> DELETE de (anio, mes) + INSERT de las filas nuevas,
 *                          que es lo que permite reprocesar un mes sin
 *                          duplicar registros.
 */
@Service
public class GestionBaseDatosComprasService {

    private final ContratoPersonal029Repository contratoRepo;
    private final ProveedorRepository proveedorRepo;
    private final HistorialCompraRepository historialRepo;
    private final ProcesoMensualRepository procesoRepo;
    private final PublicacionGuatecomprasRepository publicacionRepo;

    public GestionBaseDatosComprasService(ContratoPersonal029Repository contratoRepo,
                                          ProveedorRepository proveedorRepo,
                                          HistorialCompraRepository historialRepo,
                                          ProcesoMensualRepository procesoRepo,
                                          PublicacionGuatecomprasRepository publicacionRepo) {
        this.contratoRepo = contratoRepo;
        this.proveedorRepo = proveedorRepo;
        this.historialRepo = historialRepo;
        this.procesoRepo = procesoRepo;
        this.publicacionRepo = publicacionRepo;
    }

    /** Equivalente de cargar_bd(): mapas por NIT en orden estable (id). */
    @Transactional(readOnly = true)
    public DatosBd cargarBd() {
        DatosBd bd = new DatosBd();
        for (ContratoPersonal029 c : contratoRepo.findAll(Sort.by("id"))) {
            String nit = c.getNit() == null ? "" : c.getNit().trim();
            if (nit.isEmpty()) continue;
            String contrato = c.getContrato() == null || c.getContrato().isEmpty()
                    ? "N/A" : c.getContrato();
            ContratoInfo info = new ContratoInfo(
                    nn(c.getNombre()), contrato, nn(c.getCargo()),
                    c.getNpg() == null ? "" : c.getNpg().trim(), c.getAnio());
            bd.getContratos().put(nit, info);
            if (!info.getNpg().isEmpty()) {
                bd.getNpgs029().put(nit, info.getNpg());
            }
        }
        for (Proveedor p : proveedorRepo.findAll(Sort.by("id"))) {
            String nit = p.getNit() == null ? "" : p.getNit().trim();
            if (nit.isEmpty()) continue;
            bd.getProveedores().put(nit, new ProveedorInfo(
                    nn(p.getNombre()),
                    Constantes.zfill3(nn(p.getRenglon())),
                    nn(p.getDescripcion())));
        }
        return bd;
    }

    /** Equivalente de cargar_historial(). */
    @Transactional(readOnly = true)
    public List<RegistroHistorial> cargarHistorial() {
        List<RegistroHistorial> out = new ArrayList<>();
        for (HistorialCompra h : historialRepo.findAll(Sort.by("id"))) {
            out.add(aRegistro(h));
        }
        return out;
    }

    /**
     * Equivalente de fusionar_bd() + guardar_bd_batch(), pero como UPSERT
     * real por NIT en MySQL:
     *  - contrato solo se actualiza si el nuevo no es N/A,
     *  - cargo solo se llena si estaba vacio,
     *  - npg se actualiza si el nuevo trae valor,
     *  - anio siempre se actualiza al del mes procesado,
     *  - proveedores nuevos se insertan; los existentes no se tocan.
     */
    @Transactional
    public void fusionarBd(Map<String, ContratoInfo> nuevos029,
                           Map<String, ProveedorInfo> nuevosProv,
                           int anio) {
        for (Map.Entry<String, ContratoInfo> e : nuevos029.entrySet()) {
            String nit = e.getKey();
            ContratoInfo d = e.getValue();
            ContratoPersonal029 ent = contratoRepo.findByNit(nit)
                    .orElseGet(ContratoPersonal029::new);
            if (ent.getId() == null) {
                ent.setNit(nit);
                ent.setNombre(d.getNombre());
                ent.setContrato(d.getContrato());
                ent.setCargo(d.getCargo());
                ent.setNpg(d.getNpg());
            } else {
                if (!"N/A".equals(d.getContrato())) ent.setContrato(d.getContrato());
                if (d.getCargo() != null && !d.getCargo().isEmpty()
                        && (ent.getCargo() == null || ent.getCargo().isEmpty())) {
                    ent.setCargo(d.getCargo());
                }
                if (d.getNpg() != null && !d.getNpg().isEmpty()) ent.setNpg(d.getNpg());
            }
            ent.setAnio(anio);
            contratoRepo.save(ent);
        }
        for (Map.Entry<String, ProveedorInfo> e : nuevosProv.entrySet()) {
            if (proveedorRepo.findByNit(e.getKey()).isEmpty()) {
                ProveedorInfo d = e.getValue();
                Proveedor p = new Proveedor();
                p.setNit(e.getKey());
                p.setNombre(d.getNombre());
                p.setRenglon(d.getRenglon());
                p.setDescripcion(d.getDesc());
                proveedorRepo.save(p);
            }
        }
    }

    /**
     * Equivalente de guardar_historial(): borra lo que hubiera de ese
     * (anio, mes) e inserta las filas nuevas. Retorna cuantas se
     * reemplazaron. Todo en una transaccion: si el INSERT falla, el
     * DELETE tambien se revierte.
     */
    @Transactional
    public long guardarHistorial(List<FilaCompra> filas, int anio, int mes,
                                 Long idUsuario) {
        long reemplazadas = historialRepo.deleteByAnioAndMes(anio, mes);
        LocalDateTime ahora = LocalDateTime.now();
        List<HistorialCompra> nuevas = new ArrayList<>(filas.size());
        for (FilaCompra f : filas) {
            HistorialCompra h = new HistorialCompra();
            h.setAnio(anio);
            h.setMes(mes);
            h.setCheque(f.getCheque());
            h.setNit(f.getNit());
            h.setNombre(f.getProveedor());
            h.setRenglon(f.getRenglon());
            h.setMonto(BigDecimal.valueOf(f.getPrecio()));
            h.setNpg(f.getNpg());
            h.setModalidad(f.getModalidad());
            h.setContrato(f.getContrato());
            String desc = f.getDesc() == null ? "" : f.getDesc();
            h.setDescripcion(TextoUtil.corta(desc, 200));
            h.setIdUsuarioProceso(idUsuario);
            h.setFechaProceso(ahora);
            nuevas.add(h);
        }
        historialRepo.saveAll(nuevas);
        return reemplazadas;
    }

    @Transactional
    public void registrarProceso(int anio, int mes, Long idUsuario, int totalFilas,
                                 double totalMonto, List<String> alertas,
                                 String rutaExcel, String estado) {
        ProcesoMensual p = new ProcesoMensual();
        p.setAnio(anio);
        p.setMes(mes);
        p.setFechaProceso(LocalDateTime.now());
        p.setIdUsuario(idUsuario);
        p.setTotalFilas(totalFilas);
        p.setTotalMonto(BigDecimal.valueOf(totalMonto));
        p.setAlertas(alertas == null ? "" : String.join("\n", alertas));
        p.setRutaExcelGenerado(rutaExcel);
        p.setEstado(estado);
        procesoRepo.save(p);
    }

    /**
     * UPSERT del catalogo de publicaciones Guatecompras por NPG.
     * Retorna {nuevos, actualizados}.
     */
    @Transactional
    public int[] upsertPublicaciones(List<RegistroGuatecompras> regs,
                                     String origen) {
        int nuevos = 0;
        int actualizados = 0;
        if (regs == null) return new int[]{0, 0};
        for (RegistroGuatecompras r : regs) {
            String npg = r.getNpg() == null ? "" : r.getNpg().trim();
            if (npg.isEmpty()) continue;
            Optional<PublicacionGuatecompras> existente = publicacionRepo.findByNpg(npg);
            if (existente.isPresent()) {
                existente.get().aplicar(r, origen);
                publicacionRepo.save(existente.get());
                actualizados++;
            } else {
                publicacionRepo.save(PublicacionGuatecompras.de(r, origen));
                nuevos++;
            }
        }
        return new int[]{nuevos, actualizados};
    }

    /** Catalogo completo como registros del motor (NIT, nombre, desc, monto). */
    @Transactional(readOnly = true)
    public List<RegistroGuatecompras> publicacionesComoRegistros() {
        List<RegistroGuatecompras> out = new ArrayList<>();
        for (PublicacionGuatecompras p : publicacionRepo.findAll(Sort.by("id"))) {
            RegistroGuatecompras r = new RegistroGuatecompras();
            r.setNpg(nn(p.getNpg()));
            r.setFecha(nn(p.getFechaTexto()));
            r.setModalidad(nn(p.getModalidad()));
            r.setDesc(nn(p.getDescripcion()));
            r.setNit(nn(p.getNit()));
            r.setProveedor(nn(p.getNombre()));
            r.setMonto(p.getMonto() == null ? 0 : p.getMonto().doubleValue());
            out.add(r);
        }
        return out;
    }

    /**
     * Busqueda por NIT exacto o nombre parcial sin tildes en las 3 tablas.
     * El norm() se aplica en memoria porque MySQL con la colacion por
     * defecto no ignora tildes de forma fiable y el volumen es pequeno
     * (cientos de registros, no millones).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buscar(String q) {
        return buscar(q, "TODOS", "", null, null);
    }

    public Map<String, Object> buscar(String q, String tipo, String renglon,
                                      Integer anio, Integer mes) {
        return buscar(q, tipo, renglon, anio, mes, false);
    }

    /**
     * Busqueda con filtros combinables. El texto busca en NIT (exacto),
     * nombre, NPG, contrato, cheque y descripcion. {@code sinPago} deja
     * solo publicaciones cuyo NPG no aparece en el historial de cheques.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buscar(String q, String tipo, String renglon,
                                      Integer anio, Integer mes, boolean sinPago) {
        Map<String, Object> out = new LinkedHashMap<>();
        String qn = TextoUtil.norm(q == null ? "" : q.trim());
        String tf = tipo == null || tipo.isBlank() ? "TODOS" : tipo;
        String rf = renglon == null ? "" : renglon.trim();
        boolean hayFiltro = !qn.isEmpty() || !rf.isEmpty() || anio != null
                || mes != null || sinPago || "PUBLICACIONES".equals(tf);

        List<ContratoPersonal029> contratos = new ArrayList<>();
        List<Proveedor> proveedores = new ArrayList<>();
        List<HistorialCompra> pagos = new ArrayList<>();
        List<PublicacionGuatecompras> publicaciones = new ArrayList<>();
        if (hayFiltro) {
            if ("TODOS".equals(tf) || "CONTRATOS".equals(tf)) {
                for (ContratoPersonal029 c : contratoRepo.findAll(Sort.by("id"))) {
                    boolean texto = qn.isEmpty()
                            || qn.equals(nn(c.getNit()).trim())
                            || TextoUtil.norm(nn(c.getNombre())).contains(qn)
                            || TextoUtil.norm(nn(c.getNpg())).contains(qn)
                            || TextoUtil.norm(nn(c.getContrato())).contains(qn)
                            || TextoUtil.norm(nn(c.getCargo())).contains(qn);
                    boolean porAnio = anio == null
                            || (c.getAnio() != null && c.getAnio().equals(anio));
                    if (texto && porAnio && rf.isEmpty()) contratos.add(c);
                }
            }
            if ("TODOS".equals(tf) || "PROVEEDORES".equals(tf)) {
                for (Proveedor p : proveedorRepo.findAll(Sort.by("id"))) {
                    boolean texto = qn.isEmpty()
                            || qn.equals(nn(p.getNit()).trim())
                            || TextoUtil.norm(nn(p.getNombre())).contains(qn)
                            || TextoUtil.norm(nn(p.getDescripcion())).contains(qn);
                    boolean porRenglon = rf.isEmpty()
                            || rf.equals(nn(p.getRenglon()).trim());
                    if (texto && porRenglon && anio == null && mes == null) {
                        proveedores.add(p);
                    }
                }
            }
            if ("TODOS".equals(tf) || "PAGOS".equals(tf)) {
                for (HistorialCompra h : historialRepo.findAll(
                        Sort.by(Sort.Direction.DESC, "anio", "mes"))) {
                    boolean texto = qn.isEmpty()
                            || qn.equals(nn(h.getNit()).trim())
                            || TextoUtil.norm(nn(h.getNombre())).contains(qn)
                            || TextoUtil.norm(nn(h.getNpg())).contains(qn)
                            || TextoUtil.norm(nn(h.getContrato())).contains(qn)
                            || TextoUtil.norm(nn(h.getCheque())).contains(qn)
                            || TextoUtil.norm(nn(h.getDescripcion())).contains(qn);
                    boolean porRenglon = rf.isEmpty()
                            || rf.equals(nn(h.getRenglon()).trim());
                    boolean porAnio = anio == null
                            || (h.getAnio() != null && h.getAnio().equals(anio));
                    boolean porMes = mes == null
                            || (h.getMes() != null && h.getMes().equals(mes));
                    if (texto && porRenglon && porAnio && porMes) pagos.add(h);
                }
            }
            if ("TODOS".equals(tf) || "PUBLICACIONES".equals(tf)) {
                java.util.Set<String> npgPagados = new java.util.HashSet<>();
                if (sinPago) {
                    for (HistorialCompra h : historialRepo.findAll()) {
                        String n = nn(h.getNpg()).trim();
                        if (!n.isEmpty()) npgPagados.add(n);
                    }
                }
                for (PublicacionGuatecompras p : publicacionRepo.findAll(
                        Sort.by(Sort.Direction.DESC, "anio", "mes", "id"))) {
                    boolean texto = qn.isEmpty()
                            || qn.equals(nn(p.getNit()).trim())
                            || TextoUtil.norm(nn(p.getNpg())).contains(qn)
                            || TextoUtil.norm(nn(p.getNombre())).contains(qn)
                            || TextoUtil.norm(nn(p.getDescripcion())).contains(qn);
                    boolean porAnio = anio == null
                            || (p.getAnio() != null && p.getAnio().equals(anio));
                    boolean porMes = mes == null
                            || (p.getMes() != null && p.getMes().equals(mes));
                    boolean noPagado = !sinPago || !npgPagados.contains(nn(p.getNpg()).trim());
                    if (texto && porAnio && porMes && noPagado && rf.isEmpty()) {
                        publicaciones.add(p);
                    }
                }
            }
        }
        out.put("contratos", contratos);
        out.put("proveedores", proveedores);
        out.put("pagos", pagos);
        out.put("publicaciones", publicaciones);
        return out;
    }

    /** Estadisticas para la pantalla de resumen de BD. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<HistorialCompra> todos = historialRepo.findAll(Sort.by("anio", "mes", "id"));

        Map<String, double[]> porMes = new LinkedHashMap<>();
        Map<String, double[]> porProveedor = new LinkedHashMap<>();
        Map<String, String> nombreProveedor = new LinkedHashMap<>();
        double total = 0;
        for (HistorialCompra h : todos) {
            String clave = String.format("%02d/%d", h.getMes(), h.getAnio());
            double m = h.getMonto() == null ? 0 : h.getMonto().doubleValue();
            porMes.computeIfAbsent(clave, k -> new double[2]);
            porMes.get(clave)[0] += 1;
            porMes.get(clave)[1] += m;
            String nit = nn(h.getNit());
            if (!nit.isEmpty()) {
                porProveedor.computeIfAbsent(nit, k -> new double[2]);
                porProveedor.get(nit)[0] += 1;
                porProveedor.get(nit)[1] += m;
                nombreProveedor.put(nit, nn(h.getNombre()));
            }
            total += m;
        }
        List<Map<String, Object>> meses = new ArrayList<>();
        for (Map.Entry<String, double[]> e : porMes.entrySet()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("mes", e.getKey());
            fila.put("cantidad", (int) e.getValue()[0]);
            fila.put("monto", e.getValue()[1]);
            meses.add(fila);
        }
        List<Map<String, Object>> top = new ArrayList<>();
        porProveedor.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("nit", e.getKey());
                    fila.put("nombre", nombreProveedor.get(e.getKey()));
                    fila.put("pagos", (int) e.getValue()[0]);
                    fila.put("monto", e.getValue()[1]);
                    top.add(fila);
                });
        out.put("meses", meses);
        out.put("top", top);
        out.put("totalRegistros", todos.size());
        out.put("totalMonto", total);
        out.put("totalContratos", contratoRepo.count());
        out.put("totalProveedores", proveedorRepo.count());
        out.put("totalPublicaciones", publicacionRepo.count());
        return out;
    }

    /** Tarjetas del dashboard DAFIM. */
    @Transactional(readOnly = true)
    public Map<String, Object> estadisticasDashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalContratos", contratoRepo.count());
        out.put("totalProveedores", proveedorRepo.count());
        out.put("totalHistorial", historialRepo.count());
        out.put("totalPublicaciones", publicacionRepo.count());
        Optional<ProcesoMensual> ultimo = procesoRepo.findTopByOrderByAnioDescMesDesc();
        if (ultimo.isPresent()) {
            ProcesoMensual p = ultimo.get();
            out.put("ultimoMes", String.format("%02d/%d", p.getMes(), p.getAnio()));
            out.put("ultimoMesNombre",
                    Constantes.MESES_NOMBRE.getOrDefault(p.getMes(), "") + " " + p.getAnio());
            out.put("ultimoEstado", p.getEstado());
            out.put("ultimoTotalFilas", p.getTotalFilas());
            out.put("ultimoTotalMonto",
                    p.getTotalMonto() == null ? 0.0 : p.getTotalMonto().doubleValue());
        } else {
            out.put("ultimoMes", null);
        }
        return out;
    }

    /** Set de meses ya guardados, como "MM/aaaa", para avisar reprocesos. */
    @Transactional(readOnly = true)
    public boolean existeMesGuardado(int anio, int mes) {
        return !historialRepo.findByAnioAndMes(anio, mes).isEmpty();
    }

    /**
     * Aplica los NPGs extraidos de PDFs de confirmacion (flujo "Cargar NPGs
     * historicos"), con la misma logica de la celda 3 del Colab:
     *  - si el NIT ya existe: actualiza npg si trae valor, y contrato solo
     *    si el guardado era N/A;
     *  - si no existe: crea el registro con cargo vacio y el anio dado.
     * Retorna {actualizados, nuevos}.
     */
    @Transactional
    public int[] aplicarNpgs(List<com.granados.sistema.dafim.compras.dto.NpgConfirmacion> resultados,
                             int anio) {
        int actualizados = 0;
        int nuevos = 0;
        for (com.granados.sistema.dafim.compras.dto.NpgConfirmacion r : resultados) {
            String nit = r.getNit();
            if (nit == null || nit.isEmpty()) continue;
            Optional<ContratoPersonal029> existente = contratoRepo.findByNit(nit);
            if (existente.isPresent()) {
                ContratoPersonal029 ent = existente.get();
                if (r.getNpg() != null && !r.getNpg().isEmpty()) {
                    ent.setNpg(r.getNpg());
                }
                String contratoActual = ent.getContrato() == null ? "" : ent.getContrato();
                if (r.getContrato() != null && !r.getContrato().isEmpty()
                        && ("N/A".equals(contratoActual) || contratoActual.isEmpty())) {
                    ent.setContrato(r.getContrato());
                }
                contratoRepo.save(ent);
                actualizados++;
            } else {
                ContratoPersonal029 ent = new ContratoPersonal029();
                ent.setNit(nit);
                ent.setNombre(r.getNombre());
                ent.setContrato(r.getContrato() == null || r.getContrato().isEmpty()
                        ? "N/A" : r.getContrato());
                ent.setCargo("");
                ent.setNpg(r.getNpg());
                ent.setAnio(anio);
                contratoRepo.save(ent);
                nuevos++;
            }
        }
        return new int[]{actualizados, nuevos};
    }

    /** Snapshot completo de la BD para exportar. */
    @Transactional(readOnly = true)
    public Map<String, ContratoInfo> contratosComoMapa() {
        return cargarBd().getContratos();
    }

    @Transactional(readOnly = true)
    public Map<String, ProveedorInfo> proveedoresComoMapa() {
        return cargarBd().getProveedores();
    }

    private static RegistroHistorial aRegistro(HistorialCompra h) {
        RegistroHistorial r = new RegistroHistorial();
        r.setAnio(h.getAnio() == null ? 0 : h.getAnio());
        r.setMes(h.getMes() == null ? 0 : h.getMes());
        r.setCheque(nn(h.getCheque()));
        r.setNit(nn(h.getNit()));
        r.setNombre(nn(h.getNombre()));
        r.setRenglon(nn(h.getRenglon()));
        r.setMonto(h.getMonto() == null ? 0 : h.getMonto().doubleValue());
        r.setNpg(nn(h.getNpg()));
        r.setModalidad(nn(h.getModalidad()));
        r.setContrato(nn(h.getContrato()));
        r.setDescripcion(nn(h.getDescripcion()));
        return r;
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }
}
