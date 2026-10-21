package com.villaserena.api.reservas.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import com.villaserena.api.huespedes.HuespedDatos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReservaWebPeticion(
        @NotNull(message = "El tipo de habitación es obligatorio.") Long tipoHabitacionId,
        @NotNull(message = "La fecha de entrada es obligatoria.") LocalDate entrada,
        @NotNull(message = "La fecha de salida es obligatoria.") LocalDate salida,
        @NotNull(message = "El número de huéspedes es obligatorio.") @Min(value = 1, message = "Debe haber al menos un huésped.") Integer numeroHuespedes,
        @NotNull(message = "Los datos del huésped son obligatorios.") @Valid HuespedDatos huesped,
        @Schema(description = "Hora estimada local de llegada (America/Guatemala), independiente del check-in. "
                + "Opcional; omitida o null significa sin indicar.", example = "18:30", nullable = true)
        @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]",
                message = "La hora estimada de llegada debe tener formato HH:mm entre 00:00 y 23:59.")
        // Omitir el nuevo campo nulo conserva las huellas de idempotencia anteriores.
        @JsonInclude(JsonInclude.Include.NON_NULL) String horaEstimadaLlegada) {

    public LocalTime horaEstimadaLlegadaLocal() {
        return horaEstimadaLlegada == null ? null : LocalTime.parse(horaEstimadaLlegada);
    }
}
