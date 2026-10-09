package com.villaserena.api.canal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.villaserena.api.huespedes.HuespedDatos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Reserva que envía un canal externo (esquema {@code ReservaCanalPeticion}).
 * {@code montoTotal} es lo que ya cobró el canal: el hotel no recalcula tarifas
 * para estas reservas (RN-TAR-010).
 */
public record ReservaCanalPeticion(
        @NotBlank(message = "El identificador externo es obligatorio.") @Size(max = 60) String identificadorExterno,
        @NotNull(message = "El tipo de habitación es obligatorio.") Long tipoHabitacionId,
        @NotNull(message = "La fecha de entrada es obligatoria.") LocalDate entrada,
        @NotNull(message = "La fecha de salida es obligatoria.") LocalDate salida,
        @NotNull(message = "El número de huéspedes es obligatorio.") @Min(value = 1, message = "Debe haber al menos un huésped.") Integer numeroHuespedes,
        @NotNull(message = "El monto total es obligatorio.") @DecimalMin(value = "0.00", inclusive = false, message = "El monto total debe ser mayor que cero.") @Digits(integer = 10, fraction = 2) BigDecimal montoTotal,
        @NotNull(message = "Los datos del huésped son obligatorios.") @Valid HuespedDatos huesped) {
}
