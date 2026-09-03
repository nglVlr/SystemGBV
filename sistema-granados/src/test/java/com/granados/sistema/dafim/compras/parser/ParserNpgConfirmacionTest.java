package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.NpgConfirmacion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParserNpgConfirmacionTest {

    @Test
    void leeConfirmacionReal() throws Exception {
        try (var in = getClass().getResourceAsStream("/parser/npg.pdf")) {
            NpgConfirmacion r = ParserNpgConfirmacion.parsear(in, "npg.pdf");
            assertEquals("", r.getError());
            // confirmacion real de Guatecompras (abril 2026)
            assertEquals("E581998146", r.getNpg());
            assertEquals("77132556", r.getNit());
            assertEquals("98-2026", r.getContrato());
        }
    }

    @Test
    void toleraFormatoAlternativoDePdf() throws Exception {
        // Simula una confirmacion con OTRO layout: sin "Publicacion (NPG)",
        // con "NIT del proveedor" y "Descripcion del NPG".
        byte[] pdf;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage pagina = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(pagina);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, pagina)) {
                cs.beginText();
                cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11);
                cs.setLeading(14f);
                cs.newLineAtOffset(50, 700);
                cs.showText("GUATECOMPRAS - constancia de NPG publicado");
                cs.newLine();
                cs.showText("Numero: E599112233");
                cs.newLine();
                cs.showText("NIT del proveedor: 12345678 - LOPEZ,PEREZ,,JUAN,CARLOS");
                cs.newLine();
                cs.showText("Descripcion del NPG: Servicios de mantenimiento municipal");
                cs.newLine();
                cs.showText("Documento adjunto: E599112233@045-2026 JUAN.pdf");
                cs.endText();
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            doc.save(bos);
            pdf = bos.toByteArray();
        }
        NpgConfirmacion r = ParserNpgConfirmacion.parsear(
                new java.io.ByteArrayInputStream(pdf), "alterno.pdf");
        assertEquals("", r.getError());
        assertEquals("E599112233", r.getNpg());
        assertEquals("12345678", r.getNit());
        assertEquals("45-2026", r.getContrato());
    }
}
