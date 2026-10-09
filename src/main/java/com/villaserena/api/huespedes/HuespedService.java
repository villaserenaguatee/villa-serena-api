package com.villaserena.api.huespedes;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Busca el huésped por correo o lo crea (RN-RES-019). Lo usan la web, Recepción y el canal. */
@Service
public class HuespedService {

    private final HuespedRepository huespedes;

    public HuespedService(HuespedRepository huespedes) {
        this.huespedes = huespedes;
    }

    /** Si el correo ya existe, devuelve ese perfil sin cambiar sus datos. */
    @Transactional
    public Huesped obtenerOCrear(HuespedDatos datos) {
        return huespedes.findByCorreoIgnoreCase(datos.correo().trim()).orElseGet(() -> {
            Huesped nuevo = new Huesped();
            nuevo.setNombreCompleto(datos.nombreCompleto().trim());
            nuevo.setCorreo(datos.correo().trim());
            nuevo.setTelefono(datos.telefono().trim());
            nuevo.setNacionalidad(datos.nacionalidad().trim());
            nuevo.setTipoDocumento(datos.tipoDocumento());
            nuevo.setNumeroDocumento(datos.numeroDocumento().trim());
            return huespedes.save(nuevo);
        });
    }
}
