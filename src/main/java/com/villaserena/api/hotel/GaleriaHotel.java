package com.villaserena.api.hotel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;

@Component
public class GaleriaHotel {
    private final JdbcTemplate jdbc;
    private final AlmacenArchivos almacen;

    public GaleriaHotel(JdbcTemplate jdbc, AlmacenArchivos almacen) {
        this.jdbc = jdbc;
        this.almacen = almacen;
    }

    private static List<FotoHotelPeticion> ordenar(List<FotoHotelPeticion> fotos) {
        return fotos.stream().sorted(Comparator.comparing(FotoHotelPeticion::orden)
                .thenComparing(FotoHotelPeticion::clave)).toList();
    }

    public static String revision(List<FotoHotelPeticion> fotos) {
        StringBuilder contenido = new StringBuilder();
        for (var foto : ordenar(fotos)) contenido.append(foto.clave().length()).append(':')
                .append(foto.clave()).append(':').append(foto.orden()).append(';');
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido.toString().getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(hash) + "\"";
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no está disponible", error);
        }
    }

    /** HotelService mantiene bloqueada la fila única durante comparación y sustitución. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void actualizar(List<FotoHotelPeticion> fotos, String revision) {
        if (fotos == null) return;
        var actuales = jdbc.query("SELECT object_key, sort_order FROM hotel_photos ORDER BY sort_order, id",
                (rs, i) -> new FotoHotelPeticion(rs.getString(1), rs.getInt(2)));
        if (ordenar(actuales).equals(ordenar(fotos))) return;
        if (fotos.stream().map(FotoHotelPeticion::clave).distinct().count() != fotos.size()
                || fotos.stream().map(FotoHotelPeticion::orden).distinct().count() != fotos.size()) {
            throw invalida("Las fotografías y sus posiciones no se pueden repetir.");
        }
        Set<String> anteriores = actuales.stream().map(FotoHotelPeticion::clave).collect(Collectors.toSet());
        for (var foto : fotos) {
            if (anteriores.contains(foto.clave())) continue;
            if (!foto.clave().matches("hotel/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)")) {
                throw invalida("Selecciona una fotografía cargada para la galería del hotel.");
            }
        }
        if (!revision(actuales).equals(revision)) throw ApiException.conflicto("GALERIA_CAMBIADA",
                "Actualiza la galería y revisa los cambios antes de guardar. Las fotografías existentes se conservaron.");
        for (var foto : fotos) {
            if (anteriores.contains(foto.clave())) continue;
            boolean existe;
            try {
                existe = almacen.existePublico(foto.clave());
            } catch (RuntimeException error) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ALMACEN_NO_DISPONIBLE",
                        "No se pudo comprobar la fotografía. Conservamos la galería anterior.");
            }
            if (!existe) throw invalida("La fotografía no está disponible en el almacenamiento público.");
        }
        jdbc.update("DELETE FROM hotel_photos");
        for (var foto : fotos) jdbc.update("INSERT INTO hotel_photos (object_key, sort_order) VALUES (?, ?)",
                foto.clave(), foto.orden());
    }

    private static ApiException invalida(String mensaje) {
        return ApiException.datosInvalidos(mensaje, List.of(new DetalleError("fotos", mensaje)));
    }
}
