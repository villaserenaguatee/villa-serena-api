package com.villaserena.api.catalogo.dto;

public record FotoCatalogo(
        String clave,
        String url,
        boolean principal,
        int orden
) {}
