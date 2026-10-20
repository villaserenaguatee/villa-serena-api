package com.villaserena.api.archivos;

import static org.assertj.core.api.Assertions.*;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.villaserena.api.config.PropiedadesVillaSerena;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Opt-in: escribe un objeto aislado y lo elimina; nunca se ejecuta por defecto en CI. */
@EnabledIfEnvironmentVariable(named = "STORAGE_SMOKE_TEST", matches = "true")
class AlmacenS3RemoteTest {
    @Test
    void verificaCargaLecturaFirmaYPrivacidadRemotas() throws Exception {
        Properties valores = new Properties();
        String archivo = System.getenv().getOrDefault("STORAGE_SMOKE_CONFIG", ".env.dev");
        if (!archivo.isBlank() && Files.isRegularFile(Path.of(archivo))) {
            try (Reader reader = Files.newBufferedReader(Path.of(archivo), StandardCharsets.UTF_8)) {
                valores.load(reader);
            }
        }
        String endpoint = valor(valores, "STORAGE_ENDPOINT", "");
        String region = valor(valores, "STORAGE_REGION", "us-east-1");
        String bucket = valor(valores, "STORAGE_BUCKET_PRIVATE", "");
        String usuario = valor(valores, "STORAGE_ACCESS_KEY_ID", "");
        String secreto = valor(valores, "STORAGE_SECRET_ACCESS_KEY", "");
        boolean estiloRuta = Boolean.parseBoolean(valor(valores, "STORAGE_PATH_STYLE", "false"));
        assertThat(bucket).as("Configura STORAGE_BUCKET_PRIVATE").isNotBlank();
        assertThat(usuario.isBlank()).as("Configura ambas credenciales o usa el rol/perfil AWS").isEqualTo(secreto.isBlank());
        if (!endpoint.isBlank()) assertThat(URI.create(endpoint).getScheme()).isEqualTo("https");
        var props = new PropiedadesVillaSerena(null, null, null, null, null, null, null, null,
                new PropiedadesVillaSerena.Archivos(null, endpoint, region, usuario, secreto, bucket, 10, estiloRuta), null);
        var almacen = new AlmacenS3(props);
        AwsCredentialsProvider credenciales = usuario.isBlank() ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(usuario, secreto));
        var builder = S3Client.builder().region(Region.of(region)).credentialsProvider(credenciales)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(estiloRuta).chunkedEncodingEnabled(false).build());
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        String clave = "_checks/" + UUID.randomUUID() + "/archivo.pdf";
        byte[] contenido = "%PDF-1.4 comprobación remota de almacenamiento".getBytes(StandardCharsets.UTF_8);
        try (S3Client limpieza = builder.build()) {
            try {
                almacen.guardarPrivado(clave, contenido, "application/pdf");
                assertThat(almacen.existePrivado(clave)).isTrue();
                assertThat(almacen.leerPrivado(clave)).isEqualTo(contenido);
                assertThat(almacen.existePrivado(clave + ".ausente")).isFalse();
                URI firmada = URI.create(almacen.urlFirmada(clave));
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                var respuesta = http.send(HttpRequest.newBuilder(firmada).timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertThat(respuesta.statusCode()).isEqualTo(200);
                assertThat(respuesta.body()).isEqualTo(contenido);
                URI anonima = new URI(firmada.getScheme(), firmada.getAuthority(), firmada.getPath(), null, null);
                var privada = http.send(HttpRequest.newBuilder(anonima).timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                assertThat(privada.statusCode()).as("El bucket privado no admite descargas anónimas").isIn(403, 404);
            } finally {
                limpieza.deleteObject(b -> b.bucket(bucket).key(clave));
            }
        } finally {
            almacen.cerrar();
            if (credenciales instanceof DefaultCredentialsProvider predeterminadas) predeterminadas.close();
        }
    }

    private static String valor(Properties props, String clave, String defecto) {
        return System.getenv().getOrDefault(clave, props.getProperty(clave, defecto));
    }
}
