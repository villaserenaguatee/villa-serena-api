package com.villaserena.api.hotel;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.estadia.Nit;

/**
 * Información pública y administrativa del hotel (HU-HUE-01, HU-ADM-08): la
 * configuración general y fiscal del hotel y sus fotos del bucket público. Las
 * horas de check-in (15:00), check-out (12:00) y la política de cancelación son
 * constantes del sistema.
 */
@Service
public class HotelService {

    public static final String HORA_CHECK_IN = "15:00";
    public static final String HORA_CHECK_OUT = "12:00";
    public static final String POLITICA_CANCELACION =
            "Reembolso total con al menos 48 horas antes de las 15:00 de llegada; sin reembolso después.";

    private final JdbcTemplate jdbc;
    private final String urlPublica;

    public HotelService(JdbcTemplate jdbc, PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.urlPublica = propiedades.archivos().urlPublica();
    }

    /** 404 si el hotel todavía no está configurado (sin datos de demostración ni registro de Administración). */
    @Transactional(readOnly = true)
    public HotelPublico publico() {
        List<String> fotos = jdbc.queryForList("SELECT object_key AS \"clave_objeto\" FROM hotel_photos ORDER BY sort_order, id",
                String.class).stream().map(clave -> urlPublica + "/" + clave).toList();
        return jdbc.query("""
                SELECT name AS "nombre", description AS "descripcion", address AS "direccion", phone AS "telefono", email AS "correo" FROM hotel_configuration WHERE id = 1""",
                (rs, i) -> new HotelPublico(rs.getString(1), rs.getString(2), fotos,
                        new HotelPublico.Ubicacion(rs.getString(3)),
                        new HotelPublico.Contacto(rs.getString(4), rs.getString(5)), HORA_CHECK_IN, HORA_CHECK_OUT))
                .stream().findFirst()
                .orElseThrow(HotelService::noConfigurado);
    }

    @Transactional(readOnly = true)
    public HotelAdmin admin() {
        List<FotoHotelPeticion> fotos = jdbc.query(
                "SELECT object_key AS \"clave\", sort_order AS \"orden\" FROM hotel_photos ORDER BY sort_order, id",
                (rs, i) -> new FotoHotelPeticion(rs.getString(1), rs.getInt(2)));
        return jdbc.query("""
                SELECT name AS "nombre", description AS "descripcion", address AS "direccion",
                       phone AS "telefono", email AS "correo", trade_name AS "nombre_comercial",
                       legal_name AS "razon_social", tax_id AS "nit", tax_address AS "direccion_fiscal"
                FROM hotel_configuration WHERE id = 1""",
                (rs, i) -> new HotelAdmin(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getString(9),
                        fotos, HORA_CHECK_IN, HORA_CHECK_OUT, POLITICA_CANCELACION))
                .stream().findFirst()
                .orElseThrow(HotelService::noConfigurado);
    }

    @Transactional
    public HotelAdmin actualizar(HotelAdminPeticion peticion) {
        String nit = peticion.nit().trim();
        if ("CF".equalsIgnoreCase(nit) || !Nit.esValido(nit)) {
            throw ApiException.datosInvalidos("El NIT del hotel no es válido.",
                    List.of(new DetalleError("nit", "El NIT del hotel no es válido.")));
        }
        for (FotoHotelPeticion foto : peticion.fotos()) {
            String clave = foto.clave().trim();
            if (clave.startsWith("incidencias/") || clave.startsWith("/") || clave.contains("..")) {
                throw ApiException.datosInvalidos("La clave de la foto no es válida.",
                        List.of(new DetalleError("fotos", "La clave de la foto no es válida.")));
            }
        }
        int actualizadas = jdbc.update("""
                UPDATE hotel_configuration SET name=?, description=?, address=?, phone=?, email=?,
                    trade_name=?, legal_name=?, tax_id=?, tax_address=? WHERE id=1""",
                peticion.nombre().trim(), peticion.descripcion().trim(), peticion.direccion().trim(),
                peticion.telefono().trim(), peticion.correo().trim(), peticion.nombreComercial().trim(),
                peticion.razonSocial().trim(), Nit.normalizar(nit), peticion.direccionFiscal().trim());
        if (actualizadas == 0) throw noConfigurado();
        jdbc.update("DELETE FROM hotel_photos");
        for (FotoHotelPeticion foto : peticion.fotos()) {
            jdbc.update("INSERT INTO hotel_photos (object_key, sort_order) VALUES (?, ?)",
                    foto.clave().trim(), foto.orden());
        }
        return admin();
    }

    /** La clave única de PostgreSQL serializa creaciones concurrentes, incluso entre instancias. */
    @Transactional
    public Inicializacion inicializar(InicializarHotelPeticion p) {
        if ("CF".equalsIgnoreCase(p.nit()) || !Nit.esValido(p.nit())) {
            throw ApiException.datosInvalidos("El NIT del hotel no es válido.",
                    List.of(new DetalleError("nit", "El NIT del hotel no es válido.")));
        }
        int creadas = jdbc.update("""
                INSERT INTO hotel_configuration
                    (id, name, description, address, phone, email, trade_name, legal_name, tax_id, tax_address)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING""",
                p.nombre(), p.descripcion(), p.direccion(), p.telefono(), p.correo(),
                p.nombreComercial(), p.razonSocial(), Nit.normalizar(p.nit()), p.direccionFiscal());
        HotelAdmin actual = admin();
        if (!List.of(p.nombre(), p.descripcion(), p.direccion(), p.telefono(), p.correo(),
                p.nombreComercial(), p.razonSocial(), nitComparable(p.nit()), p.direccionFiscal())
                .equals(List.of(actual.nombre().trim(), actual.descripcion().trim(), actual.direccion().trim(),
                        actual.telefono().trim(), actual.correo().trim(), actual.nombreComercial().trim(),
                        actual.razonSocial().trim(), nitComparable(actual.nit()), actual.direccionFiscal().trim()))) {
            throw ApiException.conflicto("HOTEL_YA_CONFIGURADO",
                    "El hotel ya tiene una configuración diferente. Consulta los datos actuales; no se sobrescribieron.");
        }
        return new Inicializacion(actual, creadas == 1);
    }

    private static String nitComparable(String nit) { return Nit.normalizar(nit).replace("-", ""); }

    public record Inicializacion(HotelAdmin hotel, boolean creada) { }

    private static ApiException noConfigurado() {
        return new ApiException(HttpStatus.NOT_FOUND, "HOTEL_NO_CONFIGURADO",
                "La información del hotel todavía no está disponible.");
    }
}
