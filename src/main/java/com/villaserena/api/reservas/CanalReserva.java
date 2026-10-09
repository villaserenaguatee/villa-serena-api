package com.villaserena.api.reservas;

/** Canal de origen (RN-RES-010). BOOKING y EXPEDIA son externos. */
public enum CanalReserva {
    DIRECTO_WEB, RECEPCION, BOOKING, EXPEDIA;

    public boolean esExterno() {
        return this == BOOKING || this == EXPEDIA;
    }
}
