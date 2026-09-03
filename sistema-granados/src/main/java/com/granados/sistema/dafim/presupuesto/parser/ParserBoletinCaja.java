package com.granados.sistema.dafim.presupuesto.parser;

import com.granados.sistema.dafim.presupuesto.dto.BoletinParseado;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria;
import com.granados.sistema.dafim.presupuesto.dto.LineaCuentaMonetaria.TipoDineroCaja;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser del reporte SICOIN GL "Boletin de Caja Consolidado Diario por
 * Cuenta Corriente" (R00815627.rpt): lee solo las filas de cuenta monetaria
 * (codigo fuente-extendido o retenciones de 3 digitos) con sus 4 importes.
 * Las filas de cuenta fisica / CES / TOTAL se descartan para no duplicar.
 *
 * Las descripciones CODEDE pueden partirse en varias lineas; se acumulan
 * hasta completar los 4 montos (igual que el parser de ejecucion).
 */
public final class ParserBoletinCaja {

    private static final int NUM_MONTOS = 4;

    /** Codigo tipo fuente extendido: 21-0101-0001-0-0-1 o 31-0101-0004-329-1-2. */
    private static final Pattern RE_CODIGO_FUENTE = Pattern.compile(
            "^\\s*(\\d{2}-\\d{4}-\\d{4}(?:-\\d+)*)\\s+(.*)$");

    /** Retenciones / anticipos cortos: 118, 201, 301... */
    private static final Pattern RE_CODIGO_CORTO = Pattern.compile(
            "^\\s*(\\d{3})\\s+(\\p{L}.*)$");

    private static final Pattern RE_MONTO = Pattern.compile("-?\\d[\\d,]*\\.\\d{2}");
    private static final Pattern RE_FECHA_CORTE = Pattern.compile(
            "Fecha de:\\s*(\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_EJERCICIO = Pattern.compile(
            "Ejercicio:\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_TOTAL = Pattern.compile("^TOTAL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_CUENTA_FISICA = Pattern.compile(
            "^Cuenta\\s+Fisica\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_ENCABEZADO = Pattern.compile(
            "^(SIAF:|MUNICIPALIDAD|DEPARTAMENTO|CLASIFICACI|Usuario:|Boletin|"
                    + "CUENTA\\s|MONETARIA|DESCRIPCION|SALDO ANTERIOR|al:\\s|Pagina\\b|"
                    + "R008\\d+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ParserBoletinCaja() {}

    /**
     * Clasifica una cuenta del boletin: FUNCIONAMIENTO, INVERSION o
     * DESCONOCIDO. Orden: texto de la descripcion, luego ultimo segmento
     * del codigo (1 / 2). Cuentas cortas (118, 201, 301) quedan desconocidas.
     */
    public static TipoDineroCaja clasificarTipo(String codigo, String descripcion) {
        String cod = codigo == null ? "" : codigo.strip();
        if (cod.isEmpty() || cod.indexOf('-') < 0) {
            return TipoDineroCaja.DESCONOCIDO;
        }
        String desc = descripcion == null ? "" : descripcion.toUpperCase();
        desc = desc.replace('\u00C1', 'A').replace('\u00C9', 'E')
                .replace('\u00CD', 'I').replace('\u00D3', 'O')
                .replace('\u00DA', 'U').replace('\u00DC', 'U');
        if (desc.contains("FUNCIONAMIENTO")) {
            return TipoDineroCaja.FUNCIONAMIENTO;
        }
        if (desc.contains("INVERSION")) {
            return TipoDineroCaja.INVERSION;
        }
        int sep = cod.lastIndexOf('-');
        if (sep >= 0 && sep < cod.length() - 1) {
            String ultimo = cod.substring(sep + 1);
            if ("1".equals(ultimo)) return TipoDineroCaja.FUNCIONAMIENTO;
            if ("2".equals(ultimo)) return TipoDineroCaja.INVERSION;
        }
        return TipoDineroCaja.DESCONOCIDO;
    }

    public static BoletinParseado parsear(InputStream in) throws IOException {
        List<String> lineas = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                for (String l : stripper.getText(doc).split("\n")) lineas.add(l);
            }
        }
        return parsearLineas(lineas);
    }

    /** Separado para probarse con texto plano sin un PDF real. */
    public static BoletinParseado parsearLineas(List<String> lineas) {
        BoletinParseado boletin = new BoletinParseado();
        List<LineaCuentaMonetaria> cuentas = new ArrayList<>();

        for (int i = 0; i < lineas.size(); i++) {
            String ln = lineas.get(i).strip();
            if (ln.isEmpty()) continue;

            Matcher mfecha = RE_FECHA_CORTE.matcher(ln);
            if (mfecha.find() && boletin.getFechaCorte() == null) {
                boletin.setFechaCorte(LocalDate.parse(mfecha.group(1), FMT_FECHA));
                if (boletin.getAnio() == 0) {
                    boletin.setAnio(boletin.getFechaCorte().getYear());
                }
            }
            Matcher mej = RE_EJERCICIO.matcher(ln);
            if (mej.find()) {
                boletin.setAnio(Integer.parseInt(mej.group(1)));
                continue;
            }
            if (RE_ENCABEZADO.matcher(ln).lookingAt()) continue;
            if (RE_CUENTA_FISICA.matcher(ln).lookingAt()) continue;
            if (RE_TOTAL.matcher(ln).lookingAt()) continue;
            // CES / cuentas fisicas pegadas: empiezan con digito suelto o sin
            // el patron fuente / corto con letra
            if (ln.contains("CES_") || ln.matches("^\\d-\\d.*") || ln.matches("^\\d{7,}.*")) {
                continue;
            }

            Matcher mFuente = RE_CODIGO_FUENTE.matcher(ln);
            Matcher mCorto = RE_CODIGO_CORTO.matcher(ln);
            String codigo = null;
            StringBuilder resto = null;
            if (mFuente.matches()) {
                codigo = mFuente.group(1);
                resto = new StringBuilder(mFuente.group(2));
            } else if (mCorto.matches()) {
                codigo = mCorto.group(1);
                resto = new StringBuilder(mCorto.group(2));
            }
            if (codigo == null) continue;

            while (contarMontos(resto) < NUM_MONTOS && i + 1 < lineas.size()) {
                i++;
                String siguiente = lineas.get(i).strip();
                if (siguiente.isEmpty()) continue;
                // no consumir el ancla de la siguiente cuenta
                if (RE_CODIGO_FUENTE.matcher(siguiente).matches()
                        || RE_CODIGO_CORTO.matcher(siguiente).matches()
                        || RE_CUENTA_FISICA.matcher(siguiente).lookingAt()
                        || RE_TOTAL.matcher(siguiente).lookingAt()
                        || RE_ENCABEZADO.matcher(siguiente).lookingAt()) {
                    i--;
                    break;
                }
                resto.append(' ').append(siguiente);
            }
            if (contarMontos(resto) < NUM_MONTOS) {
                throw new IllegalStateException(
                        "Cuenta monetaria sin 4 importes: " + codigo + " " + resto);
            }
            cuentas.add(construirCuenta(codigo, resto.toString()));
        }

        boletin.setCuentas(cuentas);
        BigDecimal total = BigDecimal.ZERO;
        for (LineaCuentaMonetaria c : cuentas) {
            total = total.add(c.getNuevoSaldo());
        }
        boletin.setTotalNuevoSaldo(total);
        if (boletin.getAnio() == 0 && boletin.getFechaCorte() != null) {
            boletin.setAnio(boletin.getFechaCorte().getYear());
        }
        return boletin;
    }

    private static LineaCuentaMonetaria construirCuenta(String codigo, String resto) {
        List<BigDecimal> montos = new ArrayList<>();
        Matcher m = RE_MONTO.matcher(resto);
        int primerMonto = -1;
        while (m.find()) {
            if (primerMonto < 0) primerMonto = m.start();
            montos.add(new BigDecimal(m.group().replace(",", "")));
        }
        // si la descripcion trae numeros de proyecto, pueden sobrar montos:
        // se toman los ultimos 4 (saldo ant, credito, debito, nuevo)
        if (montos.size() < NUM_MONTOS) {
            throw new IllegalStateException("Cuenta monetaria con " + montos.size()
                    + " importes (se esperaban " + NUM_MONTOS + "): " + codigo + " " + resto);
        }
        int offset = montos.size() - NUM_MONTOS;
        String desc = primerMonto >= 0 ? resto.substring(0, primerMonto).strip() : resto.strip();
        LineaCuentaMonetaria l = new LineaCuentaMonetaria();
        l.setCodigo(codigo);
        l.setDescripcion(desc);
        l.setSaldoAnterior(montos.get(offset));
        l.setMontoCredito(montos.get(offset + 1));
        l.setMontoDebito(montos.get(offset + 2));
        l.setNuevoSaldo(montos.get(offset + 3));
        l.setTipo(clasificarTipo(codigo, desc));
        return l;
    }

    private static int contarMontos(CharSequence texto) {
        int n = 0;
        Matcher m = RE_MONTO.matcher(texto);
        while (m.find()) n++;
        return n;
    }
}
