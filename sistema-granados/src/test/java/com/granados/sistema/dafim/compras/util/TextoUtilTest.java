package com.granados.sistema.dafim.compras.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextoUtilTest {

    @Test
    void normQuitaTildesComasYEspacios() {
        assertEquals("MARIA JOSE PEREZ", TextoUtil.norm("  María,,José \t Pérez "));
        assertEquals("NINO CANU", TextoUtil.norm("Niño Cañú"));
        assertEquals("", TextoUtil.norm("   "));
    }

    @Test
    void limpiarDescQuitaPrefijosDePago() {
        assertEquals("Mantenimiento de vehiculo",
                TextoUtil.limpiarDesc("Pago de Mantenimiento de vehiculo"));
        assertEquals("Mantenimiento",
                TextoUtil.limpiarDesc("POR PAGO DE Mantenimiento"));
        // solo quita UN prefijo, como el Python (break tras el primero)
        assertEquals("pago de X", TextoUtil.limpiarDesc("pago de pago de X"));
        assertEquals("Sin prefijo", TextoUtil.limpiarDesc("  Sin prefijo  "));
    }

    @Test
    void limpiarNitNormaliza() {
        assertEquals("123456", TextoUtil.limpiarNit(" 123456K "));
        assertEquals("789", TextoUtil.limpiarNit("789.0"));
        assertEquals("45678", TextoUtil.limpiarNit("4 5 6 7 8"));
    }

    @Test
    void cortaRespetaLimite() {
        assertEquals("abc", TextoUtil.corta("abc", 5));
        assertEquals("abcde", TextoUtil.corta("abcdefgh", 5));
        assertEquals("", TextoUtil.corta(null, 5));
    }

    @Test
    void interseccionCuentaPalabrasComunes() {
        Set<String> a = Set.of("MARIA", "JOSE", "GARCIA");
        Set<String> b = Set.of("GARCIA", "JOSE", "OTRA");
        assertEquals(2, TextoUtil.interseccion(a, b));
    }
}
