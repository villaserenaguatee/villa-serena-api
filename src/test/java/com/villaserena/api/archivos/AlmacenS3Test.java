package com.villaserena.api.archivos;

import static org.assertj.core.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.villaserena.api.config.PropiedadesVillaSerena;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Contrato HTTP del SDK; la compatibilidad remota se verifica explícitamente por separado. */
class AlmacenS3Test {
    private HttpServer servidor;
    private String endpoint;
    private AlmacenS3 almacen;
    private final ConcurrentHashMap<String, byte[]> objetos = new ConcurrentHashMap<>();
    private final AtomicReference<String> autorizacion = new AtomicReference<>();
    private final AtomicReference<String> codificacion = new AtomicReference<>();

    @BeforeEach
    void preparar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            try {
                String clave = intercambio.getRequestURI().getPath();
                autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
                codificacion.set(intercambio.getRequestHeaders().getFirst("Content-Encoding"));
                if (clave.endsWith("/denegado")) {
                    intercambio.sendResponseHeaders(403, -1);
                } else if (intercambio.getRequestMethod().equals("PUT")) {
                    objetos.put(clave, intercambio.getRequestBody().readAllBytes());
                    intercambio.sendResponseHeaders(200, -1);
                } else {
                    byte[] datos = objetos.get(clave);
                    if (datos == null) intercambio.sendResponseHeaders(404, -1);
                    else if (intercambio.getRequestMethod().equals("HEAD")) {
                        intercambio.getResponseHeaders().set("Content-Length", String.valueOf(datos.length));
                        intercambio.sendResponseHeaders(200, -1);
                    } else {
                        intercambio.sendResponseHeaders(200, datos.length);
                        intercambio.getResponseBody().write(datos);
                    }
                }
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
        endpoint = "http://127.0.0.1:" + servidor.getAddress().getPort();
        almacen = crear(endpoint, "auto", "clave-pruebas", "secreto-pruebas", true, "privado");
    }

    private AlmacenS3 crear(String url, String region, String usuario, String secreto, boolean ruta, String bucket) {
        return new AlmacenS3(new PropiedadesVillaSerena(null, null, null, null, null, null, null, null,
                new PropiedadesVillaSerena.Archivos(null, url, region, usuario, secreto, bucket, bucket.isEmpty() ? "" : bucket + "-publico", 10, ruta), null));
    }

    @AfterEach
    void cerrar() {
        almacen.cerrar();
        servidor.stop(0);
    }

    @Test
    void r2EnviaBytesSinChunkedYFirmaConRegionAuto() {
        byte[] pdf = "%PDF-1.4 prueba".getBytes(StandardCharsets.UTF_8);
        almacen.guardarPrivado("facturas/factura-prueba.pdf", pdf, "application/pdf");
        assertThat(objetos.get("/privado/facturas/factura-prueba.pdf")).isEqualTo(pdf);
        assertThat(autorizacion.get()).contains("/auto/s3/aws4_request");
        assertThat(codificacion.get()).isNull();
        assertThat(almacen.existePrivado("facturas/factura-prueba.pdf")).isTrue();
        assertThat(almacen.leerPrivado("facturas/factura-prueba.pdf")).isEqualTo(pdf);
        assertThat(almacen.existePrivado("ausente")).isFalse();
        String url = URLDecoder.decode(almacen.urlFirmada("facturas/factura-prueba.pdf"), StandardCharsets.UTF_8);
        assertThat(url).startsWith(endpoint + "/privado/").contains("X-Amz-Signature", "X-Amz-Expires=600",
                "/auto/s3/aws4_request", "Factura-Villa-Serena-prueba.pdf");
    }

    @Test
    void erroresDePermisosNoSeConfundenConArchivosAusentes() {
        assertThatThrownBy(() -> almacen.existePrivado("denegado"))
                .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(403));
    }

    @Test
    void s3NativoFirmaEnSuRegionSinEndpointPersonalizado() {
        var s3 = crear("", "us-west-2", "clave-pruebas", "secreto-pruebas", false, "hotel-privado");
        try {
            URI url = URI.create(s3.urlFirmada("incidencias/foto.jpg"));
            assertThat(url.getHost()).isEqualTo("hotel-privado.s3.us-west-2.amazonaws.com");
            assertThat(URLDecoder.decode(url.getRawQuery(), StandardCharsets.UTF_8)).contains("/us-west-2/s3/aws4_request");
        } finally {
            s3.cerrar();
        }
    }

    @Test
    void cadenaAwsPredeterminadaIncluyeElTokenDeCredencialesTemporales() {
        var valores = java.util.Map.of("aws.accessKeyId", "clave-temporal-pruebas",
                "aws.secretAccessKey", "secreto-temporal-pruebas", "aws.sessionToken", "token-temporal-pruebas");
        var anteriores = new java.util.HashMap<String, String>();
        valores.forEach((key, value) -> anteriores.put(key, System.setProperty(key, value)));
        var s3 = crear("", "us-east-1", "", "", false, "hotel-privado");
        try {
            String url = URLDecoder.decode(s3.urlFirmada("foto.jpg"), StandardCharsets.UTF_8);
            assertThat(url).contains("X-Amz-Security-Token=token-temporal-pruebas", "clave-temporal-pruebas");
        } finally {
            s3.cerrar();
            anteriores.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }

    @Test
    void noExigeCredencialesHastaUsarElAlmacenYRechazaConfiguracionParcial() {
        var incompleto = crear(endpoint, "auto", "clave-pruebas", "", true, "privado");
        try {
            assertThatThrownBy(() -> incompleto.urlFirmada("foto.jpg"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Configura juntas");
        } finally {
            incompleto.cerrar();
        }
        var sinBucket = crear(endpoint, "auto", "", "", true, "");
        try {
            assertThatThrownBy(() -> sinBucket.existePrivado("foto.jpg"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("STORAGE_BUCKET_PRIVATE");
            assertThatThrownBy(() -> sinBucket.existePublico("tipos/foto.jpg"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("STORAGE_BUCKET_PUBLIC");
        } finally {
            sinBucket.cerrar();
        }
    }

    @Test
    void publicoPermiteGuardarYVerificarExistenciaConTratamiento403() {
        byte[] foto = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        almacen.guardarPublico("tipos/suite.jpg", foto, "image/jpeg");
        assertThat(objetos.get("/privado-publico/tipos/suite.jpg")).isEqualTo(foto);
        assertThat(almacen.existePublico("tipos/suite.jpg")).isTrue();
        assertThat(almacen.existePublico("tipos/ausente.jpg")).isFalse();
        // Sin s3:ListBucket, 403 en HeadObject indica archivo ausente bajo la política de tipos/*
        assertThat(almacen.existePublico("tipos/denegado")).isFalse();
    }
}
