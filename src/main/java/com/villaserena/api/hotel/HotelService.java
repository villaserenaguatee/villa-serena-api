package com.villaserena.api.hotel;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;

/**
 * Información pública del hotel (HU-HUE-01): la configuración del hotel y sus fotos
 * del bucket público. Las horas de check-in (15:00) y check-out (12:00) son
 * constantes del sistema.
 */
@Service
public class HotelService {

    public static final String HORA_CHECK_IN = "15:00";
    public static final String HORA_CHECK_OUT = "12:00";

    private final JdbcTemplate jdbc;
    private final String urlPublica;

    public HotelService(JdbcTemplate jdbc, PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.urlPublica = propiedades.archivos().urlPublica();
    }

    /** 404 si el hotel todavía no está configurado (sin datos de demostración ni registro de Administración). */
    @Transactional(readOnly = true)
    public HotelPublico publico() {
        List<String> fotos = jdbc.queryForList("SELECT clave_objeto FROM fotos_hotel ORDER BY orden, id",
                String.class).stream().map(clave -> urlPublica + "/" + clave).toList();
        return jdbc.query("""
                SELECT nombre, descripcion, direccion, telefono, correo FROM configuracion_hotel WHERE id = 1""",
                (rs, i) -> new HotelPublico(rs.getString(1), rs.getString(2), fotos,
                        new HotelPublico.Ubicacion(rs.getString(3)),
                        new HotelPublico.Contacto(rs.getString(4), rs.getString(5)), HORA_CHECK_IN, HORA_CHECK_OUT))
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOTEL_NO_CONFIGURADO",
                        "La información del hotel todavía no está disponible."));
    }
}
