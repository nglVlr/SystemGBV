package com.granados.sistema.dafim.paquetes.service;

import com.granados.sistema.dafim.paquetes.dto.FacturaSatDatos;
import com.granados.sistema.dafim.paquetes.dto.PaqueteDatos;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Casa cada linea de los paquetes con UNA factura, por monto exacto y
 * similitud de descripcion. Una factura (numero de autorizacion) solo se
 * puede usar UNA vez: ni dos veces en el mismo lote, ni si ya quedo
 * guardada en un mes anterior.
 *
 * ESTRATEGIA EN DOS FASES (esto es lo importante):
 *
 * FASE 1 - EXACTOS: solo se asignan parejas cuya descripcion normalizada
 * es IDENTICA (mismos tokens) y el monto cuadra al centavo. Cuando hay
 * varias lineas identicas y varias facturas identicas (el mismo servicio
 * repetido), se reparten una a una: factura mas antigua a la linea que
 * aparece primero.
 *
 * FASE 2 - INEQUIVOCOS: para las lineas que quedaron sin factura (por
 * typos del Excel, espacios, etc.), solo se asigna si el match es CLARO:
 * la mejor factura libre supera un umbral alto (0.80) Y le saca un margen
 * evidente (0.12) a la segunda mejor. Si dos facturas de distinto caserio
 * o distinta maquina se parecen entre si, NO se adivina: la linea queda
 * PENDIENTE para resolverla a mano con el buscador.
 *
 * Por que asi: cuando falta la factura correcta de una linea (archivo
 * corrupto, duplicado descartado, factura que no vino), un umbral bajo
 * hacia que esa linea "viuda" agarrara la factura de OTRO caserio u OTRA
 * maquina, porque todas comparten muchas palabras ("pago de X horas de
 * renta de maquinaria tipo ... para mejoramiento de carretera ...").
 * Es preferible dejar la linea pendiente que asignarla mal.
 *
 * Logica pura, sin Spring ni base de datos, para poder probarla sola.
 */
public final class EmparejadorFacturas {

    private EmparejadorFacturas() { }

    /** Similitud minima para una asignacion automatica NO exacta. */
    static final double UMBRAL_AUTOMATICO = 0.86;

    /** Ventaja minima sobre la segunda mejor candidata para no ser ambiguo. */
    static final double MARGEN_DOMINANCIA = 0.12;

    public static class Resultado {
        /** paqueteIdx -> (ordenLinea -> indice de factura en la lista original). */
        public final Map<Integer, Map<Integer, Integer>> asignaciones = new LinkedHashMap<>();
        /** Indices de facturas repetidas (misma autorizacion en el lote). */
        public final List<Integer> repetidasEnLote = new ArrayList<>();
        /** Indices de facturas cuya autorizacion ya estaba en la BD. */
        public final List<Integer> yaEnBd = new ArrayList<>();
        /** Indices de facturas validas que no casaron con ninguna linea. */
        public final List<Integer> sinPaquete = new ArrayList<>();
        /** Indices de facturas con error de lectura. */
        public final List<Integer> conError = new ArrayList<>();
    }

    /** Referencia plana a una linea, con su posicion global. */
    private static final class Ref {
        final int paqueteIdx;
        final int orden;
        final int global;
        final PaqueteDatos.Linea linea;

        Ref(int paqueteIdx, int orden, int global, PaqueteDatos.Linea linea) {
            this.paqueteIdx = paqueteIdx;
            this.orden = orden;
            this.global = global;
            this.linea = linea;
        }
    }

    /**
     * @param paquetes         paquetes leidos del Excel, en orden
     * @param facturas         facturas leidas de los PDFs, en orden de subida
     * @param autorizacionesBd autorizaciones que YA existen en la base de
     *                         datos (de este mes o de meses anteriores)
     */
    public static Resultado emparejar(List<PaqueteDatos> paquetes,
                                      List<FacturaSatDatos> facturas,
                                      Set<String> autorizacionesBd) {
        Resultado r = new Resultado();

        // 1) depurar el lote: errores, repetidas y ya guardadas
        Set<String> vistas = new HashSet<>();
        List<Integer> candidatas = new ArrayList<>();
        for (int i = 0; i < facturas.size(); i++) {
            FacturaSatDatos f = facturas.get(i);
            if (f.getError() != null && !f.getError().isEmpty()) {
                r.conError.add(i);
            } else if (!f.getAutorizacion().isEmpty()
                    && autorizacionesBd.contains(f.getAutorizacion())) {
                r.yaEnBd.add(i);
            } else if (!f.getAutorizacion().isEmpty()
                    && !vistas.add(f.getAutorizacion())) {
                r.repetidasEnLote.add(i);
            } else {
                candidatas.add(i);
            }
        }

        // aplanar las lineas en orden de aparicion
        List<Ref> lineas = new ArrayList<>();
        int global = 0;
        for (int pi = 0; pi < paquetes.size(); pi++) {
            for (PaqueteDatos.Linea l : paquetes.get(pi).getLineas()) {
                lineas.add(new Ref(pi, l.getOrden(), global++, l));
            }
            r.asignaciones.put(pi, new LinkedHashMap<>());
        }

        Set<Integer> lineasHechas = new HashSet<>();
        Set<Integer> usadas = new LinkedHashSet<>();

        // orden estable de facturas para repartir identicas: mas antigua
        // primero; a igual fecha, por nombre de archivo
        List<Integer> facturasOrdenadas = new ArrayList<>(candidatas);
        facturasOrdenadas.sort(Comparator
                .comparing((Integer fi) -> facturas.get(fi).getFechaEmision(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(fi -> facturas.get(fi).getArchivo()));

        // ---------------- FASE 1: coincidencias EXACTAS ----------------
        for (Ref ref : lineas) {
            Set<String> tl = tokens(ref.linea.getConcepto());
            if (tl.isEmpty()) continue;
            for (int fi : facturasOrdenadas) {
                if (usadas.contains(fi)) continue;
                FacturaSatDatos f = facturas.get(fi);
                if (Math.abs(f.getMonto() - ref.linea.getMonto()) > 0.005) continue;
                if (tl.equals(tokens(f.getDescripcion()))) {
                    usadas.add(fi);
                    lineasHechas.add(ref.global);
                    r.asignaciones.get(ref.paqueteIdx).put(ref.orden, fi);
                    break;
                }
            }
        }

        // ------------- FASE 2: solo matches INEQUIVOCOS -------------
        for (Ref ref : lineas) {
            if (lineasHechas.contains(ref.global)) continue;
            int mejor = -1;
            double mejorSim = -1;
            double segundaSim = -1;
            for (int fi : facturasOrdenadas) {
                if (usadas.contains(fi)) continue;
                FacturaSatDatos f = facturas.get(fi);
                if (Math.abs(f.getMonto() - ref.linea.getMonto()) > 0.005) continue;
                double sim = similitudFina(ref.linea.getConcepto(), f.getDescripcion());
                if (sim > mejorSim) {
                    segundaSim = mejorSim;
                    mejorSim = sim;
                    mejor = fi;
                } else if (sim > segundaSim) {
                    segundaSim = sim;
                }
            }
            boolean claro = mejor >= 0
                    && mejorSim >= UMBRAL_AUTOMATICO
                    && (segundaSim < 0 || mejorSim - segundaSim >= MARGEN_DOMINANCIA);
            if (claro) {
                usadas.add(mejor);
                lineasHechas.add(ref.global);
                r.asignaciones.get(ref.paqueteIdx).put(ref.orden, mejor);
            }
            // si es ambiguo o debil: la linea queda PENDIENTE a proposito
        }

        // 3) lo que sobro
        for (int fi : candidatas) {
            if (!usadas.contains(fi)) r.sinPaquete.add(fi);
        }
        return r;
    }

    // --------------------------- similitud ---------------------------

    /** Similitud Jaccard de tokens normalizados, entre 0 y 1. */
    public static double similitud(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0;
        if (ta.equals(tb)) return 1;
        int comunes = 0;
        for (String t : ta) if (tb.contains(t)) comunes++;
        int union = ta.size() + tb.size() - comunes;
        return union == 0 ? 0 : (double) comunes / union;
    }

    /**
     * Similitud para la FASE 2: igual que la Jaccard normal, pero dos
     * tokens tambien cuentan como iguales si son palabras largas (5+
     * letras, sin digitos) con distancia de edicion 1 (un typo:
     * "maqunaria" ~ "maquinaria", "Cabera" ~ "Cabrera"). Los numeros y
     * palabras cortas se exigen EXACTOS: 16 horas nunca es 18 horas, y
     * "patrol" nunca es "pipa". Nombres de lugar distintos ("Cabrera" vs
     * "Pastor") quedan lejos de la distancia 1, asi que no se confunden.
     */
    static double similitudFina(String a, String b) {
        List<String> ta = new ArrayList<>(tokens(a));
        List<String> tb = new ArrayList<>(tokens(b));
        if (ta.isEmpty() || tb.isEmpty()) return 0;
        boolean[] usadoB = new boolean[tb.size()];
        int comunes = 0;
        for (String x : ta) {
            for (int j = 0; j < tb.size(); j++) {
                if (usadoB[j]) continue;
                if (tokenEquivalente(x, tb.get(j))) {
                    usadoB[j] = true;
                    comunes++;
                    break;
                }
            }
        }
        int union = ta.size() + tb.size() - comunes;
        return union == 0 ? 0 : (double) comunes / union;
    }

    private static boolean tokenEquivalente(String a, String b) {
        if (a.equals(b)) return true;
        if (a.length() < 5 || b.length() < 5) return false;
        if (Math.abs(a.length() - b.length()) > 1) return false;
        // solo palabras puras (sin digitos): las cantidades deben ser exactas
        for (int i = 0; i < a.length(); i++) {
            if (Character.isDigit(a.charAt(i))) return false;
        }
        for (int i = 0; i < b.length(); i++) {
            if (Character.isDigit(b.charAt(i))) return false;
        }
        return distanciaEdicion(a, b) <= 1;
    }

    /** Levenshtein acotado a 2 (corta temprano). */
    private static int distanciaEdicion(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int minFila = cur[0];
            for (int j = 1; j <= m; j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + costo);
                minFila = Math.min(minFila, cur[j]);
            }
            if (minFila > 2) return 3;
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }

    static Set<String> tokens(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        String plano = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ");
        for (String t : plano.split("\\s+")) {
            // palabras vacias tipicas que no aportan a distinguir
            if (t.length() >= 2 && !t.equals("DE") && !t.equals("LA")
                    && !t.equals("EL") && !t.equals("DEL") && !t.equals("PARA")
                    && !t.equals("POR") && !t.equals("PAGO") && !t.equals("CON")
                    && !t.equals("UNA") && !t.equals("CADA")) {
                out.add(t);
            }
        }
        return out;
    }
}
