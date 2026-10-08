package com.villaserena.api.facturacion;

import java.time.OffsetDateTime;

/** Enlace temporal al PDF del bucket privado (esquema {@code DescargaFactura}). */
public record DescargaFactura(String url, OffsetDateTime expiraEn) {
}
