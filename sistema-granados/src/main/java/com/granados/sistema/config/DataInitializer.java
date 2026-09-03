package com.granados.sistema.config;

import com.granados.sistema.usuarios.entity.Rol;
import com.granados.sistema.usuarios.entity.Usuario;
import com.granados.sistema.usuarios.repository.RolRepository;
import com.granados.sistema.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Crea los roles y los tres usuarios iniciales la primera vez que arranca
 * el sistema (si ya existen no hace nada):
 *
 *   superadmin  (SUPERADMIN) — clave inicial: ver README
 *   admin_dafim / Dafim2026*     (ADMIN_DAFIM)
 *   admin_rrhh  / Rrhh2026*      (ADMIN_RRHH)
 *
 * IMPORTANTE: cambiar estas contrasenas despues del primer ingreso desde
 * el modulo de usuarios.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RolRepository rolRepo;
    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder encoder;

    public DataInitializer(RolRepository rolRepo, UsuarioRepository usuarioRepo,
                           PasswordEncoder encoder) {
        this.rolRepo = rolRepo;
        this.usuarioRepo = usuarioRepo;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Rol superadmin = rol(Rol.SUPERADMIN, "Administrador general del sistema");
        Rol dafim = rol(Rol.ADMIN_DAFIM,
                "Direccion de Administracion Financiera (todos los modulos DAFIM)");
        Rol rrhhAdmin = rol(Rol.ADMIN_RRHH, "Recursos Humanos (acceso completo al modulo)");
        rol(Rol.COMPRAS, "Compras directas");
        rol(Rol.PAQUETES, "Paquetes de facturas");
        rol(Rol.PRESUPUESTO, "Presupuesto (ejecucion, caja, fuentes y bancos)");
        rol(Rol.DINERO, "Dinero real (caja, fuentes y bancos)");
        rol(Rol.REMUNERACIONES, "Remuneraciones (oficio LAIP Art. 10.4)");
        rol(Rol.RRHH, "Recursos Humanos");

        usuario("superadmin", "pro9876", "Administrador General",
                Set.of(superadmin));
        usuario("admin_dafim", "Dafim2026*", "Encargado DAFIM", Set.of(dafim));
        usuario("admin_rrhh", "Rrhh2026*", "Encargado RRHH", Set.of(rrhhAdmin));
    }

    private Rol rol(String nombre, String descripcion) {
        return rolRepo.findByNombre(nombre)
                .orElseGet(() -> rolRepo.save(new Rol(nombre, descripcion)));
    }

    private void usuario(String username, String password, String nombreCompleto,
                         Set<Rol> roles) {
        if (usuarioRepo.existsByUsername(username)) return;
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setNombreCompleto(nombreCompleto);
        u.setEmail("");
        u.setActivo(true);
        u.setRoles(roles);
        usuarioRepo.save(u);
        log.info("Usuario inicial creado: {} (cambiar la contrasena)", username);
    }
}
