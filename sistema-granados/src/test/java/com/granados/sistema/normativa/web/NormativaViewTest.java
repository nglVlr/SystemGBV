package com.granados.sistema.normativa.web;

import com.granados.sistema.config.GlobalModelAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NormativaController.class)
@Import(GlobalModelAdvice.class)
class NormativaViewTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void laipRenderizaDecretoGuatemalaYNoMezclaOtrasLeyes() throws Exception {
        mvc.perform(get("/normativa/laip"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Decreto 57-2008")))
                .andExpect(content().string(containsString("numeral 11")))
                .andExpect(content().string(not(containsString("Decreto 12-2002"))))
                .andExpect(content().string(not(containsString("Decreto 57-92"))))
                .andExpect(content().string(not(containsString("Decreto 1441"))))
                .andExpect(content().string(not(containsString("Decreto 534"))));
    }

    @Test
    @WithMockUser(username = "admin_rrhh", roles = "ADMIN_RRHH")
    void trabajoEsAccesibleParaRrhhYNoMeteLaipDeOficio() throws Exception {
        mvc.perform(get("/normativa/trabajo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Decreto 1441")))
                .andExpect(content().string(not(containsString("articulo 10, numeral 11"))));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void indiceSeparaLasLeyesYNoSaleEnElMenuGrande() throws Exception {
        mvc.perform(get("/normativa"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Decreto 57-2008")))
                .andExpect(content().string(containsString("Decreto 12-2002")))
                .andExpect(content().string(containsString("leyes-discreto")))
                .andExpect(content().string(not(containsString("menu-seccion\">Normativa"))));
    }
}
