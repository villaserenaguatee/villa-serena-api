package com.villaserena.api.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.config.PropiedadesVillaSerena;

/** Alta explícita y única del primer administrador; deshabilitada por defecto. */
@Component
public class PrimerAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrimerAdministrador.class);

    private final JdbcTemplate jdbc;
    private final EmpleadoRepository empleados;
    private final PasswordEncoder passwordEncoder;
    private final PropiedadesVillaSerena.PrimerAdmin datos;

    public PrimerAdministrador(EmpleadoRepository empleados, PasswordEncoder passwordEncoder,
            PropiedadesVillaSerena propiedades, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.empleados = empleados;
        this.passwordEncoder = passwordEncoder;
        this.datos = propiedades.primerAdmin();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (datos == null || !datos.habilitado()) {
            return;
        }
        if (vacio(datos.correo()) || vacio(datos.nombre()) || vacio(datos.contrasena())) {
            throw new IllegalStateException("Bootstrap habilitado: se requieren ADMIN_EMAIL, ADMIN_NAME y ADMIN_PASSWORD.");
        }
        if (!datos.correo().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || datos.correo().trim().length() > 150 || datos.nombre().trim().length() > 150
                || datos.contrasena().length() < 12 || datos.contrasena().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Bootstrap: correo y nombre válidos; contraseña de al menos 12 caracteres y hasta 72 bytes UTF-8.");
        }
        // Serializa el primer arranque de varias réplicas dentro de esta transacción.
        jdbc.execute("SELECT pg_advisory_xact_lock(730001)");
        if (empleados.existsByRol(RolEmpleado.ADMIN)) {
            return;
        }
        String correo = datos.correo().trim();
        if (empleados.existsByCorreoIgnoreCase(correo)) {
            throw new IllegalStateException("El correo del primer administrador ya pertenece a un empleado.");
        }
        Empleado admin = new Empleado();
        admin.setNombreCompleto(datos.nombre().trim());
        admin.setCorreo(correo);
        admin.setTelefono("N/D");
        admin.setRol(RolEmpleado.ADMIN);
        admin.setEstado(EstadoEmpleado.ACTIVO);
        admin.setContrasenaHash(passwordEncoder.encode(datos.contrasena()));
        admin.setDebeCambiarContrasena(true);
        empleados.save(admin);
        log.info("Primer Administrador creado desde las variables de entorno (debe cambiar su contraseña).");
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
