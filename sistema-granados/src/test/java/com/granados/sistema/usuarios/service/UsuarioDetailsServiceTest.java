package com.granados.sistema.usuarios.service;

import com.granados.sistema.usuarios.entity.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioDetailsServiceTest {

    @Test
    void adminDafimGanaTodosLosModulosDafim() {
        Set<String> auths = nombres(UsuarioDetailsService.expandir(
                Set.of(new Rol(Rol.ADMIN_DAFIM, "x"))));
        assertTrue(auths.contains("ROLE_COMPRAS"));
        assertTrue(auths.contains("ROLE_PAQUETES"));
        assertTrue(auths.contains("ROLE_PRESUPUESTO"));
        assertTrue(auths.contains("ROLE_REMUNERACIONES"));
        assertTrue(auths.contains("ROLE_ADMIN_DAFIM"));
        assertTrue(!auths.contains("ROLE_DINERO"));
    }

    @Test
    void superadminGanaTodosLosModulos() {
        Set<String> auths = nombres(UsuarioDetailsService.expandir(
                Set.of(new Rol(Rol.SUPERADMIN, "x"))));
        assertTrue(auths.contains("ROLE_RRHH"));
        assertTrue(auths.contains("ROLE_COMPRAS"));
        assertTrue(auths.contains("ROLE_SUPERADMIN"));
    }

    @Test
    void moduloSueltoNoAbreElResto() {
        Set<String> auths = nombres(UsuarioDetailsService.expandir(
                Set.of(new Rol(Rol.COMPRAS, "x"))));
        assertTrue(auths.contains("ROLE_COMPRAS"));
        assertTrue(!auths.contains("ROLE_PAQUETES"));
        assertTrue(!auths.contains("ROLE_RRHH"));
    }

    @Test
    void rolDineroViejoAbrePresupuesto() {
        Set<String> auths = nombres(UsuarioDetailsService.expandir(
                Set.of(new Rol(Rol.DINERO, "x"))));
        assertTrue(auths.contains("ROLE_DINERO"));
        assertTrue(auths.contains("ROLE_PRESUPUESTO"));
        assertTrue(!auths.contains("ROLE_COMPRAS"));
    }

    private static Set<String> nombres(java.util.List<GrantedAuthority> a) {
        return a.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
