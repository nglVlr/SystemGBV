package com.granados.sistema.dafim.presupuesto.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PresupuestoFuentesIT {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void fuentesNoDa500ConDatosReales() throws Exception {
        mvc.perform(get("/dafim/presupuesto/fuentes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void detalleFuenteNoDa500ConDatosReales() throws Exception {
        mvc.perform(get("/dafim/presupuesto/fuentes/{codigo}", "31-0151-0001"))
                .andExpect(status().isOk());
    }
}
