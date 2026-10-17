package com.villaserena.api.piso;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
@Service
public class SolicitudesEventos {
 private final JdbcTemplate jdbc;private final SimpMessagingTemplate mensajes;
 public SolicitudesEventos(JdbcTemplate jdbc,SimpMessagingTemplate mensajes){this.jdbc=jdbc;this.mensajes=mensajes;}
 public void publicar(long id){var d=jdbc.queryForMap("SELECT s.id,s.tipo,s.estado,h.numero,h.piso FROM solicitudes s JOIN habitaciones h ON h.id=s.habitacion_id WHERE s.id=?",id);Object payload=Map.of("id",id,"tipo",d.get("tipo"),"estado",d.get("estado"),"habitacion",Map.of("numero",d.get("numero"),"piso",d.get("piso")));TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){try{mensajes.convertAndSend("/topic/solicitudes",payload);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(SolicitudesEventos.class).warn("No se pudo publicar el cambio de solicitud {}",id);}}});}
}
