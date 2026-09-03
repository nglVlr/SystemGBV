package com.granados.sistema.usuarios.service;

import com.granados.sistema.usuarios.entity.Rol;
import com.granados.sistema.usuarios.entity.Usuario;
import com.granados.sistema.usuarios.repository.RolRepository;
import com.granados.sistema.usuarios.repository.UsuarioRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * CRUD de usuarios con las reglas de negocio del SUPERADMIN:
 *  - el username es unico y no se puede cambiar despues de creado,
 *  - un usuario no puede eliminarse ni desactivarse a si mismo,
 *  - la contrasena solo se actualiza si se escribe una nueva,
 *  - siempre queda registrado el ultimo acceso de cada usuario.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepo;
    private final RolRepository rolRepo;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarioRepo, RolRepository rolRepo,
                          PasswordEncoder encoder) {
        this.usuarioRepo = usuarioRepo;
        this.rolRepo = rolRepo;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarioRepo.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> porId(Long id) {
        return usuarioRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> porUsername(String username) {
        return usuarioRepo.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<Rol> roles() {
        return rolRepo.findAll(Sort.by("id"));
    }

    /** Roles que el superadmin marca al crear un usuario (sin aliases viejos). */
    @Transactional(readOnly = true)
    public List<Rol> rolesFormulario() {
        List<Rol> todos = rolRepo.findAll();
        List<String> orden = List.of(
                Rol.COMPRAS, Rol.PAQUETES, Rol.PRESUPUESTO,
                Rol.REMUNERACIONES, Rol.RRHH, Rol.SUPERADMIN);
        List<Rol> out = new ArrayList<>();
        for (String n : orden) {
            for (Rol r : todos) {
                if (n.equals(r.getNombre())) {
                    out.add(r);
                    break;
                }
            }
        }
        return out;
    }

    /** Crea un usuario nuevo. Lanza IllegalArgumentException con mensaje claro. */
    @Transactional
    public Usuario crear(String username, String password, String nombreCompleto,
                         String email, List<Long> rolesIds) {
        String user = username == null ? "" : username.trim().toLowerCase();
        if (user.isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (usuarioRepo.existsByUsername(user)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException(
                    "La contrasena debe tener al menos 6 caracteres.");
        }
        Usuario u = new Usuario();
        u.setUsername(user);
        u.setPassword(encoder.encode(password));
        u.setNombreCompleto(nombreCompleto == null ? "" : nombreCompleto.trim());
        u.setEmail(email == null ? "" : email.trim());
        u.setActivo(true);
        u.setRoles(rolesDesdeIds(rolesIds));
        return usuarioRepo.save(u);
    }

    /** Actualiza datos. Password en blanco significa conservar la actual. */
    @Transactional
    public Usuario actualizar(Long id, String password, String nombreCompleto,
                              String email, List<Long> rolesIds, Long idActual) {
        Usuario u = usuarioRepo.findById(id).orElseThrow(
                () -> new IllegalArgumentException("El usuario no existe."));
        if (password != null && !password.isBlank()) {
            if (password.length() < 6) {
                throw new IllegalArgumentException(
                        "La contrasena debe tener al menos 6 caracteres.");
            }
            u.setPassword(encoder.encode(password));
        }
        u.setNombreCompleto(nombreCompleto == null ? "" : nombreCompleto.trim());
        u.setEmail(email == null ? "" : email.trim());
        Set<Rol> nuevos = rolesDesdeIds(rolesIds);
        // que el SUPERADMIN no se quede sin su propio rol por accidente
        if (u.getId().equals(idActual) && u.tieneRol(Rol.SUPERADMIN)) {
            boolean conserva = nuevos.stream()
                    .anyMatch(r -> Rol.SUPERADMIN.equals(r.getNombre()));
            if (!conserva) {
                throw new IllegalArgumentException(
                        "No puedes quitarte a ti mismo el rol SUPERADMIN.");
            }
        }
        u.setRoles(nuevos);
        return usuarioRepo.save(u);
    }

    @Transactional
    public void cambiarActivo(Long id, Long idActual) {
        if (id.equals(idActual)) {
            throw new IllegalArgumentException(
                    "No puedes desactivar tu propio usuario.");
        }
        Usuario u = usuarioRepo.findById(id).orElseThrow(
                () -> new IllegalArgumentException("El usuario no existe."));
        u.setActivo(!u.isActivo());
        usuarioRepo.save(u);
    }

    @Transactional
    public void eliminar(Long id, Long idActual) {
        if (id.equals(idActual)) {
            throw new IllegalArgumentException(
                    "No puedes eliminar tu propio usuario.");
        }
        Usuario u = usuarioRepo.findById(id).orElseThrow(
                () -> new IllegalArgumentException("El usuario no existe."));
        usuarioRepo.delete(u);
    }

    /** Registra la fecha del ultimo acceso cuando el login es exitoso. */
    @EventListener
    @Transactional
    public void alIniciarSesion(AuthenticationSuccessEvent ev) {
        String username = ev.getAuthentication().getName();
        usuarioRepo.findByUsername(username).ifPresent(u -> {
            u.setUltimoAcceso(LocalDateTime.now());
            usuarioRepo.save(u);
        });
    }

    private Set<Rol> rolesDesdeIds(List<Long> ids) {
        Set<Rol> roles = new LinkedHashSet<>();
        if (ids != null) {
            for (Long rid : ids) {
                rolRepo.findById(rid).ifPresent(roles::add);
            }
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un rol.");
        }
        return roles;
    }
}
