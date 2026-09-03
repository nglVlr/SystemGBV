package com.granados.sistema.dafim.remuneraciones.service;

import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.remuneraciones.dto.FilaRemuneracion;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Oficio LAIP Art. 10 num. 4: remuneraciones. Columnas A-P del formato
 * vigente mas Q DESCUENTOS y R LIQUIDO A RECIBIR.
 */
public final class ExcelGeneradorRemuneraciones {

    private static final String[] ENCABEZADOS = {
            "No.", "RENGLÓN", "NOMBRE EMPLEADO / SERVIDOR PÚBLICO",
            "CARGO / SERVICIOS PRESTADOS", "DEPENDENCIA", "DIETAS", "SUELDO BASE",
            "HONORARIOS", "COMPLMENTO POR ANTIGÜEDAD", "BONIFICACIÓN PROFESIONAL",
            "BONO ESPECÍFICO", "BONIFICACIÓN INCENTIVO", "GASTOS FUNERARIOS",
            "GASTOS DE REPRESENTACIÓN", "OTRAS REMUNERACIONES", "TOTAL INGRESOS",
            "DESCUENTOS", "LÍQUIDO A RECIBIR"};

    private static final int[] ANCHOS = {
            6, 10, 36, 32, 22, 12, 13, 12, 14, 14, 13, 14, 13, 16, 14, 14, 13, 14};

    private ExcelGeneradorRemuneraciones() {}

    public static Path generar(List<FilaRemuneracion> filas, int mes, int anio,
                               Path pathSalida) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet ws = wb.createSheet("Hoja1");

            Font fd = wb.createFont();
            fd.setFontName("Calibri");
            fd.setFontHeightInPoints((short) 9);
            Font fb = wb.createFont();
            fb.setFontName("Calibri");
            fb.setFontHeightInPoints((short) 9);
            fb.setBold(true);
            Font ft = wb.createFont();
            ft.setFontName("Calibri");
            ft.setFontHeightInPoints((short) 11);
            ft.setBold(true);

            DataFormat df = wb.createDataFormat();
            short fmtMoneda = df.getFormat("#,##0.00");

            CellStyle titulo = wb.createCellStyle();
            titulo.setFont(ft);
            CellStyle etiqueta = wb.createCellStyle();
            etiqueta.setFont(fb);
            CellStyle hdr = borde(wb.createCellStyle());
            hdr.setFont(fb);
            hdr.setAlignment(HorizontalAlignment.CENTER);
            hdr.setVerticalAlignment(VerticalAlignment.CENTER);
            hdr.setWrapText(true);
            CellStyle seccion = wb.createCellStyle();
            seccion.setFont(fb);
            CellStyle texto = borde(wb.createCellStyle());
            texto.setFont(fd);
            texto.setWrapText(true);
            texto.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle numero = borde(wb.createCellStyle());
            numero.setFont(fd);
            numero.setAlignment(HorizontalAlignment.CENTER);
            CellStyle dinero = borde(wb.createCellStyle());
            dinero.setFont(fd);
            dinero.setDataFormat(fmtMoneda);
            dinero.setAlignment(HorizontalAlignment.RIGHT);

            int r = 0;
            poner(ws, r, 0, "NOMBRE DE LA INSTITUCIÓN:", etiqueta);
            poner(ws, r, 3, "MUNICIPALIDAD DE GRANADOS BAJA VERAPAZ", titulo);
            r++;
            poner(ws, r, 0, "DEPENDENCIA RESPONSABLE:", etiqueta);
            poner(ws, r, 3, "DIRECCION DE ADMINISTRACION FINANCIERA MUNICIPAL (DAFIM)", titulo);
            r++;
            LocalDate finMes = YearMonth.of(anio, mes).atEndOfMonth();
            poner(ws, r, 0, "MES QUE CORRESPONDE:", etiqueta);
            poner(ws, r, 3, String.format("%02d/%02d/%04d", finMes.getDayOfMonth(), mes, anio)
                    + " (" + Constantes.MESES_NOMBRE.get(mes) + " " + anio + ")", titulo);
            r++;
            LocalDate hoy = LocalDate.now();
            poner(ws, r, 0, "FECHA DE EMISIÓN:", etiqueta);
            poner(ws, r, 3, String.format("%02d/%02d/%04d", hoy.getDayOfMonth(),
                    hoy.getMonthValue(), hoy.getYear()), titulo);
            r++;
            poner(ws, r, 0, "EJERCICIO " + anio, etiqueta);
            r += 2;
            poner(ws, r, 0, "REMUNERACIONES DE FUNCIONARIOS, EMPLEADOS Y ASESORES", titulo);
            if (ws.getRow(r) != null) {
                ws.addMergedRegion(new CellRangeAddress(r, r, 0, 17));
            }
            r++;
            poner(ws, r, 0, "(ARTÍCULO 10, NUMERAL 4, LEY DE ACCESO A LA INFORMACIÓN PÚBLICA)", etiqueta);
            r += 2;

            Row h = ws.createRow(r);
            h.setHeightInPoints(32);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(ENCABEZADOS[i]);
                c.setCellStyle(hdr);
            }
            r++;

            String ultimo = "";
            for (FilaRemuneracion f : filas) {
                String rg = f.getRenglon() == null ? "" : f.getRenglon();
                if (!rg.equals(ultimo)) {
                    Row sec = ws.createRow(r++);
                    Cell sc = sec.createCell(0);
                    sc.setCellValue("RENGLÓN " + (f.renglonExcel().length() <= 2
                            ? Constantes.zfill3(f.renglonExcel()) : f.renglonExcel()));
                    sc.setCellStyle(seccion);
                    ultimo = rg;
                }
                Row row = ws.createRow(r++);
                row.setHeightInPoints(18);
                textoNum(row, 0, f.getNumero(), numero);
                textoCel(row, 1, f.renglonExcel(), texto);
                textoCel(row, 2, nn(f.getNombre()), texto);
                textoCel(row, 3, nn(f.getCargo()), texto);
                textoCel(row, 4, nn(f.getDependencia()), texto);
                dinero(row, 5, f.getDietas(), dinero);
                dinero(row, 6, f.getSueldoBase(), dinero);
                dinero(row, 7, f.getHonorarios(), dinero);
                dinero(row, 8, f.getComplementoAntiguedad(), dinero);
                dinero(row, 9, f.getBonifProfesional(), dinero);
                dinero(row, 10, f.getBonoEspecifico(), dinero);
                dinero(row, 11, f.getBonifIncentivo(), dinero);
                dinero(row, 12, f.getGastosFunerarios(), dinero);
                dinero(row, 13, f.getGastosRepresentacion(), dinero);
                dinero(row, 14, f.getOtrasRemuneraciones(), dinero);
                dinero(row, 15, f.getTotalIngresos(), dinero);
                dinero(row, 16, f.getDescuentos(), dinero);
                dinero(row, 17, f.getLiquido(), dinero);
            }

            r += 2;
            poner(ws, r, 0, "INFORMACIÓN DE VIÁTICOS", titulo);
            r += 2;
            Row vh = ws.createRow(r);
            String[] vcols = {"RENGLÓN", "NOMBRE", "CARGO", "DEPENDENCIA", "TOTAL VIÁTICO MENSUAL"};
            for (int i = 0; i < vcols.length; i++) {
                Cell c = vh.createCell(1 + i);
                c.setCellValue(vcols[i]);
                c.setCellStyle(hdr);
            }

            for (int i = 0; i < ANCHOS.length; i++) {
                ws.setColumnWidth(i, ANCHOS[i] * 256);
            }

            Files.createDirectories(pathSalida.getParent() == null
                    ? Path.of(".") : pathSalida.getParent());
            try (OutputStream os = Files.newOutputStream(pathSalida)) {
                wb.write(os);
            }
        }
        return pathSalida;
    }

    private static void poner(Sheet ws, int row, int col, String v, CellStyle st) {
        Row r = ws.getRow(row);
        if (r == null) r = ws.createRow(row);
        Cell c = r.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(st);
    }

    private static void textoCel(Row row, int col, String v, CellStyle st) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(st);
    }

    private static void textoNum(Row row, int col, int v, CellStyle st) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(st);
    }

    private static void dinero(Row row, int col, double v, CellStyle st) {
        Cell c = row.createCell(col);
        if (v != 0) c.setCellValue(v);
        else c.setCellValue("");
        c.setCellStyle(st);
    }

    private static CellStyle borde(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }
}
