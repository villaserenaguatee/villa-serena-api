package com.villaserena.api.piso.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.villaserena.api.reservas.dto.HabitacionReferencia;

/** Vistas de incidencias del contrato. */
public final class Incidencias {

    private Incidencias() {
    }

    /** Respuesta al reportar (esquema {@code IncidenciaCreada}). */
    public record Creada(Long id, Long habitacionId, String estado, OffsetDateTime reportadaEn) {
    }

    /** Fila de la lista (esquema {@code IncidenciaResumen}); la foto con URL firmada. */
    public record Resumen(Long id, HabitacionReferencia habitacion, String descripcion, boolean impideUso,
            boolean habitacionOcupada, String estado, ResponsableEmpleado reportadaPor, OffsetDateTime reportadaEn,
            ResponsableEmpleado tecnicoACargo, OffsetDateTime resueltaEn, String fotoUrl) {
    }

    /** Detalle (esquema {@code IncidenciaDetalle}). */
    public record Detalle(Long id, HabitacionReferencia habitacion, String descripcion, boolean impideUso,
            boolean habitacionOcupada, String estado, ResponsableEmpleado reportadaPor, OffsetDateTime reportadaEn,
            ResponsableEmpleado tecnicoACargo, OffsetDateTime resueltaEn, String fotoUrl, String solucion,
            List<HistorialOperacion> historial) {
    }

    /** Cambio de estado con el nombre del responsable (esquema {@code HistorialOperacion}). */
    public record HistorialOperacion(String estadoAnterior, String estadoNuevo, String responsable,
            OffsetDateTime fechaHora, String motivo) {
    }
}
