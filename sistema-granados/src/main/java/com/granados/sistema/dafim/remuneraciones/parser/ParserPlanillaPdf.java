package com.granados.sistema.dafim.remuneraciones.parser;

import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.remuneraciones.dto.FilaPlanilla;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Planilla SICOIN GL (reporte R00815454): una fila por empleado con
 * devengado, IGSS, fianza, otras deducciones, bono 37-2001 y liquido.
 */
public final class ParserPlanillaPdf {

    private static final Pattern RE_RENGLON = Pattern.compile(
            "PERSONAL\\s+(\\d{3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_DEPENDENCIA = Pattern.compile("^(\\d{3})\\s+(.+)$");
    private static final Pattern RE_MONTO = Pattern.compile("-?[\\d,]+\\.\\d{2}");
    private static final Pattern RE_SEQ = Pattern.compile("^(\\d{1,3})\\s+(.*)$");
    private static final Pattern RE_RUIDO = Pattern.compile(
            "^(SIAF:|MUNICIPALIDAD|DEPARTAMENTO|CLASIFICACI|Usuario:|Planilla$|"
                    + "Periodo|EXPEDIENTE|CONCEPTO:|Nombre Ocupaci|\\| Total|"
                    + "Pagina:|Página|REPORTE:|ALCALDE MUNICIPAL|DIRECTOR FINANCIERO|"
                    + "CONCEJAL I|SUMAS TOTALES|Total por Actividad|"
                    + "\\(Total Devengado)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_ESTRUCTURA = Pattern.compile(
            "^(01 ACTIVIDADES|00 SIN SUBPROGRAMA|000 SIN)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> INICIO_CARGO = Set.of(
            "ENCARGADO", "ENCARGADA", "DIRECTOR", "DIRECTORA", "ASISTENTE",
            "TRABAJADOR", "TRABAJADORA", "PILOTO", "TECNICO", "TECNICA",
            "ELECTRICISTA", "ELECTRISISTA", "SECRETARIO", "SECRETARIA",
            "ALCALDE", "CONCEJAL", "CONSEJAL", "SINDICO", "VICE",
            "SUPERVISOR", "SUPERVISORA", "OPERARIO", "OPERADOR", "OFICIAL",
            "INSPECTOR", "AUXILIAR", "JEFE", "JEFA", "COORDINADOR",
            "COORDINADORA", "MAESTRO", "MAESTRA", "ABOGADO", "ABOGADA",
            "ASESOR", "ASESORA", "MENSAJERO", "CONSERJE", "CHOFER",
            "CONTADOR", "CONTADORA", "TESORERO", "TESORERA", "RECEPCIONISTA",
            "DIGITADOR", "DIGITADORA", "COMPRADOR", "COMPRADORA", "FONTANERO",
            "FONTANERA", "GUARDIAN", "GUARDIANA", "ALBANIL", "SEGURIDAD",
            "JARDINERO", "JARDINERA", "COCINERO", "COCINERA", "VIGILANTE");
    /** Palabras de cargo partido en la linea del correlativo, no un segundo nombre. */
    private static final Set<String> CONTINUACION_CARGO = Set.of(
            "MUNICIPAL", "MUNICIPALIDAD", "PUBLICOS", "PUBLICAS", "CAMPO",
            "SERVICIOS", "RELACIONES", "SECRETARIA", "SECRETARIO", "ACCESO",
            "LIBRE", "INF", "PUB", "EVALUACION", "PROYECTOS", "PERSONAL",
            "OFICINA", "UNIDAD", "DIRECCION", "DAFIM", "DMP", "CATASTRO",
            "DISCAPACIDAD", "HUMANOS", "RECURSOS", "PLANIFICACION", "IUSI",
            "SISCODE", "GUATECOMPRAS", "FERTILIZANTE", "AGRICOLA", "MAYOR",
            "ADULTO", "TIPO", "BODEGA", "FARMACIA", "INVENTARIO", "MUJER",
            "LAIP", "INFORMACION", "INTEGRADA", "FINANCIERA", "ADMINISTRACION",
            "AREA", "URBANA", "RURAL", "CALLES", "LIMPIEZA", "OBRAS",
            "SEGUNDO", "PRIMERO", "TERCERO", "DE", "LA", "DEL", "LAS", "LOS",
            "Y", "EN", "A", "UN", "UNA", "EL", "I", "II", "III", "IV",
            "1", "2", "3", "C");

    private ParserPlanillaPdf() {}

    public static List<FilaPlanilla> parsear(InputStream in) throws IOException {
        List<String> lineas = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                for (String l : stripper.getText(doc).split("\n")) lineas.add(l);
            }
        }
        return parsearLineas(lineas);
    }

    public static List<FilaPlanilla> parsearLineas(List<String> lineas) {
        List<FilaPlanilla> out = new ArrayList<>();
        String renglon = "";
        String dependencia = "";
        String pendiente = null;

        for (String raw : lineas) {
            String ln = raw == null ? "" : raw.strip();
            if (ln.isEmpty()) continue;

            Matcher mr = RE_RENGLON.matcher(ln);
            if (mr.find()) {
                renglon = Constantes.zfill3(mr.group(1));
            }

            if (RE_RUIDO.matcher(ln).lookingAt() || RE_ESTRUCTURA.matcher(ln).lookingAt()) {
                continue;
            }

            Matcher md = RE_DEPENDENCIA.matcher(ln);
            if (md.matches() && !"000".equals(md.group(1))) {
                String resto = md.group(2).strip();
                if (!resto.toUpperCase(Locale.ROOT).startsWith("SIN ")) {
                    dependencia = resto;
                    continue;
                }
            }

            Matcher mseq = RE_SEQ.matcher(ln);
            if (mseq.matches() && tieneMontos(mseq.group(2))) {
                FilaPlanilla f = armarFila(renglon, dependencia, pendiente, mseq.group(2));
                pendiente = null;
                if (f != null) out.add(f);
                continue;
            }

            if (ln.contains(",") && !ln.startsWith("MARV") && !esFirma(ln)) {
                pendiente = ln;
            }
        }
        return out;
    }

    private static boolean esFirma(String ln) {
        String u = ln.toUpperCase(Locale.ROOT);
        return u.contains("MARROQUIN FREDDY") || u.contains("CANAHU");
    }

    private static boolean tieneMontos(String resto) {
        int n = 0;
        Matcher m = RE_MONTO.matcher(resto);
        while (m.find()) n++;
        return n >= 8;
    }

    private static FilaPlanilla armarFila(String renglon, String dependencia,
                                          String pendiente, String resto) {
        Matcher am = RE_MONTO.matcher(resto);
        int first = -1;
        List<Double> montos = new ArrayList<>();
        while (am.find()) {
            if (first < 0) first = am.start();
            montos.add(Double.parseDouble(am.group().replace(",", "")));
        }
        if (montos.size() < 8) return null;
        String extraCargo = first > 0 ? resto.substring(0, first).strip() : "";
        String base = pendiente == null ? "" : pendiente;
        String[] wrap = partirWrap(extraCargo, base);
        String paraNombre = base.isEmpty() ? extraCargo : base;
        if (paraNombre.isBlank()) return null;
        String[] nc = partirNombreCargo(paraNombre);
        if (!base.isEmpty() && !wrap[0].isEmpty()) {
            nc[0] = (nc[0] + " " + wrap[0]).replaceAll("\\s+", " ").strip();
        }
        String cargo = (nc[1] + " " + wrap[1]).replaceAll("\\s+", " ").strip();
        FilaPlanilla f = new FilaPlanilla();
        f.setRenglon(renglon.isEmpty() ? "011" : renglon);
        f.setNombre(nc[0]);
        f.setCargo(cargo);
        f.setDependencia(dependencia);
        f.setTotalDevengado(r2(montos.get(0)));
        f.setIgss(r2(montos.get(1)));
        f.setFianza(r2(montos.get(2)));
        f.setOtrasDeducciones(r2(montos.get(3)));
        if (montos.size() >= 9) {
            f.setBoniLey(r2(montos.get(5)));
            f.setBonifMunicipal(r2(montos.get(6)));
            f.setOtrosIngresos(r2(montos.get(7)));
            f.setTotalRecibir(r2(montos.get(8)));
        } else {
            f.setBoniLey(r2(montos.get(4)));
            f.setBonifMunicipal(r2(montos.get(5)));
            f.setOtrosIngresos(r2(montos.get(6)));
            f.setTotalRecibir(r2(montos.get(7)));
        }
        return f;
    }

    /**
     * En la linea del correlativo a veces viaja el resto del cargo
     * ({@code PUBLICOS}, {@code CAMPO}) y a veces el segundo nombre
     * ({@code ERNESTO}, {@code LIGUORI}) antes de seguir el cargo.
     */
    static String[] partirWrap(String extra, String pendiente) {
        if (extra == null || extra.isBlank()) return new String[]{"", ""};
        String[] toks = extra.strip().split("\\s+");
        int i = 0;
        if (contieneInicioCargo(pendiente)) {
            while (i < toks.length && esNombrePropioWrap(toks[i])) i++;
        }
        String nom = i == 0 ? "" : String.join(" ", java.util.Arrays.copyOfRange(toks, 0, i));
        String car = i >= toks.length ? ""
                : String.join(" ", java.util.Arrays.copyOfRange(toks, i, toks.length));
        return new String[]{nom, car};
    }

    static String[] partirNombreCargo(String texto) {
        String t = texto.replaceAll("\\s+", " ").strip();
        int coma = t.indexOf(',');
        if (coma < 0) return new String[]{t, ""};
        String apellidos = t.substring(0, coma).strip();
        String resto = t.substring(coma + 1).strip();
        String[] toks = resto.isEmpty() ? new String[0] : resto.split(" ");
        int corte = toks.length;
        for (int i = 0; i < toks.length; i++) {
            if (INICIO_CARGO.contains(sinTilde(toks[i]))) {
                corte = i;
                break;
            }
        }
        if (corte == toks.length && toks.length > 3) corte = 2;
        StringBuilder nombres = new StringBuilder();
        StringBuilder cargo = new StringBuilder();
        for (int i = 0; i < toks.length; i++) {
            if (i < corte) {
                if (nombres.length() > 0) nombres.append(' ');
                nombres.append(toks[i]);
            } else {
                if (cargo.length() > 0) cargo.append(' ');
                cargo.append(toks[i]);
            }
        }
        String nombre = (apellidos + ", " + nombres).strip();
        if (nombre.endsWith(",")) nombre = nombre.substring(0, nombre.length() - 1).strip();
        return new String[]{nombre, cargo.toString().strip()};
    }

    private static boolean contieneInicioCargo(String s) {
        if (s == null || s.isBlank()) return false;
        for (String w : s.split("\\s+")) {
            if (INICIO_CARGO.contains(sinTilde(w))) return true;
        }
        return false;
    }

    private static boolean esNombrePropioWrap(String tok) {
        String w = sinTilde(tok);
        if (w.length() < 3) return false;
        if (INICIO_CARGO.contains(w) || CONTINUACION_CARGO.contains(w)) return false;
        for (int i = 0; i < w.length(); i++) {
            if (!Character.isLetter(w.charAt(i))) return false;
        }
        return true;
    }

    private static String sinTilde(String s) {
        return s.toUpperCase(Locale.ROOT)
                .replace("Á", "A").replace("É", "E").replace("Í", "I")
                .replace("Ó", "O").replace("Ú", "U").replace("Ü", "U")
                .replace("Ñ", "N");
    }

    private static double r2(double v) {
        return ValidadorComprasService.round2(v);
    }
}
