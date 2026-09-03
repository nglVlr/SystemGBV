package com.granados.sistema.dafim.paquetes.service;

import com.granados.sistema.dafim.paquetes.dto.FacturaSatDatos;
import com.granados.sistema.dafim.paquetes.dto.PaqueteDatos;
import com.granados.sistema.dafim.paquetes.entity.FacturaPdf;
import com.granados.sistema.dafim.paquetes.entity.FacturaSat;
import com.granados.sistema.dafim.paquetes.entity.LineaPaquete;
import com.granados.sistema.dafim.paquetes.entity.PaqueteFacturas;
import com.granados.sistema.dafim.paquetes.parser.ParserFacturaSat;
import com.granados.sistema.dafim.paquetes.parser.ParserPaquetesExcel;
import com.granados.sistema.dafim.paquetes.repository.FacturaPdfRepository;
import com.granados.sistema.dafim.paquetes.repository.FacturaSatRepository;
import com.granados.sistema.dafim.paquetes.repository.LineaPaqueteRepository;
import com.granados.sistema.dafim.paquetes.repository.PaqueteFacturasRepository;
import com.granados.sistema.config.ExclusiveJobs;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orquesta el modulo de paquetes de facturas: procesa el mes (Excel de
 * paquetes + PDFs de facturas), guarda todo en la BD, arma el PDF unido
 * de cada paquete para imprimir y permite asignaciones manuales.
 */
@Service
public class PaquetesService {

    /** Un PDF subido: nombre + bytes (se conserva para reimprimir). */
    public record PdfSubido(String nombre, byte[] datos) { }

    /** Reporte del procesamiento para mostrar en pantalla. */
    public static class ReporteProceso {
        public int paquetes;
        public int lineas;
        public int facturasLeidas;
        public int asignadas;
        public int sinPaquete;
        public int lineasPendientes;
        public final List<String> repetidas = new ArrayList<>();
        public final List<String> yaEnBd = new ArrayList<>();
        public final List<String> conError = new ArrayList<>();
    }

    /** Resultado de la busqueda global por DTE: la factura y, si ya quedo
     *  asignada, la linea (para saber a que paquete/mes pertenece). */
    public record ResultadoBusqueda(FacturaSat factura, LineaPaquete linea) { }

    private final PaqueteFacturasRepository paquetes;
    private final LineaPaqueteRepository lineas;
    private final FacturaSatRepository facturas;
    private final FacturaPdfRepository pdfs;
    private final ExclusiveJobs jobs;
    private final TransactionTemplate tx;

    public PaquetesService(PaqueteFacturasRepository paquetes,
                           LineaPaqueteRepository lineas,
                           FacturaSatRepository facturas,
                           FacturaPdfRepository pdfs,
                           ExclusiveJobs jobs,
                           PlatformTransactionManager txManager) {
        this.paquetes = paquetes;
        this.lineas = lineas;
        this.facturas = facturas;
        this.pdfs = pdfs;
        this.jobs = jobs;
        this.tx = new TransactionTemplate(txManager);
    }

    public boolean mesYaProcesado(int anio, int mes) {
        return paquetes.existsByAnioAndMes(anio, mes);
    }

    // ============================ PROCESAR MES ============================

    public ReporteProceso procesarMes(int anio, int mes, InputStream excel,
                                      List<PdfSubido> archivosPdf) throws IOException {
        List<PaqueteDatos> paquetesDatos = ParserPaquetesExcel.parsear(excel);
        if (paquetesDatos.isEmpty()) {
            throw new IllegalStateException(
                    "El Excel no trae ninguna hoja con columnas de concepto y monto.");
        }

        List<FacturaSatDatos> datosFacturas = new ArrayList<>();
        for (PdfSubido a : archivosPdf) {
            datosFacturas.add(ParserFacturaSat.parsear(
                    new ByteArrayInputStream(a.datos()), a.nombre()));
        }

        try {
            return jobs.run("paquetes-" + anio + "-" + mes, () -> {
                if (mesYaProcesado(anio, mes)) {
                    throw new IllegalStateException("El mes " + mes + "/" + anio
                            + " ya fue procesado. Eliminalo primero si quieres repetirlo.");
                }
                Set<String> enBd = new HashSet<>(facturas.todasLasAutorizaciones());
                EmparejadorFacturas.Resultado res =
                        EmparejadorFacturas.emparejar(paquetesDatos, datosFacturas, enBd);
                return tx.execute(status -> persistirMes(
                        anio, mes, paquetesDatos, datosFacturas, archivosPdf, res));
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }
    }

    private ReporteProceso persistirMes(int anio, int mes,
                                        List<PaqueteDatos> paquetesDatos,
                                        List<FacturaSatDatos> datosFacturas,
                                        List<PdfSubido> archivosPdf,
                                        EmparejadorFacturas.Resultado res) {
        Map<Integer, FacturaSat> guardadas = new LinkedHashMap<>();
        Set<Integer> descartadas = new HashSet<>();
        descartadas.addAll(res.repetidasEnLote);
        descartadas.addAll(res.yaEnBd);
        descartadas.addAll(res.conError);
        for (int i = 0; i < datosFacturas.size(); i++) {
            if (descartadas.contains(i)) continue;
            FacturaSatDatos d = datosFacturas.get(i);
            FacturaSat f = new FacturaSat();
            f.setAutorizacion(d.getAutorizacion());
            f.setSerie(d.getSerie());
            f.setNumeroDte(d.getNumeroDte());
            f.setNitEmisor(d.getNitEmisor());
            f.setNombreEmisor(recortar(d.getNombreEmisor(), 120));
            f.setFechaEmision(d.getFechaEmision());
            f.setMonto(d.getMonto());
            f.setDescripcion(recortar(d.getDescripcion(), 600));
            f.setAnio(anio);
            f.setMes(mes);
            f.setArchivo(recortar(d.getArchivo(), 150));
            f = facturas.save(f);
            guardadas.put(i, f);

            FacturaPdf pdf = new FacturaPdf();
            pdf.setFacturaId(f.getId());
            pdf.setDatos(archivosPdf.get(i).datos());
            pdfs.save(pdf);
        }

        // 5) guardar paquetes y lineas con su factura asignada
        ReporteProceso rep = new ReporteProceso();
        int numero = 0;
        for (int pi = 0; pi < paquetesDatos.size(); pi++) {
            PaqueteDatos pd = paquetesDatos.get(pi);
            PaqueteFacturas p = new PaqueteFacturas();
            p.setAnio(anio);
            p.setMes(mes);
            p.setNumero(++numero);
            p.setNombre(recortar(pd.getNombreHoja(), 60));
            p.setTotalEsperado(pd.getTotalEsperado() > 0
                    ? pd.getTotalEsperado() : pd.sumaLineas());
            p = paquetes.save(p);

            Map<Integer, Integer> asig = res.asignaciones.getOrDefault(pi, Map.of());
            for (PaqueteDatos.Linea ld : pd.getLineas()) {
                LineaPaquete l = new LineaPaquete();
                l.setPaquete(p);
                l.setOrden(ld.getOrden());
                l.setConcepto(recortar(ld.getConcepto(), 400));
                l.setMonto(ld.getMonto());
                Integer fi = asig.get(ld.getOrden());
                if (fi != null && guardadas.containsKey(fi)) {
                    l.setFactura(guardadas.get(fi));
                    rep.asignadas++;
                } else {
                    rep.lineasPendientes++;
                }
                lineas.save(l);
                rep.lineas++;
            }
            rep.paquetes++;
        }

        rep.facturasLeidas = datosFacturas.size();
        rep.sinPaquete = res.sinPaquete.size();
        for (int i : res.repetidasEnLote) rep.repetidas.add(nombreDe(datosFacturas, i));
        for (int i : res.yaEnBd) rep.yaEnBd.add(nombreDe(datosFacturas, i));
        for (int i : res.conError) {
            rep.conError.add(nombreDe(datosFacturas, i) + ": "
                    + datosFacturas.get(i).getError());
        }
        return rep;
    }

    private static String nombreDe(List<FacturaSatDatos> lista, int i) {
        String a = lista.get(i).getArchivo();
        return a == null || a.isEmpty() ? ("PDF #" + (i + 1)) : a;
    }

    private static String recortar(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ============================== CONSULTAS =============================

    /** Paquetes de un mes en orden. */
    public List<PaqueteFacturas> paquetesDelMes(int anio, int mes) {
        return paquetes.findByAnioAndMesOrderByNumeroAsc(anio, mes);
    }

    public List<LineaPaquete> lineasDe(Long paqueteId) {
        return lineas.findByPaqueteIdOrderByOrdenAsc(paqueteId);
    }

    /** Meses procesados: [anio, mes, cantidad de paquetes]. */
    public List<int[]> mesesProcesados() {
        Map<String, int[]> agrupado = new LinkedHashMap<>();
        for (PaqueteFacturas p : paquetes.findAllByOrderByAnioDescMesDesc()) {
            String k = p.getAnio() + "-" + p.getMes();
            agrupado.computeIfAbsent(k,
                    x -> new int[]{p.getAnio(), p.getMes(), 0})[2]++;
        }
        return new ArrayList<>(agrupado.values());
    }

    public List<FacturaSat> facturasLibresDelMes(int anio, int mes) {
        return facturas.libresDelMes(anio, mes);
    }

    /** Busqueda global: todas las facturas (de cualquier mes) cuyo DTE
     *  contenga el texto dado, con su paquete si ya esta asignada. */
    public List<ResultadoBusqueda> buscarPorDte(String dte) {
        List<FacturaSat> encontradas =
                facturas.findByNumeroDteContainingIgnoreCaseOrderByAnioDescMesDescIdDesc(dte);
        List<ResultadoBusqueda> out = new ArrayList<>();
        for (FacturaSat f : encontradas) {
            LineaPaquete l = lineas.findByFactura_Id(f.getId()).orElse(null);
            out.add(new ResultadoBusqueda(f, l));
        }
        return out;
    }

    /** Bitacora: DTE, NIT, emisor, descripcion o autorizacion. */
    public List<ResultadoBusqueda> buscarGeneral(String q) {
        if (q == null || q.isBlank()) return List.of();
        List<FacturaSat> encontradas = facturas.buscarGeneral(q.trim(), PageRequest.of(0, 40));
        List<ResultadoBusqueda> out = new ArrayList<>();
        for (FacturaSat f : encontradas) {
            LineaPaquete l = lineas.findByFactura_Id(f.getId()).orElse(null);
            out.add(new ResultadoBusqueda(f, l));
        }
        return out;
    }

    public Optional<PaqueteFacturas> paquete(Long id) {
        return paquetes.findById(id);
    }

    public Optional<byte[]> pdfDeFactura(Long facturaId) {
        return pdfs.findFirstByFacturaId(facturaId).map(FacturaPdf::getDatos);
    }

    // ======================== IMPRIMIR UN PAQUETE ========================

    /**
     * Une los PDFs de las facturas del paquete EN EL ORDEN de sus lineas
     * y devuelve un solo PDF listo para mandar a imprimir.
     */
    public byte[] imprimirPaquete(Long paqueteId) throws IOException {
        List<LineaPaquete> ls = lineas.findByPaqueteIdOrderByOrdenAsc(paqueteId);
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        merger.setDestinationStream(salida);
        boolean alguna = false;
        for (LineaPaquete l : ls) {
            if (l.getFactura() == null) continue;
            Optional<byte[]> pdf = pdfDeFactura(l.getFactura().getId());
            if (pdf.isPresent()) {
                merger.addSource(new ByteArrayInputStream(pdf.get()));
                alguna = true;
            }
        }
        if (!alguna) {
            throw new IllegalStateException(
                    "El paquete no tiene facturas asignadas todavia.");
        }
        merger.mergeDocuments(null);
        return salida.toByteArray();
    }

    // ========================= ASIGNACION MANUAL =========================

    @Transactional
    public String asignarManual(Long lineaId, Long facturaId) {
        Optional<LineaPaquete> ol = lineas.findById(lineaId);
        Optional<FacturaSat> of = facturas.findById(facturaId);
        if (ol.isEmpty() || of.isEmpty()) return "Linea o factura no encontrada.";
        LineaPaquete l = ol.get();
        FacturaSat f = of.get();
        if (Math.abs(f.getMonto() - l.getMonto()) > 0.005) {
            return "El monto de la factura (Q" + f.getMonto()
                    + ") no coincide con la linea (Q" + l.getMonto() + ").";
        }
        boolean libre = facturas.libresDelMes(f.getAnio(), f.getMes()).stream()
                .anyMatch(x -> x.getId().equals(f.getId()));
        if (!libre) return "Esa factura ya esta asignada a otra linea.";
        l.setFactura(f);
        lineas.save(l);
        return "";
    }

    @Transactional
    public void quitarAsignacion(Long lineaId) {
        lineas.findById(lineaId).ifPresent(l -> {
            l.setFactura(null);
            lineas.save(l);
        });
    }

    // ============================ ELIMINAR MES ===========================

    @Transactional
    public void eliminarMes(int anio, int mes) {
        List<PaqueteFacturas> ps = paquetes.findByAnioAndMesOrderByNumeroAsc(anio, mes);
        if (!ps.isEmpty()) {
            lineas.deleteByPaqueteIn(ps);
            paquetes.deleteByAnioAndMes(anio, mes);
        }
        List<FacturaSat> fs = facturas.findByAnioAndMesOrderByIdAsc(anio, mes);
        if (!fs.isEmpty()) {
            pdfs.deleteByFacturaIdIn(fs.stream().map(FacturaSat::getId).toList());
            facturas.deleteByAnioAndMes(anio, mes);
        }
    }
}
