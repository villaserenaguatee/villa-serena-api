package com.villaserena.api.comun;

import java.time.OffsetDateTime;

/**
 * Un cambio de estado con los estados como texto (esquema {@code HistorialOperacion}).
 * Sirve para pedidos, solicitudes e incidencias, cuyos estados no son los de la
 * reserva. {@code responsable} es el nombre del empleado o un actor como SISTEMA;
 * nunca correo, teléfono ni documento.
 */
public record HistorialOperacionVista(String estadoAnterior, String estadoNuevo, String responsable,
        OffsetDateTime fechaHora, String motivo) {
}
