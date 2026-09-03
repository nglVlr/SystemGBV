package com.granados.sistema.dafim.presupuesto.service;

import com.granados.sistema.dafim.compras.util.TextoUtil;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.BusquedaPago;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.FuenteResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.LineaFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.RenglonResumen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Orden y recorte de listados de presupuesto ya calculados. No recalcula
 * saldos, no toca el matcher de ¿Con que pago? ni el overlay de apartados.
 */
public final class PresupuestoFiltros {

    public static final String ORD_SALDO = "saldo";
    public static final String ORD_CODIGO = "codigo";
    public static final String ORD_EJECUCION = "ejecucion";
    public static final String ORD_REAL = "real";

    public static final String SALDO_CON = "con";
    public static final String SALDO_AGOTADAS = "agotadas";

    public static final String DINERO_REAL = "real";
    public static final String DINERO_CERO = "cero";

    public static final String BANCO_ALCANZA = "alcanza";
    public static final String BANCO_NO = "no";

    public static final String TIPO_PRES = "pres";
    public static final String TIPO_BANCO = "banco";

    public static final String GRUPO_FUENTE = "fuente";
    public static final String GRUPO_DIA = "dia";

    /** Atajos de ¿Con que pago?: solo se pintan si existen en la carga. */
    public static final List<String> ATAJOS_PREFERIDOS = List.of(
            "154", "211", "274", "029", "111", "191", "196", "241", "262", "263");

    private PresupuestoFiltros() {}

    public static String ordenRenglones(String orden) {
        String o = orden == null ? "" : orden.strip().toLowerCase(Locale.ROOT);
        if (ORD_CODIGO.equals(o) || ORD_EJECUCION.equals(o)) return o;
        return ORD_SALDO;
    }

    public static String ordenFuentes(String orden) {
        String o = orden == null ? "" : orden.strip().toLowerCase(Locale.ROOT);
        if (ORD_CODIGO.equals(o) || ORD_REAL.equals(o)) return o;
        return ORD_SALDO;
    }

    public static List<RenglonResumen> filtrarRenglones(List<RenglonResumen> lista,
                                                        String q, BigDecimal saldoMin,
                                                        BigDecimal saldoMax, String ejecucion,
                                                        String saldoChip, String orden) {
        if (lista == null) return List.of();
        String buscado = texto(q);
        String ejec = ejecucion == null ? "" : ejecucion.strip();
        String chip = chip(saldoChip);
        List<RenglonResumen> out = new ArrayList<>();
        for (RenglonResumen r : lista) {
            if (!buscado.isEmpty()) {
                String hay = TextoUtil.norm(nz(r.getRenglon()) + " " + nz(r.getDescripcion()));
                if (!hay.contains(buscado)) continue;
            }
            BigDecimal saldo = nz(r.getSaldoDisponible());
            if (saldoMin != null && saldo.compareTo(saldoMin) < 0) continue;
            if (saldoMax != null && saldo.compareTo(saldoMax) > 0) continue;
            if (!pasaSaldoChip(saldo, chip)) continue;
            if (!pasaEjecucion(r.getPctEjecucion(), ejec)) continue;
            out.add(r);
        }
        String ord = ordenRenglones(orden);
        if (ORD_CODIGO.equals(ord)) {
            out.sort(Comparator.comparing(r -> nz(r.getRenglon())));
        } else if (ORD_EJECUCION.equals(ord)) {
            out.sort(Comparator.comparingDouble(RenglonResumen::getPctEjecucion).reversed());
        } else {
            out.sort(Comparator.comparing(RenglonResumen::getSaldoDisponible,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        }
        return out;
    }

    public static BigDecimal sumarSaldoRenglones(List<RenglonResumen> lista) {
        BigDecimal total = BigDecimal.ZERO;
        if (lista == null) return total;
        for (RenglonResumen r : lista) {
            total = total.add(nz(r.getSaldoDisponible()));
        }
        return total;
    }

    public static List<FuenteResumen> filtrarFuentes(List<FuenteResumen> lista, String q,
                                                     String saldoChip, String dinero,
                                                     String orden) {
        if (lista == null) return List.of();
        String buscado = texto(q);
        String chip = chip(saldoChip);
        String din = dinero == null ? "" : dinero.strip().toLowerCase(Locale.ROOT);
        List<FuenteResumen> out = new ArrayList<>();
        for (FuenteResumen f : lista) {
            if (!buscado.isEmpty()) {
                String hay = TextoUtil.norm(nz(f.getCodigo()) + " " + nz(f.getNombre()));
                if (!hay.contains(buscado)) continue;
            }
            if (!pasaSaldoChip(nz(f.getSaldoDisponible()), chip)) continue;
            BigDecimal real = nz(f.getDineroReal());
            if (DINERO_REAL.equals(din) && real.signum() <= 0) continue;
            if (DINERO_CERO.equals(din) && real.signum() > 0) continue;
            out.add(f);
        }
        String ord = ordenFuentes(orden);
        if (ORD_CODIGO.equals(ord)) {
            out.sort(Comparator.comparing(f -> nz(f.getCodigo())));
        } else if (ORD_REAL.equals(ord)) {
            out.sort(Comparator.comparing(FuenteResumen::getDineroReal,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        } else {
            out.sort(Comparator.comparing(FuenteResumen::getSaldoDisponible,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        }
        return out;
    }

    /**
     * Recorta y reordena lineas ya devueltas por {@code dondePagar}. El
     * total del renglon se conserva (cifra de negocio).
     */
    public static List<BusquedaPago> aplicarVistaDondePagar(List<BusquedaPago> resultados,
                                                            boolean soloPresAlcanza,
                                                            boolean soloBancoAlcanza,
                                                            String saldoChip,
                                                            String bancoChip) {
        if (resultados == null) return List.of();
        String chipSaldo = chip(saldoChip);
        String chipBanco = bancoChip == null ? "" : bancoChip.strip().toLowerCase(Locale.ROOT);
        List<BusquedaPago> out = new ArrayList<>();
        for (BusquedaPago b : resultados) {
            List<LineaFuente> lineas = new ArrayList<>();
            if (b.getLineas() != null) {
                for (LineaFuente l : b.getLineas()) {
                    if (l == null) continue;
                    if (soloPresAlcanza && !l.isAlcanza()) continue;
                    if (soloBancoAlcanza && !l.isAlcanzaBanco()) continue;
                    if (!pasaSaldoChip(nz(l.getSaldoDisponible()), chipSaldo)) continue;
                    if (BANCO_ALCANZA.equals(chipBanco) && !l.isAlcanzaBanco()) continue;
                    if (BANCO_NO.equals(chipBanco) && l.isAlcanzaBanco()) continue;
                    lineas.add(l);
                }
            }
            lineas.sort((a, c) -> {
                int ra = rangoAlcanza(a);
                int rc = rangoAlcanza(c);
                if (ra != rc) return Integer.compare(ra, rc);
                return nz(c.getSaldoDisponible()).compareTo(nz(a.getSaldoDisponible()));
            });
            if (!lineas.isEmpty()) {
                out.add(new BusquedaPago(b.getRenglon(), b.getDescripcion(),
                        b.getTotalDisponible(), lineas));
            }
        }
        return out;
    }

    public static List<String> atajosRenglon(List<RenglonResumen> renglones) {
        Set<String> presentes = new LinkedHashSet<>();
        if (renglones != null) {
            for (RenglonResumen r : renglones) {
                if (r.getRenglon() != null && !r.getRenglon().isBlank()) {
                    presentes.add(r.getRenglon().strip());
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String codigo : ATAJOS_PREFERIDOS) {
            if (presentes.contains(codigo)) out.add(codigo);
        }
        return out;
    }

    public static List<Apartado> filtrarApartados(List<Apartado> lista, String q, String tipo,
                                                  String fuente, LocalDate desde, LocalDate hasta) {
        if (lista == null) return List.of();
        String buscado = texto(q);
        String tip = tipo == null ? "" : tipo.strip().toLowerCase(Locale.ROOT);
        String fu = fuente == null ? "" : fuente.strip();
        List<Apartado> out = new ArrayList<>();
        for (Apartado a : lista) {
            if (a == null) continue;
            if (!buscado.isEmpty()) {
                String hay = TextoUtil.norm(nz(a.getConcepto()) + " " + nz(a.getRenglon())
                        + " " + nz(a.getFuente()) + " " + nz(a.getUsuario()));
                if (!hay.contains(buscado)) continue;
            }
            BigDecimal banco = nz(a.getMontoBanco());
            if (TIPO_PRES.equals(tip) && banco.signum() > 0) continue;
            if (TIPO_BANCO.equals(tip) && banco.signum() <= 0) continue;
            if (!fu.isEmpty() && !fu.equals(a.getFuente() == null ? "" : a.getFuente().strip())) {
                continue;
            }
            LocalDate dia = fechaDia(a.getFecha());
            if (desde != null && (dia == null || dia.isBefore(desde))) continue;
            if (hasta != null && (dia == null || dia.isAfter(hasta))) continue;
            out.add(a);
        }
        return out;
    }

    public static String grupoApartados(String grupo) {
        return GRUPO_DIA.equalsIgnoreCase(grupo == null ? "" : grupo.strip())
                ? GRUPO_DIA : GRUPO_FUENTE;
    }

    /** Solo presenta la lista ya filtrada. No cambia montos ni estados. */
    public static Map<String, List<Apartado>> agruparApartados(List<Apartado> lista, String grupo) {
        Map<String, List<Apartado>> out = new LinkedHashMap<>();
        if (lista == null) return out;
        boolean porDia = GRUPO_DIA.equals(grupoApartados(grupo));
        for (Apartado a : lista) {
            if (a == null) continue;
            String clave;
            if (porDia) {
                LocalDate dia = fechaDia(a.getFecha());
                clave = dia == null ? "Sin fecha"
                        : String.format("%02d/%02d/%04d",
                        dia.getDayOfMonth(), dia.getMonthValue(), dia.getYear());
            } else {
                clave = a.getFuente() == null || a.getFuente().isBlank()
                        ? "(sin fuente)" : a.getFuente().strip();
            }
            out.computeIfAbsent(clave, k -> new ArrayList<>()).add(a);
        }
        return out;
    }

    public static List<String> fuentesEnApartados(List<Apartado> lista) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (lista == null) return List.of();
        for (Apartado a : lista) {
            if (a == null || a.getFuente() == null || a.getFuente().isBlank()) continue;
            out.add(a.getFuente().strip());
        }
        return new ArrayList<>(out);
    }

    private static int rangoAlcanza(LineaFuente l) {
        if (l.isAlcanza() && l.isAlcanzaBanco()) return 0;
        if (l.isAlcanza()) return 1;
        return 2;
    }

    private static boolean pasaEjecucion(double pct, String ejec) {
        if (ejec.isEmpty() || "todos".equalsIgnoreCase(ejec)) return true;
        if ("lt70".equals(ejec)) return pct < 70;
        if ("70-90".equals(ejec)) return pct >= 70 && pct <= 90;
        if ("gt90".equals(ejec)) return pct > 90;
        return true;
    }

    private static boolean pasaSaldoChip(BigDecimal saldo, String chip) {
        if (SALDO_CON.equals(chip)) return saldo.signum() > 0;
        if (SALDO_AGOTADAS.equals(chip)) return saldo.signum() <= 0;
        return true;
    }

    private static String texto(String q) {
        return q == null || q.isBlank() ? "" : TextoUtil.norm(q);
    }

    private static String chip(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static LocalDate fechaDia(LocalDateTime fecha) {
        return fecha == null ? null : fecha.toLocalDate();
    }
}
