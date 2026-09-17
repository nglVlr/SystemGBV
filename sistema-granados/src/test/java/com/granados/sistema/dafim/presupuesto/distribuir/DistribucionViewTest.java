package com.granados.sistema.dafim.presupuesto.distribuir;

import com.granados.sistema.config.GlobalModelAdvice;
import com.granados.sistema.config.SecurityConfig;
import com.granados.sistema.dafim.presupuesto.distribuir.DistribucionService.MesAnio;
import com.granados.sistema.dafim.presupuesto.repository.FuenteFinanciamientoRepository;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vista del asistente de distribucion: el GET renderiza la hoja para el rol
 * PRESUPUESTO (con la plantilla real de Thymeleaf, para cazar errores de
 * parseo) y la seguridad de /dafim/presupuesto/** se aplica igual que en el
 * resto del modulo (otros roles fuera, sin sesion al login).
 */
@WebMvcTest(controllers = DistribucionController.class)
@Import({GlobalModelAdvice.class, SecurityConfig.class})
class DistribucionViewTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    DistribucionService distribucion;

    @MockBean
    PresupuestoService presupuesto;

    @MockBean
    FuenteFinanciamientoRepository fuentes;

    @MockBean
    GeminiService gemini;

    @BeforeEach
    void stubs() {
        when(distribucion.mesesConFacturas()).thenReturn(List.of(
                new MesAnio(2026, 9, "Septiembre 2026", 1)));
        when(distribucion.cargarFilas(anyInt(), anyInt())).thenReturn(List.of(
                new FilaDistribucion(7L, "COMPRA DE CEMENTO GRIS",
                        "FERRETERIA EL PINO", new BigDecimal("1500.00"))));
        when(presupuesto.destinosPosibles()).thenReturn(List.of());
        when(fuentes.findAllByOrderByCodigo()).thenReturn(List.of());
        when(gemini.estaDisponible()).thenReturn(false);
    }

    @Test
    @WithMockUser(username = "presu", roles = "PRESUPUESTO")
    void presupuestoEntraALaHoja() throws Exception {
        mvc.perform(get("/dafim/presupuesto/distribuir"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Distribuir pagos")))
                .andExpect(content().string(containsString("Clasificar con IA")))
                .andExpect(content().string(containsString("Septiembre 2026")))
                // Sin key de Gemini la vista avisa que clasifica con reglas locales
                .andExpect(content().string(containsString("GEMINI_API_KEY")));
    }

    @Test
    @WithMockUser(username = "rrhh", roles = "RRHH")
    void rrhhNoEntraALaHoja() throws Exception {
        mvc.perform(get("/dafim/presupuesto/distribuir"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinSesionRedirigeAlLogin() throws Exception {
        mvc.perform(get("/dafim/presupuesto/distribuir"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }
}
