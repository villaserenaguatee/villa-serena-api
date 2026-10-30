package com.villaserena.api.experiencias;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class ExperienciasDto {
 private ExperienciasDto() {}
 public record Config(@NotNull @Positive Long amenidadId,@NotBlank @Size(max=80) String categoria,
  @NotNull Boolean activa,@NotNull @Pattern(regexp="INMEDIATA|APROBACION|COORDINACION") String modalidad,
  @NotNull @Pattern(regexp="GRATUITA|POR_PERSONA|POR_RESERVA") String tarifa,
  @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2) BigDecimal precio,
  @NotNull @Pattern(regexp="SIN_CARGO|CUENTA|ANTICIPADO") String cobro,
  @NotNull @Min(1) @Max(1440) Integer duracionMinutos,@NotNull Boolean requiereEstadia,
  @NotNull Boolean cancelable,@NotNull @Min(0) @Max(525600) Integer antelacionCancelacionMinutos,
  @NotNull @Min(0) Long version) {}
 public record Slot(@NotNull Instant inicio,@NotNull @Min(1) @Max(10000) Integer capacidad,@NotNull Boolean activo,@NotNull @Min(0) Long version) {}
 public record Booking(@NotNull UUID clave,@NotNull @Positive Long horarioId,@NotNull @Min(1) @Max(10000) Integer personas) {}
 public record Decision(@NotNull @Min(0) Long version,@NotNull @Pattern(regexp="APROBAR|RECHAZAR") String accion,@Size(max=500) String motivo) {}
 public record Cancel(@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String motivo) {}
 public record Inquiry(@NotNull UUID clave,@NotBlank @Size(max=1000) String comentario) {}
 public record Answer(@NotBlank @Size(max=1000) String respuesta) {}
}
