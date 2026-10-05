package com.villaserena.api.estadia;

/**
 * NIT de Guatemala (RN-FAC-005): dígitos y un dígito verificador (0-9 o K), con
 * guion opcional, o "CF" (consumidor final).
 * <p>
 * Verificador: cada dígito del cuerpo se multiplica por su posición contada desde
 * la derecha empezando en 2; el resultado es (11 − suma mod 11) mod 11, y 10 es K.
 */
public final class Nit {

    private Nit() {
    }

    public static boolean esValido(String nit) {
        if (nit == null) {
            return false;
        }
        String limpio = nit.trim().toUpperCase().replace("-", "");
        if ("CF".equals(limpio)) {
            return true;
        }
        if (!limpio.matches("^[0-9]+[0-9K]$") || limpio.length() < 2) {
            return false;
        }
        String cuerpo = limpio.substring(0, limpio.length() - 1);
        char verificador = limpio.charAt(limpio.length() - 1);
        int suma = 0;
        for (int i = 0; i < cuerpo.length(); i++) {
            suma += (cuerpo.charAt(i) - '0') * (cuerpo.length() + 1 - i);
        }
        int resultado = (11 - suma % 11) % 11;
        char esperado = resultado == 10 ? 'K' : (char) ('0' + resultado);
        return verificador == esperado;
    }

    /** Forma normalizada para guardar: "CF" o el NIT en mayúsculas, sin espacios. */
    public static String normalizar(String nit) {
        String limpio = nit.trim().toUpperCase();
        return "CF".equals(limpio) ? "CF" : limpio;
    }
}
