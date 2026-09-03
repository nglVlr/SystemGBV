package com.granados.sistema.dafim.presupuesto.web;

import com.granados.sistema.config.GlobalModelAdvice;
import com.granados.sistema.config.SecurityConfig;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresupuestoController.class)
@Import({GlobalModelAdvice.class, SecurityConfig.class})
class PresupuestoSeguridadViewTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    PresupuestoService presupuesto;

    @BeforeEach
    void stubs() {
        when(presupuesto.porFuente()).thenReturn(List.of());
        when(presupuesto.cargaCajaActiva()).thenReturn(Optional.empty());
        when(presupuesto.cargaActiva()).thenReturn(Optional.empty());
        when(presupuesto.historialCargas()).thenReturn(List.of());
        when(presupuesto.historialCargasCaja()).thenReturn(List.of());
    }

    @Test
    @WithMockUser(username = "presu", roles = "PRESUPUESTO")
    void presupuestoEntraACajaFuentesYCargas() throws Exception {
        mvc.perform(get("/dafim/presupuesto/fuentes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fuentes")))
                .andExpect(content().string(not(containsString(">Dinero</span>"))));

        mvc.perform(get("/dafim/presupuesto/cargar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boletin de caja")))
                .andExpect(content().string(containsString("Caja y cargas")));
    }

    @Test
    @WithMockUser(username = "comp", roles = "COMPRAS")
    void comprasNoEntraAFuentes() throws Exception {
        mvc.perform(get("/dafim/presupuesto/fuentes"))
                .andExpect(status().isForbidden());
    }
}
