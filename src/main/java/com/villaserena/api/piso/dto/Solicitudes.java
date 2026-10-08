package com.villaserena.api.piso.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.villaserena.api.reservas.dto.HabitacionReferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Vistas y peticiones de las solicitudes del huésped (OBJ-3B-2). */
public final class Solicitudes {

    private Solicitudes() {
    }

    /** Artículo del catálogo con su máximo por solicitud (esquema {@code ArticuloSolicitable}). */
    public record ArticuloSolicitable(Long id, String nombre, int cantidadMaxima) {
    }

    /** Artículo pedido (esquema {@code ArticuloSolicitud}). */
    public record Articulo(Long articuloId, String nombre, int cantidad) {
    }

    /** Lo que ve el huésped (esquema {@code SolicitudHuesped}). */
    public record DelHuesped(Long id, String tipo, OffsetDateTime creadaEn, String estado, String comentario,
            List<Articulo> articulos) {
    }

    /** Lo que ve Limpieza, sin datos personales del huésped (esquema {@code SolicitudLimpieza}). */
    public record ParaLimpieza(Long id, String tipo, OffsetDateTime creadaEn, String estado, String comentario,
            List<Articulo> articulos, HabitacionReferencia habitacion, ResponsableEmpleado empleadoACargo) {
    }

    /** Esquema {@code SolicitarLimpiezaPeticion}; el cuerpo puede faltar. */
    public record PedirLimpieza(@Size(max = 500) String comentario) {
    }

    /** Esquema {@code SolicitarArticulosPeticion}. */
    public record PedirArticulos(
            @NotEmpty(message = "Elige al menos un artículo.") @Valid List<ArticuloPedido> articulos) {
    }

    /** Esquema {@code ArticuloSolicitudPeticion}. */
    public record ArticuloPedido(@NotNull @Positive Long articuloId,
            @NotNull @Positive(message = "La cantidad debe ser al menos 1.") Integer cantidad) {
    }
}
