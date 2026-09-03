package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.ContratoUtil;
import com.granados.sistema.dafim.compras.util.FechaUtil;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Porteo de parsear_machote(): lee un Excel de compras directas ya hecho
 * (formato de 15 columnas de cualquier mes) para cargarlo al historial.
 * Si el renglon viene vacio se infiere: contrato de servicios -> 029,
 * si no por keywords de la descripcion, y como ultimo recurso 299.
 */
public final class ParserMachote {

    private static final Pattern RE_NUM_FILA = Pattern.compile("^\\d+(\\.0)?$");
    private static final Pattern RE_CONTRATO_COL = Pattern.compile(
            "^0*(\\d{1,3})\\s*[-\u2013]\\s*(\\d{4})$");
    private static final Pattern RE_NPG = Pattern.compile("^E\\d{8,10}$");

    private ParserMachote() {}

    public static List<FilaCompra> parsear(InputStream in) throws IOException {
        List<FilaCompra> filas = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet hoja = wb.getSheetAt(0);
            int ultima = hoja.getLastRowNum();

            // primera fila de datos: columna A == '1'
            int inicio = -1;
            int tope = Math.min(40, ultima + 1);
            for (int i = 0; i < tope; i++) {
                String v = CeldaUtil.notna(hoja.getRow(i), 0)
                        ? CeldaUtil.texto(hoja.getRow(i), 0).strip() : "";
                if ("1".equals(v) || "1.0".equals(v)) {
                    inicio = i;
                    break;
                }
            }
            if (inicio < 0) {
                throw new IllegalArgumentException(
                        "No se encontro la primera fila de datos (columna A = 1). "
                        + "Verifica que sea un Excel de compras directas.");
            }

            for (int i = inicio; i <= ultima; i++) {
                Row row = hoja.getRow(i);
                String[] r = new String[15];
                for (int c = 0; c < 15; c++) {
                    r[c] = CeldaUtil.notna(row, c)
                            ? CeldaUtil.texto(row, c).strip() : "";
                }
                if (r[0].isEmpty()) continue;
                if (!RE_NUM_FILA.matcher(r[0]).matches()) break;

                String renglon = r[6].replace(".0", "").strip();
                if (!renglon.isEmpty() && renglon.chars().allMatch(Character::isDigit)) {
                    renglon = Constantes.zfill3(renglon);
                }
                if (renglon.isEmpty()) {
                    if (!ContratoUtil.extraerContrato(r[2]).isEmpty()
                            && r[2].toUpperCase().contains("SERVICIOS")) {
                        renglon = "029";
                    } else {
                        String kw = Constantes.rengKw(r[2]);
                        renglon = kw.isEmpty() ? "299" : kw;
                    }
                }

                double monto = CeldaUtil.notna(row, 4)
                        ? CeldaUtil.numero(row, 4, 0.0) : 0.0;

                String contrato = r[13].replace(".0", "").strip();
                if (contrato.isEmpty() || "0".equals(contrato) || "nan".equals(contrato)
                        || "N/A".equals(contrato) || "NA".equals(contrato)) {
                    String c2 = ContratoUtil.extraerContrato(r[2]);
                    contrato = c2.isEmpty() ? "N/A" : c2;
                } else {
                    Matcher mn = RE_CONTRATO_COL.matcher(contrato);
                    if (mn.matches()) {
                        contrato = Integer.parseInt(mn.group(1)) + "-" + mn.group(2);
                    }
                }

                String npg = r[9].strip().toUpperCase();
                if (!npg.isEmpty() && !RE_NPG.matcher(npg).matches()) npg = "";

                FilaCompra f = new FilaCompra();
                f.setModalidad(r[1].isEmpty() ? "CASO DE EXCEPCION" : r[1]);
                f.setDesc(r[2]);
                f.setPrecio(ValidadorComprasService.round2(monto));
                f.setRenglon(renglon);
                f.setProveedor(r[7]);
                f.setNit(TextoUtil.limpiarNit(r[8]));
                f.setNpg(npg);
                f.setFechaPub(leerFecha(row, 10, r[10]));
                f.setFechaAdj(leerFecha(row, 11, r[11]));
                f.setContrato(contrato);
                f.setFechaCont(leerFecha(row, 14, r[14].isEmpty() ? "N/A" : r[14]));
                f.setCheque("");
                filas.add(f);
            }
        }
        return filas;
    }

    private static String leerFecha(Row row, int col, String fallback) {
        Object v = CeldaUtil.fecha(row, col);
        if (v == null) return fallback == null ? "" : fallback;
        String s = FechaUtil.fmt(v);
        return s == null || s.isBlank() || "null".equals(s) ? fallback : s;
    }
}
