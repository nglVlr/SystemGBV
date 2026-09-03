package com.granados.sistema.dafim.paquetes;

import com.granados.sistema.dafim.paquetes.dto.FacturaSatDatos;
import com.granados.sistema.dafim.paquetes.parser.ParserFacturaSat;

import java.lang.reflect.Method;

/** Acceso al metodo paquete-privado llenarDesdeTexto para los tests. */
final class ParserFacturaSatTestHelper {

    private ParserFacturaSatTestHelper() { }

    static void llenar(FacturaSatDatos f, String texto) {
        try {
            Method m = ParserFacturaSat.class.getDeclaredMethod(
                    "llenarDesdeTexto", FacturaSatDatos.class, String.class);
            m.setAccessible(true);
            m.invoke(null, f, texto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
