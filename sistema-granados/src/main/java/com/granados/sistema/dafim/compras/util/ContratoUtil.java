package com.granados.sistema.dafim.compras.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Porteo de extraer_contrato() del motor Python.
 * Cubre: CONTRATO NO. 38-2026 | No: 038-2026 | numero 0038-2026 |
 *        NRO 38-2026 | N 38 - 2026 | CONTRATO 38-2026
 * Normaliza ceros a la izquierda: 038-2026 / 0038-2026 -> 38-2026
 */
public final class ContratoUtil {

    private static final Pattern RE_CONTRATO = Pattern.compile(
            "CONTRATO\\s*(?:ADMINISTRATIVO\\s*)?(?:N[U\u00DA]MERO|NRO|N[O\u00B0\u00BA]?)?\\s*[.:;,]*\\s*"
            + "0*(\\d{1,3})\\s*[\\-\u2013]\\s*(\\d{4})");

    private static final Pattern RE_NORMALIZA = Pattern.compile(
            "^0*(\\d{1,3})\\s*[\\-\u2013]\\s*(\\d{4})$");

    private ContratoUtil() {}

    public static String extraerContrato(Object texto) {
        String t = String.valueOf(texto).toUpperCase();
        Matcher m = RE_CONTRATO.matcher(t);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) + "-" + m.group(2);
        }
        return "";
    }

    /** Para valores de columna tipo "038-2026" (parser de machote). */
    public static String normalizar(String contrato) {
        Matcher m = RE_NORMALIZA.matcher(contrato);
        if (m.matches()) {
            return Integer.parseInt(m.group(1)) + "-" + m.group(2);
        }
        return contrato;
    }
}
