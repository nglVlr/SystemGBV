package com.granados.sistema.dafim.presupuesto.web;

import com.granados.sistema.config.GlobalModelAdvice;
import com.granados.sistema.dafim.presupuesto.entity.CargaCaja;
import com.granados.sistema.dafim.presupuesto.entity.CargaPresupuesto;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresupuestoController.class)
@Import(GlobalModelAdvice.class)
class PresupuestoCargasViewTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PresupuestoService presupuesto;

    @BeforeEach
    void vacioPorDefecto() {
        when(presupuesto.historialCargas()).thenReturn(List.of());
        when(presupuesto.historialCargasCaja()).thenReturn(List.of());
        when(presupuesto.cargaActiva()).thenReturn(Optional.empty());
        when(presupuesto.cargaCajaActiva()).thenReturn(Optional.empty());
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void subirMuestraLosDosPdfsSinCodigosTecnicos() throws Exception {
        mvc.perform(get("/dafim/presupuesto/cargar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ejecucion de egresos")))
                .andExpect(content().string(containsString("Boletin de caja")))
                .andExpect(content().string(containsString("apartados")))
                .andExpect(content().string(containsString("Fuentes")))
                .andExpect(content().string(not(containsString(">Dinero</span>"))))
                .andExpect(content().string(not(containsString("R00814981"))))
                .andExpect(content().string(not(containsString("R00815627"))))
                .andExpect(content().string(not(containsString("NullPointer"))));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void historialNoMuestraEstadoCrudoReemplazada() throws Exception {
        CargaPresupuesto activa = new CargaPresupuesto();
        activa.setId(2L);
        activa.setEstado(CargaPresupuesto.EST_ACTIVA);
        activa.setNombreArchivo("nuevo.pdf");
        activa.setTotalLineas(10);
        activa.setTotalVigente(BigDecimal.ZERO);
        CargaPresupuesto vieja = new CargaPresupuesto();
        vieja.setId(1L);
        vieja.setEstado(CargaPresupuesto.EST_REEMPLAZADA);
        vieja.setNombreArchivo("viejo.pdf");
        vieja.setTotalLineas(8);
        vieja.setTotalVigente(BigDecimal.ZERO);
        when(presupuesto.historialCargas()).thenReturn(List.of(activa, vieja));

        mvc.perform(get("/dafim/presupuesto/cargas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Activa")))
                .andExpect(content().string(containsString("En historial")))
                .andExpect(content().string(not(containsString("REEMPLAZADA"))))
                .andExpect(content().string(not(containsString("R008"))));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void archivoVacioPideElPdfDeEgresosEnEspanol() throws Exception {
        MockMultipartFile vacio = new MockMultipartFile(
                "archivo", "egresos.pdf", "application/pdf", new byte[0]);

        mvc.perform(multipart("/dafim/presupuesto/cargar").file(vacio).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dafim/presupuesto/cargar"))
                .andExpect(flash().attribute("error",
                        containsString("PDF de ejecucion de egresos")));
        verify(presupuesto, never()).importarPdf(any(), any());
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void jergaDelParserNoLlegaAlAviso() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "archivo", "egresos.pdf", "application/pdf", new byte[] {37, 80, 68, 70});
        when(presupuesto.importarPdf(any(), any())).thenThrow(new IllegalStateException(
                "Fila de ejecucion con 7 importes (se esperaban 11): 011 21-0101-0001"));

        mvc.perform(multipart("/dafim/presupuesto/cargar").file(pdf).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dafim/presupuesto/cargar"))
                .andExpect(flash().attribute("error",
                        containsString("ejecucion de egresos")))
                .andExpect(flash().attribute("error",
                        not(containsString("Fila de ejecucion"))))
                .andExpect(flash().attribute("error",
                        not(containsString("R008"))));
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void excelSeRechazaSinLlamarAlParser() throws Exception {
        MockMultipartFile xlsx = new MockMultipartFile(
                "archivo", "presupuesto.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});

        mvc.perform(multipart("/dafim/presupuesto/cargar").file(xlsx).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("PDF")));
        verify(presupuesto, never()).importarPdf(any(), any());
    }

    @Test
    @WithMockUser(username = "admin_dafim", roles = "ADMIN_DAFIM")
    void cajaVaciaPideElPdfDelBoletin() throws Exception {
        MockMultipartFile vacio = new MockMultipartFile(
                "archivo", "caja.pdf", "application/pdf", new byte[0]);

        mvc.perform(multipart("/dafim/presupuesto/cargar-caja").file(vacio).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dafim/presupuesto/cargar"))
                .andExpect(flash().attribute("error",
                        containsString("boletin de caja")));
        verify(presupuesto, never()).importarBoletinCaja(any(), any());
    }
}
