package com.villaserena.api.estadia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos del comprador de la factura: NIT de Guatemala o "CF". */
public record CompradorFactura(
        @NotBlank(message = "El NIT es obligatorio (o CF).") String nit,
        @NotBlank(message = "El nombre del comprador es obligatorio.") @Size(max = 150) String nombreComprador) {
}
