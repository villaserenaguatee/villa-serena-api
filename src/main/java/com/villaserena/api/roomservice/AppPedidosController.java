package com.villaserena.api.roomservice;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.roomservice.dto.MenuApp;
import com.villaserena.api.roomservice.dto.NuevoPedidoPeticion;
import com.villaserena.api.roomservice.dto.PedidoHuesped;

import jakarta.validation.Valid;

/**
 * Menú y pedidos del huésped en la app (HU-HUE-09, HU-HUE-10, HU-HUE-11). Solo sus
 * propias reservas: una ajena responde 404.
 */
@RestController
@RequestMapping("/api/v1/app/reservas/{codigo}")
@PreAuthorize("hasRole('HUESPED')")
public class AppPedidosController {

    private final PedidoService pedidos;
    private final MenuService menu;

    public AppPedidosController(PedidoService pedidos, MenuService menu) {
        this.pedidos = pedidos;
        this.menu = menu;
    }

    @GetMapping("/menu")
    public MenuApp menu(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return menu.menu();
    }

    @GetMapping("/pedidos")
    public List<PedidoHuesped> mios(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return pedidos.mios(codigo, UsuarioActual.id());
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoHuesped crear(@PathVariable String codigo, @Valid @RequestBody NuevoPedidoPeticion p) {
        ReservaService.validarCodigo(codigo);
        return pedidos.crear(codigo, UsuarioActual.id(), p);
    }

    @GetMapping("/pedidos/{id}")
    public PedidoHuesped mio(@PathVariable String codigo, @PathVariable long id) {
        ReservaService.validarCodigo(codigo);
        return pedidos.mio(codigo, UsuarioActual.id(), id);
    }
}
