package com.granados.sistema.dafim.compras.parser;

import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserSicoinTest {

    @Test
    void leeRegistrosConContinuacionYContrato() {
        List<String> lineas = List.of(
                "MUNICIPALIDAD DE GRANADOS",
                "TALLER HERMANOS LOPEZ NIT: 1111",
                "11 00 001 002 003 165  4001 IMPRESO  CUR  41001 05/06/2026 "
                        + "3,500.00 Pago de Mantenimiento de vehiculo municipal",
                "placa O-123 segun factura",
                "Total por proveedor  3,500.00",
                "RAMIREZ COY PEDRO NIT: 7777",
                "11 00 001 002 003 029  4002 IMPRESO  CUR  41005 05/06/2026 "
                        + "2,600.00 PAGO DE servicios COMO: ALBANIL, SEGUN",
                "CONTRATO NUMERO 03-2026 de fecha",
                "Total por proveedor  2,600.00");
        List<RegistroSicoin> regs = ParserSicoin.parsearLineas(lineas);
        assertEquals(2, regs.size());
        assertEquals("41001", regs.get(0).getCheque());
        assertEquals("165", regs.get(0).getRenglon());
        assertEquals(3500.00, regs.get(0).getMonto());
        assertTrue(regs.get(0).getDesc().startsWith("Mantenimiento"));
        assertTrue(regs.get(0).getDesc().contains("placa O-123"));
        assertEquals("3-2026", regs.get(1).getContrato());
        assertEquals("029", regs.get(1).getRenglon());
    }

    @Test
    void conservaComoYRenglonEnvueltoEnLaDescripcion() {
        List<RegistroSicoin> regs = ParserSicoin.parsearLineas(List.of(
                "GARCIA,GONZALEZ,,MALVI,OSVALDO     NIT:  100192076",
                "19 02 001 002 000 029  209804343 IMPRESO Inversion Social  33749 ABC 45203 20/08/2026 "
                        + "2,500.00 Pago de POR SERVICIOS PRESTADOS A LA",
                "MUNICIPALIDAD DE GRANADOS COMO:",
                "31-0151-0003 00 TRABAJADOR OPERATIVO, SEGUN CONTRATO",
                "NO. 38-2026 CORRESPONDIENTE AL MES DE",
                "JULIO 2026",
                "Total por proveedor:  2,500.00",
                "GARCIA GARCIA,JARVIS EDUARDO     NIT:  555",
                "19 02 001 001 000 035  209812949 IMPRESO Inversion Social  2341 - - 45426 20/08/2026 "
                        + "2,600.00 PAGO DE LA PLANILLA PLANILLA DE PERSONAL",
                "CALLES GRANADOS BAJA VERAPAZ CORRESPONDIENTE AL MES DE JULIO 2026 PARA EL EMPLEADO JARVIS",
                "035 QUE REALIZA TRABAJO DE LIMPIEZA DE",
                "CALLES",
                "Total por proveedor:  2,600.00"));
        assertEquals(2, regs.size());
        assertTrue(regs.get(0).getDesc().toUpperCase().contains("COMO"));
        assertTrue(regs.get(0).getDesc().toUpperCase().contains("TRABAJADOR OPERATIVO"));
        assertTrue(regs.get(1).getDesc().toUpperCase().contains("LIMPIEZA"));
        assertEquals("035", regs.get(1).getRenglon());
    }

    @Test
    void leePdfReal() throws Exception {
        try (var in = getClass().getResourceAsStream("/parser/sicoin.pdf")) {
            List<RegistroSicoin> regs = ParserSicoin.parsear(in);
            // PDF real de junio 2026: 620 registros (592 IMPRESO, 28 ANULADO)
            assertEquals(620, regs.size());
            long impresos = regs.stream().filter(r -> "IMPRESO".equals(r.getStatus())).count();
            assertEquals(592, impresos);
            // ninguna descripcion debe arrastrar la fuente de financiamiento
            assertTrue(regs.stream().noneMatch(r -> r.getDesc().matches(".*\\d{2}-\\d{4}-\\d{4}.*\\d{2}.*")
                    && r.getDesc().contains("-0101-")));
        }
    }
}
