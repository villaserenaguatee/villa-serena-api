package com.villaserena.api.piso;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.archivos.*;
import com.villaserena.api.comun.ApiException;
/** Adaptador local persistente, con enlaces firmados; no requiere un bucket simulado. */
@Component @Primary @Profile("local")
public class ArchivosLocales implements AlmacenArchivos {
 private final JdbcTemplate jdbc;private final String web;private final byte[] secret=new byte[32];
 public ArchivosLocales(JdbcTemplate jdbc,@Value("${villaserena.archivos.url-lectura}") String web){this.jdbc=jdbc;this.web=web;new SecureRandom().nextBytes(secret);}
 private ApiException invalid(String text){return ApiException.datosInvalidos(text,List.of());}
 public void guardarPrivado(String clave,byte[] data,String mime){if(data==null||data.length==0||data.length>5242880)throw invalid("El archivo debe pesar hasta 5 MiB.");if(Set.of("image/png","image/jpeg").contains(mime)){try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(data))){var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw invalid("Imagen inválida.");var r=readers.next();try{r.setInput(in);if(r.getWidth(0)<1||r.getHeight(0)<1||(long)r.getWidth(0)*r.getHeight(0)>16000000)throw invalid("Dimensiones de imagen no permitidas.");var image=r.read(0);var out=new ByteArrayOutputStream();ImageIO.write(image,mime.equals("image/png")?"png":"jpeg",out);data=out.toByteArray();}finally{r.dispose();}}catch(IOException e){throw invalid("Imagen inválida.");}}else if(!mime.equals("application/pdf")||data.length<5||!new String(data,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw invalid("Tipo de archivo no permitido.");if(data.length>5242880)throw invalid("Archivo demasiado grande.");jdbc.update("INSERT INTO local_private_files(id,key,mime,content) VALUES(?,?,?,?) ON CONFLICT(key) DO NOTHING",UUID.randomUUID(),clave,mime,data);}
 public boolean existePrivado(String clave){return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM local_private_files WHERE key=?)",Boolean.class,clave);}
 public byte[] leerPrivado(String clave){var rows=jdbc.queryForList("SELECT content AS \"contenido\" FROM local_private_files WHERE key=?",byte[].class,clave);if(rows.isEmpty())throw ApiException.noEncontrado();return rows.getFirst();}
 public String urlFirmada(String clave){var ids=jdbc.queryForList("SELECT id  FROM local_private_files WHERE key=?",UUID.class,clave);if(ids.isEmpty())throw ApiException.noEncontrado();long exp=Instant.now().plusSeconds(300).getEpochSecond();UUID id=ids.getFirst();return java.net.URI.create(web).resolve("/api/archivos/lectura/"+id+"?exp="+exp+"&firma="+firma(id,exp)).toString();}
 private String firma(UUID id,long exp){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal((id+":"+exp).getBytes(StandardCharsets.US_ASCII)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 public ResponseEntity<byte[]> descargar(UUID id,long exp,String token){long now=Instant.now().getEpochSecond();if(exp<now||exp>now+300||!token.matches("[a-f0-9]{64}")||!MessageDigest.isEqual(firma(id,exp).getBytes(StandardCharsets.US_ASCII),token.getBytes(StandardCharsets.US_ASCII)))throw new ApiException(HttpStatus.FORBIDDEN,"ENLACE_VENCIDO","El enlace no es válido o venció. Solicita uno nuevo desde tu cuenta.");var rows=jdbc.queryForList("SELECT mime,content AS \"contenido\",key AS \"clave\" FROM local_private_files WHERE id=?",id);if(rows.isEmpty())throw ApiException.noEncontrado();var f=rows.getFirst();String mime=(String)f.get("mime");return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").header("Content-Disposition",mime.equals("application/pdf")?"attachment; filename=\""+((String)f.get("clave")).substring(((String)f.get("clave")).lastIndexOf('/' )+1).replaceFirst("^factura-","Factura-Villa-Serena-")+"\"":"inline").body((byte[])f.get("contenido"));}
}
