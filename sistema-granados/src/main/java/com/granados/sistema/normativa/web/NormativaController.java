package com.granados.sistema.normativa.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Textos de normativa aplicable a la Municipalidad de Granados, Baja Verapaz.
 * Acceso: cualquier usuario autenticado (DAFIM, RRHH o superadmin).
 *
 * LAIP vive solo en /normativa/laip. Las demas leyes tienen su propia ruta.
 */
@Controller
@RequestMapping("/normativa")
public class NormativaController {

    @GetMapping
    public String indice() {
        return "normativa/index";
    }

    @GetMapping("/laip")
    public String laip() {
        return "normativa/laip";
    }

    @GetMapping("/codigo-municipal")
    public String codigoMunicipal() {
        return "normativa/codigo-municipal";
    }

    @GetMapping("/contrataciones")
    public String contrataciones() {
        return "normativa/contrataciones";
    }

    @GetMapping("/presupuesto")
    public String presupuesto() {
        return "normativa/presupuesto";
    }

    @GetMapping("/trabajo")
    public String trabajo() {
        return "normativa/trabajo";
    }

    @GetMapping("/probidad")
    public String probidad() {
        return "normativa/probidad";
    }
}
