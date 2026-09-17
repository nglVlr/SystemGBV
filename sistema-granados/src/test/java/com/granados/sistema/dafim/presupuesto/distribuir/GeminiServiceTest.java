package com.granados.sistema.dafim.presupuesto.distribuir;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de GeminiService con la capa HTTP mockeada: no se hace red real.
 */
@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock HttpClient http;
    @Mock HttpResponse<String> resp;

    GeminiService servicio;

    @BeforeEach
    void armar() {
        servicio = new GeminiService("key-de-prueba", "gemini-2.0-flash",
                http, Duration.ofSeconds(5));
    }

    private static FilaDistribucion fila(long id, String descripcion, String emisor) {
        return new FilaDistribucion(id, descripcion, emisor, new BigDecimal("100"));
    }

    /** Envuelve el array pedido en la estructura real de generateContent. */
    private static String jsonGemini(String textoInterno) throws Exception {
        var root = MAPPER.createObjectNode();
        root.putArray("candidates").addObject()
                .putObject("content").putArray("parts").addObject()
                .put("text", textoInterno);
        return MAPPER.writeValueAsString(root);
    }

    private void respuestaOk(String textoInterno) throws Exception {
        // doReturn: send() es generico y when(...).thenReturn(resp) no infiere String
        doReturn(resp).when(http).send(any(), any());
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn(jsonGemini(textoInterno));
    }

    @Test
    void clasificaTodasLasFilasEnUnaSolaLlamada() throws Exception {
        respuestaOk("[{\"fila\":1,\"grupo\":\"EDUCACION\",\"renglon\":\"291\",\"explicacion\":\"Utiles escolares\"},"
                + "{\"fila\":2,\"grupo\":\"INFRAESTRUCTURA\",\"renglon\":\"274\",\"explicacion\":\"Cemento\"}]");

        List<FilaDistribucion> filas = List.of(
                fila(1L, "Utiles escolares", "Libreria La Central"),
                fila(2L, "Sacos de cemento", "Ferreteria El Clavo"));
        List<FilaDistribucion> out = servicio.clasificar(filas);

        assertEquals(2, out.size());
        assertEquals("EDUCACION", out.get(0).getGrupo());
        assertEquals("291", out.get(0).getRenglon());
        assertEquals("Utiles escolares", out.get(0).getExplicacion());
        assertEquals("INFRAESTRUCTURA", out.get(1).getGrupo());
        assertEquals("274", out.get(1).getRenglon());
        verify(http, times(1)).send(any(), any());
    }

    @Test
    void aceptaLaRespuestaEnvueltaEnMarkdown() throws Exception {
        respuestaOk("```json\n[{\"fila\":1,\"grupo\":\"SALUD\",\"renglon\":\"263\",\"explicacion\":\"Medicinas\"}]\n```");

        List<FilaDistribucion> out = servicio.clasificar(
                List.of(fila(1L, "Medicamentos", "Farmacia")));

        assertEquals(1, out.size());
        assertEquals("SALUD", out.get(0).getGrupo());
        assertEquals("263", out.get(0).getRenglon());
    }

    @Test
    void normalizaGrupoConTildeYRenglonSinCeros() throws Exception {
        respuestaOk("[{\"fila\":1,\"grupo\":\"educación\",\"renglon\":\"29\",\"explicacion\":\"x\"},"
                + "{\"fila\":2,\"grupo\":\"RARO\",\"renglon\":\"abcd\",\"explicacion\":\"y\"}]");

        List<FilaDistribucion> out = servicio.clasificar(List.of(
                fila(1L, "a", "b"), fila(2L, "c", "d")));

        assertEquals("EDUCACION", out.get(0).getGrupo());
        assertEquals("029", out.get(0).getRenglon());
        assertEquals("OTROS", out.get(1).getGrupo());
        assertEquals("", out.get(1).getRenglon());
    }

    @Test
    void timeoutDevuelveVacioParaCaerAlClasificadorLocal() throws Exception {
        when(http.send(any(), any())).thenThrow(new HttpTimeoutException("request timed out"));

        List<FilaDistribucion> out = servicio.clasificar(
                List.of(fila(1L, "Cemento", "Ferreteria")));

        assertTrue(out.isEmpty());
    }

    @Test
    void httpNo200DevuelveVacio() throws Exception {
        doReturn(resp).when(http).send(any(), any());
        when(resp.statusCode()).thenReturn(503);

        List<FilaDistribucion> out = servicio.clasificar(
                List.of(fila(1L, "Cemento", "Ferreteria")));

        assertTrue(out.isEmpty());
    }

    @Test
    void jsonInvalidoDevuelveVacio() throws Exception {
        respuestaOk("esto no es json");

        List<FilaDistribucion> out = servicio.clasificar(
                List.of(fila(1L, "Cemento", "Ferreteria")));

        assertTrue(out.isEmpty());
    }

    @Test
    void sinKeyNoLlamaHttpYNiSeIntenta() {
        GeminiService sinKey = new GeminiService("", "gemini-2.0-flash", http, Duration.ofSeconds(5));

        assertFalse(sinKey.estaDisponible());
        assertTrue(sinKey.clasificar(List.of(fila(1L, "Cemento", "Ferreteria"))).isEmpty());
        verifyNoInteractions(http);
    }

    @Test
    void conKeyEstaDisponible() {
        assertTrue(servicio.estaDisponible());
    }
}
