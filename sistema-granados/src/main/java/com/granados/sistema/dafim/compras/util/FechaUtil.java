package com.granados.sistema.dafim.compras.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.regex.Pattern;

/** Porteo de fmt_fecha() del motor Python: siempre dd/MM/yyyy. */
public final class FechaUtil {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private FechaUtil() {}

    public static String fmt(Object f) {
        if (f instanceof LocalDate ld) return ld.format(DDMMYYYY);
        if (f instanceof LocalDateTime ldt) return ldt.toLocalDate().format(DDMMYYYY);
        if (f instanceof Date d) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy").format(d);
        }
        String s = String.valueOf(f);
        if (ISO.matcher(s).lookingAt()) {
            return s.substring(8, 10) + "/" + s.substring(5, 7) + "/" + s.substring(0, 4);
        }
        return s.length() <= 10 ? s : s.substring(0, 10);
    }
}
