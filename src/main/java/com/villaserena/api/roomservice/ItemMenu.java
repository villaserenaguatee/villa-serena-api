package com.villaserena.api.roomservice;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ítem del menú (tabla {@code menu_items}). La categoría es texto en la base, no
 * una tabla aparte; el contrato pide categorías con id, así que {@code MenuService}
 * los deriva.
 */
@Entity
@Table(name = "menu_items")
@Getter
@Setter
public class ItemMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false)
    private String categoria;

    @Column(name = "category_id")
    private Long categoriaId;

    @Column(name = "name", nullable = false)
    private String nombre;

    @Column(name = "description", nullable = false)
    private String descripcion;

    @Column(name = "price", nullable = false)
    private BigDecimal precio;

    @Column(name = "photo_key")
    private String claveFoto;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false)
    private DisponibilidadMenu disponibilidad;

    /** ACTIVO o INACTIVO: solo ADMIN los cambia (HU-ADM-05), fuera de este alcance. */
    @Column(name = "status", nullable = false)
    private String estado;

    @Column(name = "exhausted_at")
    private Instant agotadoEn;

    @Column(name = "exhausted_by_employee_id")
    private Long agotadoPorEmpleadoId;

    public boolean estaActivo() {
        return "ACTIVO".equals(estado);
    }

    public boolean sePuedePedir() {
        return estaActivo() && disponibilidad == DisponibilidadMenu.DISPONIBLE;
    }
}
