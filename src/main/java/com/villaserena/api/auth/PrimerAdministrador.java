package com.villaserena.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.config.PropiedadesVillaSerena;

/**
 * Primer Administrador (ALC-TRA-01): al arrancar, si ADMIN_EMAIL, ADMIN_NAME y
 * ADMIN_PASSWORD están en el entorno y no existe un empleado con ese correo, lo
 * crea como ADMIN con contraseña temporal. Si falta alguna variable, no hace nada.
 */
@Component
public class PrimerAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrimerAdministrador.class);

    private final EmpleadoRepository empleados;
    private final PasswordEncoder passwordEncoder;
    private final PropiedadesVillaSerena.PrimerAdmin datos;

    public PrimerAdministrador(EmpleadoRepository empleados, PasswordEncoder passwordEncoder,
            PropiedadesVillaSerena propiedades) {
        this.empleados = empleados;
        this.passwordEncoder = passwordEncoder;
        this.datos = propiedades.primerAdmin();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (datos == null || vacio(datos.correo()) || vacio(datos.nombre()) || vacio(datos.contrasena())) {
            return;
        }
        String correo = datos.correo().trim();
        if (empleados.existsByCorreoIgnoreCase(correo)) {
            return;
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
