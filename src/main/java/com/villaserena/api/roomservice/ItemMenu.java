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
 * Ítem del menú (tabla {@code items_menu}). La categoría es texto en la base, no
 * una tabla aparte; el contrato pide categorías con id, así que {@code MenuService}
 * los deriva.
 */
@Entity
@Table(name = "items_menu")
@Getter
@Setter
public class ItemMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(name = "clave_foto")
    private String claveFoto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisponibilidadMenu disponibilidad;

    /** ACTIVO o INACTIVO: solo ADMIN los cambia (HU-ADM-05), fuera de este alcance. */
    @Column(nullable = false)
    private String estado;

    @Column(name = "agotado_en")
    private Instant agotadoEn;

    @Column(name = "agotado_por_empleado_id")
    private Long agotadoPorEmpleadoId;

    public boolean estaActivo() {
        return "ACTIVO".equals(estado);
    }

    public boolean sePuedePedir() {
        return estaActivo() && disponibilidad == DisponibilidadMenu.DISPONIBLE;
    }
}
