package com.granados.sistema.dafim.presupuesto.web;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * Textos de la pantalla de Cargas: validacion de archivo y avisos al
 * usuario, sin jerga de parsers ni codigos internos de reportes.
 */
public final class MensajesCarga {

    static final String EGRESOS_VACIO = "Selecciona el PDF de ejecucion de egresos.";
    static final String CAJA_VACIO = "Selecciona el PDF del boletin de caja.";
    static final String EGRESOS_NO_PDF =
            "Ese archivo no es un PDF. Sube el PDF de ejecucion de egresos.";
    static final String CAJA_NO_PDF =
            "Ese archivo no es un PDF. Sube el PDF del boletin de caja.";

    private static final String FALLBACK_EGRESOS =
            "No se pudo leer el PDF de ejecucion de egresos. "
                    + "Verifica que sea el reporte de ejecucion de egresos de SICOIN, "
                    + "no otro archivo.";
    private static final String FALLBACK_CAJA =
            "No se pudo leer el PDF del boletin de caja. "
                    + "Verifica que sea el boletin de caja consolidado de SICOIN, "
                    + "no otro archivo.";

    private MensajesCarga() {}

    public static String errorEgresos(Throwable e) {
        return sanitizar(e, FALLBACK_EGRESOS);
    }

    public static String errorCaja(Throwable e) {
        return sanitizar(e, FALLBACK_CAJA);
    }

    public static boolean noEsPdf(MultipartFile archivo) {
        if (archivo == null) return true;
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || nombre.isBlank()) return false;
        return !nombre.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    static String sanitizar(Throwable e, String fallback) {
        if (e == null || esExcepcionTecnica(e)) return fallback;
        String m = e.getMessage();
        if (m == null || m.isBlank() || esJerga(m)) return fallback;
        return m.strip();
    }

    private static boolean esExcepcionTecnica(Throwable e) {
        return e instanceof NullPointerException
                || e instanceof IndexOutOfBoundsException
                || e instanceof ArithmeticException;
    }

    static boolean esJerga(String m) {
        if (m.length() > 220) return true;
        String t = m.toLowerCase(Locale.ROOT);
        if ("null".equals(t)) return true;
        return t.contains("nullpointer")
                || t.contains("r008")
                || t.contains("fila de ejecucion")
                || t.contains("cuenta monetaria con")
                || t.contains("cuenta monetaria sin")
                || t.contains("end-of-file")
                || t.contains("pdfbox")
                || t.contains("expected line")
                || t.contains("importes (se esperaban")
                || t.contains("at offset")
                || t.contains("header doesn't")
                || t.contains("missing root")
                || t.contains(".java")
                || t.contains("exception:")
                || t.contains("java.");
    }
}
