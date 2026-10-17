package com.villaserena.api.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;

/**
 * Teléfonos del huésped para push (HU-HUE-17). El token siempre se liga al huésped
 * del JWT: nunca se acepta un id que venga en la petición.
 */
@Service
public class DispositivosPushService {

    private final DispositivoPushRepository dispositivos;

    public DispositivosPushService(DispositivoPushRepository dispositivos) {
        this.dispositivos = dispositivos;
    }

    /** Resultado: el registro y si se creó ahora (201) o ya existía (200). */
    public record Resultado(long id, boolean creado) {
    }

    @Transactional
    public Resultado registrar(long huespedId, String tokenExpo) {
        return dispositivos.findByTokenExpo(tokenExpo)
                .map(existente -> {
                    if (!existente.getHuespedId().equals(huespedId)) {
                        // El teléfono cambió de dueño (otro huésped inició sesión en él):
                        // el token pasa al nuevo, porque las push van al aparato.
                        existente.setHuespedId(huespedId);
                    }
                    return new Resultado(existente.getId(), false);
                })
                .orElseGet(() -> {
                    DispositivoPush nuevo = new DispositivoPush();
                    nuevo.setHuespedId(huespedId);
                    nuevo.setTokenExpo(tokenExpo);
                    return new Resultado(dispositivos.save(nuevo).getId(), true);
                });
    }

    @Transactional
    public void eliminar(long huespedId, long id) {
        DispositivoPush dispositivo = dispositivos.findById(id)
                .filter(d -> d.getHuespedId().equals(huespedId))
                .orElseThrow(ApiException::noEncontrado);
        dispositivos.delete(dispositivo);
        // Se vacía ya: el envío de push consulta los teléfonos con SQL directo y
        // tiene que ver la baja aunque siga abierta la transacción.
        dispositivos.flush();
    }
}
