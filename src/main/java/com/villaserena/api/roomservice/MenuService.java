package com.villaserena.api.roomservice;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.roomservice.dto.CategoriaMenu;
import com.villaserena.api.roomservice.dto.ItemMenuApp;
import com.villaserena.api.roomservice.dto.MenuApp;

/**
 * Menú de Room Service (HU-RS-05, HU-HUE-09). Los ítems agotados se muestran pero
 * no se pueden pedir.
 * <p>
 * El contrato pide categorías con id y los ítems con {@code categoriaId}, pero en la
 * base la categoría es un texto de {@code items_menu} y no existe tabla de
 * categorías. Los ids se derivan del orden en que aparecen las categorías, así que
 * son estables mientras no se agregue una categoría nueva. Es una diferencia entre
 * el contrato y el esquema que hay que resolver en la issue del contrato; aquí no se
 * crean migraciones.
 */
@Service
public class MenuService {

    private final ItemMenuRepository items;
    private final Clock reloj;
    private final String urlPublica;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public MenuService(ItemMenuRepository items, Clock reloj, PropiedadesVillaSerena propiedades,
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.items = items;
        this.reloj = reloj;
        this.urlPublica = propiedades.archivos().urlPublica();
        this.jdbc = jdbc;
    }

    /** El menú activo, agrupado por categoría. Es el mismo para la app y para Room Service. */
    @Transactional(readOnly = true)
    public MenuApp menu() {
        Map<String, Long> idsCategoria = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT id, name FROM menu_categories ORDER BY id", (rs, i) -> {
                idsCategoria.put(rs.getString("name"), rs.getLong("id"));
                return null;
            });
        } catch (Exception ignored) {
            // fallback defensivo
        }
        List<ItemMenuApp> vista = new ArrayList<>();
        for (ItemMenu item : items.activos()) {
            long categoriaId = item.getCategoriaId() != null
                    ? item.getCategoriaId()
                    : idsCategoria.computeIfAbsent(item.getCategoria(), c -> idsCategoria.size() + 1L);
            vista.add(new ItemMenuApp(item.getId(), categoriaId, item.getNombre(), item.getDescripcion(),
                    item.getPrecio(), fotoUrl(item), item.getDisponibilidad()));
        }
        List<CategoriaMenu> categorias = idsCategoria.entrySet().stream()
                .map(e -> new CategoriaMenu(e.getValue(), e.getKey()))
                .toList();
        return new MenuApp(categorias, vista);
    }

    /**
     * Marca un ítem AGOTADO (HU-RS-05). Solo en ese sentido: reactivarlo es de ADMIN
     * (HU-ADM-05), fuera de este alcance. No altera los pedidos ya hechos, porque
     * guardan su propio precio y su propia línea.
     */
    @Transactional
    public ItemMenuApp agotar(long itemId, long empleadoId) {
        ItemMenu item = items.findById(itemId)
                .filter(ItemMenu::estaActivo)
                .orElseThrow(ApiException::noEncontrado);
        if (item.getDisponibilidad() == DisponibilidadMenu.AGOTADO) {
            throw ApiException.conflicto("ITEM_YA_AGOTADO", "El ítem ya estaba marcado como agotado.");
        }
        item.setDisponibilidad(DisponibilidadMenu.AGOTADO);
        item.setAgotadoEn(reloj.instant());
        item.setAgotadoPorEmpleadoId(empleadoId);
        items.save(item);
        return new ItemMenuApp(item.getId(), categoriaIdDe(item.getCategoria()), item.getNombre(),
                item.getDescripcion(), item.getPrecio(), fotoUrl(item), item.getDisponibilidad());
    }

    private long categoriaIdDe(String categoria) {
        return menu().categorias().stream()
                .filter(c -> c.nombre().equals(categoria))
                .findFirst()
                .map(CategoriaMenu::id)
                .orElse(0L);
    }

    private String fotoUrl(ItemMenu item) {
        return item.getClaveFoto() == null ? null : urlPublica + "/" + item.getClaveFoto();
    }
}
