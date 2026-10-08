package com.villaserena.api.archivos;

import java.net.URI;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.villaserena.api.config.PropiedadesVillaSerena;

import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link AlmacenArchivos} sobre MinIO con el AWS SDK (API S3). Usa rutas tipo
 * {@code endpoint/bucket/clave}, que es lo que MinIO espera. Los clientes se crean al
 * primer uso, así el API arranca aunque MinIO no esté encendido.
 */
@Component
public class AlmacenS3 implements AlmacenArchivos {

    private final PropiedadesVillaSerena.Archivos config;
    private volatile S3Client cliente;
    private volatile S3Presigner firmador;

    public AlmacenS3(PropiedadesVillaSerena propiedades) {
        this.config = propiedades.archivos();
    }

    @Override
    public void guardarPrivado(String clave, byte[] contenido, String tipoContenido) {
        cliente().putObject(b -> b.bucket(config.bucketPrivado()).key(clave).contentType(tipoContenido),
                RequestBody.fromBytes(contenido));
    }

    @Override
    public boolean existePrivado(String clave) {
        try {
            cliente().headObject(b -> b.bucket(config.bucketPrivado()).key(clave));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public String urlFirmada(String clave) {
        return firmador().presignGetObject(b -> b
                .signatureDuration(Duration.ofMinutes(config.minutosUrlFirmada()))
                .getObjectRequest(g -> g.bucket(config.bucketPrivado()).key(clave)))
                .url().toString();
    }

    private synchronized S3Client cliente() {
        if (cliente == null) {
            cliente = S3Client.builder()
                    .endpointOverride(URI.create(config.endpoint()))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(credenciales())
                    .serviceConfiguration(rutas())
                    .build();
        }
        return cliente;
    }

    private synchronized S3Presigner firmador() {
        if (firmador == null) {
            firmador = S3Presigner.builder()
                    .endpointOverride(URI.create(config.endpoint()))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(credenciales())
                    .serviceConfiguration(rutas())
                    .build();
        }
        return firmador;
    }

    private StaticCredentialsProvider credenciales() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(config.usuario(), config.contrasena()));
    }

    private static S3Configuration rutas() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    @PreDestroy
    synchronized void cerrar() {
        if (cliente != null) {
            cliente.close();
        }
        if (firmador != null) {
            firmador.close();
        }
    }
}
