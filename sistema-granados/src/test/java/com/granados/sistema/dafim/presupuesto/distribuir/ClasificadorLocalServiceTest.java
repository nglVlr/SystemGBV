package com.granados.sistema.dafim.presupuesto.distribuir;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasificadorLocalServiceTest {

    private final ClasificadorLocalService clasificador = new ClasificadorLocalService();

    private static FilaDistribucion fila(String descripcion, String emisor) {
        return new FilaDistribucion(1L, descripcion, emisor, new BigDecimal("100"));
    }

    // --------------------- caso educacion vs municipal ---------------------

    /**
     * Las dos descripciones tienen keywords de EDUCACION y de MUNICIPAL;
     * gana la mas larga (la mas especifica), igual que en Constantes.
     */
    @Test
    void educacionGanaAMunicipalCuandoLaKeywordEsMasLarga() {
        // "UTILES ESCOLARES" (16) le gana a "LIBRERIA" (8)
        FilaDistribucion f = clasificador.clasificar(
                fila("COMPRA DE UTILES ESCOLARES EN LIBRERIA LA CENTRAL", "LIBRERIA LA CENTRAL"));
        assertEquals("EDUCACION", f.getGrupo());
    }

    @Test
    void municipalGanaCuandoLosUtilesSonDeOficinaAunqueSeanParaEscuela() {
        // "UTILES DE OFICINA" (17) le gana a "ESCUELA" (7)
        FilaDistribucion f = clasificador.clasificar(
                fila("UTILES DE OFICINA PARA LA ESCUELA", "PAPELERA X"));
        assertEquals("MUNICIPAL", f.getGrupo());
    }

    // --------------------- normalizacion y keywords ---------------------

    @Test
    void ignoraTildesYMinusculas() {
        assertEquals("EDUCACION",
                clasificador.clasificarGrupo("útiles escolares", ""));
        FilaDistribucion f = clasificador.clasificar(
                fila("material de librería", ""));
        assertEquals("MUNICIPAL", f.getGrupo());
        assertEquals("291", f.getRenglon());
    }

    @Test
    void renglonReusaLasKeywordsDelMotorDeCompras() {
        FilaDistribucion f = clasificador.clasificar(
                fila("Pago de sacos de cemento", "Ferreteria El Clavo"));
        assertEquals("274", f.getRenglon());
        assertEquals("INFRAESTRUCTURA", f.getGrupo());
    }

    @Test
    void saludEInfraestructuraPorKeywordPropia() {
        assertEquals("SALUD",
                clasificador.clasificarGrupo("Medicamentos para el centro de salud", "Farmacia"));
        assertEquals("INFRAESTRUCTURA",
                clasificador.clasificarGrupo("Horas de renta de maquinaria", ""));
        assertEquals("SERVICIOS",
                clasificador.clasificarGrupo("Pago de energia electrica", "EEGSA"));
    }

    @Test
    void sinKeywordCaeEnOtrosYRenglonVacio() {
        FilaDistribucion f = clasificador.clasificar(
                fila("Servicio varios del mes", "Proveedor Sin Rubro"));
        assertEquals("OTROS", f.getGrupo());
        assertEquals("", f.getRenglon());
        assertTrue(f.getExplicacion().contains("OTROS"));
    }

    @Test
    void explicacionMencionaLaKeywordQuePego() {
        FilaDistribucion f = clasificador.clasificar(
                fila("Pago de sacos de cemento", ""));
        assertTrue(f.getExplicacion().contains("CEMENTO"));
        assertTrue(f.getExplicacion().contains("274"));
    }

    @Test
    void clasificarUsaTambienElEmisor() {
        FilaDistribucion f = clasificador.clasificar(
                fila("Pago del mes", "Colegio San Jose"));
        assertEquals("EDUCACION", f.getGrupo());
    }
}
