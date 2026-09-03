package com.granados.sistema.web;

import com.granados.sistema.config.GlobalModelAdvice;
import com.granados.sistema.config.SecurityConfig;
import com.granados.sistema.dafim.compras.service.GestionBaseDatosComprasService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import com.granados.sistema.usuarios.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import({GlobalModelAdvice.class, SecurityConfig.class})
@WithMockUser(username = "admin", roles = "SUPERADMIN")
class PantallasInicioViewTest {

    @Autowired
    MockMvc mvc;

    @MockBean UsuarioService usuarios;
    @MockBean PresupuestoService presupuesto;
    @MockBean GestionBaseDatosComprasService compras;

    @Test
    void loginTieneEtiquetasYEnlaceParaSaltar() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("saltar-contenido")))
                .andExpect(content().string(containsString("for=\"username\"")))
                .andExpect(content().string(containsString("for=\"password\"")))
                .andExpect(content().string(containsString("id=\"formularioLogin\"")))
                .andExpect(content().string(containsString("cursorSeguidor")))
                .andExpect(content().string(containsString("v=20260902e")))
                .andExpect(content().string(not(containsString(">Dinero</span>"))));
    }

    @Test
    void dashboardModulosUsanContenedorValido() throws Exception {
        when(usuarios.porUsername("admin")).thenReturn(Optional.empty());
        when(presupuesto.resumenGeneral()).thenReturn(Optional.empty());
        when(compras.estadisticasDashboard()).thenReturn(Map.of());

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mesa-mando")))
                .andExpect(content().string(containsString("class=\"modulo-cuerpo\"")))
                .andExpect(content().string(not(containsString("<span class=\"modulo-cuerpo\""))))
                .andExpect(content().string(containsString("<div class=\"modulo-cuerpo\">")))
                .andExpect(content().string(containsString("Compras directas")))
                .andExpect(content().string(containsString("modulo-icono")))
                .andExpect(content().string(containsString("Presupuesto")))
                .andExpect(content().string(containsString("Normativa")))
                .andExpect(content().string(containsString("fuentes y si alcanza")))
                .andExpect(content().string(containsString("Caja y cargas")))
                .andExpect(content().string(not(containsString("<h2>Dinero</h2>"))))
                .andExpect(content().string(not(containsString(">Dinero</span>"))));
    }
}
