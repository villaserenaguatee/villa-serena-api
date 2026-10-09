package com.villaserena.api.huespedes;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.reservas.dto.HuespedVista;

/** Busca el huésped por correo o lo crea (RN-RES-019). Lo usan la web, Recepción y el canal. */
@Service
public class HuespedService {

    /** Máximo de resultados de la búsqueda de huéspedes de Recepción. */
    private static final int MAX_RESULTADOS = 50;

    private final HuespedRepository huespedes;
    private final JdbcTemplate jdbc;

    public HuespedService(HuespedRepository huespedes, JdbcTemplate jdbc) {
        this.huespedes = huespedes;
        this.jdbc = jdbc;
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

    /**
     * Registro de Recepción (HU-REC-01): si el correo ya existe, devuelve ese perfil
     * sin cambios y {@code yaExistia}. Dos registros simultáneos con el mismo correo
     * no fallan: el índice único decide y el segundo recibe el perfil existente.
     */
    @Transactional
    public RegistroHuespedRespuesta registrar(HuespedDatos datos) {
        List<Long> nuevo = jdbc.queryForList("""
                INSERT INTO huespedes
                    (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT ((lower(correo))) DO NOTHING
                RETURNING id""", Long.class,
                datos.nombreCompleto().trim(), datos.correo().trim(), datos.telefono().trim(),
                datos.nacionalidad().trim(), datos.tipoDocumento().name(), datos.numeroDocumento().trim());
        Huesped huesped = nuevo.isEmpty()
                ? huespedes.findByCorreoIgnoreCase(datos.correo().trim()).orElseThrow()
                : huespedes.findById(nuevo.getFirst()).orElseThrow();
        return new RegistroHuespedRespuesta(nuevo.isEmpty(), HuespedVista.de(huesped));
    }

    /** Búsqueda por nombre, número de documento o correo (HU-REC-01), sin distinguir mayúsculas. */
    @Transactional(readOnly = true)
    public List<HuespedVista> buscar(String texto) {
        String q = texto == null ? "" : texto.trim();
        if (q.length() < 2) {
            throw ApiException.datosInvalidos("Escribe al menos 2 caracteres para buscar.",
                    List.of(new DetalleError("q", "Debe tener al menos 2 caracteres.")));
        }
        String patron = "%" + escaparLike(q) + "%";
        return jdbc.query("""
                SELECT id, nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento
                FROM huespedes
                WHERE nombre_completo ILIKE ? OR numero_documento ILIKE ? OR correo ILIKE ?
                ORDER BY nombre_completo, id
                LIMIT ?""",
                (rs, i) -> new HuespedVista(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), TipoDocumento.valueOf(rs.getString(6)), rs.getString(7)),
                patron, patron, patron, MAX_RESULTADOS);
    }

    /** Escapa los comodines de LIKE para buscar el texto tal como se escribió. */
    public static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
