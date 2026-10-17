package com.villaserena.api.roomservice.dto;

import com.villaserena.api.roomservice.EstadoPedido;

import jakarta.validation.constraints.NotNull;

/**
 * Petición de avance (esquema {@code AvanzarPedidoPeticion}). {@code estadoEsperado}
 * es el estado que el empleado ve en su pantalla: si ya no es ese, se responde 409.
 */
public record AvanzarPedidoPeticion(
        @NotNull(message = "El estado esperado es obligatorio.") EstadoPedido estadoEsperado,
        @NotNull(message = "El nuevo estado es obligatorio.") EstadoPedido nuevoEstado) {
}
