package com.villaserena.api.archivos;

import java.net.URI;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.villaserena.api.config.PropiedadesVillaSerena;

import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** R2 en desarrollo y S3 en producción; las conexiones se crean al primer uso. */
@Component
public class AlmacenS3 implements AlmacenArchivos {

    private final PropiedadesVillaSerena.Archivos config;
    private volatile S3Client cliente;
    private volatile S3Presigner firmador;
    private DefaultCredentialsProvider credencialesPredeterminadas;

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
                .getObjectRequest(g -> {
                    g.bucket(config.bucketPrivado()).key(clave);
                    if (clave.startsWith("facturas/")) g.responseContentDisposition("attachment; filename=\""
                            + clave.substring(clave.lastIndexOf('/')+1).replaceFirst("^factura-", "Factura-Villa-Serena-") + "\"");
                }))
                .url().toString();
    }

    @Override
    public byte[] leerPrivado(String clave) {
        return cliente().getObjectAsBytes(b -> b.bucket(config.bucketPrivado()).key(clave)).asByteArray();
    }

    private synchronized S3Client cliente() {
        if (cliente == null) {
            validarBucket();
            var builder = S3Client.builder()
                    .region(Region.of(config.region()))
                    .credentialsProvider(credenciales())
                    .serviceConfiguration(rutas())
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
            if (!vacio(config.endpoint())) builder.endpointOverride(URI.create(config.endpoint()));
            cliente = builder.build();
        }
        return cliente;
    }

    private synchronized S3Presigner firmador() {
        if (firmador == null) {
            validarBucket();
            var builder = S3Presigner.builder()
                    .region(Region.of(config.region()))
                    .credentialsProvider(credenciales())
                    .serviceConfiguration(rutas());
            if (!vacio(config.endpoint())) builder.endpointOverride(URI.create(config.endpoint()));
            firmador = builder.build();
        }
        return firmador;
    }

    private AwsCredentialsProvider credenciales() {
        if (vacio(config.usuario()) != vacio(config.contrasena())) {
            throw new IllegalStateException("Configura juntas STORAGE_ACCESS_KEY_ID y STORAGE_SECRET_ACCESS_KEY.");
        }
        if (!vacio(config.usuario())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(config.usuario(), config.contrasena()));
        }
        if (credencialesPredeterminadas == null) {
            credencialesPredeterminadas = DefaultCredentialsProvider.builder().build();
        }
        return credencialesPredeterminadas;
    }

    private void validarBucket() {
        if (vacio(config.bucketPrivado())) {
            throw new IllegalStateException("Falta STORAGE_BUCKET_PRIVATE para usar archivos remotos.");
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private S3Configuration rutas() {
        // R2 requiere cargas sin chunked encoding; S3 también admite este formato.
        return S3Configuration.builder().pathStyleAccessEnabled(config.estiloRuta())
                .chunkedEncodingEnabled(false).build();
    }

    @PreDestroy
    synchronized void cerrar() {
        if (cliente != null) {
            cliente.close();
        }
        if (firmador != null) {
            firmador.close();
        }
        if (credencialesPredeterminadas != null) {
            credencialesPredeterminadas.close();
        }
    }
}
