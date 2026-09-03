package com.granados.sistema.web;

import com.granados.sistema.dafim.compras.service.GestionBaseDatosComprasService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import com.granados.sistema.usuarios.entity.Usuario;
import com.granados.sistema.usuarios.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * Pantalla de inicio (dashboard general) y login.
 */
@Controller
public class DashboardController {

    private final UsuarioService usuarioService;
    private final PresupuestoService presupuestoService;
    private final GestionBaseDatosComprasService gestionCompras;

    public DashboardController(UsuarioService usuarioService,
                               PresupuestoService presupuestoService,
                               GestionBaseDatosComprasService gestionCompras) {
        this.usuarioService = usuarioService;
        this.presupuestoService = presupuestoService;
        this.gestionCompras = gestionCompras;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String inicio(Authentication auth, Model model) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "redirect:/login";
        }
        Usuario u = usuarioService.porUsername(auth.getName()).orElse(null);
        model.addAttribute("usuario", u);
        model.addAttribute("nombreMostrar",
                u != null && u.getNombreCompleto() != null
                        && !u.getNombreCompleto().isBlank()
                        ? u.getNombreCompleto() : auth.getName());

        // Indicadores del hero: solo se agregan si hay datos reales; la
        // plantilla oculta las tarjetas cuyo atributo no exista.
        presupuestoService.resumenGeneral().ifPresent(resumen -> {
            model.addAttribute("pctEjecucion", resumen.getPctEjecucion());
            model.addAttribute("vigentePresupuesto", resumen.getTotalVigente());
            model.addAttribute("devengadoPresupuesto", resumen.getTotalDevengado());
        });

        Map<String, Object> statsCompras = gestionCompras.estadisticasDashboard();
        Object ultimoMesNombre = statsCompras.get("ultimoMesNombre");
        if (ultimoMesNombre != null && !ultimoMesNombre.toString().isBlank()) {
            model.addAttribute("ultimoMesCompras", ultimoMesNombre.toString());
            Object estado = statsCompras.get("ultimoEstado");
            if (estado != null && !estado.toString().isBlank()) {
                model.addAttribute("ultimoEstadoCompras", estado.toString());
            }
        }
        Object totalHistorial = statsCompras.get("totalHistorial");
        if (totalHistorial instanceof Number && ((Number) totalHistorial).longValue() > 0) {
            model.addAttribute("totalPagosHistorial", ((Number) totalHistorial).longValue());
        }
        return "dashboard";
    }

    @GetMapping("/error/403")
    public String accesoDenegado() {
        return "error/403";
    }
}
