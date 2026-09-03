package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.util.ContratoUtil;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Porteo EXACTO de parsear_pdf(): reporte de presupuesto SICOIN.
 *
 * El PDF viene por proveedor: una linea de encabezado con nombre y NIT, y
 * debajo una linea por transaccion con renglon, estado (IMPRESO/ANULADO/
 * CREADO), numero de CHEQUE, fecha y monto. La descripcion puede continuar
 * en las lineas siguientes (se van concatenando hasta 350 caracteres).
 *
 * NOTA DE PORTEO: el Python usaba pdfplumber; aqui PDFBox con
 * setSortByPosition(true), que produce el texto linea por linea en el
 * mismo orden visual. Si un PDF real llegara a extraerse con espaciado
 * distinto, ajustar las regex manteniendo la misma logica.
 */
public final class ParserSicoin {

    private static final Pattern RE_NIT = Pattern.compile(
            "^(.{3,90}?)\\s+NIT:\\s*(E?\\d+K?)\\s*$");
    private static final Pattern RE_REG = Pattern.compile(
            "^(\\d{2})\\s(\\d{2})\\s(\\d{3})\\s(\\d{3})\\s(\\d{3})\\s(\\d{3})\\s+(\\d+)\\s*"
                    + "(IMPRESO|ANULADO|CREADO)\\s+.*?(\\d{5})\\s+(\\d{2}/\\d{2}/\\d{4})\\s+"
                    + "(-?[\\d,]+\\.\\d{2})\\s*(.*)$");
    /**
     * PDFBox (sortByPosition) puede mezclar en una sola linea fisica la
     * fuente de financiamiento (columna izquierda) con la continuacion de
     * la descripcion (columna derecha): "22-0101-0001 00 TRABAJADOR ...".
     * pdfplumber las entregaba en lineas separadas. Este patron detecta la
     * fuente como PREFIJO para recortarla y conservar el texto que sigue.
     */
    private static final Pattern RE_FUENTE_PREFIJO = Pattern.compile(
            "^\\d{2}-\\d{4}-\\d{4}\\s+\\d{2}\\b\\s*");
    private static final Pattern RE_TOTAL = Pattern.compile(
            "^Total por proveedor", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_RUIDO = Pattern.compile(
            "^(SIAF:|MUNICIPALIDAD DE GRANADOS Fecha|MUNICIPALIDAD DE GRANADOS$|"
                    + "DEPARTAMENTO DE |CLASIFICACI|Usuario:|DETALLE DE|"
                    + "Ejercicio:|Nro Tipo|Estructura|Transac\\.|P\u00e1gina|Pagina|R008)",
            Pattern.CASE_INSENSITIVE);
    /** Continuacion de descripcion que PDFBox parte en "035 QUE REALIZA...". */
    private static final Pattern RE_ENVUELTO_RENGLON = Pattern.compile(
            "^\\d{3}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]");
    private static final Pattern RE_PREFIJO_DESC = Pattern.compile(
            "^(Pago de|PAGO DE LA PLANILLA|PAGO DE)\\s*");
    private static final Pattern RE_INICIA_DIGITO = Pattern.compile("^\\d");
    private static final Pattern RE_LINEA_CONTRATO = Pattern.compile("^\\d{1,3}\\-\\d{4}\\b");
    private static final Pattern RE_ESPACIOS = Pattern.compile("\\s+");

    private ParserSicoin() {}

    public static List<RegistroSicoin> parsear(InputStream in) throws IOException {
        List<String> lineas = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String t = stripper.getText(doc);
                for (String l : t.split("\n")) lineas.add(l);
            }
        }
        return parsearLineas(lineas);
    }

    /** Separado para poder probarse con texto plano sin un PDF real. */
    public static List<RegistroSicoin> parsearLineas(List<String> lineas) {
        List<RegistroSicoin> registros = new ArrayList<>();
        String nitActual = "";
        String nombreActual = "";
        RegistroSicoin regAbierto = null;

        for (String lnRaw : lineas) {
            String ln = lnRaw.strip();
            if (ln.isEmpty()) continue;

            Matcher mnit = RE_NIT.matcher(ln);
            if (mnit.lookingAt() && ln.contains("NIT:")) {
                regAbierto = null;
                nombreActual = mnit.group(1).strip();
                String nitRaw = mnit.group(2);
                nitActual = nitRaw.startsWith("E")
                        ? "E" + nitRaw.substring(1)
                        : nitRaw.toUpperCase().replace("K", "");
                continue;
            }

            Matcher mreg = RE_REG.matcher(ln);
            if (mreg.lookingAt()) {
                String renglon = mreg.group(6);
                String status = mreg.group(8);
                String cheque = mreg.group(9);
                String fecha = mreg.group(10);
                double monto = Double.parseDouble(mreg.group(11).replace(",", ""));
                String resto = mreg.group(12).strip();
                String desc = RE_PREFIJO_DESC.matcher(resto).replaceFirst("").strip();

                RegistroSicoin reg = new RegistroSicoin();
                reg.setCheque(cheque);
                reg.setNit(nitActual);
                reg.setNombre(nombreActual);
                reg.setRenglon(renglon);
                reg.setMonto(ValidadorComprasService.round2(monto));
                reg.setDesc(desc);
                reg.setContrato("N/A");
                reg.setStatus(status);
                reg.setFecha(fecha);
                registros.add(reg);
                regAbierto = reg;
                continue;
            }

            if (RE_TOTAL.matcher(ln).lookingAt()) {
                regAbierto = null;
                continue;
            }
            if (RE_RUIDO.matcher(ln).lookingAt()) {
                continue;
            }
            Matcher mfu = RE_FUENTE_PREFIJO.matcher(ln);
            if (mfu.lookingAt()) {
                String resto = ln.substring(mfu.end()).strip();
                if (resto.isEmpty()) continue; // linea de fuente sola (caso pdfplumber)
                ln = resto; // fuente + texto en la misma linea (caso PDFBox)
            }
            if (regAbierto != null) {
                boolean esTexto = !RE_INICIA_DIGITO.matcher(ln).lookingAt();
                boolean esContrato = RE_LINEA_CONTRATO.matcher(ln).lookingAt();
                boolean esRenglonEnvuelto = RE_ENVUELTO_RENGLON.matcher(ln).lookingAt();
                if ((esTexto || esContrato || esRenglonEnvuelto)
                        && regAbierto.getDesc().length() < 350) {
                    regAbierto.setDesc(regAbierto.getDesc() + " " + ln);
                }
            }
        }

        for (RegistroSicoin r : registros) {
            String desc = RE_ESPACIOS.matcher(r.getDesc()).replaceAll(" ").strip();
            r.setDesc(desc);
            String c = ContratoUtil.extraerContrato(desc);
            if (!c.isEmpty()) r.setContrato(c);
        }
        return registros;
    }
}
