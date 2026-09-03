package com.granados.sistema.dafim.compras.service;

import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.PersonaRemuneracion;
import com.granados.sistema.dafim.compras.dto.ResultadoValidacion;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.TextoUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Porteo EXACTO de validar() y validar_remuneraciones() del motor Python.
 *
 * Los textos del reporte se conservan caracter por caracter (prefijos
 * "[OK]  ", "[ERR] ", "[OK ]  ", "[REV]  ", sangrias) porque el equipo de
 * DAFIM ya esta acostumbrado a leerlos asi desde la version de Colab.
 *
 * El redondeo replica round(x, 2) de Python: half-even sobre el valor
 * binario exacto del double (por eso BigDecimal(double) y no valueOf).
 */
public final class ValidadorComprasService {

    private ValidadorComprasService() {}

    /** round(x, 2) de Python: half-even sobre el double exacto. */
    public static double round2(double v) {
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** {:,.2f} de Python. */
    private static String q(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    /** repr de una lista Python de strings: ['a', 'b'] o []. */
    static String reprLista(List<String> xs) {
        if (xs.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(xs.get(i)).append('\'');
        }
        return sb.append(']').toString();
    }

    public static ResultadoValidacion validar(List<FilaCompra> filas, List<Cheque> cheques) {
        List<String> rep = new ArrayList<>();
        boolean[] ok = {true};

        int nCh = cheques.size();
        int nF = filas.size();
        double mCh = round2(cheques.stream().mapToDouble(Cheque::getMonto).sum());
        double mF = round2(filas.stream().mapToDouble(FilaCompra::getPrecio).sum());

        chk(rep, ok, nF == nCh,
                "Filas " + nF + " = cheques IMPRESO " + nCh,
                "Filas " + nF + " != cheques " + nCh);
        chk(rep, ok, Math.abs(mF - mCh) < 0.01,
                "Monto cuadra al centavo: Q" + q(mF),
                "Monto NO cuadra: filas Q" + q(mF) + " vs cheques Q" + q(mCh));

        // NPGs duplicados, en orden de primera aparicion (Counter de Python)
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        for (FilaCompra f : filas) {
            if (f.getNpg() != null && !f.getNpg().isEmpty()) {
                cuenta.merge(f.getNpg(), 1, Integer::sum);
            }
        }
        List<String> dups = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cuenta.entrySet()) {
            if (e.getValue() > 1) dups.add(e.getKey());
        }
        List<String> dups5 = dups.subList(0, Math.min(5, dups.size()));
        chk(rep, ok, dups.isEmpty(),
                "Sin NPGs duplicados",
                "NPGs duplicados: " + reprLista(dups5));

        long sinReng = filas.stream()
                .filter(f -> f.getRenglon() == null || f.getRenglon().isEmpty()).count();
        chk(rep, ok, sinReng == 0,
                "Todas las filas tienen renglon",
                sinReng + " sin renglon");

        long malos = filas.stream()
                .filter(f -> Constantes.RENGLONES_EXCLUIDOS.contains(f.getRenglon())).count();
        chk(rep, ok, malos == 0,
                "Sin renglones excluidos",
                malos + " con renglon excluido");

        long sinNit = filas.stream()
                .filter(f -> f.getNit() == null || f.getNit().isEmpty()).count();
        rep.add("[" + (sinNit == 0 ? "OK " : "REV") + "]  Filas sin NIT: " + sinNit);

        List<FilaCompra> r029 = filas.stream()
                .filter(f -> "029".equals(f.getRenglon())).toList();
        long sc = r029.stream()
                .filter(f -> "N/A".equals(f.getContrato()) || "".equals(f.getContrato()))
                .count();
        rep.add("[" + (sc == 0 ? "OK " : "REV") + "]  R029: " + r029.size()
                + " | sin contrato: " + sc);

        // Distribucion por renglon, ordenada por clave (sorted de Python)
        Map<String, Integer> cnt = new TreeMap<>();
        for (FilaCompra f : filas) {
            cnt.merge(f.getRenglon() == null ? "" : f.getRenglon(), 1, Integer::sum);
        }
        StringBuilder dist = new StringBuilder("       Distribucion: ");
        boolean primero = true;
        for (Map.Entry<String, Integer> e : cnt.entrySet()) {
            if (!primero) dist.append(", ");
            dist.append('R').append(e.getKey()).append(':').append(e.getValue());
            primero = false;
        }
        rep.add(dist.toString());

        long bc = filas.stream().filter(f -> "BAJA CUANTIA".equals(f.getModalidad())).count();
        rep.add("       BAJA CUANTIA: " + bc + " | CASO EXCEPCION: " + (filas.size() - bc));

        ResultadoValidacion rv = new ResultadoValidacion();
        rv.setOk(ok[0]);
        rv.setReporte(rep);
        return rv;
    }

    private static void chk(List<String> rep, boolean[] ok, boolean cond,
                            String msgOk, String msgErr) {
        rep.add((cond ? "[OK]  " : "[ERR] ") + (cond ? msgOk : msgErr));
        if (!cond) ok[0] = false;
    }

    public static List<String> validarRemuneraciones(List<FilaCompra> filas,
                                                     List<PersonaRemuneracion> personasRem) {
        List<String> obs = new ArrayList<>();
        if (personasRem == null || personasRem.isEmpty()) {
            obs.add("(sin archivo de remuneraciones - validacion omitida)");
            return obs;
        }
        List<FilaCompra> filas029 = filas.stream()
                .filter(f -> "029".equals(f.getRenglon())).toList();
        double montoFilas = round2(filas029.stream().mapToDouble(FilaCompra::getPrecio).sum());
        double montoRem = round2(personasRem.stream()
                .mapToDouble(PersonaRemuneracion::getMonto).sum());
        obs.add("R029 en Excel: " + filas029.size() + " pagos | Q" + q(montoFilas));
        obs.add("R029 en remuneraciones: " + personasRem.size()
                + " personas | Q" + q(montoRem));
        if (Math.abs(montoFilas - montoRem) < 0.01) {
            obs.add("[OK] Montos R029 cuadran con remuneraciones");
        } else {
            obs.add("[REV] Diferencia de Q" + q(Math.abs(montoFilas - montoRem))
                    + " (puede ser pago de mes atrasado o persona en otra planilla)");
        }
        // personas en remuneraciones que no aparecen en el Excel
        Set<String> nombresExcel = new LinkedHashSet<>();
        for (FilaCompra f : filas029) nombresExcel.add(TextoUtil.norm(f.getProveedor()));
        List<String> faltantes = new ArrayList<>();
        for (PersonaRemuneracion p : personasRem) {
            Set<String> pn = TextoUtil.palabras(p.getNombre());
            boolean hay = false;
            for (String ne : nombresExcel) {
                Set<String> neSet = new LinkedHashSet<>(List.of(ne.split("\\s+")));
                neSet.remove("");
                if (TextoUtil.interseccion(pn, neSet) >= 2) { hay = true; break; }
            }
            if (!hay) faltantes.add(p.getNombre());
        }
        if (!faltantes.isEmpty()) {
            obs.add("[REV] En remuneraciones pero SIN cheque este mes ("
                    + faltantes.size() + "):");
            for (int i = 0; i < Math.min(10, faltantes.size()); i++) {
                obs.add("      - " + faltantes.get(i));
            }
        } else {
            obs.add("[OK] Todas las personas de remuneraciones tienen su cheque");
        }
        return obs;
    }
}
