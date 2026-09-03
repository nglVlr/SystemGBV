package com.granados.sistema.dafim.remuneraciones.web;

import com.granados.sistema.config.ExclusiveJobs;
import com.granados.sistema.config.GlobalModelAdvice;
import com.granados.sistema.config.StorageService;
import com.granados.sistema.rrhh.service.RrhhService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RemuneracionesController.class)
@Import(GlobalModelAdvice.class)
class RemuneracionesViewTest {

    @Autowired
    MockMvc mvc;

    @MockBean StorageService storage;
    @MockBean ExclusiveJobs jobs;
    @MockBean RrhhService rrhh;

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void formularioPideSicoinYPlanillas() throws Exception {
        mvc.perform(get("/dafim/remuneraciones/procesar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SICOIN")))
                .andExpect(content().string(containsString("Planilla 011")))
                .andExpect(content().string(containsString("Planilla 022")));
    }
}
