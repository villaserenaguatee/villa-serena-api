package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.*;

/** La galería pertenece al tipo; los IDs físicos acreditan el contenido de la oferta. */
public record GuardarOfertaPeticion(
        @Positive long tipoHabitacionId,
        @NotBlank @Size(max=100) String nombre,
        @NotBlank @Size(max=4000) String descripcion,
        @Min(1) int piso,
        @Min(1) int capacidad,
        @NotNull @DecimalMin("0.01") BigDecimal superficieM2,
        @NotEmpty @Size(max=20) Map<@NotBlank @Size(max=40) String, @NotNull @Min(1) @Max(30) Integer> camas,
        @NotNull @Size(max=100) List<@NotBlank @Size(max=200) String> caracteristicas,
        @NotNull @Size(max=30) List<@NotBlank @Size(max=100) String> preferencias,
        @NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal precioPorNoche,
        @NotNull Boolean publicada,
        @Min(0) int orden,
        @NotNull @Size(max=1000) List<@NotNull @Positive Long> habitacionIds) {}
