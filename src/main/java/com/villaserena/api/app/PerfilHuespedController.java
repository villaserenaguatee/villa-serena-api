package com.villaserena.api.app;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;

/** Perfil propio: nunca recibe un identificador de otro huésped. */
@RestController
public class PerfilHuespedController {
 private final JdbcTemplate jdbc;
 public PerfilHuespedController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/api/v1/app/perfil")
 @PreAuthorize("hasRole('HUESPED')")
 public Map<String,Object> perfil(){
  var rows=jdbc.queryForList("SELECT id,full_name AS \"nombreCompleto\",email AS \"correo\",phone AS \"telefono\",nationality AS \"nacionalidad\",document_type AS \"tipoDocumento\",document_number AS \"numeroDocumento\",passport_issuing_country AS \"paisEmisorPasaporte\",created_at AS \"creadoEn\"  FROM guests WHERE id=?",UsuarioActual.id());
  return rows.stream().findFirst().orElseThrow(ApiException::noEncontrado);
 }
}
