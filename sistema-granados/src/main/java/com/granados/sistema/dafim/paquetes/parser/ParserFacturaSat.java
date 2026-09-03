package com.granados.sistema.dafim.paquetes.parser;

import com.granados.sistema.dafim.paquetes.dto.FacturaSatDatos;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee una factura electronica (FEL) de la SAT en PDF y extrae:
 * numero de autorizacion (UUID), serie, numero de DTE, NIT y nombre del
 * emisor, fecha de emision, monto total y descripcion de los items.
 *
 * Calibrado con facturas reales de "Factura Pequeno Contribuyente" del
 * portal de la SAT. Los patrones tienen respaldos para tolerar layouts
 * ligeramente distintos (factura normal, cambiaria, etc.).
 */
public final class ParserFacturaSat {

    private ParserFacturaSat() { }

    private static final Pattern RE_AUTORIZACION = Pattern.compile(
            "\\b([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\\b");
    private static final Pattern RE_SERIE = Pattern.compile(
            "Serie:\\s*([0-9A-Za-z]+)");
    private static final Pattern RE_DTE = Pattern.compile(
            "N[u\u00fa]mero\\s+de\\s+DTE:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_NIT_EMISOR = Pattern.compile(
            "Nit\\s+Emisor:\\s*(\\d{4,12}K?)", Pattern.CASE_INSENSITIVE);
    /** El nombre del emisor va en la misma linea que "NUMERO DE AUTORIZACION". */
    private static final Pattern RE_NOMBRE_EMISOR = Pattern.compile(
            "^(.{3,90}?)\\s+N[U\u00da]MERO\\s+DE\\s+AUTORIZACI", Pattern.MULTILINE);
    private static final Pattern RE_FECHA = Pattern.compile(
            "emisi[o\u00f3]n:\\s*(\\d{1,2})-([A-Za-z]{3})-(\\d{4})", Pattern.CASE_INSENSITIVE);
    /** Linea TOTALES: descuentos primero, el gran total es el ULTIMO numero. */
    private static final Pattern RE_TOTALES = Pattern.compile(
            "TOTALES:\\s*(?:[\\d,]+\\.\\d{2}\\s+)*([\\d,]+\\.\\d{2})");
    /** Detecta el INICIO de un item: numero, B/S, cantidad. El resto de la
     *  linea (grupo 1) trae texto de la descripcion MEZCLADO con montos,
     *  porque PDFBox junta visualmente la fila completa; se limpia despues
     *  quitando los numeros con decimales en vez de contar columnas. */
    private static final Pattern RE_ITEM_INICIO = Pattern.compile(
            "^\\d+\\s+(?:Bien|Servicio)\\s+[\\d,\\.]+\\s+(.*)$");
    /** Cualquier numero con decimales: precio, descuentos, total o IVA. */
    private static final Pattern RE_NUMERO_DECIMAL = Pattern.compile(
            "[\\d,]+\\.\\d+");
    private static final Pattern RE_RUIDO_ITEM = Pattern.compile(
            "^(#No\\b|\\(Q\\)|TOTALES:|\\*\\s*(No genera|Sujeto)|Datos del certificador"
                    + "|Superintendencia|Moneda:|NIT Receptor|Nombre Receptor|Numero Acceso"
                    + "|Fecha y hora|Factura|FACTURA|IVA\\b|\\d+\\s*$|\\.\\s*$)");

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("ene", 1), Map.entry("feb", 2), Map.entry("mar", 3),
            Map.entry("abr", 4), Map.entry("may", 5), Map.entry("jun", 6),
            Map.entry("jul", 7), Map.entry("ago", 8), Map.entry("sep", 9),
            Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dic", 12),
            // por si la SAT emite en ingles
            Map.entry("jan", 1), Map.entry("apr", 4), Map.entry("aug", 8), Map.entry("dec", 12));

    public static FacturaSatDatos parsear(InputStream in, String nombreArchivo) {
        FacturaSatDatos f = new FacturaSatDatos();
        f.setArchivo(nombreArchivo == null ? "" : nombreArchivo);
        String texto;
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            texto = stripper.getText(doc);
        } catch (IOException e) {
            f.setError("No se pudo leer el PDF de la factura.");
            return f;
        }
        return llenarDesdeTexto(f, texto);
    }

    /** Separado para poder probar la logica sin un PDF. */
    static FacturaSatDatos llenarDesdeTexto(FacturaSatDatos f, String texto) {
        Matcher m = RE_AUTORIZACION.matcher(texto);
        if (m.find()) {
            f.setAutorizacion(m.group(1).toUpperCase(Locale.ROOT));
        } else {
            // respaldo: el archivo de la SAT suele llamarse igual que la autorizacion
            Matcher ma = RE_AUTORIZACION.matcher(f.getArchivo());
            if (ma.find()) f.setAutorizacion(ma.group(1).toUpperCase(Locale.ROOT));
        }
        m = RE_SERIE.matcher(texto);
        if (m.find()) f.setSerie(m.group(1).toUpperCase(Locale.ROOT));
        m = RE_DTE.matcher(texto);
        if (m.find()) f.setNumeroDte(m.group(1));
        m = RE_NIT_EMISOR.matcher(texto);
        if (m.find()) f.setNitEmisor(m.group(1).toUpperCase(Locale.ROOT));
        m = RE_NOMBRE_EMISOR.matcher(texto);
        if (m.find()) f.setNombreEmisor(m.group(1).strip());
        m = RE_FECHA.matcher(texto);
        if (m.find()) {
            Integer mes = MESES.get(m.group(2).toLowerCase(Locale.ROOT));
            if (mes != null) {
                try {
                    f.setFechaEmision(LocalDate.of(Integer.parseInt(m.group(3)),
                            mes, Integer.parseInt(m.group(1))));
                } catch (Exception ignorada) {
                    // fecha fuera de rango: se deja nula
                }
            }
        }
        m = RE_TOTALES.matcher(texto);
        if (m.find()) f.setMonto(numero(m.group(1)));

        f.setDescripcion(extraerDescripcion(texto));

        if (f.getAutorizacion().isEmpty() && f.getNumeroDte().isEmpty()) {
            f.setError("No parece una factura FEL: no se hallo autorizacion ni DTE.");
        } else if (f.getMonto() <= 0) {
            f.setError("No se pudo leer el monto total de la factura.");
        }
        return f;
    }

    /**
     * Junta la descripcion de todos los items: la linea que abre cada item
     * mas sus continuaciones, hasta llegar a TOTALES.
     *
     * El layout de la SAT varia la cantidad de columnas de dinero segun el
     * tipo de factura (con o sin "Otros Descuentos", con o sin IVA
     * desglosado), y PDFBox mezcla esos montos en la misma linea visual que
     * el inicio de la descripcion. En vez de exigir un numero fijo de
     * columnas, se toma TODO el texto de cada item y se le QUITAN los
     * numeros con decimales (montos e IVA) y la palabra "IVA": lo que
     * sobra es la descripcion real, sin importar cuantas columnas traiga
     * la factura.
     */
    private static String extraerDescripcion(String texto) {
        List<String> items = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean enTabla = false;
        boolean itemAbierto = false;
        for (String cruda : texto.split("\r?\n")) {
            String ln = cruda.strip();
            if (ln.isEmpty()) continue;
            if (ln.startsWith("#No")) { enTabla = true; continue; }
            if (ln.startsWith("TOTALES:")) break;
            if (!enTabla) continue;
            Matcher mi = RE_ITEM_INICIO.matcher(ln);
            if (mi.matches()) {
                if (actual.length() > 0) items.add(actual.toString());
                actual = new StringBuilder(mi.group(1));
                itemAbierto = true;
                continue;
            }
            if (itemAbierto && !RE_RUIDO_ITEM.matcher(ln).lookingAt()) {
                actual.append(' ').append(ln);
            }
        }
        if (actual.length() > 0) items.add(actual.toString());

        List<String> limpios = new ArrayList<>();
        for (String item : items) {
            String sinMontos = RE_NUMERO_DECIMAL.matcher(item).replaceAll(" ");
            sinMontos = sinMontos.replaceAll("(?i)\\bIVA\\b", " ");
            sinMontos = sinMontos.replaceAll("\\s+", " ").strip();
            if (!sinMontos.isEmpty()) limpios.add(sinMontos);
        }
        return String.join(" | ", limpios);
    }

    private static double numero(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
