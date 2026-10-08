package com.villaserena.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.villaserena.api.archivos.AlmacenArchivos;

/** Bucket privado en memoria para las pruebas que no necesitan MinIO. */
@TestConfiguration(proxyBeanMethods = false)
public class AlmacenEnMemoria {

    public static final String URL_FIRMADA = "https://minio.test/firmada/";

    @Bean
    @Primary
    Almacen almacenEnMemoria() {
        return new Almacen();
    }

    public static class Almacen implements AlmacenArchivos {

        private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

        @Override
        public void guardarPrivado(String clave, byte[] contenido, String tipoContenido) {
            objetos.put(clave, contenido);
        }

        @Override
        public boolean existePrivado(String clave) {
            return objetos.containsKey(clave);
        }

        @Override
        public String urlFirmada(String clave) {
            return URL_FIRMADA + clave;
        }

        public Map<String, byte[]> objetos() {
            return objetos;
        }
    }
}
