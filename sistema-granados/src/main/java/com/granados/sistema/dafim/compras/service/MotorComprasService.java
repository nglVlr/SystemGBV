package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.dto.ContratoInfo;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.ProveedorInfo;
import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.dto.ResultadoConstruccion;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.ContratoUtil;
import com.granados.sistema.dafim.compras.util.FechaUtil;
import com.granados.sistema.dafim.compras.util.TextoUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Porteo EXACTO de construir_filas() y extraer_para_bd() del motor Python v2.0.
 *
 * Orden de resolucion por cheque:
 *   1. Match directo por numero de cheque en el PDF SICOIN (solo IMPRESO y
 *      renglones de compra directa). Si hay varias lineas, gana la de mayor monto.
 *   2. Busqueda difusa por nombre en BD de contratos 029 (minimo 3 palabras en
 *      comun y al menos 1 significativa, es decir no apellido comun).
 *   3. Busqueda difusa por nombre en BD de proveedores (minimo 3 palabras).
 *   4. Inferencia por palabra clave del nombre, o renglon 299 + alerta REVISAR.
 *
 * Modalidad y NPG:
 *   - Se toma una publicacion del TXT aun no usada, primero por NIT y si no
 *     hay por nombre (mismo umbral fuzzy que 029). Gana la de mayor solape
 *     de descripcion; el monto mas cercano desempata.
 *   - Renglon 029: siempre CASO DE EXCEPCION. El NPG historico de la BD solo
 *     se usa si el mes no trajo publicaciones de esa persona.
 *   - Si no hay candidato: NPG vacio + alerta SIN NPG, y modalidad por umbral
 *     (<= Q25,000 BAJA CUANTIA, si no CASO DE EXCEPCION).
 *   - Un NPG solo puede usarse una vez en el mes (anti-duplicado).
 *
 * Las clases del motor son estaticas y sin dependencias de Spring a proposito:
 * asi se prueban de forma aislada y se comparan 1:1 contra el Python original.
 */
public final class MotorComprasService {

    private static final Pattern RE_CARGO = Pattern.compile("COMO[:\\s]+(.{4,60}?)[,.]");

    private MotorComprasService() {}

    /** Formato Q con miles y 2 decimales, identico a {:,.2f} de Python. */
    static String q(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    public static ResultadoConstruccion construirFilas(
            List<Cheque> cheques,
            List<RegistroGuatecompras> txtRegs,
            List<RegistroSicoin> pdfRegs,
            Map<String, ProveedorInfo> bdProveedores,
            Map<String, ContratoInfo> bdContratos,
            Map<String, String> bdNpgs029) {
        return construirFilas(cheques, txtRegs, pdfRegs, bdProveedores,
                bdContratos, bdNpgs029, List.of());
    }

    public static ResultadoConstruccion construirFilas(
            List<Cheque> cheques,
            List<RegistroGuatecompras> txtRegs,
            List<RegistroSicoin> pdfRegs,
            Map<String, ProveedorInfo> bdProveedores,
            Map<String, ContratoInfo> bdContratos,
            Map<String, String> bdNpgs029,
            List<RegistroGuatecompras> catalogo) {
        if (catalogo == null) catalogo = List.of();

        ResultadoConstruccion out = new ResultadoConstruccion();
        List<FilaCompra> filas = out.getFilas();
        List<String> alertas = out.getAlertas();
        Map<String, ContratoInfo> nuevos029 = out.getNuevos029();
        Map<String, ProveedorInfo> nuevosProv = out.getNuevosProv();

        // --- indice del PDF por cheque (solo IMPRESO + renglon CD) ---
        Map<String, List<RegistroSicoin>> pdfPorCheque = new LinkedHashMap<>();
        for (RegistroSicoin r : pdfRegs) {
            if ("IMPRESO".equals(r.getStatus())
                    && Constantes.RENGLONES_CD.contains(r.getRenglon())) {
                pdfPorCheque.computeIfAbsent(r.getCheque(), k -> new ArrayList<>()).add(r);
            }
        }

        Set<String> npgUsados = new LinkedHashSet<>();

        for (Cheque c : cheques) {
            String nombreC = c.getNombre();
            double montoC = c.getMonto();
            String fechaC = FechaUtil.fmt(c.getFecha());
            String numCheq = c.getCheque();

            String nit = "";
            String renglon = "";
            String desc = "";
            String contrato = "N/A";
            String npg = "";
            String modalidad = "";

            // ---- 1) match directo en PDF SICOIN ----
            List<RegistroSicoin> regsPdf = pdfPorCheque.get(numCheq);
            if (regsPdf != null && !regsPdf.isEmpty()) {
                RegistroSicoin r = regsPdf.get(0);
                for (RegistroSicoin cand : regsPdf) {
                    if (cand.getMonto() > r.getMonto()) r = cand;
                }
                nit = r.getNit();
                renglon = r.getRenglon();
                desc = r.getDesc();
                if (r.getContrato() != null && !"N/A".equals(r.getContrato())) {
                    contrato = r.getContrato();
                }
            }

            // ---- 2) difuso contra BD de contratos 029 ----
            if (renglon.isEmpty()) {
                Set<String> palabras = TextoUtil.palabras(nombreC);
                Set<String> sig = TextoUtil.menos(palabras, Constantes.PALABRAS_COMUNES);
                for (Map.Entry<String, ContratoInfo> e : bdContratos.entrySet()) {
                    ContratoInfo fila = e.getValue();
                    Set<String> pn = TextoUtil.palabras(fila.getNombre());
                    Set<String> pnSig = TextoUtil.menos(pn, Constantes.PALABRAS_COMUNES);
                    if (TextoUtil.interseccion(palabras, pn) >= 3
                            && TextoUtil.interseccion(sig, pnSig) >= 1) {
                        nit = e.getKey();
                        renglon = "029";
                        contrato = (fila.getContrato() == null || fila.getContrato().isEmpty())
                                ? "N/A" : fila.getContrato();
                        String cargo = fila.getCargo() == null ? "" : fila.getCargo();
                        if (!cargo.isEmpty()) {
                            desc = "POR PAGO DE SERVICIOS PRESTADOS A LA MUNICIPALIDAD DE "
                                    + "GRANADOS COMO: " + cargo + ", SEGUN CONTRATO NO. " + contrato;
                        }
                        break;
                    }
                }
            }

            // ---- 3) difuso contra BD de proveedores ----
            if (renglon.isEmpty()) {
                Set<String> palabras = TextoUtil.palabras(nombreC);
                for (Map.Entry<String, ProveedorInfo> e : bdProveedores.entrySet()) {
                    ProveedorInfo fila = e.getValue();
                    Set<String> pn = TextoUtil.palabras(fila.getNombre());
                    if (TextoUtil.interseccion(palabras, pn) >= 3) {
                        nit = e.getKey();
                        renglon = fila.getRenglon();
                        if (fila.getDesc() != null && !fila.getDesc().isEmpty()) {
                            desc = fila.getDesc();
                        }
                        break;
                    }
                }
            }

            // ---- 4) inferencia por keyword o 299 + REVISAR ----
            if (renglon.isEmpty()) {
                String kw = Constantes.rengKw(nombreC);
                renglon = kw.isEmpty() ? "299" : kw;
                alertas.add("REVISAR cheque " + numCheq + ": " + nombreC
                        + " | Q" + q(montoC) + " -> R" + renglon + " sin registro en PDF");
            }

            // ---- modalidad y NPG (persona + descripcion; monto desempata) ----
            RegistroGuatecompras pub = elegirPublicacion(
                    nit, nombreC, desc, montoC, txtRegs, catalogo, npgUsados);
            if (pub != null) {
                npg = pub.getNpg() == null ? "" : pub.getNpg();
                if (pub.getModalidad() != null && !pub.getModalidad().isEmpty()) {
                    modalidad = pub.getModalidad();
                }
            }
            if ("029".equals(renglon)) {
                modalidad = "CASO DE EXCEPCION";
                if (npg.isEmpty()) {
                    String deBd = bdNpgs029.get(nit);
                    if (deBd != null && !deBd.isEmpty()) npg = deBd;
                }
            }
            if (modalidad.isEmpty()) {
                modalidad = montoC <= 25000 ? "BAJA CUANTIA" : "CASO DE EXCEPCION";
            }

            if (!npg.isEmpty()) {
                if (npgUsados.contains(npg)) {
                    npg = "";
                } else {
                    npgUsados.add(npg);
                }
            }
            if (npg.isEmpty()) {
                alertas.add("SIN NPG cheque " + numCheq + ": " + nombreC
                        + " | Q" + q(montoC));
            }

            // ---- contrato para 029 ----
            if ("029".equals(renglon) && "N/A".equals(contrato)) {
                String c2 = ContratoUtil.extraerContrato(desc);
                if (!c2.isEmpty()) {
                    contrato = c2;
                } else {
                    alertas.add("R029 SIN CONTRATO cheque " + numCheq + ": " + nombreC);
                }
            }
            if ("029".equals(renglon) && !nit.isEmpty() && !bdContratos.containsKey(nit)) {
                alertas.add("\uD83C\uDD95 R029 NUEVO (no esta en BD): " + nombreC
                        + " | NIT:" + nit + " | contrato:" + contrato
                        + " -> se agregara al guardar");
            }
            if (nit.isEmpty()) {
                alertas.add("SIN NIT cheque " + numCheq + ": " + nombreC + " | Q" + q(montoC));
            }
            if (desc.isEmpty()) {
                desc = nombreC;
            }

            FilaCompra fila = new FilaCompra();
            fila.setCheque(numCheq);
            fila.setModalidad(modalidad);
            fila.setDesc(TextoUtil.limpiarDesc(desc));
            fila.setPrecio(montoC);
            fila.setRenglon(renglon);
            fila.setProveedor(nombreC);
            fila.setNit(nit);
            fila.setNpg(npg);
            fila.setFechaPub(fechaC);
            fila.setFechaAdj(fechaC);
            fila.setContrato(contrato);
            fila.setFechaCont("N/A");
            filas.add(fila);

            // ---- acumular para actualizar la BD al guardar ----
            // Paridad Python: el cargo SIEMPRE se extrae por regex de la desc,
            // el npg cae a la BD historica si el anti-duplicado lo vacio, y en
            // proveedores el ultimo cheque del NIT gana (dict assignment).
            if ("029".equals(renglon) && !nit.isEmpty()) {
                String cargo = "";
                Matcher m = RE_CARGO.matcher(desc.toUpperCase());
                if (m.find()) cargo = m.group(1).trim();
                String npgBd = npg;
                if (npgBd.isEmpty()) npgBd = bdNpgs029.getOrDefault(nit, "");
                nuevos029.put(nit, new ContratoInfo(nombreC, contrato, cargo, npgBd, null));
            } else if (!nit.isEmpty() && !"029".equals(renglon) && !"184".equals(renglon)) {
                nuevosProv.put(nit, new ProveedorInfo(nombreC, renglon,
                        TextoUtil.corta(TextoUtil.limpiarDesc(desc), 120)));
            }
        }

        // ---- orden final: no-029 primero, 029 al final ----
        List<FilaCompra> ordenadas = new ArrayList<>(filas.size());
        for (FilaCompra f : filas) if (!"029".equals(f.getRenglon())) ordenadas.add(f);
        for (FilaCompra f : filas) if ("029".equals(f.getRenglon())) ordenadas.add(f);
        out.setFilas(ordenadas);

        List<RegistroGuatecompras> sinCheque = new ArrayList<>();
        for (RegistroGuatecompras t : txtRegs) {
            String n = t.getNpg() == null ? "" : t.getNpg();
            if (!n.isEmpty() && !npgUsados.contains(n)) sinCheque.add(t);
        }
        out.setNpgsSinCheque(sinCheque);
        return out;
    }

    /**
     * Elige una publicacion aun no usada: primero el TXT del mes, luego el
     * catalogo de la BD. En cada pool: mismo NIT; si no hay, nombre (3
     * palabras, 1 significativa). Gana el mayor solape de descripcion; el
     * monto mas cercano desempata.
     */
    static RegistroGuatecompras elegirPublicacion(
            String nit, String nombreCheque, String descCheque, double montoCheque,
            List<RegistroGuatecompras> txtRegs, Set<String> npgUsados) {
        return elegirPublicacion(nit, nombreCheque, descCheque, montoCheque,
                txtRegs, List.of(), npgUsados);
    }

    static RegistroGuatecompras elegirPublicacion(
            String nit, String nombreCheque, String descCheque, double montoCheque,
            List<RegistroGuatecompras> txtRegs, List<RegistroGuatecompras> catalogo,
            Set<String> npgUsados) {
        RegistroGuatecompras r = elegirDe(
                txtRegs, nit, nombreCheque, descCheque, montoCheque, npgUsados);
        if (r != null) return r;
        if (catalogo == null || catalogo.isEmpty()) return null;
        return elegirDe(catalogo, nit, nombreCheque, descCheque, montoCheque, npgUsados);
    }

    private static RegistroGuatecompras elegirDe(
            List<RegistroGuatecompras> pool, String nit, String nombreCheque,
            String descCheque, double montoCheque, Set<String> npgUsados) {
        if (pool == null || pool.isEmpty()) return null;
        List<RegistroGuatecompras> candidatos = new ArrayList<>();
        if (nit != null && !nit.isEmpty()) {
            for (RegistroGuatecompras t : pool) {
                if (usada(t, npgUsados)) continue;
                if (nit.equals(t.getNit())) candidatos.add(t);
            }
        }
        if (candidatos.isEmpty()) {
            for (RegistroGuatecompras t : pool) {
                if (usada(t, npgUsados)) continue;
                if (nombreCoincide(nombreCheque, t.getProveedor())) candidatos.add(t);
            }
        }
        if (candidatos.isEmpty()) return null;

        RegistroGuatecompras mejor = candidatos.get(0);
        int mejorSolape = solapeDesc(descCheque, mejor.getDesc());
        double mejorDif = Math.abs(montoCheque - mejor.getMonto());
        for (int i = 1; i < candidatos.size(); i++) {
            RegistroGuatecompras t = candidatos.get(i);
            int solape = solapeDesc(descCheque, t.getDesc());
            double dif = Math.abs(montoCheque - t.getMonto());
            if (solape > mejorSolape || (solape == mejorSolape && dif < mejorDif)) {
                mejor = t;
                mejorSolape = solape;
                mejorDif = dif;
            }
        }
        return mejor;
    }

    private static boolean usada(RegistroGuatecompras t, Set<String> npgUsados) {
        String n = t.getNpg() == null ? "" : t.getNpg();
        return n.isEmpty() || npgUsados.contains(n);
    }

    static boolean nombreCoincide(String nombreCheque, String nombreTxt) {
        Set<String> palabras = TextoUtil.palabras(nombreCheque);
        Set<String> sig = TextoUtil.menos(palabras, Constantes.PALABRAS_COMUNES);
        Set<String> pn = TextoUtil.palabras(nombreTxt);
        Set<String> pnSig = TextoUtil.menos(pn, Constantes.PALABRAS_COMUNES);
        return TextoUtil.interseccion(palabras, pn) >= 3
                && TextoUtil.interseccion(sig, pnSig) >= 1;
    }

    static int solapeDesc(String a, String b) {
        return TextoUtil.interseccion(
                TextoUtil.palabras(TextoUtil.limpiarDesc(a)),
                TextoUtil.palabras(TextoUtil.limpiarDesc(b)));
    }

    /**
     * Porteo EXACTO de extraer_para_bd(): de una lista de filas ya armadas
     * (por ejemplo un machote de otro mes) saca lo que corresponde guardar
     * en contratos_029 y proveedores. Semantica setdefault de Python:
     * el nombre queda del PRIMER cheque del NIT; contrato/cargo/npg se van
     * completando solo si estaban vacios.
     */
    public static ResultadoConstruccion extraerParaBd(List<FilaCompra> filas) {
        ResultadoConstruccion out = new ResultadoConstruccion();
        Map<String, ContratoInfo> nuevos029 = out.getNuevos029();
        Map<String, ProveedorInfo> nuevosProv = out.getNuevosProv();
        for (FilaCompra f : filas) {
            String nit = f.getNit() == null ? "" : f.getNit();
            if (nit.isEmpty()) continue;
            String renglon = f.getRenglon() == null ? "" : f.getRenglon();
            String desc = f.getDesc() == null ? "" : f.getDesc();
            if ("029".equals(renglon)) {
                Matcher cm = RE_CARGO.matcher(desc.toUpperCase());
                boolean hayCargo = cm.find();
                ContratoInfo d = nuevos029.computeIfAbsent(nit,
                        k -> new ContratoInfo(f.getProveedor(), "N/A", "", "", null));
                if (!"N/A".equals(f.getContrato())) d.setContrato(f.getContrato());
                if (hayCargo && (d.getCargo() == null || d.getCargo().isEmpty())) {
                    d.setCargo(cm.group(1).trim());
                }
                if (f.getNpg() != null && !f.getNpg().isEmpty()
                        && (d.getNpg() == null || d.getNpg().isEmpty())) {
                    d.setNpg(f.getNpg());
                }
            } else if (!"184".equals(renglon) && !renglon.isEmpty()) {
                if (!nuevosProv.containsKey(nit)) {
                    nuevosProv.put(nit, new ProveedorInfo(f.getProveedor(), renglon,
                            TextoUtil.corta(TextoUtil.limpiarDesc(desc), 120)));
                }
            }
        }
        return out;
    }
}
