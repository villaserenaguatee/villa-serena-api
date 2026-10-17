package com.villaserena.api.reservas;

import java.util.function.Supplier;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import com.villaserena.api.comun.ApiException;

/** La respuesta y la reserva se confirman en la misma transacción persistente. */
@Service
public class IdempotenciaReservas {
 private final JdbcTemplate jdbc; private final ObjectMapper json; private final TransactionTemplate tx;
 public IdempotenciaReservas(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager){this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);}
 public <T> T ejecutar(String alcance,String clave,Object peticion,Class<T> tipo,Supplier<T> crear){
  if(clave==null)return crear.get(); // Compatibilidad con clientes existentes.
  if(!clave.matches("[a-zA-Z0-9_-]{16,100}"))throw ApiException.datosInvalidos("Identificador de solicitud inválido.",java.util.List.of());
  final String huella;
  try{huella=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(peticion).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
  return tx.execute(status->{
   jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",alcance+":"+clave);
   var rows=jdbc.queryForList("SELECT huella,respuesta::text AS respuesta FROM reservas_solicitudes WHERE alcance=? AND clave=?",alcance,clave);
   if(!rows.isEmpty()){
    if(!huella.equals(rows.getFirst().get("huella")))throw ApiException.conflicto("BOOKING_REQUEST_CONFLICT","Esta solicitud ya se utilizó con otros datos.");
    return json.readValue((String)rows.getFirst().get("respuesta"),tipo);
   }
   T resultado=crear.get();
   jdbc.update("INSERT INTO reservas_solicitudes(alcance,clave,huella,respuesta) VALUES(?,?,?,?::jsonb)",alcance,clave,huella,json.writeValueAsString(resultado));
   return resultado;
  });
 }
}
