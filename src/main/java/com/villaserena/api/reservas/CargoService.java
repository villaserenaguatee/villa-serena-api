package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.comun.Responsable;

/**
 * Registro común de cargos. Room Service (Hugo) lo usa al entregar un pedido
 * (HU-RS-06) y Recepción para los cargos manuales (HU-REC-13).
 */
@Service
public class CargoService {

    private final CargoRepository cargos;
    private final CuentaRepository cuentas;
    private final ReservaRepository reservas;
    private final Clock reloj;

    public CargoService(CargoRepository cargos, CuentaRepository cuentas, ReservaRepository reservas, Clock reloj) {
        this.cargos = cargos;
        this.cuentas = cuentas;
        this.reservas = reservas;
        this.reloj = reloj;
    }

    /**
     * Agrega un cargo adicional. Solo con reserva EN_ESTADIA y cuenta ABIERTA
     * (RN-PAG-010); cantidad y precio mayores que 0 (RN-PAG-019). El total se calcula.
     */
    @Transactional
    public Cargo registrarCargo(long cuentaId, NuevoCargo datos, Responsable responsable) {
        if (datos.tipo() == TipoCargo.ALOJAMIENTO) {
            throw new IllegalArgumentException("El alojamiento lo registra ReservaService al crear la reserva.");
        }
        if (datos.cantidad() <= 0 || datos.precioUnitario() == null
                || datos.precioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.datosInvalidos("La cantidad y el precio deben ser mayores que 0.",
                    List.of(new DetalleError("cantidad", "Debe ser mayor que 0."),
                            new DetalleError("precioUnitario", "Debe ser mayor que 0.")));
        }
        Cuenta cuenta = cuentas.findById(cuentaId).orElseThrow(ApiException::noEncontrado);
        Reserva reserva = reservas.findById(cuenta.getReservaId()).orElseThrow(ApiException::noEncontrado);
        if (!cuenta.estaAbierta() || reserva.getEstado() != EstadoReserva.EN_ESTADIA) {
            throw ApiException.conflicto("CUENTA_NO_ABIERTA",
                    "Solo se agregan cargos con la reserva en estadía y la cuenta abierta.");
        }
        return guardar(cuentaId, datos, responsable.empleadoId());
    }

    /** Cargo por alojamiento al crear la reserva (RN-PAG-009). Lo genera el sistema. */
    @Transactional
    Cargo registrarAlojamiento(long cuentaId, String concepto, BigDecimal total) {
        return guardar(cuentaId, new NuevoCargo(TipoCargo.ALOJAMIENTO, null, concepto, 1, total, null), null);
    }

    private Cargo guardar(long cuentaId, NuevoCargo datos, Long empleadoId) {
        Cargo cargo = new Cargo();
        cargo.setCuentaId(cuentaId);
        cargo.setTipo(datos.tipo());
        cargo.setCategoriaServicio(datos.tipo() == TipoCargo.SERVICIO ? datos.categoria() : null);
        cargo.setConcepto(datos.concepto());
        cargo.setCantidad(datos.cantidad());
        cargo.setPrecioUnitario(datos.precioUnitario().setScale(2, RoundingMode.HALF_UP));
        cargo.setTotal(cargo.getPrecioUnitario().multiply(BigDecimal.valueOf(datos.cantidad()))
                .setScale(2, RoundingMode.HALF_UP));
        cargo.setPedidoId(datos.pedidoId());
        cargo.setCreadoPorEmpleadoId(empleadoId);
        cargo.setCreadoEn(reloj.instant());
        return cargos.save(cargo);
    }
}
