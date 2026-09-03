package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publicaciones del mes en Guatecompras (TXT exportado).
 *
 * Cada bloque empieza con el NPG (E + 8 a 10 digitos). El export 2026 trae
 * fecha con doble punto ({@code 24.ago..2026}), modalidad en la linea 6 y,
 * en casos Art. 44, una submodalidad extra antes de la descripcion.
 */
public final class ParserGuatecompras {

    private static final Pattern RE_SPLIT = Pattern.compile("(?=E\\d{8,10}\\b)");
    private static final Pattern RE_NPG = Pattern.compile("^E\\d{8,10}$");
    private static final Pattern RE_MONTO = Pattern.compile("^\\d+\\.?\\d*$");
    private static final Pattern RE_NIT = Pattern.compile("^\\d{4,12}K?$",
            Pattern.CASE_INSENSITIVE);
    /** Acepta 24.ago.2026 y el doble punto del export: 24.ago..2026 */
    private static final Pattern RE_FECHA = Pattern.compile(
            "(\\d+)\\.(\\w+)\\.+(\\d{4})");
    private static final Pattern RE_SUBMODALIDAD = Pattern.compile(
            "(?i)(art\\.?\\s*44|inciso\\s+[a-z]|sub\\s*modalidad)");

    private ParserGuatecompras() {}

    public static List<RegistroGuatecompras> parsear(String contenido) {
        List<RegistroGuatecompras> regs = new ArrayList<>();
        Set<String> vistos = new LinkedHashSet<>();
        if (contenido == null || contenido.isBlank()) return regs;
        if (contenido.startsWith("\uFEFF")) contenido = contenido.substring(1);

        String[] bloques = RE_SPLIT.split(contenido.strip());
        for (String bRaw : bloques) {
            String b = bRaw.strip();
            if (b.isEmpty()) continue;

            List<String> lines = new ArrayList<>();
            for (String l : b.split("\n")) {
                String t = l.strip();
                if (!t.isEmpty()) lines.add(t);
            }
            if (lines.size() < 6) continue;

            String npg = lines.get(0);
            if (!RE_NPG.matcher(npg).matches() || vistos.contains(npg)) continue;
            vistos.add(npg);

            double monto = 0.0;
            for (int i = lines.size() - 1; i >= 0; i--) {
                String t = lines.get(i).replace(",", "").strip()
                        .replaceFirst("(?i)^Q\\.?\\s*", "");
                if (RE_MONTO.matcher(t).matches()) {
                    monto = Double.parseDouble(t);
                    break;
                }
            }

            int idxMod = indiceModalidad(lines);
            String lineaMod = idxMod >= 0 ? lines.get(idxMod) : "";
            String modalidad = esBajaCuantia(lineaMod)
                    ? "BAJA CUANTIA" : "CASO DE EXCEPCION";

            int idxDesc = idxMod + 1;
            if (idxDesc < lines.size() && esSubmodalidad(lines.get(idxDesc))) {
                idxDesc++;
            }
            String desc = idxDesc < lines.size() ? lines.get(idxDesc) : "";

            String nit = "";
            String proveedor = "";
            for (int i = 1; i < lines.size(); i++) {
                if (RE_NIT.matcher(lines.get(i)).matches()) {
                    nit = TextoUtil.limpiarNit(lines.get(i));
                    proveedor = i + 1 < lines.size() ? lines.get(i + 1) : "";
                    break;
                }
            }

            String fecha;
            Matcher m = RE_FECHA.matcher(lines.size() > 1 ? lines.get(1) : "");
            if (m.lookingAt()) {
                String dia = m.group(1);
                if (dia.length() < 2) dia = "0" + dia;
                String mesTxt = m.group(2).toLowerCase(Locale.ROOT);
                if (mesTxt.length() > 3) mesTxt = mesTxt.substring(0, 3);
                fecha = dia + "/" + Constantes.MES_MAP.getOrDefault(mesTxt, "00")
                        + "/" + m.group(3);
            } else {
                fecha = lines.size() > 1 ? lines.get(1) : "";
            }

            RegistroGuatecompras r = new RegistroGuatecompras();
            r.setNpg(npg);
            r.setFecha(fecha);
            r.setModalidad(modalidad);
            r.setDesc(desc);
            r.setNit(nit);
            r.setProveedor(proveedor);
            r.setMonto(ValidadorComprasService.round2(monto));
            regs.add(r);
        }
        return regs;
    }

    private static boolean esBajaCuantia(String linea) {
        return linea.contains("Art.43") || linea.contains("Art. 43")
                || linea.contains("Baja Cuant") || linea.contains("Baja cuant");
    }

    private static boolean esSubmodalidad(String linea) {
        return RE_SUBMODALIDAD.matcher(linea).find();
    }

    /**
     * En el export 2026 la modalidad es la linea 6 (indice 6) si hay al menos
     * 7 lineas. En el layout Colab viejo tambien. Si el bloque es mas corto,
     * se busca la primera linea que hable de Art.43 / Art.44 / Baja cuantia.
     */
    private static int indiceModalidad(List<String> lines) {
        if (lines.size() > 6) return 6;
        for (int i = 2; i < lines.size(); i++) {
            String l = lines.get(i);
            if (esBajaCuantia(l) || l.contains("Art.44") || l.contains("Art. 44")
                    || l.contains("Excepci")) {
                return i;
            }
        }
        return Math.min(6, lines.size() - 1);
    }
}
