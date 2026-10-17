package com.villaserena.api.comun;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ejecuta algo solo si la transacción actual se confirma.
 * <p>
 * Los avisos en tiempo real se publican así a propósito: si se publicaran dentro de
 * la transacción y esta se deshiciera, las pantallas mostrarían un pedido o una
 * habitación que no existe, y nada lo corregiría.
 */
public final class TrasCommit {

    private TrasCommit() {
    }

    public static void ejecutar(Runnable accion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            accion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accion.run();
            }
        });
    }
}
