package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserGuatecomprasTest {

    private static final String TXT = String.join("\n",
            "E568000010",
            "3.jun.2026",
            "Municipalidad de Granados",
            "Compra Directa",
            "Publicado",
            "linea 5",
            "Compra Directa con Oferta Electronica (Art.43 LCE Inciso b)",
            "MANTENIMIENTO DE VEHICULO MUNICIPAL PLACA O-123",
            "linea 8",
            "1111",
            "TALLER HERMANOS LOPEZ",
            "Q. 3,500.00",
            "3,500.00",
            "",
            "E568000011",
            "15.junio.2026",
            "Municipalidad de Granados",
            "Compra Directa",
            "Publicado",
            "linea 5",
            "Caso de Excepcion (Art. 44 LCE)",
            "SERVICIOS TECNICOS DE APOYO ADMINISTRATIVO",
            "linea 8",
            "123456K",
            "ANA LUCIA MENDEZ SIS",
            "2,900.00");

    @Test
    void separaPublicacionesYExtraeCampos() {
        List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(TXT);
        assertEquals(2, regs.size());

        RegistroGuatecompras r0 = regs.get(0);
        assertEquals("E568000010", r0.getNpg());
        assertEquals("03/06/2026", r0.getFecha());
        assertEquals("BAJA CUANTIA", r0.getModalidad());
        assertEquals("1111", r0.getNit());
        assertEquals("TALLER HERMANOS LOPEZ", r0.getProveedor());
        assertEquals(3500.00, r0.getMonto());

        RegistroGuatecompras r1 = regs.get(1);
        assertEquals("15/06/2026", r1.getFecha());
        assertEquals("CASO DE EXCEPCION", r1.getModalidad());
        assertEquals("123456", r1.getNit());
        assertEquals(2900.00, r1.getMonto());
    }

    @Test
    void aceptaBomYMontoSoloConPrefijoQ() {
        String txt = "\uFEFF" + String.join("\n",
                "E568000010",
                "3.jun.2026",
                "Municipalidad de Granados",
                "Compra Directa",
                "Publicado",
                "linea 5",
                "Compra Directa con Oferta Electronica (Art.43 LCE Inciso b)",
                "MANTENIMIENTO DE VEHICULO",
                "linea 8",
                "1111",
                "TALLER HERMANOS LOPEZ",
                "Q. 3,500.00");
        List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(txt);
        assertEquals(1, regs.size());
        assertEquals("E568000010", regs.get(0).getNpg());
        assertEquals(3500.00, regs.get(0).getMonto());
    }

    @Test
    void archivoVacioONuloDaListaVacia() {
        assertEquals(0, ParserGuatecompras.parsear("").size());
        assertEquals(0, ParserGuatecompras.parsear(null).size());
    }

    /** Layout real de Guatecompras 2026: fecha con doble punto y NIT en linea 8. */
    private static final String BLOQUE_ART43 = String.join("\n",
            "E589747711",
            "24.ago..2026 07:30:53",
            "Publicado",
            "Guatecompras",
            "MUNICIPALIDAD DE GRANADOS, BAJA VERAPAZ",
            "ALCALDIA MUNICIPAL",
            "Compra de Baja Cuantía (Art.43 inciso a)",
            "por trabajos de herreria en la comunidad de santa rosa granados, baja verapaz",
            "206965826",
            "ORTÍZ,ORTÍZ,REYES,MERIDA,SENAIDA",
            "8,000.00");

    private static final String BLOQUE_ART44 = String.join("\n",
            "E588937274",
            "11.ago..2026 10:06:18",
            "Publicado",
            "Guatecompras",
            "MUNICIPALIDAD DE GRANADOS, BAJA VERAPAZ",
            "ALCALDIA MUNICIPAL",
            "Procedimientos Regulados por el artículo 44 LCE (Casos de Excepción)",
            "Contratación de Servicios Básicos (Art. 44 inciso g)",
            "pago de servicio de energia electrica correspondiente al mes de julio 2026.",
            "14946203",
            "DISTRIBUIDORA DE ELECTRICIDAD DE ORIENTE SOCIEDAD ANONIMA",
            "40,681.13");

    private static final String BLOQUE_E59 = String.join("\n",
            "E590038931",
            "27.ago..2026 09:54:57",
            "Publicado",
            "Guatecompras",
            "MUNICIPALIDAD DE GRANADOS, BAJA VERAPAZ",
            "ALCALDIA MUNICIPAL",
            "Compra de Baja Cuantía (Art.43 inciso a)",
            "PAGO DE SONIDO Y PANTALA PARA ELECCION Y CORONACION DE FERIA EL GUAPINOL E INAGURACION DE FERIA",
            "40941442",
            "ORTIZ,ROSALES,,JUANA,PAOLA",
            "9,600.00");

    @Test
    void fechaConDoblePuntoDelExportDeGuatecompras() {
        List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(BLOQUE_ART43);
        assertEquals(1, regs.size());
        assertEquals("24/08/2026", regs.get(0).getFecha());
        assertEquals("E589747711", regs.get(0).getNpg());
        assertEquals("206965826", regs.get(0).getNit());
        assertEquals(8000.00, regs.get(0).getMonto());
    }

    @Test
    void art44UsaLaDescripcionRealNoLaSubmodalidad() {
        List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(BLOQUE_ART44);
        assertEquals(1, regs.size());
        RegistroGuatecompras r = regs.get(0);
        assertEquals("E588937274", r.getNpg());
        assertEquals("CASO DE EXCEPCION", r.getModalidad());
        assertEquals("14946203", r.getNit());
        assertEquals(40681.13, r.getMonto());
        assertTrue(r.getDesc().toLowerCase().contains("energia electrica"));
        assertFalse(r.getDesc().toLowerCase().contains("inciso g"));
    }

    @Test
    void npgE59NoSePegaAlBloqueAnterior() {
        String txt = BLOQUE_ART43 + "\n" + BLOQUE_E59;
        List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(txt);
        assertEquals(2, regs.size());
        assertEquals("E589747711", regs.get(0).getNpg());
        assertEquals(8000.00, regs.get(0).getMonto());
        assertEquals("E590038931", regs.get(1).getNpg());
        assertEquals("27/08/2026", regs.get(1).getFecha());
        assertEquals(9600.00, regs.get(1).getMonto());
        assertEquals("40941442", regs.get(1).getNit());
    }

    @Test
    void txtRealDeAgostoTiene267PublicacionesIncluyendoE59() throws Exception {
        String contenido = contenidoAgosto();
        List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(contenido);
        assertEquals(267, regs.size());
        assertTrue(regs.stream().anyMatch(r -> "E590038931".equals(r.getNpg())));
        RegistroGuatecompras e59 = regs.stream()
                .filter(r -> "E590038931".equals(r.getNpg()))
                .findFirst().orElseThrow();
        assertEquals(9600.00, e59.getMonto());
        RegistroGuatecompras art44 = regs.stream()
                .filter(r -> "E588937274".equals(r.getNpg()))
                .findFirst().orElseThrow();
        assertTrue(art44.getDesc().toLowerCase().contains("energia"));
    }

    @Test
    void dumpEneroAgostoTiene2737PublicacionesUnicas() throws Exception {
        Path dump = Path.of("c:/Users/ngl/Downloads/NPG DE ENERO HASTA AGOSTO 2026.txt");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(dump),
                "Dump de NPG no esta en Downloads");
        String contenido = Files.readString(dump, StandardCharsets.UTF_8);
        List<RegistroGuatecompras> regs = ParserGuatecompras.parsear(contenido);
        assertEquals(2737, regs.size());
        long unicos = regs.stream().map(RegistroGuatecompras::getNpg).distinct().count();
        assertEquals(2737, unicos);
        assertTrue(regs.stream().anyMatch(r -> "E590038931".equals(r.getNpg())));
    }

    private static String contenidoAgosto() throws Exception {
        Path classpath = Path.of("src/test/resources/parser/agosto-2026-guatecompras.txt");
        if (Files.exists(classpath)) {
            return Files.readString(classpath, StandardCharsets.UTF_8);
        }
        Path downloads = Path.of("c:/Users/ngl/Downloads/AGOSTO 2026.txt");
        if (Files.exists(downloads)) {
            return Files.readString(downloads, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(
                "No esta el TXT de agosto (test/resources/parser o Downloads).");
    }
}
