package com.villaserena.api.roomservice.dto;

import java.util.List;

/** Menú completo (esquema {@code MenuApp}). Los agotados se incluyen pero no se pueden pedir. */
public record MenuApp(List<CategoriaMenu> categorias, List<ItemMenuApp> items) {
}
