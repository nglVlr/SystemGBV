package com.granados.sistema.usuarios.service;

import com.granados.sistema.usuarios.entity.Rol;
import com.granados.sistema.usuarios.entity.Usuario;
import com.granados.sistema.usuarios.repository.UsuarioRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adaptador entre la tabla usuarios y Spring Security.
 * Cada rol se expone como ROLE_{NOMBRE}. Los roles viejos ADMIN_DAFIM y
 * ADMIN_RRHH se expanden a los modulos equivalentes.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepo;

    public UsuarioDetailsService(UsuarioRepository usuarioRepo) {
        this.usuarioRepo = usuarioRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario u = usuarioRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + username));
        return User.builder()
                .username(u.getUsername())
                .password(u.getPassword())
                .disabled(!u.isActivo())
                .authorities(expandir(u.getRoles()))
                .build();
    }

    static List<GrantedAuthority> expandir(Set<Rol> roles) {
        Set<String> nombres = new LinkedHashSet<>();
        if (roles != null) {
            for (Rol r : roles) {
                if (r != null && r.getNombre() != null) nombres.add(r.getNombre());
            }
        }
        if (nombres.contains(Rol.SUPERADMIN)) {
            nombres.addAll(Rol.MODULOS);
        }
        if (nombres.contains(Rol.ADMIN_DAFIM)) {
            nombres.addAll(Rol.MODULOS_DAFIM);
        }
        if (nombres.contains(Rol.ADMIN_RRHH)) {
            nombres.add(Rol.RRHH);
        }
        if (nombres.contains(Rol.DINERO)) {
            nombres.add(Rol.PRESUPUESTO);
        }
        List<GrantedAuthority> auths = new ArrayList<>();
        for (String n : nombres) {
            auths.add(new SimpleGrantedAuthority("ROLE_" + n));
        }
        return auths;
    }
}
