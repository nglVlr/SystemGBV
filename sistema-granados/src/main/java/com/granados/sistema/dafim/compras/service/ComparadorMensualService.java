package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.RegistroHistorial;
import com.granados.sistema.dafim.compras.util.Constantes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Porteo EXACTO de comparar_con_mes_anterior() del motor Python.
 *
 * Reporta contra el mes anterior guardado en historial_compras:
 * proveedores nuevos, proveedores que dejaron de cobrar y cambios de
 * monto en renglon 029. Los emojis y sangrias se conservan porque asi
 * lee el reporte el equipo de DAFIM desde la version de Colab.
 */
public final class ComparadorMensualService {

    private ComparadorMensualService() {}

    private static String q(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String corta(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    public static List<String> comparar(List<FilaCompra> filas,
                                        List<RegistroHistorial> historial,
                                        int anio, int mes) {
        List<String> obs = new ArrayList<>();
        int mesAnt = mes > 1 ? mes - 1 : 12;
        int anioAnt = mes > 1 ? anio : anio - 1;

        List<RegistroHistorial> prev = new ArrayList<>();
        for (RegistroHistorial h : historial) {
            if (h.getAnio() == anioAnt && h.getMes() == mesAnt) prev.add(h);
        }
        if (prev.isEmpty()) {
            obs.add("(no hay datos de " + mesAnt + "/" + anioAnt
                    + " en la BD para comparar)");
            return obs;
        }
        obs.add(String.format(Locale.US, "Comparando contra %02d/%d (%d registros):",
                mesAnt, anioAnt, prev.size()));

        Set<String> nitsPrev = new LinkedHashSet<>();
        for (RegistroHistorial h : prev) {
            if (h.getNit() != null && !h.getNit().isEmpty()) nitsPrev.add(h.getNit());
        }
        Set<String> nitsHoy = new LinkedHashSet<>();
        for (FilaCompra f : filas) {
            if (f.getNit() != null && !f.getNit().isEmpty()) nitsHoy.add(f.getNit());
        }
        Set<String> nuevos = new LinkedHashSet<>(nitsHoy);
        nuevos.removeAll(nitsPrev);
        Set<String> seFueron = new LinkedHashSet<>(nitsPrev);
        seFueron.removeAll(nitsHoy);

        if (!nuevos.isEmpty()) {
            obs.add("  \uD83C\uDD95 Proveedores nuevos este mes: " + nuevos.size());
            Map<String, String> nombres = new LinkedHashMap<>();
            for (FilaCompra f : filas) nombres.put(f.getNit(), f.getProveedor());
            int i = 0;
            for (String n : nuevos) {
                if (i++ >= 8) break;
                obs.add("     + " + corta(nombres.getOrDefault(n, ""), 45) + " (" + n + ")");
            }
            if (nuevos.size() > 8) obs.add("     ... y " + (nuevos.size() - 8) + " mas");
        }
        if (!seFueron.isEmpty()) {
            obs.add("  \uD83D\uDC4B Cobraron el mes pasado pero NO este mes: " + seFueron.size());
            Map<String, String> nombresPrev = new LinkedHashMap<>();
            for (RegistroHistorial h : prev) {
                nombresPrev.put(h.getNit() == null ? "" : h.getNit(),
                        h.getNombre() == null ? "" : h.getNombre());
            }
            int i = 0;
            for (String n : seFueron) {
                if (i++ >= 8) break;
                obs.add("     - " + corta(nombresPrev.getOrDefault(n, ""), 45) + " (" + n + ")");
            }
            if (seFueron.size() > 8) obs.add("     ... y " + (seFueron.size() - 8) + " mas");
        }

        // cambios de monto en R029
        Map<String, Double> montoPrev029 = new LinkedHashMap<>();
        for (RegistroHistorial h : prev) {
            String reng = h.getRenglon() == null ? "" : h.getRenglon();
            if ("029".equals(Constantes.zfill3(reng))) {
                montoPrev029.put(h.getNit(), h.getMonto());
            }
        }
        List<Object[]> cambios = new ArrayList<>();
        for (FilaCompra f : filas) {
            if ("029".equals(f.getRenglon()) && montoPrev029.containsKey(f.getNit())) {
                double antes = montoPrev029.get(f.getNit());
                if (Math.abs(f.getPrecio() - antes) > 0.01) {
                    cambios.add(new Object[]{f.getProveedor(), antes, f.getPrecio()});
                }
            }
        }
        if (!cambios.isEmpty()) {
            obs.add("  \uD83D\uDCB1 R029 con cambio de monto: " + cambios.size());
            for (int i = 0; i < Math.min(8, cambios.size()); i++) {
                Object[] c = cambios.get(i);
                obs.add("     " + corta((String) c[0], 35) + ": Q" + q((Double) c[1])
                        + " -> Q" + q((Double) c[2]));
            }
        }
        if (nuevos.isEmpty() && seFueron.isEmpty() && cambios.isEmpty()) {
            obs.add("  [OK] Sin diferencias relevantes");
        }
        return obs;
    }
}
