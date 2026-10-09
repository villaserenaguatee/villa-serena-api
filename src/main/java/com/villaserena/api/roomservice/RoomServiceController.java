package com.villaserena.api.roomservice;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.reservas.dto.MotivoPeticion;
import com.villaserena.api.roomservice.dto.AvanzarPedidoPeticion;
import com.villaserena.api.roomservice.dto.ItemMenuApp;
import com.villaserena.api.roomservice.dto.MenuApp;
import com.villaserena.api.roomservice.dto.PedidoRoomService;

import jakarta.validation.Valid;

/** Pantalla de Room Service: cola, detalle, avanzar, cancelar y menú (HU-RS-01 a HU-RS-06). */
@RestController
@RequestMapping("/api/v1/room-service")
@PreAuthorize("hasRole('ROOM_SERVICE')")
public class RoomServiceController {

    private final PedidoService pedidos;
    private final MenuService menu;

    public RoomServiceController(PedidoService pedidos, MenuService menu) {
        this.pedidos = pedidos;
        this.menu = menu;
    }

    @GetMapping("/pedidos")
    public List<PedidoRoomService> cola() {
        return pedidos.cola();
    }

    @GetMapping("/pedidos/{id}")
    public PedidoRoomService detalle(@PathVariable long id) {
        return pedidos.detalle(id);
    }

    @PostMapping("/pedidos/{id}/avanzar")
    public PedidoRoomService avanzar(@PathVariable long id, @Valid @RequestBody AvanzarPedidoPeticion p) {
        return pedidos.avanzar(id, p.estadoEsperado(), p.nuevoEstado(), UsuarioActual.id());
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public PedidoRoomService cancelar(@PathVariable long id, @Valid @RequestBody MotivoPeticion p) {
        return pedidos.cancelar(id, p.motivo(), Responsable.empleado(UsuarioActual.id()));
    }

    @GetMapping("/menu")
    public MenuApp menu() {
        return menu.menu();
    }

    @PostMapping("/menu/items/{id}/agotar")
    public ItemMenuApp agotar(@PathVariable long id) {
        return menu.agotar(id, UsuarioActual.id());
    }
}
