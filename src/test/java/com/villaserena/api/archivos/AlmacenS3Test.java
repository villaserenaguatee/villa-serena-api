package com.villaserena.api.archivos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.villaserena.api.config.PropiedadesVillaSerena;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * {@link AlmacenS3} contra un MinIO real (la misma imagen del Docker local): guarda en
 * el bucket privado, comprueba si existe y la URL firmada permite descargar la foto,
 * mientras que sin firma el bucket privado no la entrega.
 */
class AlmacenS3Test {

    static final String USUARIO = "villaserena";
    static final String CONTRASENA = "contrasena-de-prueba";
    static final String BUCKET = "villaserena-privado";

    static GenericContainer<?> minio;
    static AlmacenS3 almacen;
    static String endpoint;

    @BeforeAll
    static void arrancarMinio() {
        minio = new GenericContainer<>("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
                .withCommand("server", "/data")
                .withEnv("MINIO_ROOT_USER", USUARIO)
                .withEnv("MINIO_ROOT_PASSWORD", CONTRASENA)
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
        minio.start();
        endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        try (S3Client s3 = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(USUARIO, CONTRASENA)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()) {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
        almacen = new AlmacenS3(new PropiedadesVillaSerena(null, null, null, null, null, null, null, null,
                new PropiedadesVillaSerena.Archivos(null, endpoint, USUARIO, CONTRASENA, BUCKET, 10), null));
    }

    @AfterAll
    static void detenerMinio() {
        almacen.cerrar();
        minio.stop();
    }

    @Test
    void guardaYEntregaLaFotoSoloConUrlFirmada() throws Exception {
        byte[] foto = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        almacen.guardarPrivado("incidencias/prueba.jpg", foto, "image/jpeg");

        assertThat(almacen.existePrivado("incidencias/prueba.jpg")).isTrue();
        assertThat(almacen.existePrivado("incidencias/no-existe.jpg")).isFalse();

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String url = almacen.urlFirmada("incidencias/prueba.jpg");
        assertThat(url).contains("X-Amz-Signature").contains("X-Amz-Expires=600");
        HttpResponse<byte[]> firmada = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(firmada.statusCode()).isEqualTo(200);
        assertThat(firmada.body()).isEqualTo(foto);

        HttpResponse<byte[]> sinFirma = http.send(HttpRequest.newBuilder(
                URI.create(endpoint + "/" + BUCKET + "/incidencias/prueba.jpg")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(sinFirma.statusCode()).isEqualTo(403);
    }
}
