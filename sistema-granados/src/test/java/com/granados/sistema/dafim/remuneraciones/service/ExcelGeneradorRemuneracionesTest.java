package com.granados.sistema.dafim.remuneraciones.service;

import com.granados.sistema.dafim.remuneraciones.dto.FilaRemuneracion;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelGeneradorRemuneracionesTest {

    @TempDir
    Path tmp;

    @Test
    void escribeEncabezadoLaipColumnasQRSeccionesYViaticos() throws Exception {
        FilaRemuneracion concejal = fila("064", "HILARIO PEREZ", 11000, 0, 11000, 1760, 9240);
        concejal.setDietas(11000);
        FilaRemuneracion emp = fila("011", "TERESO LARIOS", 0, 3120, 3370, 369.09, 3000.91);
        emp.setBonifIncentivo(250);
        Path out = tmp.resolve("rem.xlsx");
        ExcelGeneradorRemuneraciones.generar(List.of(concejal, emp), 7, 2026, out);

        try (Workbook wb = WorkbookFactory.create(out.toFile())) {
            Sheet sh = wb.getSheetAt(0);
            assertTrue(texto(sh, 0, 3).contains("GRANADOS"));
            assertTrue(texto(sh, 6, 0).toUpperCase().contains("REMUNERACIONES"));
            Row hdr = null;
            int hdrIdx = -1;
            for (int i = 0; i <= 12; i++) {
                Row r = sh.getRow(i);
                if (r != null && "No.".equals(celda(r, 0))) {
                    hdr = r;
                    hdrIdx = i;
                    break;
                }
            }
            assertTrue(hdr != null);
            assertEquals("DESCUENTOS", celda(hdr, 16));
            assertTrue(celda(hdr, 17).toUpperCase().contains("LIQUIDO")
                    || celda(hdr, 17).toUpperCase().contains("LÍQUIDO"));

            boolean sec64 = false, sec011 = false, viaticos = false;
            boolean vioConcejal = false, vioTereso = false;
            for (int i = hdrIdx; i <= sh.getLastRowNum(); i++) {
                Row r = sh.getRow(i);
                if (r == null) continue;
                String a = celda(r, 0);
                String c = celda(r, 2);
                if (a.toUpperCase().contains("RENGL") && a.contains("64")) sec64 = true;
                if (a.toUpperCase().contains("RENGL") && a.contains("011")) sec011 = true;
                if (a.toUpperCase().contains("VI") && a.toUpperCase().contains("TICO")) viaticos = true;
                if (c.contains("HILARIO")) {
                    vioConcejal = true;
                    assertEquals(11000.0, r.getCell(15).getNumericCellValue(), 0.01);
                    assertEquals(1760.0, r.getCell(16).getNumericCellValue(), 0.01);
                    assertEquals(9240.0, r.getCell(17).getNumericCellValue(), 0.01);
                }
                if (c.contains("TERESO")) vioTereso = true;
                if (c.contains("TERESO")) {
                    assertEquals("CARGO", celda(r, 3));
                }
            }
            assertTrue(sec64);
            assertTrue(sec011);
            assertTrue(viaticos);
            assertTrue(vioConcejal);
            assertTrue(vioTereso);
        }
    }

    private static FilaRemuneracion fila(String rg, String nombre, double dietas, double sueldo,
                                         double p, double q, double r) {
        FilaRemuneracion f = new FilaRemuneracion();
        f.setRenglon(rg);
        f.setNombre(nombre);
        f.setCargo("CARGO");
        f.setNumero(1);
        f.setDietas(dietas);
        f.setSueldoBase(sueldo);
        f.setTotalIngresos(p);
        f.setDescuentos(q);
        f.setLiquido(r);
        return f;
    }

    private static String texto(Sheet sh, int row, int col) {
        Row r = sh.getRow(row);
        return r == null ? "" : celda(r, col);
    }

    private static String celda(Row r, int col) {
        if (r.getCell(col) == null) return "";
        return switch (r.getCell(col).getCellType()) {
            case STRING -> r.getCell(col).getStringCellValue();
            case NUMERIC -> String.valueOf(r.getCell(col).getNumericCellValue());
            default -> "";
        };
    }
}
