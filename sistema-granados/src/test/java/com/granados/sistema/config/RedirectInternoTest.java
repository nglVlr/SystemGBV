package com.granados.sistema.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedirectInternoTest {

    @Test
    void sinRefererVuelveAlInicio() {
        assertEquals("/", RedirectInterno.ruta(null, "localhost"));
        assertEquals("/", RedirectInterno.ruta("  ", "localhost"));
    }

    @Test
    void rutaRelativaSeConservaConQuery() {
        assertEquals("/dafim/compras/procesar",
                RedirectInterno.ruta("/dafim/compras/procesar", "localhost"));
        assertEquals("/login?error",
                RedirectInterno.ruta("/login?error", "localhost"));
    }

    @Test
    void mismoHostAbsolutoSeReduceARuta() {
        assertEquals("/dafim/remuneraciones/procesar",
                RedirectInterno.ruta(
                        "http://localhost:8085/dafim/remuneraciones/procesar",
                        "localhost"));
        assertEquals("/rrhh/empleados?q=ana",
                RedirectInterno.ruta(
                        "https://LOCALHOST/rrhh/empleados?q=ana",
                        "localhost"));
    }

    @Test
    void hostAjenoOProtocoloRelativoVuelveAlInicio() {
        assertEquals("/", RedirectInterno.ruta("https://evil.example/dafim", "localhost"));
        assertEquals("/", RedirectInterno.ruta("//evil.example/dafim", "localhost"));
        assertEquals("/", RedirectInterno.ruta("javascript:alert(1)", "localhost"));
        assertEquals("/", RedirectInterno.ruta("dafim/compras", "localhost"));
        assertEquals("/", RedirectInterno.ruta("http://localhost.evil.example/", "localhost"));
    }
}
