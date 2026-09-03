package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.Cheque;
import com.granados.sistema.dafim.compras.dto.FilaCompra;
import com.granados.sistema.dafim.compras.dto.RegistroGuatecompras;
import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.dto.ResultadoConstruccion;
import com.granados.sistema.dafim.compras.service.ExcelGeneradorService;
import com.granados.sistema.dafim.compras.service.MotorComprasService;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fuentes reales de agosto 2026: el Excel legal sigue 1 fila = 1 cheque.
 */
class AgostoFuentesRealesTest {

    @Test
    void procesarAgostoMantieneUnaFilaPorChequeEIncluyeE59EnCatalogo(@TempDir Path tmp)
            throws Exception {
        byte[] txtBytes = recurso("/parser/agosto-2026-guatecompras.txt");
        byte[] xlsBytes = recurso("/parser/agosto-2026-cheques.xls");
        byte[] pdfBytes = recurso("/parser/agosto-2026-sicoin.pdf");
        assumeTrue(txtBytes != null, "Falta TXT de agosto");
        assumeTrue(xlsBytes != null, "Falta XLS de cheques de agosto");
        assumeTrue(pdfBytes != null, "Falta PDF SICOIN de agosto");

        List<RegistroGuatecompras> pubs = ParserGuatecompras.parsear(
                new String(txtBytes, StandardCharsets.UTF_8));
        assertEquals(267, pubs.size());

        List<Cheque> cheques;
        try (InputStream in = new ByteArrayInputStream(xlsBytes)) {
            cheques = ParserCheques.parsear(in);
        }
        assertTrue(cheques.size() > 0);

        List<RegistroSicoin> sicoin;
        try (InputStream in = new ByteArrayInputStream(pdfBytes)) {
            sicoin = ParserSicoin.parsear(in);
        }
        assertTrue(sicoin.size() > 0);

        ResultadoConstruccion rc = MotorComprasService.construirFilas(
                cheques, pubs, sicoin, Map.of(), Map.of(), Map.of());
        assertEquals(cheques.size(), rc.getFilas().size(),
                "El oficio legal es 1 fila por cheque IMPRESO");

        Set<String> npgsDelMes = pubs.stream()
                .map(RegistroGuatecompras::getNpg)
                .collect(Collectors.toSet());
        Set<String> usadosEnFilas = rc.getFilas().stream()
                .map(FilaCompra::getNpg)
                .filter(Objects::nonNull)
                .filter(npgsDelMes::contains)
                .collect(Collectors.toSet());
        Set<String> sinCheque = rc.getNpgsSinCheque().stream()
                .map(RegistroGuatecompras::getNpg)
                .collect(Collectors.toSet());
        assertTrue(usadosEnFilas.stream().noneMatch(sinCheque::contains),
                "Un NPG no puede estar en una fila y en publicados sin cheque");
        assertEquals(npgsDelMes.size(), usadosEnFilas.size() + sinCheque.size(),
                "Cada NPG del mes queda en una fila o en publicados sin cheque");

        boolean e59EnFila = rc.getFilas().stream()
                .anyMatch(f -> "E590038931".equals(f.getNpg()));
        boolean e59SinCheque = rc.getNpgsSinCheque().stream()
                .anyMatch(p -> "E590038931".equals(p.getNpg()));
        assertTrue(e59EnFila || e59SinCheque, "E590038931 no se puede perder");

        var rv = ValidadorComprasService.validar(rc.getFilas(), cheques);
        assertTrue(rv.isOk(), String.join("\n", rv.getReporte()));

        Path excel = tmp.resolve("COMPRAS_DIRECTAS_AGOSTO_2026.xlsx");
        ExcelGeneradorService.generarExcel(rc.getFilas(), 8, 2026, excel);
        assertTrue(Files.size(excel) > 0);
    }

    private static byte[] recurso(String classpath) throws Exception {
        try (InputStream in = AgostoFuentesRealesTest.class.getResourceAsStream(classpath)) {
            return in == null ? null : in.readAllBytes();
        }
    }
}
