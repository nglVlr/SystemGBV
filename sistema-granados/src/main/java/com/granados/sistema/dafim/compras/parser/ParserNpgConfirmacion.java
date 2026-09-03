package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.NpgConfirmacion;
import com.granados.sistema.dafim.compras.util.ContratoUtil;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Porteo de parsear_pdf_npg(): PDF de confirmacion de publicacion de
 * Guatecompras (distinto al PDF de SICOIN). Extrae NPG, NIT, nombre,
 * descripcion y numero de contrato para el flujo "Cargar NPGs historicos".
 */
public final class ParserNpgConfirmacion {

    private static final Pattern RE_NPG = Pattern.compile(
            "Publicaci[o\u00f3]n\\s*\\(NPG\\)[:\\s]*([E]\\d{8,10})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_NIT = Pattern.compile(
            "Nit[:\\s]+(\\d+)\\s*[-\u2013]\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_DESC = Pattern.compile(
            "Descripci[o\u00f3]n[:\\s]+(.+?)(?:Modalidad|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RE_CON_DOC = Pattern.compile(
            "E\\d{8,10}@0*(\\d{1,3})\\s*[-\u2013]\\s*(\\d{4})");
    private static final Pattern RE_ESPACIOS = Pattern.compile("\\s+");

    // ------- respaldos para PDFs de confirmacion con OTRO formato -------
    /** Cualquier E-numero suelto (el NPG se repite en los docs asociados). */
    private static final Pattern RE_NPG_SUELTO = Pattern.compile("\\bE\\d{8,10}\\b");
    /** NIT en variantes: "NIT del proveedor: 123", "Nit. 123 - nombre", etc. */
    private static final Pattern RE_NIT_LAXO = Pattern.compile(
            "NIT\\s*(?:DEL\\s+PROVEEDOR)?\\s*[:.]?\\s*(\\d{5,12}K?)"
                    + "(?:\\s*[-\u2013]\\s*([^\\n]{3,90}))?",
            Pattern.CASE_INSENSITIVE);
    /** Descripcion hasta Sub Modalidad, Modalidad, Nit o doble salto. */
    private static final Pattern RE_DESC_LAXA = Pattern.compile(
            "Descripci[o\u00f3]n(?:\\s+del\\s+NPG)?[:\\s]+(.+?)"
                    + "(?:Sub\\s*Modalidad|Modalidad|Nit|\\n\\s*\\n|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ParserNpgConfirmacion() {}

    public static NpgConfirmacion parsear(InputStream in, String nombreArchivo) {
        NpgConfirmacion out = new NpgConfirmacion();
        out.setArchivo(nombreArchivo);
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                if (p > 1) sb.append('\n');
                sb.append(stripper.getText(doc));
            }
            String texto = sb.toString();

            Matcher mNpg = RE_NPG.matcher(texto);
            Matcher mNit = RE_NIT.matcher(texto);
            Matcher mDesc = RE_DESC.matcher(texto);

            String desc = "";
            if (mDesc.find()) {
                desc = RE_ESPACIOS.matcher(mDesc.group(1)).replaceAll(" ").strip();
            }
            out.setNpg(mNpg.find() ? mNpg.group(1).strip() : "");
            if (mNit.find()) {
                out.setNit(TextoUtil.limpiarNit(mNit.group(1)));
                out.setNombre(mNit.group(2).strip());
            } else {
                out.setNit("");
                out.setNombre("");
            }

            // ---- respaldos: el PDF de confirmacion puede venir en otro formato ----
            if (out.getNpg().isEmpty()) {
                out.setNpg(npgMasFrecuente(texto));
            }
            if (out.getNit().isEmpty()) {
                Matcher ml = RE_NIT_LAXO.matcher(texto);
                if (ml.find()) {
                    out.setNit(TextoUtil.limpiarNit(ml.group(1)));
                    if (ml.group(2) != null) out.setNombre(ml.group(2).strip());
                }
            }
            if (desc.isEmpty()) {
                Matcher md2 = RE_DESC_LAXA.matcher(texto);
                if (md2.find()) {
                    desc = RE_ESPACIOS.matcher(md2.group(1)).replaceAll(" ").strip();
                }
            }
            String contrato = ContratoUtil.extraerContrato(desc);
            if (contrato.isEmpty()) {
                Matcher mc = RE_CON_DOC.matcher(texto);
                if (mc.find()) {
                    contrato = Integer.parseInt(mc.group(1)) + "-" + mc.group(2);
                }
            }
            out.setContrato(contrato);
            out.setDesc(desc.length() > 200 ? desc.substring(0, 200) : desc);
        } catch (Exception e) {
            out.setNpg("");
            out.setNit("");
            out.setNombre("");
            out.setContrato("");
            out.setDesc("");
            out.setError("No se pudo leer el PDF de confirmacion.");
        }
        return out;
    }

    /**
     * Respaldo: cuando el PDF no dice "Publicacion (NPG)", el NPG suele ser
     * el E-numero MAS repetido del documento (aparece en el encabezado y en
     * los nombres de los documentos asociados). En caso de empate gana el
     * primero en aparecer.
     */
    private static String npgMasFrecuente(String texto) {
        java.util.LinkedHashMap<String, Integer> conteo = new java.util.LinkedHashMap<>();
        Matcher m = RE_NPG_SUELTO.matcher(texto);
        while (m.find()) conteo.merge(m.group(), 1, Integer::sum);
        String mejor = "";
        int max = 0;
        for (java.util.Map.Entry<String, Integer> e : conteo.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); mejor = e.getKey(); }
        }
        return mejor;
    }
}
