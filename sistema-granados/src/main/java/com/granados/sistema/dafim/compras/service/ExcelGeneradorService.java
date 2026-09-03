package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.RegistroHistorial;
import com.granados.sistema.dafim.compras.dto.ContratoInfo;
import com.granados.sistema.dafim.compras.dto.ProveedorInfo;
import com.granados.sistema.dafim.compras.util.Constantes;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Porteo EXACTO de generar_excel() del motor Python (openpyxl -> Apache POI).
 *
 * El formato es un ENTREGABLE LEGAL (informe de oficio, Art. 10 Num. 11 de la
 * LAIP), asi que se replica celda por celda:
 *  - 6 filas de titulo combinadas A:O, Calibri 11 negrita, centradas.
 *  - Encabezados en la fila 9 (15 columnas), Calibri 9 negrita, bordes finos.
 *  - Columnas de texto forzado (formato '@'): 2,3,7,8,9,10,11,12,13,14,15
 *    para que NIT, NPG, contrato (12-2026) y renglon (029) no se conviertan
 *    en numeros o fechas al abrir el archivo.
 *  - Columna F por fila: formula =+D{fila}*E{fila} (asi la emite openpyxl).
 *  - Fila final: TOTAL en columna E y =SUM(F...) en columna F, en negrita.
 *  - Anchos de columna y altura de fila de datos (30 pt) identicos.
 */
public final class ExcelGeneradorService {

    /** Columnas 1-based que se fuerzan a texto, igual que COLS_TEXTO en Python. */
    private static final int[] COLS_TEXTO = {2, 3, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    private static final String[] ENCABEZADOS = {
            "No.", "MODALIDAD DE COMPRA", "DESCRIPCION DE LA COMPRA O CONTRATACION",
            "CANTIDAD", "PRECIO UNITARIO", "MONTO TOTAL DE LA COMPRA", "RENGLON",
            "NOMBRE DEL PROVEEDOR", "NIT", "NPG", "FECHA PUBLICACION",
            "FECHA ADJUDICACION", "ACTIVA", "NUMERO DE CONTRATO", "FECHA DE CONTRATO"};

    private static final int[] ANCHOS = {5, 18, 55, 6, 13, 13, 8, 30, 13, 14, 13, 13, 10, 15, 13};

    private ExcelGeneradorService() {}

    private static boolean esTexto(int col1) {
        for (int c : COLS_TEXTO) if (c == col1) return true;
        return false;
    }

    public static Path generarExcel(List<FilaCompra> filas, int mes, int anio,
                                    Path pathSalida) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet ws = wb.createSheet("COMPRAS DIRECTAS");

            Font fd = wb.createFont();
            fd.setFontName("Calibri"); fd.setFontHeightInPoints((short) 9);
            Font fb = wb.createFont();
            fb.setFontName("Calibri"); fb.setFontHeightInPoints((short) 9); fb.setBold(true);
            Font ft = wb.createFont();
            ft.setFontName("Calibri"); ft.setFontHeightInPoints((short) 11); ft.setBold(true);

            DataFormat df = wb.createDataFormat();
            short fmtTexto = df.getFormat("@");
            short fmtMoneda = df.getFormat("#,##0.00");

            CellStyle titulo = wb.createCellStyle();
            titulo.setFont(ft);
            titulo.setAlignment(HorizontalAlignment.CENTER);
            titulo.setVerticalAlignment(VerticalAlignment.CENTER);
            titulo.setWrapText(true);

            CellStyle hdr = borde(wb.createCellStyle());
            hdr.setFont(fb);
            hdr.setAlignment(HorizontalAlignment.CENTER);
            hdr.setVerticalAlignment(VerticalAlignment.CENTER);
            hdr.setWrapText(true);

            // estilos de datos por columna (alineacion + formato + borde)
            CellStyle[] estilos = new CellStyle[15];
            HorizontalAlignment[] al = {
                    HorizontalAlignment.CENTER, HorizontalAlignment.CENTER,
                    HorizontalAlignment.LEFT, HorizontalAlignment.CENTER,
                    HorizontalAlignment.RIGHT, HorizontalAlignment.RIGHT,
                    HorizontalAlignment.CENTER, HorizontalAlignment.LEFT,
                    HorizontalAlignment.CENTER, HorizontalAlignment.CENTER,
                    HorizontalAlignment.CENTER, HorizontalAlignment.CENTER,
                    HorizontalAlignment.CENTER, HorizontalAlignment.CENTER,
                    HorizontalAlignment.CENTER};
            for (int i = 0; i < 15; i++) {
                CellStyle s = borde(wb.createCellStyle());
                s.setFont(fd);
                s.setAlignment(al[i]);
                s.setVerticalAlignment(VerticalAlignment.CENTER);
                if (i != 4 && i != 5) s.setWrapText(true);
                int col1 = i + 1;
                if (col1 == 5 || col1 == 6) s.setDataFormat(fmtMoneda);
                else if (esTexto(col1)) s.setDataFormat(fmtTexto);
                estilos[i] = s;
            }

            // ---- 6 filas de titulo combinadas A:O ----
            String[] titulos = {
                    "MUNICIPALIDAD DE GRANADOS, BAJA VERAPAZ",
                    "DIRECCION DE ADMINISTRACION FINANCIERA INTEGRADA MUNICIPAL -DAFIM-",
                    "CLASIFICACION INSTITUCIONAL SIAF: 12101505",
                    "INFORMACION DE OFICIO, ARTICULO 10, NUMERAL 11, LEY DE ACCESO A LA "
                            + "INFORMACION PUBLICA",
                    "COMPRAS DIRECTAS / CONTRATACIONES DE BIENES Y SERVICIOS",
                    "CORRESPONDIENTE AL MES DE " + Constantes.MESES_NOMBRE.get(mes) + " " + anio};
            for (int i = 0; i < titulos.length; i++) {
                Row r = ws.createRow(i);
                Cell c = r.createCell(0);
                c.setCellValue(titulos[i]);
                c.setCellStyle(titulo);
                ws.addMergedRegion(new CellRangeAddress(i, i, 0, 14));
            }

            // ---- encabezados en fila 9 (indice 8) ----
            int hr = 8;
            Row rh = ws.createRow(hr);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell c = rh.createCell(i);
                c.setCellValue(ENCABEZADOS[i]);
                c.setCellStyle(hdr);
            }
            for (int i = 0; i < ANCHOS.length; i++) {
                ws.setColumnWidth(i, ANCHOS[i] * 256);
            }

            // ---- filas de datos ----
            int ds = hr + 1;
            for (int idx = 0; idx < filas.size(); idx++) {
                FilaCompra f = filas.get(idx);
                int rn0 = ds + idx;          // indice 0-based POI
                int rn1 = rn0 + 1;           // numero de fila Excel 1-based
                Row r = ws.createRow(rn0);
                r.setHeightInPoints(30f);
                for (int ci = 0; ci < 15; ci++) {
                    Cell c = r.createCell(ci);
                    c.setCellStyle(estilos[ci]);
                }
                r.getCell(0).setCellValue(idx + 1);
                r.getCell(1).setCellValue(nn(f.getModalidad()));
                r.getCell(2).setCellValue(nn(f.getDesc()));
                r.getCell(3).setCellValue(1);
                r.getCell(4).setCellValue(f.getPrecio());
                r.getCell(5).setCellFormula("+D" + rn1 + "*E" + rn1);
                r.getCell(6).setCellValue(nn(f.getRenglon()));
                r.getCell(7).setCellValue(nn(f.getProveedor()));
                r.getCell(8).setCellValue(nn(f.getNit()));
                r.getCell(9).setCellValue(nn(f.getNpg()));
                r.getCell(10).setCellValue(nn(f.getFechaPub()));
                r.getCell(11).setCellValue(nn(f.getFechaAdj()));
                r.getCell(12).setCellValue("ACTIVA");
                r.getCell(13).setCellValue(nn(f.getContrato()));
                r.getCell(14).setCellValue(nn(f.getFechaCont()));
            }

            // ---- fila TOTAL ----
            int tr0 = ds + filas.size();
            Row rt = ws.createRow(tr0);
            CellStyle soloBorde = borde(wb.createCellStyle());
            CellStyle totalTxt = borde(wb.createCellStyle());
            totalTxt.setFont(fb);
            totalTxt.setAlignment(HorizontalAlignment.RIGHT);
            totalTxt.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle totalNum = borde(wb.createCellStyle());
            totalNum.setFont(fb);
            totalNum.setAlignment(HorizontalAlignment.RIGHT);
            totalNum.setVerticalAlignment(VerticalAlignment.CENTER);
            totalNum.setDataFormat(fmtMoneda);
            for (int ci = 0; ci < 15; ci++) {
                Cell c = rt.createCell(ci);
                c.setCellStyle(soloBorde);
            }
            rt.getCell(4).setCellValue("TOTAL");
            rt.getCell(4).setCellStyle(totalTxt);
            rt.getCell(5).setCellFormula("SUM(F" + (ds + 1) + ":F" + tr0 + ")");
            rt.getCell(5).setCellStyle(totalNum);

            // que Excel recalcule las formulas al abrir
            ws.setForceFormulaRecalculation(true);

            Files.createDirectories(pathSalida.getParent());
            try (OutputStream os = Files.newOutputStream(pathSalida)) {
                wb.write(os);
            }
        }
        return pathSalida;
    }

    /**
     * Excel de respaldo de la BD completa: 3 hojas (contratos_029,
     * proveedores, historial), todo como texto salvo el monto, para no
     * perder ceros a la izquierda ni formatos al reabrir.
     */
    public static Path generarExcelBd(Map<String, ContratoInfo> contratos,
                                      Map<String, ProveedorInfo> proveedores,
                                      List<RegistroHistorial> historial,
                                      Path pathSalida) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Font fb = wb.createFont();
            fb.setFontName("Calibri"); fb.setFontHeightInPoints((short) 10); fb.setBold(true);
            CellStyle hdr = wb.createCellStyle();
            hdr.setFont(fb);
            DataFormat df = wb.createDataFormat();
            CellStyle txt = wb.createCellStyle();
            txt.setDataFormat(df.getFormat("@"));
            CellStyle num = wb.createCellStyle();
            num.setDataFormat(df.getFormat("#,##0.00"));

            // hoja 1: contratos_029
            Sheet h1 = wb.createSheet("contratos_029");
            fila(h1, 0, hdr, "nit", "nombre", "contrato", "cargo", "npg", "anio");
            int r = 1;
            for (Map.Entry<String, ContratoInfo> e : contratos.entrySet()) {
                ContratoInfo d = e.getValue();
                fila(h1, r++, txt, e.getKey(), nn(d.getNombre()), nn(d.getContrato()),
                        nn(d.getCargo()), nn(d.getNpg()),
                        d.getAnio() == null ? "" : String.valueOf(d.getAnio()));
            }

            // hoja 2: proveedores
            Sheet h2 = wb.createSheet("proveedores");
            fila(h2, 0, hdr, "nit", "nombre", "renglon", "descripcion");
            r = 1;
            for (Map.Entry<String, ProveedorInfo> e : proveedores.entrySet()) {
                ProveedorInfo d = e.getValue();
                fila(h2, r++, txt, e.getKey(), nn(d.getNombre()),
                        nn(d.getRenglon()), nn(d.getDesc()));
            }

            // hoja 3: historial (monto numerico, el resto texto)
            Sheet h3 = wb.createSheet("historial");
            fila(h3, 0, hdr, "anio", "mes", "cheque", "nit", "nombre", "renglon",
                    "monto", "npg", "modalidad", "contrato", "descripcion");
            r = 1;
            for (RegistroHistorial h : historial) {
                Row row = h3.createRow(r++);
                String[] vals = {String.valueOf(h.getAnio()), String.valueOf(h.getMes()),
                        nn(h.getCheque()), nn(h.getNit()), nn(h.getNombre()),
                        nn(h.getRenglon())};
                for (int i = 0; i < vals.length; i++) {
                    Cell c = row.createCell(i);
                    c.setCellValue(vals[i]);
                    c.setCellStyle(txt);
                }
                Cell cm = row.createCell(6);
                cm.setCellValue(h.getMonto());
                cm.setCellStyle(num);
                String[] resto = {nn(h.getNpg()), nn(h.getModalidad()),
                        nn(h.getContrato()), nn(h.getDescripcion())};
                for (int i = 0; i < resto.length; i++) {
                    Cell c = row.createCell(7 + i);
                    c.setCellValue(resto[i]);
                    c.setCellStyle(txt);
                }
            }
            for (Sheet s : new Sheet[]{h1, h2, h3}) {
                int cols = s.getRow(0).getLastCellNum();
                for (int i = 0; i < cols; i++) s.setColumnWidth(i, 20 * 256);
            }

            Files.createDirectories(pathSalida.getParent());
            try (OutputStream os = Files.newOutputStream(pathSalida)) {
                wb.write(os);
            }
        }
        return pathSalida;
    }

    private static void fila(Sheet s, int rowIdx, CellStyle estilo, String... vals) {
        Row row = s.createRow(rowIdx);
        for (int i = 0; i < vals.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(vals[i]);
            c.setCellStyle(estilo);
        }
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static CellStyle borde(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }
}
