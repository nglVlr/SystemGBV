package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Porteo de parsear_cheques(): reporte de cheques del banco (.xls o .xlsx).
 *
 * Columnas fijas del reporte: 2 = numero de cheque, 8 = fecha,
 * 14 = estado, 15 = nombre del beneficiario, 25 = monto.
 * Solo se toman los cheques con estado IMPRESO (sin importar mayusculas).
 */
public final class ParserCheques {

    private static final Pattern RE_NUM = Pattern.compile("^\\d{4,6}$");

    private ParserCheques() {}

    public static List<Cheque> parsear(InputStream in) throws IOException {
        List<Cheque> cheques = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet hoja = wb.getSheetAt(0);
            for (Row row : hoja) {
                String num = CeldaUtil.texto(row, 2).trim().replace(".0", "");
                String estado = CeldaUtil.notna(row, 14)
                        ? CeldaUtil.texto(row, 14).trim().toUpperCase(Locale.ROOT) : "";
                if (RE_NUM.matcher(num).matches() && "IMPRESO".equals(estado)) {
                    Cheque c = new Cheque();
                    c.setCheque(num);
                    c.setNombre(CeldaUtil.notna(row, 15)
                            ? CeldaUtil.texto(row, 15).trim() : "");
                    c.setMonto(CeldaUtil.notna(row, 25)
                            ? ValidadorComprasService.round2(CeldaUtil.numero(row, 25, 0.0))
                            : 0.0);
                    c.setFecha(CeldaUtil.fecha(row, 8));
                    cheques.add(c);
                }
            }
        }
        if (cheques.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se encontraron cheques con estado IMPRESO. "
                    + "Verifica que sea el reporte de cheques del banco (.xls o .xlsx) "
                    + "y que la columna de estado diga IMPRESO.");
        }
        return cheques;
    }
}
