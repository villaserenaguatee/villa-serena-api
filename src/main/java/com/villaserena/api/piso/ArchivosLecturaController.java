package com.villaserena.api.piso;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;
@RestController @Profile("local")
public class ArchivosLecturaController {
 private final ArchivosLocales archivos; private final JdbcTemplate jdbc;
 public ArchivosLecturaController(ArchivosLocales archivos, JdbcTemplate jdbc){this.archivos=archivos;this.jdbc=jdbc;}
 @GetMapping("/api/v1/archivos/lectura/{id}")
 @PreAuthorize("hasAnyRole('HUESPED','RECEPCION','ADMIN','MANTENIMIENTO_LIMPIEZA')")
 public ResponseEntity<byte[]> leer(@PathVariable UUID id,@RequestParam long exp,@RequestParam String firma){
  if ("HUESPED".equals(UsuarioActual.rol())) {
   boolean propio=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM local_private_files a JOIN invoices f ON f.pdf_key=a.key JOIN accounts c ON c.id=f.account_id JOIN reservations r ON r.id=c.reservation_id WHERE a.id=? AND r.guest_id=?)",Boolean.class,id,UsuarioActual.id()));
   if(!propio)throw ApiException.noEncontrado();
  }else if("MANTENIMIENTO_LIMPIEZA".equals(UsuarioActual.rol())){
   boolean incidencia=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM local_private_files WHERE id=? AND key LIKE 'incidencias/%')",Boolean.class,id));
   if(!incidencia)throw ApiException.noEncontrado();
  }
  return archivos.descargar(id,exp,firma);
 }
}
