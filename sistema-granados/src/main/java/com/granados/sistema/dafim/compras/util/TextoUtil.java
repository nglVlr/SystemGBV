package com.granados.sistema.dafim.compras.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Porteo de norm(), limpiar_desc() y limpiar_nit() del motor Python.
 * La normalizacion es la base del match difuso por nombre: debe ser identica.
 */
public final class TextoUtil {

    private static final Pattern ESPACIOS_COMAS =
            Pattern.compile("[,\\s]+", Pattern.UNICODE_CHARACTER_CLASS);

    private TextoUtil() {}

    /** upper + sin tildes + colapsa comas/espacios a un espacio. */
    public static String norm(Object s) {
        String t = String.valueOf(s).toUpperCase();
        t = t.replace('\u00C1', 'A').replace('\u00C9', 'E').replace('\u00CD', 'I')
             .replace('\u00D3', 'O').replace('\u00DA', 'U').replace('\u00DC', 'U')
             .replace('\u00D1', 'N');
        return ESPACIOS_COMAS.matcher(t).replaceAll(" ").trim();
    }

    /** Quita el prefijo "pago de " / "por pago de " (una sola vez, como el Python). */
    public static String limpiarDesc(Object d) {
        String s = String.valueOf(d).trim();
        String low = s.toLowerCase();
        String[] prefijos = {"pago de ", "por pago de ", "pago  de "};
        for (String p : prefijos) {
            if (low.startsWith(p)) {
                s = s.substring(p.length());
                break;
            }
        }
        return s.trim();
    }

    /** strip + upper + quita todas las K, espacios y ".0". */
    public static String limpiarNit(Object nit) {
        return String.valueOf(nit).trim().toUpperCase()
                .replace("K", "").replace(" ", "").replace(".0", "");
    }

    /** Palabras de un texto normalizado (equivale a norm(s).split() de Python). */
    public static Set<String> palabras(Object s) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String p : norm(s).split(" ")) {
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    /** Interseccion (conteo) de dos conjuntos, sin modificarlos. */
    public static int interseccion(Set<String> a, Set<String> b) {
        int n = 0;
        for (String x : a) if (b.contains(x)) n++;
        return n;
    }

    /** a - b como nuevo conjunto (preserva orden de a). */
    public static Set<String> menos(Set<String> a, Set<String> b) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String x : a) if (!b.contains(x)) out.add(x);
        return out;
    }

    /** Recorte seguro estilo Python s[:n]. */
    public static String corta(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}
