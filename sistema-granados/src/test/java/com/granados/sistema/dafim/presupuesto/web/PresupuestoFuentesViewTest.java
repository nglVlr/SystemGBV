package com.granados.sistema.dafim.presupuesto.web;

import com.granados.sistema.config.GlobalModelAdvice;
import com.granados.sistema.dafim.presupuesto.entity.CargaCaja;
import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.DesgloseFuente;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService.FuenteResumen;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresupuestoController.class)
@Import(GlobalModelAdvice.class)
class PresupuestoFuentesViewTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PresupuestoService presupuesto;

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void fuentesRenderizaConBoletinYDineroReal() throws Exception {
        FuenteResumen fuente = new FuenteResumen(
                "21-0101-0001", "",
                new BigDecimal("1000.00"), new BigDecimal("250.00"),
                new BigDecimal("200.00"), new BigDecimal("750.00"),
                25.0, new BigDecimal("99045.91"));
        CargaCaja caja = new CargaCaja();
        caja.setFechaCorte(LocalDate.of(2026, 8, 14));
        caja.setTotalCuentas(69);
        caja.setTotalNuevoSaldo(new BigDecimal("123456.78"));

        when(presupuesto.porFuente()).thenReturn(List.of(fuente));
        when(presupuesto.cargaCajaActiva()).thenReturn(Optional.of(caja));

        mvc.perform(get("/dafim/presupuesto/fuentes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void detalleFuenteRenderizaConBoletinYDineroReal() throws Exception {
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
        CargaCaja caja = new CargaCaja();
        caja.setFechaCorte(LocalDate.of(2026, 8, 14));
        caja.setTotalCuentas(69);
        caja.setTotalNuevoSaldo(new BigDecimal("123456.78"));

        when(presupuesto.desgloseFuente(eq("21-0101-0001"), nullable(BigDecimal.class)))
                .thenReturn(Optional.of(desglose));
        when(presupuesto.cargaCajaActiva()).thenReturn(Optional.of(caja));

        mvc.perform(get("/dafim/presupuesto/fuentes/{codigo}", "21-0101-0001"))
                .andExpect(status().isOk());
    }
}
