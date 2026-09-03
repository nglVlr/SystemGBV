package com.granados.sistema.dafim.presupuesto.web;

import com.granados.sistema.config.GlobalModelAdvice;
import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import com.granados.sistema.dafim.presupuesto.entity.CargaPresupuesto;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.BusquedaPago;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.DesgloseFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.FuenteResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.LineaFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.RenglonDetalle;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.RenglonResumen;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.ResumenApartados;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresupuestoController.class)
@Import(GlobalModelAdvice.class)
class PresupuestoFiltrosViewTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PresupuestoService presupuesto;

    @BeforeEach
    void cargaActiva() {
        CargaPresupuesto carga = new CargaPresupuesto();
        carga.setId(1L);
        when(presupuesto.cargaActiva()).thenReturn(Optional.of(carga));
        when(presupuesto.cargaCajaActiva()).thenReturn(Optional.empty());
        when(presupuesto.porRenglon()).thenReturn(List.of());
        when(presupuesto.porFuente()).thenReturn(List.of());
        when(presupuesto.listarApartados(any())).thenReturn(List.of());
        when(presupuesto.resumenApartados())
                .thenReturn(new ResumenApartados(0, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static RenglonResumen renglon(String codigo, String desc, String saldo, double pct) {
        return new RenglonResumen(codigo, desc, new BigDecimal("1000"), new BigDecimal("400"),
                new BigDecimal("300"), new BigDecimal(saldo), pct,
                BigDecimal.ZERO, new BigDecimal("300"));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void renglonesFiltraTextoYMuestraConteo() throws Exception {
        when(presupuesto.porRenglon()).thenReturn(List.of(
                renglon("154", "ARRENDAMIENTO", "80", 10),
                renglon("274", "CEMENTO", "200", 50)));

        mvc.perform(get("/dafim/presupuesto/renglones").param("q", "cemento"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("274")))
                .andExpect(content().string(containsString("CEMENTO")))
                .andExpect(content().string(not(containsString("ARRENDAMIENTO"))))
                .andExpect(content().string(containsString("1 renglones")))
                .andExpect(content().string(containsString("saldo filtrado Q")));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void renglonesOrdenaSaldoDescPorDefecto() throws Exception {
        when(presupuesto.porRenglon()).thenReturn(List.of(
                renglon("111", "PAPEL", "10", 10),
                renglon("274", "CEMENTO", "200", 40),
                renglon("154", "ARRIENDO", "50", 80)));

        String html = mvc.perform(get("/dafim/presupuesto/renglones"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int i274 = html.indexOf(">274<");
        int i154 = html.indexOf(">154<");
        int i111 = html.indexOf(">111<");
        org.junit.jupiter.api.Assertions.assertTrue(i274 > 0 && i274 < i154 && i154 < i111);
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void fuentesFiltraTablaPorNombre() throws Exception {
        FuenteResumen iva = new FuenteResumen(
                "31-0151-0001", "IVA PAZ",
                new BigDecimal("1000"), new BigDecimal("0"),
                new BigDecimal("0"), new BigDecimal("1000"),
                0, BigDecimal.ZERO);
        FuenteResumen func = new FuenteResumen(
                "21-0101-0001", "FUNCIONAMIENTO",
                new BigDecimal("1000"), new BigDecimal("0"),
                new BigDecimal("0"), new BigDecimal("500"),
                0, new BigDecimal("80"));
        when(presupuesto.porFuente()).thenReturn(List.of(iva, func));
        com.granados.sistema.dafim.presupuesto.entity.CargaCaja caja =
                new com.granados.sistema.dafim.presupuesto.entity.CargaCaja();
        caja.setTotalCuentas(1);
        caja.setTotalNuevoSaldo(BigDecimal.ZERO);
        when(presupuesto.cargaCajaActiva()).thenReturn(Optional.of(caja));

        mvc.perform(get("/dafim/presupuesto/fuentes").param("q", "paz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("31-0151-0001")))
                .andExpect(content().string(containsString("IVA PAZ")))
                .andExpect(content().string(not(containsString("FUNCIONAMIENTO"))))
                .andExpect(content().string(containsString("con dinero real")));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void dondePagarAtajosSoloLosQueExistenYVacioUtil() throws Exception {
        when(presupuesto.porRenglon()).thenReturn(List.of(
                renglon("274", "CEMENTO", "200", 50),
                renglon("999", "OTRO", "1", 1)));
        when(presupuesto.dondePagar(eq("xyznoexiste"), nullable(BigDecimal.class)))
                .thenReturn(List.of());

        mvc.perform(get("/dafim/presupuesto/donde-pagar").param("q", "xyznoexiste"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("0 resultados")))
                .andExpect(content().string(containsString("codigo de 3 digitos")))
                .andExpect(content().string(containsString("tilde")))
                .andExpect(content().string(containsString(">274</")))
                .andExpect(content().string(not(containsString(">154</"))))
                .andExpect(content().string(not(containsString("999"))));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void dondePagarOrdenaLineasAlcanzanAmbosPrimero() throws Exception {
        LineaFuente no = new LineaFuente("AA", "", "01", "", "",
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("10"),
                false, BigDecimal.ZERO, false);
        LineaFuente pres = new LineaFuente("BB", "", "01", "", "",
                new BigDecimal("80"), BigDecimal.ZERO, new BigDecimal("80"),
                true, BigDecimal.ZERO, false);
        LineaFuente ambos = new LineaFuente("CC", "", "01", "", "",
                new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("50"),
                true, new BigDecimal("50"), true);
        when(presupuesto.dondePagar(eq("274"), nullable(BigDecimal.class)))
                .thenReturn(List.of(new BusquedaPago("274", "CEMENTO",
                        new BigDecimal("140"), List.of(no, pres, ambos))));
        when(presupuesto.porRenglon()).thenReturn(List.of(renglon("274", "CEMENTO", "140", 10)));

        String html = mvc.perform(get("/dafim/presupuesto/donde-pagar").param("q", "274"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int cc = html.indexOf("CC");
        int bb = html.indexOf("BB");
        int aa = html.indexOf("AA");
        org.junit.jupiter.api.Assertions.assertTrue(cc > 0 && cc < bb && bb < aa);
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void apartadosBuscaYConservaAgregarBanco() throws Exception {
        Apartado a = new Apartado();
        a.setId(9L);
        a.setEstado(Apartado.EST_ACTIVO);
        a.setConcepto("Cemento plaza");
        a.setRenglon("274");
        a.setFuente("21-0101-0001");
        a.setUsuario("ana");
        a.setMontoPresupuesto(new BigDecimal("100"));
        a.setMontoBanco(BigDecimal.ZERO);
        a.setDescripcion("CEMENTO");
        when(presupuesto.listarApartados("ACTIVO")).thenReturn(List.of(a));
        when(presupuesto.resumenApartados())
                .thenReturn(new ResumenApartados(1, new BigDecimal("100"), BigDecimal.ZERO));

        mvc.perform(get("/dafim/presupuesto/apartados").param("q", "cemento"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cemento plaza")))
                .andExpect(content().string(containsString("Agregar banco")))
                .andExpect(content().string(containsString("Activos (global)")))
                .andExpect(content().string(containsString("solo presupuesto")));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void detalleRenglonTraeBuscadorLocalYPersisteQ() throws Exception {
        LineaPresupuesto linea = new LineaPresupuesto();
        linea.setFuente("21-0101-0001");
        linea.setDescripcion("CEMENTO");
        linea.setActividadObra("001");
        linea.setSaldoDisponible(new BigDecimal("50"));
        linea.setVigente(new BigDecimal("100"));
        linea.setDevengado(new BigDecimal("50"));
        linea.setPagado(new BigDecimal("40"));
        when(presupuesto.detalleRenglon("274"))
                .thenReturn(Optional.of(new RenglonDetalle(List.of(linea), List.of())));

        mvc.perform(get("/dafim/presupuesto/renglones/{codigo}", "274").param("q", "cemento"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Filtrar estas lineas")))
                .andExpect(content().string(containsString("con saldo")))
                .andExpect(content().string(containsString("q=cemento")));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void detalleFuenteTraeBuscadorLocal() throws Exception {
        LineaPresupuesto linea = new LineaPresupuesto();
        linea.setId(1L);
        linea.setRenglon("274");
        linea.setFuente("21-0101-0001");
        linea.setDescripcion("CEMENTO");
        linea.setPrograma("01 ACTIVIDADES CENTRALES");
        linea.setVigente(new BigDecimal("1000.00"));
        linea.setDevengado(new BigDecimal("250.00"));
        linea.setPagado(new BigDecimal("200.00"));
        linea.setSaldoDisponible(new BigDecimal("750.00"));
        DesgloseFuente desglose = PresupuestoService.armarDesglose(
                "21-0101-0001", "", null, List.of(linea),
                new BigDecimal("99045.91"), List.of());
        when(presupuesto.desgloseFuente(eq("21-0101-0001"), nullable(BigDecimal.class)))
                .thenReturn(Optional.of(desglose));

        mvc.perform(get("/dafim/presupuesto/fuentes/{codigo}", "21-0101-0001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Filtrar estas lineas")));
    }
}
