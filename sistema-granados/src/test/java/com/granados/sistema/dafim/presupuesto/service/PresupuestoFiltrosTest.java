package com.granados.sistema.dafim.presupuesto.service;

import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.BusquedaPago;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.FuenteResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.LineaFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.RenglonResumen;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresupuestoFiltrosTest {

    private static RenglonResumen renglon(String codigo, String desc, String saldo, double pct) {
        return new RenglonResumen(codigo, desc, new BigDecimal("1000"), new BigDecimal("400"),
                new BigDecimal("300"), new BigDecimal(saldo), pct,
                BigDecimal.ZERO, new BigDecimal("300"));
    }

    private static FuenteResumen fuente(String codigo, String nombre, String saldo, String real) {
        return new FuenteResumen(codigo, nombre, new BigDecimal("1000"), new BigDecimal("200"),
                new BigDecimal("100"), new BigDecimal(saldo), 20.0, new BigDecimal(real));
    }

    private static LineaFuente linea(String fuente, String saldo, boolean pres, boolean banco) {
        return new LineaFuente(fuente, "", "01 ACTIVIDADES", "", "",
                new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal(saldo),
                pres, new BigDecimal(banco ? "500" : "0"), banco);
    }

    private static Apartado apartado(String concepto, String renglon, String fuente,
                                     String usuario, String banco, LocalDateTime fecha) {
        Apartado a = new Apartado();
        a.setConcepto(concepto);
        a.setRenglon(renglon);
        a.setFuente(fuente);
        a.setUsuario(usuario);
        a.setMontoPresupuesto(new BigDecimal("100"));
        a.setMontoBanco(new BigDecimal(banco));
        a.setFecha(fecha);
        a.setEstado(Apartado.EST_ACTIVO);
        return a;
    }

    @Test
    void renglonesFiltraPorTextoIgnorandoTildes() {
        List<RenglonResumen> lista = List.of(
                renglon("154", "ARRENDAMIENTO", "80", 10),
                renglon("274", "CEMENTO", "200", 50));
        List<RenglonResumen> out = PresupuestoFiltros.filtrarRenglones(
                lista, "arrendamiento", null, null, null, null, null);
        assertEquals(List.of("154"), out.stream().map(RenglonResumen::getRenglon).toList());
    }

    @Test
    void renglonesRangoSaldoYSoloConSaldoYEjecucion() {
        List<RenglonResumen> lista = List.of(
                renglon("111", "A", "0", 95),
                renglon("154", "B", "50", 80),
                renglon("274", "C", "200", 40));
        List<RenglonResumen> conSaldo = PresupuestoFiltros.filtrarRenglones(
                lista, null, new BigDecimal("40"), new BigDecimal("80"),
                "70-90", "con", "codigo");
        assertEquals(List.of("154"), conSaldo.stream().map(RenglonResumen::getRenglon).toList());
    }

    @Test
    void renglonesOrdenSaldoDescPorDefecto() {
        List<RenglonResumen> lista = List.of(
                renglon("111", "A", "10", 10),
                renglon("274", "C", "200", 40),
                renglon("154", "B", "50", 80));
        List<RenglonResumen> out = PresupuestoFiltros.filtrarRenglones(
                lista, null, null, null, null, null, null);
        assertEquals(List.of("274", "154", "111"),
                out.stream().map(RenglonResumen::getRenglon).toList());
    }

    @Test
    void renglonesAgotadasYSumaSaldoFiltrado() {
        List<RenglonResumen> lista = List.of(
                renglon("111", "A", "0", 100),
                renglon("154", "B", "50", 10));
        List<RenglonResumen> agotadas = PresupuestoFiltros.filtrarRenglones(
                lista, null, null, null, null, "agotadas", "codigo");
        assertEquals(1, agotadas.size());
        assertEquals("111", agotadas.get(0).getRenglon());
        assertEquals(0, new BigDecimal("50").compareTo(
                PresupuestoFiltros.sumarSaldoRenglones(lista)));
    }

    @Test
    void fuentesFiltraNombreYDineroReal() {
        List<FuenteResumen> lista = List.of(
                fuente("21-0101-0001", "FUNCIONAMIENTO", "100", "80"),
                fuente("31-0151-0001", "IVA PAZ", "0", "0"));
        List<FuenteResumen> conDinero = PresupuestoFiltros.filtrarFuentes(
                lista, "paz", null, "real", "saldo");
        assertTrue(conDinero.isEmpty());
        List<FuenteResumen> cero = PresupuestoFiltros.filtrarFuentes(
                lista, "31", "agotadas", "cero", "codigo");
        assertEquals(1, cero.size());
        assertEquals("31-0151-0001", cero.get(0).getCodigo());
    }

    @Test
    void fuentesOrdenPorDineroReal() {
        List<FuenteResumen> lista = List.of(
                fuente("A", "", "9", "1"),
                fuente("B", "", "1", "50"));
        List<FuenteResumen> out = PresupuestoFiltros.filtrarFuentes(
                lista, null, null, null, "real");
        assertEquals("B", out.get(0).getCodigo());
    }

    @Test
    void dondePagarNoCambiaMatcherSoloOrdenaYFiltraLineas() {
        BusquedaPago bloque = new BusquedaPago("274", "CEMENTO", new BigDecimal("300"), List.of(
                linea("AA", "10", false, false),
                linea("BB", "80", true, false),
                linea("CC", "50", true, true)));
        List<BusquedaPago> out = PresupuestoFiltros.aplicarVistaDondePagar(
                List.of(bloque), false, false, null, null);
        assertEquals(1, out.size());
        assertEquals(0, new BigDecimal("300").compareTo(out.get(0).getTotalDisponible()));
        assertEquals(List.of("CC", "BB", "AA"),
                out.get(0).getLineas().stream().map(LineaFuente::getFuente).toList());
    }

    @Test
    void dondePagarSoloDondeAlcanzaOcultaLineasSinTocarTotal() {
        BusquedaPago bloque = new BusquedaPago("274", "CEMENTO", new BigDecimal("300"), List.of(
                linea("AA", "10", false, false),
                linea("BB", "80", true, true)));
        List<BusquedaPago> out = PresupuestoFiltros.aplicarVistaDondePagar(
                List.of(bloque), true, true, null, null);
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).getLineas().size());
        assertEquals("BB", out.get(0).getLineas().get(0).getFuente());
        assertEquals(0, new BigDecimal("300").compareTo(out.get(0).getTotalDisponible()));
    }

    @Test
    void atajosSoloLosQueExistenEnLaCarga() {
        List<RenglonResumen> carga = List.of(
                renglon("029", "PAPEL", "1", 1),
                renglon("999", "OTRO", "1", 1));
        assertEquals(List.of("029"), PresupuestoFiltros.atajosRenglon(carga));
    }

    @Test
    void apartadosFiltraConceptoFuenteBancoYFecha() {
        LocalDateTime d1 = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime d2 = LocalDateTime.of(2026, 8, 10, 10, 0);
        List<Apartado> lista = List.of(
                apartado("Cemento plaza", "274", "21-0101-0001", "ana", "0", d1),
                apartado("Dietas", "133", "31-0151-0001", "beto", "50", d2));
        List<Apartado> out = PresupuestoFiltros.filtrarApartados(
                lista, "cemento", "pres", "21-0101-0001",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));
        assertEquals(1, out.size());
        assertEquals("Cemento plaza", out.get(0).getConcepto());
        assertEquals(List.of("21-0101-0001", "31-0151-0001"),
                PresupuestoFiltros.fuentesEnApartados(lista));
    }

    @Test
    void agrupaApartadosPorFuenteOPorDiaSinCambiarDatos() {
        LocalDateTime d1 = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime d2 = LocalDateTime.of(2026, 8, 1, 18, 0);
        LocalDateTime d3 = LocalDateTime.of(2026, 8, 10, 10, 0);
        List<Apartado> lista = List.of(
                apartado("A", "274", "21-0101-0001", "ana", "0", d1),
                apartado("B", "154", "21-0101-0001", "ana", "0", d2),
                apartado("C", "133", "31-0151-0001", "beto", "50", d3));
        var porFuente = PresupuestoFiltros.agruparApartados(lista, "fuente");
        assertEquals(2, porFuente.size());
        assertEquals(2, porFuente.get("21-0101-0001").size());
        var porDia = PresupuestoFiltros.agruparApartados(lista, "dia");
        assertEquals(2, porDia.size());
        assertEquals(2, porDia.get("01/08/2026").size());
        assertEquals("A", lista.get(0).getConcepto());
    }
}
