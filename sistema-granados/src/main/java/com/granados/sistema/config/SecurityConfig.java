package com.granados.sistema.config;

import com.granados.sistema.usuarios.entity.Rol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Seguridad del sistema.
 *
 *   /admin/**              -> SUPERADMIN
 *   /dafim/compras/**      -> COMPRAS (o ADMIN_DAFIM / SUPERADMIN)
 *   /dafim/paquetes/**     -> PAQUETES
 *   /dafim/remuneraciones  -> REMUNERACIONES
 *   /dafim/presupuesto/**  -> PRESUPUESTO (incluye caja, fuentes y bancos)
 *   /rrhh/**               -> RRHH (o ADMIN_RRHH)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Limpia el registro de sesiones cuando una PC cierra el navegador. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/vendor/**",
                        "/img/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole(Rol.SUPERADMIN)
                .requestMatchers("/dafim/compras/**")
                        .hasAnyRole(Rol.COMPRAS, Rol.ADMIN_DAFIM, Rol.SUPERADMIN)
                .requestMatchers("/dafim/paquetes/**")
                        .hasAnyRole(Rol.PAQUETES, Rol.ADMIN_DAFIM, Rol.SUPERADMIN)
                .requestMatchers("/dafim/remuneraciones/**")
                        .hasAnyRole(Rol.REMUNERACIONES, Rol.ADMIN_DAFIM, Rol.SUPERADMIN)
                .requestMatchers("/dafim/presupuesto/**")
                        .hasAnyRole(Rol.PRESUPUESTO, Rol.ADMIN_DAFIM, Rol.SUPERADMIN)
                .requestMatchers("/dafim/bitacora", "/dafim/bitacora/**")
                        .hasAnyRole(Rol.COMPRAS, Rol.PAQUETES, Rol.PRESUPUESTO,
                                Rol.REMUNERACIONES, Rol.ADMIN_DAFIM,
                                Rol.SUPERADMIN)
                .requestMatchers("/rrhh/**")
                        .hasAnyRole(Rol.RRHH, Rol.ADMIN_RRHH, Rol.SUPERADMIN)
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(-1))
            .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
