package com.granados.sistema.dafim.paquetes.parser;

import com.granados.sistema.dafim.compras.parser.CeldaUtil;
import com.granados.sistema.dafim.paquetes.dto.PaqueteDatos;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lee el Excel de paquetes que manda la oficina: CADA HOJA es un paquete.
 *
 * Estructura observada en los archivos reales:
 *   fila 0: titulo ("JULIO 2026")
 *   fila 1: encabezados (algo, "concepto", "monto", fechas...)
 *   filas siguientes: numero, concepto, monto, ...
 *   ultima fila con monto y sin concepto: TOTAL del paquete
 *
 * Reglas: se saltan hojas sin datos; la fila de encabezado se detecta
 * buscando las palabras "concepto" y "monto" (en cualquier columna); las
 * columnas de concepto y monto se toman de esa fila.
 */
public final class ParserPaquetesExcel {

    private ParserPaquetesExcel() { }

    public static List<PaqueteDatos> parsear(InputStream in) throws IOException {
        List<PaqueteDatos> paquetes = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            for (int h = 0; h < wb.getNumberOfSheets(); h++) {
                // hojas ocultas no cuentan: suelen ser borradores de meses viejos
                if (wb.isSheetHidden(h) || wb.isSheetVeryHidden(h)) continue;
                Sheet hoja = wb.getSheetAt(h);
                PaqueteDatos p = leerHoja(hoja);
                if (p != null && !p.getLineas().isEmpty()) {
                    paquetes.add(p);
                }
            }
        }
        return paquetes;
    }

    private static PaqueteDatos leerHoja(Sheet hoja) {
        // 1) localizar la fila de encabezado y las columnas concepto/monto
        int filaEnc = -1;
        int colConcepto = -1;
        int colMonto = -1;
        int tope = Math.min(hoja.getLastRowNum(), 12);
        for (int r = 0; r <= tope && filaEnc < 0; r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) continue;
            for (int c = 0; c < fila.getLastCellNum(); c++) {
                String t = CeldaUtil.texto(fila, c).toLowerCase(Locale.ROOT).strip();
                if (t.startsWith("concepto") || t.startsWith("descripcion")
                        || t.startsWith("descripci\u00f3n")) {
                    colConcepto = c;
                    filaEnc = r;
                } else if (t.startsWith("monto") || t.startsWith("valor")) {
                    colMonto = c;
                }
            }
        }
        if (filaEnc < 0 || colConcepto < 0 || colMonto < 0) return null;

        PaqueteDatos p = new PaqueteDatos();
        p.setNombreHoja(hoja.getSheetName().strip());

        // 2) leer lineas; la fila con monto y SIN concepto es el total
        int orden = 0;
        for (int r = filaEnc + 1; r <= hoja.getLastRowNum(); r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) continue;
            String concepto = CeldaUtil.texto(fila, colConcepto).strip();
            double monto = CeldaUtil.numero(fila, colMonto, 0);
            if (!concepto.isEmpty() && monto > 0) {
                p.getLineas().add(new PaqueteDatos.Linea(++orden, concepto, monto));
            } else if (concepto.isEmpty() && monto > 0) {
                p.setTotalEsperado(monto);
            }
        }
        return p;
    }
}
