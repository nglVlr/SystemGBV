package com.granados.sistema.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato de fluidez: el cursor no lee layout en cada move, y el CSS
 * promociona solo las dos capas del puntero.
 */
class FluidezFrontContractTest {

    @Test
    void cursorMunicipalNoConsultaLayoutEnPointermove() throws Exception {
        String js = recurso("/static/js/app.js");
        int ini = js.indexOf("(function cursorMunicipal()");
        int fin = js.indexOf("* 3 ·", ini);
        assertTrue(ini > 0 && fin > ini, "no se encontro el bloque del cursor municipal");
        String bloque = js.substring(ini, fin);
        assertFalse(bloque.contains("elementFromPoint"));
        assertFalse(bloque.contains("getBoundingClientRect"));
        int move = bloque.indexOf("'pointermove'");
        int over = bloque.indexOf("'pointerover'");
        assertTrue(move > 0 && over > move);
        String enMove = bloque.substring(move, over);
        assertTrue(enMove.contains("if (!encendido)"));
        assertTrue(enMove.contains("pintar(ev.clientX, ev.clientY)"));
        assertFalse(enMove.contains("getBoundingClientRect"));
        assertFalse(enMove.contains("elementFromPoint"));
        assertTrue(bloque.contains("translate3d("));
    }

    @Test
    void capasDelCursorSonCompositor() throws Exception {
        String css = recurso("/static/css/app.css");
        int will = css.indexOf("will-change: transform");
        assertTrue(will > 0);
        String around = css.substring(Math.max(0, will - 220), Math.min(css.length(), will + 180));
        assertTrue(around.contains(".cursor-seguidor"));
        assertTrue(around.contains(".cursor-punto"));
        assertTrue(around.contains("contain: layout style paint"));
        assertFalse(css.contains("elementFromPoint"));
    }

    @Test
    void fondo3dNoLeeLayoutNiMueveElEscudoCadaCuadro() throws Exception {
        String js = recurso("/static/js/fondo3d.js");
        int cuadro = js.indexOf("function cuadro(ahora)");
        assertTrue(cuadro > 0);
        String cuerpo = js.substring(cuadro, js.indexOf("canvas.classList.add('listo')", cuadro));
        assertFalse(cuerpo.contains("redimensionar()"));
        assertFalse(cuerpo.contains("getElementById('fondo-escudo')"));
        assertFalse(cuerpo.contains("getAttribLocation"));
        assertTrue(js.contains("antialias: false"));
    }

    private static String recurso(String ruta) throws Exception {
        try (var in = FluidezFrontContractTest.class.getResourceAsStream(ruta)) {
            return new String(Objects.requireNonNull(in, ruta).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
