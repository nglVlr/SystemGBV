package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.PersonaRemuneracion;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Porteo de parsear_remuneraciones(): planilla de remuneraciones del mes.
 * Toma solo las filas cuyo renglon (columna 1) es 29; nombre en columna 2,
 * cargo en columna 3, monto en columna 6.
 */
public final class ParserRemuneraciones {

    private ParserRemuneraciones() {}

    public static List<PersonaRemuneracion> parsear(InputStream in) throws IOException {
        List<PersonaRemuneracion> personas = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet hoja = wb.getSheetAt(0);
            for (Row row : hoja) {
                try {
                    String c1 = CeldaUtil.texto(row, 1);
                    int reng = (int) Double.parseDouble(c1);
                    if (reng != 29) continue;
                    String nombre = CeldaUtil.notna(row, 2)
                            ? CeldaUtil.texto(row, 2).trim() : "";
                    double monto = CeldaUtil.notna(row, 6)
                            ? CeldaUtil.numero(row, 6, 0.0) : 0.0;
                    String cargo = CeldaUtil.notna(row, 3)
                            ? CeldaUtil.texto(row, 3).trim() : "";
                    if (!nombre.isEmpty() && !"nan".equals(nombre) && monto > 0) {
                        PersonaRemuneracion p = new PersonaRemuneracion();
                        p.setNombre(nombre);
                        p.setMonto(ValidadorComprasService.round2(monto));
                        p.setCargo(cargo);
                        personas.add(p);
                    }
                } catch (RuntimeException ignorada) {
                    // filas de titulo o vacias: igual que el try/except del Python
                }
            }
        }
        return personas;
    }
}
