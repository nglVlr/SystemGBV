package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.PersonaRemuneracion;
import com.granados.sistema.dafim.compras.service.ExcelGeneradorService;
import com.granados.sistema.dafim.compras.util.FechaUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsers de Excel probados con libros generados en memoria (mismo POI que
 * usa el sistema) y viaje redondo del generador legal: generar el .xlsx y
 * releerlo con ParserMachote.
 */
class ParsersExcelTest {

    // ------------------------------ cheques ---------------------------------

    @Test
    void parserChequesFiltraImpresosYNumerosValidos() throws Exception {
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet ws = wb.createSheet("Hoja1");
            CreationHelper ch = wb.getCreationHelper();
            CellStyle fecha = wb.createCellStyle();
            fecha.setDataFormat(ch.createDataFormat().getFormat("dd/mm/yyyy"));

            escribirCheque(ws, 4, fecha, 41001, "TALLER HERMANOS LOPEZ",
                    "IMPRESO", 3500.00);
            escribirCheque(ws, 5, fecha, 41002, "ESTACION SAN JOSE",
                    "IMPRESO", 12500.50);
            escribirCheque(ws, 6, fecha, 41003, "ANULADO NO VA",
                    "ANULADO", 999.99);
            escribirCheque(ws, 7, fecha, 999, "NUMERO CORTO NO VA",
                    "IMPRESO", 10.00);
            escribirCheque(ws, 8, fecha, 41004, "minusculas tambien cuenta",
                    "impreso", 100.00);

            List<Cheque> cheques = ParserCheques.parsear(aStream(wb));
            assertEquals(3, cheques.size());
            assertEquals("41001", cheques.get(0).getCheque());
            assertEquals(3500.00, cheques.get(0).getMonto());
            assertEquals("05/06/2026", FechaUtil.fmt(cheques.get(0).getFecha()));
            assertEquals(12500.50, cheques.get(1).getMonto());
            assertEquals("41004", cheques.get(2).getCheque());
        }
    }

    private static void escribirCheque(Sheet ws, int fila, CellStyle estiloFecha,
                                       double num, String nombre, String estado,
                                       double monto) {
        Row r = ws.createRow(fila);
        r.createCell(2).setCellValue(num);
        Cell f = r.createCell(8);
        Calendar cal = new GregorianCalendar(2026, Calendar.JUNE, 5);
        f.setCellValue(cal.getTime());
        f.setCellStyle(estiloFecha);
        r.createCell(14).setCellValue(estado);
        r.createCell(15).setCellValue(nombre);
        r.createCell(25).setCellValue(monto);
    }

    // --------------------------- remuneraciones -----------------------------

    @Test
    void parserRemuneracionesSoloR029ConNombreYMonto() throws Exception {
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet ws = wb.createSheet("R029");
            escribirRem(ws, 2, 29, "MARIA JOSE GARCIA LOPEZ", "CONSERJE", 3000.00);
            escribirRem(ws, 3, 29, "PEDRO ANTONIO RAMIREZ COY", "ALBANIL", 2600.00);
            escribirRem(ws, 4, 11, "PRESUPUESTADO NO VA", "OFICIAL", 4000.00);
            escribirRem(ws, 5, 29, "", "SIN NOMBRE NO VA", 1000.00);
            escribirRem(ws, 6, 29, "MONTO CERO NO VA", "X", 0.00);

            List<PersonaRemuneracion> ps = ParserRemuneraciones.parsear(aStream(wb));
            assertEquals(2, ps.size());
            assertEquals("MARIA JOSE GARCIA LOPEZ", ps.get(0).getNombre());
            assertEquals("ALBANIL", ps.get(1).getCargo());
        }
    }

    private static void escribirRem(Sheet ws, int fila, double renglon,
                                    String nombre, String cargo, double monto) {
        Row r = ws.createRow(fila);
        r.createCell(1).setCellValue(renglon);
        r.createCell(2).setCellValue(nombre);
        r.createCell(3).setCellValue(cargo);
        r.createCell(6).setCellValue(monto);
    }

    // ------------------------------ machote ---------------------------------

    @Test
    void parserMachoteInfiereRenglonYNormaliza() throws Exception {
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet ws = wb.createSheet("COMPRAS");
            for (int i = 0; i < 6; i++) {
                ws.createRow(i).createCell(0).setCellValue("TITULO " + (i + 1));
            }
            Object[][] datos = {
                {1.0, "BAJA CUANTIA", "MANTENIMIENTO DE VEHICULO", 1.0, 3500.00,
                 "", 165.0, "TALLER LOPEZ", 1111.0, "E568000001",
                 "05/06/2026", "05/06/2026", "ACTIVA", "", "N/A"},
                {2.0, "CASO DE EXCEPCION",
                 "SERVICIOS PRESTADOS COMO: CONSERJE, SEGUN CONTRATO NO. 012-2026",
                 1.0, 3000.00, "", "", "MARIA GARCIA", "4040404", "e561111111",
                 "05/06/2026", "05/06/2026", "ACTIVA", "012-2026", "02/01/2026"},
                {3.0, "", "ALQUILER DE SILLAS Y MESAS PARA EVENTO", 1.0, 950.00,
                 "", "", "EVENTOS LA FIESTA", 5555.0, "NPGMALO",
                 "05/06/2026", "05/06/2026", "ACTIVA", 0.0, ""},
            };
            int filaExcel = 9;
            for (Object[] fila : datos) {
                Row r = ws.createRow(filaExcel++);
                for (int c = 0; c < fila.length; c++) {
                    Object v = fila[c];
                    if (v instanceof Double d) {
                        r.createCell(c).setCellValue(d);
                    } else if (v instanceof String s && !s.isEmpty()) {
                        r.createCell(c).setCellValue(s);
                    }
                }
                if (filaExcel == 10) {
                    ws.createRow(filaExcel++);
                }
            }

            List<FilaCompra> filas = ParserMachote.parsear(aStream(wb));
            assertEquals(3, filas.size());
            assertEquals("165", filas.get(0).getRenglon());
            assertEquals("N/A", filas.get(0).getContrato());
            assertEquals("029", filas.get(1).getRenglon());
            assertEquals("12-2026", filas.get(1).getContrato());
            assertEquals("E561111111", filas.get(1).getNpg());
            assertEquals("196", filas.get(2).getRenglon());
            assertEquals("", filas.get(2).getNpg());
            assertEquals("CASO DE EXCEPCION", filas.get(2).getModalidad());
        }
    }

    // ------------------- viaje redondo del generador legal ------------------

    @Test
    @Tag("xssf")
    void excelLegalIdaYVuelta(@TempDir Path tmp) throws Exception {
        FilaCompra a = new FilaCompra();
        a.setCheque("41001"); a.setModalidad("BAJA CUANTIA");
        a.setDesc("Mantenimiento de vehiculo"); a.setPrecio(3500.00);
        a.setRenglon("165"); a.setProveedor("TALLER LOPEZ");
        a.setNit("1111"); a.setNpg("E568000001");
        a.setFechaPub("05/06/2026"); a.setFechaAdj("05/06/2026");
        a.setContrato("N/A"); a.setFechaCont("N/A");

        FilaCompra b = new FilaCompra();
        b.setCheque("41003"); b.setModalidad("CASO DE EXCEPCION");
        b.setDesc("servicios COMO: CONSERJE, SEGUN CONTRATO NO. 12-2026");
        b.setPrecio(3000.00); b.setRenglon("029");
        b.setProveedor("MARIA GARCIA"); b.setNit("4040404");
        b.setNpg("E561111111"); b.setFechaPub("05/06/2026");
        b.setFechaAdj("05/06/2026"); b.setContrato("12-2026");
        b.setFechaCont("N/A");

        Path salida = tmp.resolve("informe.xlsx");
        ExcelGeneradorService.generarExcel(List.of(a, b), 6, 2026, salida);
        assertTrue(Files.size(salida) > 3000);

        try (InputStream in = Files.newInputStream(salida)) {
            List<FilaCompra> leidas = ParserMachote.parsear(in);
            assertEquals(2, leidas.size());
            assertEquals("165", leidas.get(0).getRenglon());
            assertEquals(3500.00, leidas.get(0).getPrecio());
            assertEquals("029", leidas.get(1).getRenglon());
            assertEquals("12-2026", leidas.get(1).getContrato());
            assertEquals("E561111111", leidas.get(1).getNpg());
            assertEquals("4040404", leidas.get(1).getNit());
        }
    }

    // ------------------------------- helpers --------------------------------

    private static InputStream aStream(Workbook wb) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        return new ByteArrayInputStream(bos.toByteArray());
    }
}
