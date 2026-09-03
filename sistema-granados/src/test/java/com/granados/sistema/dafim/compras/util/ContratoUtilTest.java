package com.granados.sistema.dafim.compras.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContratoUtilTest {

    @Test
    void extraeYNormalizaVariantes() {
        assertEquals("38-2026", ContratoUtil.extraerContrato("SEGUN CONTRATO NO. 38-2026"));
        assertEquals("38-2026", ContratoUtil.extraerContrato("contrato numero 038-2026"));
        assertEquals("38-2026", ContratoUtil.extraerContrato("CONTRATO NRO 0038 - 2026"));
        assertEquals("38-2026", ContratoUtil.extraerContrato("CONTRATO 38-2026 sin nada"));
        assertEquals("7-2025", ContratoUtil.extraerContrato("Contrato N. 007-2025,"));
    }

    @Test
    void aceptaGuionLargo() {
        assertEquals("12-2026",
                ContratoUtil.extraerContrato("CONTRATO NO. 12\u20132026"));
    }

    @Test
    void extraeContratoAdministrativo() {
        assertEquals("23-2026", ContratoUtil.extraerContrato(
                "SEGUN CONTRATO ADMINISTRATIVO NO.023-2026 CORRESPONDIENTE AL MES"));
        assertEquals("79-2026", ContratoUtil.extraerContrato(
                "CONTRATO ADMINISTRATIVO NO.079-2026"));
    }

    @Test
    void sinContratoRegresaVacio() {
        assertEquals("", ContratoUtil.extraerContrato("pago de servicios varios"));
        assertEquals("", ContratoUtil.extraerContrato(""));
    }
}
