package com.granados.sistema.dafim.compras.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Lectura de celdas POI imitando como pandas convierte los valores a texto.
 *
 * pandas lee las celdas numericas como float, asi que str(41001) da
 * "41001.0" y el motor Python limpia con .replace('.0',''). Para que los
 * parsers Java produzcan EXACTAMENTE las mismas cadenas, aqui se replica
 * ese comportamiento (enteros como "N.0", decimales tal cual).
 */
public final class CeldaUtil {

    private CeldaUtil() {}

    /** Valor crudo de la celda: Date, Double, String, Boolean o null. */
    public static Object valor(Row fila, int col) {
        if (fila == null) return null;
        Cell c = fila.getCell(col);
        if (c == null) return null;
        CellType t = c.getCellType() == CellType.FORMULA
                ? c.getCachedFormulaResultType() : c.getCellType();
        switch (t) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(c)) return c.getDateCellValue();
                return c.getNumericCellValue();
            case STRING:
                return c.getStringCellValue();
            case BOOLEAN:
                return c.getBooleanCellValue();
            default:
                return null;
        }
    }

    /** true si la celda tiene valor (equivalente de pd.notna). */
    public static boolean notna(Row fila, int col) {
        Object v = valor(fila, col);
        if (v == null) return false;
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof Double d) return !d.isNaN();
        return true;
    }

    /**
     * str(valor) al estilo pandas: numerico entero -> "N.0",
     * decimal -> representacion normal, texto tal cual, vacio -> "".
     */
    public static String texto(Row fila, int col) {
        Object v = valor(fila, col);
        if (v == null) return "";
        if (v instanceof Double d) return floatStrPython(d);
        if (v instanceof Date dt) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy").format(dt);
        }
        if (v instanceof Boolean b) return b ? "True" : "False";
        return String.valueOf(v);
    }

    /** str(float) de Python para los valores tipicos de estos reportes. */
    public static String floatStrPython(double v) {
        if (Double.isNaN(v)) return "nan";
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return (long) v + ".0";
        }
        String s = Double.toString(v);
        if (s.contains("E") || s.contains("e")) {
            return new BigDecimal(v).stripTrailingZeros().toPlainString();
        }
        return s;
    }

    /**
     * Fecha de una celda: Date de Excel, serial numerico (sin formato de fecha)
     * o el valor crudo si no se puede interpretar.
     */
    public static Object fecha(Row fila, int col) {
        Object v = valor(fila, col);
        if (v instanceof Date) return v;
        if (v instanceof Double d && DateUtil.isValidExcelDate(d)
                && d >= 30000 && d < 80000) {
            return DateUtil.getJavaDate(d);
        }
        return v;
    }

    /** double de una celda numerica o texto numerico; def si no se puede. */
    public static double numero(Row fila, int col, double def) {
        Object v = valor(fila, col);
        if (v instanceof Double d) return d;
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim().replace(",", ""));
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
