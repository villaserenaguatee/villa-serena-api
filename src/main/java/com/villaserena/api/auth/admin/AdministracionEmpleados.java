package com.villaserena.api.auth.admin;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.auth.AreaMyl;
import com.villaserena.api.auth.Empleado;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.EstadoEmpleado;
import com.villaserena.api.auth.RolEmpleado;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;

@Service
public class AdministracionEmpleados {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final EmpleadoRepository empleados;
    private final PasswordEncoder contrasenas;
    private final TokenService tokens;

    public AdministracionEmpleados(EmpleadoRepository empleados, PasswordEncoder contrasenas, TokenService tokens) {
        this.empleados = empleados;
        this.contrasenas = contrasenas;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public List<EmpleadoAdmin> listar(RolEmpleado rol, EstadoEmpleado estado) {
        return empleados.listar(rol, estado).stream().map(EmpleadoAdmin::de).toList();
    }

    @Transactional
    public EmpleadoCreado crear(CrearEmpleadoPeticion peticion) {
        validarArea(peticion.rol(), peticion.area());
        String correo = peticion.correo().trim().toLowerCase(Locale.ROOT);
        if (empleados.existsByCorreoIgnoreCase(correo)) throw correoDuplicado();
        Empleado empleado = new Empleado();
        empleado.setNombreCompleto(peticion.nombreCompleto().trim());
        empleado.setCorreo(correo);
        empleado.setCorreoPersonal(peticion.correoPersonal());
        empleado.setTelefono(peticion.telefono().trim());
        empleado.setRol(peticion.rol());
        empleado.setArea(peticion.area());
        String temporal = nuevaTemporal(empleado);
        try {
            empleados.saveAndFlush(empleado);
        } catch (DataIntegrityViolationException e) {
            // El índice también resuelve dos altas simultáneas del mismo correo.
            if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException causa
                    && "uq_employees_email".equals(causa.getConstraintName())) throw correoDuplicado();
            throw e;
        }
        return new EmpleadoCreado(EmpleadoAdmin.de(empleado), temporal);
    }

    @Transactional
    public EmpleadoAdmin editar(long id, long actorId, EditarEmpleadoPeticion peticion) {
        if (peticion.getNombreCompleto() == null && peticion.getTelefono() == null
                && peticion.getRol() == null && !peticion.areaInformada() && !peticion.correoPersonalInformado()) {
            throw invalido("peticion", "Debes enviar al menos un campo editable.");
        }
        Empleado empleado = buscar(id);
        RolEmpleado rol = peticion.getRol() == null ? empleado.getRol() : peticion.getRol();
        if (id == actorId && rol != empleado.getRol()) {
            throw ApiException.conflicto("ROL_PROPIO", "No puedes cambiar tu propio rol.");
        }
        AreaMyl area = peticion.areaInformada() ? peticion.getArea() : empleado.getArea();
        validarArea(rol, area);
        boolean permisosCambiados = rol != empleado.getRol() || area != empleado.getArea();
        if (peticion.getNombreCompleto() != null) empleado.setNombreCompleto(peticion.getNombreCompleto().trim());
        if (peticion.getTelefono() != null) empleado.setTelefono(peticion.getTelefono().trim());
        if (peticion.correoPersonalInformado()) empleado.setCorreoPersonal(peticion.getCorreoPersonal());
        empleado.setRol(rol);
        empleado.setArea(area);
        if (permisosCambiados) tokens.revocarTodosDelEmpleado(id);
        empleados.incrementarVersion(id);
        return EmpleadoAdmin.de(empleado);
    }

    @Transactional
    public EmpleadoAdmin cambiarEstado(long id, long actorId, EstadoEmpleado estado) {
        Empleado empleado = buscar(id);
        if (id == actorId && estado == EstadoEmpleado.INACTIVO) {
            throw ApiException.conflicto("DESACTIVACION_PROPIA", "No puedes desactivar tu propia cuenta.");
        }
        empleado.setEstado(estado);
        if (estado == EstadoEmpleado.INACTIVO) tokens.revocarTodosDelEmpleado(id);
        empleados.incrementarVersion(id);
        return EmpleadoAdmin.de(empleado);
    }

    @Transactional
    public ContrasenaRestablecida restablecer(long id) {
        Empleado empleado = buscar(id);
        String temporal = nuevaTemporal(empleado);
        tokens.revocarTodosDelEmpleado(id);
        empleados.incrementarVersion(id);
        return new ContrasenaRestablecida(temporal);
    }

    private Empleado buscar(long id) {
        if (id <= 0) throw invalido("id", "El identificador debe ser positivo.");
        return empleados.buscarParaActualizar(id).orElseThrow(ApiException::noEncontrado);
    }

    private String nuevaTemporal(Empleado empleado) {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        String temporal = "Vs1-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        empleado.setContrasenaHash(contrasenas.encode(temporal));
        empleado.setDebeCambiarContrasena(true);
        empleado.setIntentosFallidos(0);
        empleado.setBloqueadoHasta(null);
        return temporal;
    }

    private static void validarArea(RolEmpleado rol, AreaMyl area) {
        if ((rol == RolEmpleado.MANTENIMIENTO_LIMPIEZA) != (area != null)) {
            throw invalido("area", "El área es obligatoria exclusivamente para Mantenimiento y Limpieza.");
        }
    }

    private static ApiException invalido(String campo, String mensaje) {
        return ApiException.datosInvalidos(mensaje, List.of(new DetalleError(campo, mensaje)));
    }

    private static ApiException correoDuplicado() {
        return ApiException.conflicto("CORREO_DUPLICADO", "Ya existe un empleado con ese correo.");
    }
}
