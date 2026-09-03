package com.granados.sistema.usuarios.web;

import com.granados.sistema.usuarios.entity.Usuario;
import com.granados.sistema.usuarios.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Gestion de usuarios (solo SUPERADMIN, garantizado por SecurityConfig).
 */
@Controller
@RequestMapping("/admin/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    private Long idActual(Authentication auth) {
        return usuarioService.porUsername(auth.getName())
                .map(Usuario::getId).orElse(-1L);
    }

    @GetMapping
    public String lista(Model model, Authentication auth) {
        model.addAttribute("usuarios", usuarioService.listar());
        model.addAttribute("idActual", idActual(auth));
        return "admin/usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("modo", "crear");
        model.addAttribute("roles", usuarioService.rolesFormulario());
        return "admin/usuario-formulario";
    }

    @PostMapping("/guardar")
    public String crear(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String nombreCompleto,
                        @RequestParam(required = false) String email,
                        @RequestParam(name = "rolesIds", required = false) List<Long> rolesIds,
                        RedirectAttributes flash) {
        try {
            usuarioService.crear(username, password, nombreCompleto, email, rolesIds);
            flash.addFlashAttribute("exito", "Usuario creado correctamente.");
            return "redirect:/admin/usuarios";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/usuarios/nuevo";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes flash) {
        Usuario u = usuarioService.porId(id).orElse(null);
        if (u == null) {
            flash.addFlashAttribute("error", "El usuario no existe.");
            return "redirect:/admin/usuarios";
        }
        model.addAttribute("modo", "editar");
        model.addAttribute("u", u);
        model.addAttribute("roles", usuarioService.rolesFormulario());
        return "admin/usuario-formulario";
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id,
                             @RequestParam(required = false) String password,
                             @RequestParam(required = false) String nombreCompleto,
                             @RequestParam(required = false) String email,
                             @RequestParam(name = "rolesIds", required = false) List<Long> rolesIds,
                             Authentication auth,
                             RedirectAttributes flash) {
        try {
            usuarioService.actualizar(id, password, nombreCompleto, email,
                    rolesIds, idActual(auth));
            flash.addFlashAttribute("exito", "Usuario actualizado.");
            return "redirect:/admin/usuarios";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/usuarios/" + id + "/editar";
        }
    }

    @PostMapping("/{id}/toggle")
    public String cambiarActivo(@PathVariable Long id, Authentication auth,
                                RedirectAttributes flash) {
        try {
            usuarioService.cambiarActivo(id, idActual(auth));
            flash.addFlashAttribute("exito", "Estado del usuario actualizado.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, Authentication auth,
                           RedirectAttributes flash) {
        try {
            usuarioService.eliminar(id, idActual(auth));
            flash.addFlashAttribute("exito", "Usuario eliminado.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }
}
