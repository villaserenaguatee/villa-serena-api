package com.villaserena.api.piso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.*;
import com.villaserena.api.reservas.*;

@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class) @ActiveProfiles("test") @Transactional
class ChatRecepcionIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired TokenService tokens; @Autowired EmpleadoRepository employees; @Autowired ReservaService reservations;
 String staff,other,guest,admin,code,otherCode;long owner;
 @BeforeEach void setup(){
  owner=employees.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow().getId();
  staff=token("ana.perez@villaserena.test");admin=token("sofia.morales@villaserena.test");
  String email=UUID.randomUUID()+"@example.test";
  db.update("INSERT INTO employees(full_name,email,phone,role,password_hash,must_change_password) SELECT 'Otra recepcionista',?,'55551234','RECEPCION',password_hash,false FROM employees WHERE id=?",email,owner);other=token(email);
  long guestId=db.queryForObject("INSERT INTO guests(full_name,email,phone,nationality,document_type,document_number) VALUES('Huésped de prueba',?,'55551234','Guatemalteca','DPI',?) RETURNING id",Long.class,UUID.randomUUID()+"@example.test",UUID.randomUUID().toString().substring(0,13));
  guest="Bearer "+tokens.emitirParaHuesped(guestId).accessToken();
  long type=db.queryForObject("SELECT id FROM room_types WHERE name='Doble Superior'",Long.class);var arrival=LocalDate.now().plusDays(5);
  code=reservations.crear(SolicitudReserva.recepcion(guestId,type,arrival,arrival.plusDays(2),1,null,owner)).getCodigo();
  otherCode=reservations.crear(SolicitudReserva.recepcion(guestId,type,arrival,arrival.plusDays(2),1,null,owner)).getCodigo();
  db.update("UPDATE reservations SET status='EN_ESTADIA' WHERE code IN (?,?)",code,otherCode);
 }
 String token(String email){return "Bearer "+tokens.emitirParaEmpleado(employees.findByCorreoIgnoreCase(email).orElseThrow()).accessToken();}
 String path(){return "/api/v1/estancias/"+code+"/chat";}
 String guestPath(){return "/api/v1/app/reservas/"+code+"/chat";}
 long send(String path,String token,String text)throws Exception{String body="{\"clave\":\""+UUID.randomUUID()+"\",\"texto\":\""+text+"\"}";var result=mvc.perform(post(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return ((Number)JsonPath.read(result,"$.id")).longValue();}
 @Test void permisosCalculadosPorAutorEdicionEliminacionYPersistencia()throws Exception{
  send(guestPath(),guest,"Mensaje huésped");send(path(),other,"Mensaje otra recepcionista");long id=send(path(),staff,"Mensaje propio");
  mvc.perform(get(path()).header("Authorization",staff)).andExpect(status().isOk()).andExpect(jsonPath("$[0].puedeModificar").value(false)).andExpect(jsonPath("$[1].puedeModificar").value(false)).andExpect(jsonPath("$[2].puedeModificar").value(true)).andExpect(jsonPath("$[2].version").value(0)).andExpect(jsonPath("$[2].author_id").doesNotExist());
  mvc.perform(get(path()).header("Authorization",other)).andExpect(jsonPath("$[1].puedeModificar").value(true)).andExpect(jsonPath("$[2].puedeModificar").value(false));
  mvc.perform(put(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Mensaje corregido\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.editadoEn").exists()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(get(path()).header("Authorization",token("ana.perez@villaserena.test"))).andExpect(jsonPath("$[2].texto").value("Mensaje corregido"));
  mvc.perform(put(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Revisión vieja\",\"version\":0}")).andExpect(status().isConflict());
  mvc.perform(delete(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict());
  for(int retry=0;retry<2;retry++)mvc.perform(delete(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.texto").value("")).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.eliminado").value(true)).andExpect(jsonPath("$.puedeModificar").value(false));
  mvc.perform(get(guestPath()).header("Authorization",guest)).andExpect(status().isOk()).andExpect(jsonPath("$[2].texto").value("")).andExpect(jsonPath("$[2].eliminado").value(true)).andExpect(jsonPath("$[0].puedeModificar").value(false));
  assertThat(db.queryForObject("SELECT text FROM stay_messages WHERE id=?",String.class,id)).isEqualTo("Mensaje corregido");
  assertThat(db.queryForObject("SELECT count(*) FROM stay_messages WHERE id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void autoresAjenosConversacionesCerradasYValidacionesSeCompruebanEnServidor()throws Exception{
  long id=send(path(),staff,"Mensaje propio");
  for(String token:new String[]{other,guest,admin}){
   mvc.perform(put(path()+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"No permitido\",\"version\":0}")).andExpect(status().isForbidden());
   mvc.perform(delete(path()+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
  }
  mvc.perform(get(path())).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/v1/estancias/"+otherCode+"/chat/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Otra conversación\",\"version\":0}")).andExpect(status().isNotFound());
  mvc.perform(put(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"   \",\"version\":0}")).andExpect(status().isBadRequest());
  mvc.perform(delete(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
  db.update("UPDATE reservations SET status='FINALIZADA' WHERE code=?",code);
  mvc.perform(get(path()).header("Authorization",staff)).andExpect(status().isOk()).andExpect(jsonPath("$[0].puedeModificar").value(false));
  mvc.perform(put(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Cerrada\",\"version\":0}")).andExpect(status().isConflict());
  mvc.perform(delete(path()+"/"+id).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict());
  assertThat(db.queryForObject("SELECT text FROM stay_messages WHERE id=?",String.class,id)).isEqualTo("Mensaje propio");
 }
 @Test void preferenciasSonPersonalesPersistentesYNoBorranMensajes()throws Exception{
  long id=send(guestPath(),guest,"Mensaje por leer");String preferences="/api/v1/estancias/chat-preferencias",change="/api/v1/estancias/"+code+"/chat-preferencias";
  mvc.perform(get(preferences).header("Authorization",staff)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.codigo=='"+code+"')].noLeidos").value(org.hamcrest.Matchers.contains(1)));
  for(String payload:new String[]{"{\"accion\":\"favorita\",\"valor\":true}","{\"accion\":\"archivada\",\"valor\":true}","{\"accion\":\"leido\",\"hasta\":"+id+"}"})mvc.perform(post(change).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
  mvc.perform(get(preferences).header("Authorization",token("ana.perez@villaserena.test"))).andExpect(jsonPath("$[?(@.codigo=='"+code+"')].favorita").value(org.hamcrest.Matchers.contains(true))).andExpect(jsonPath("$[?(@.codigo=='"+code+"')].archivada").value(org.hamcrest.Matchers.contains(true))).andExpect(jsonPath("$[?(@.codigo=='"+code+"')].noLeidos").value(org.hamcrest.Matchers.contains(0)));
  mvc.perform(get(preferences).header("Authorization",other)).andExpect(jsonPath("$[?(@.codigo=='"+code+"')].favorita").value(org.hamcrest.Matchers.contains(false))).andExpect(jsonPath("$[?(@.codigo=='"+code+"')].noLeidos").value(org.hamcrest.Matchers.contains(1)));
  mvc.perform(post(change).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"eliminar\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT count(*) FROM stay_messages WHERE id=?",Integer.class,id)).isEqualTo(1);
  mvc.perform(get(preferences).header("Authorization",guest)).andExpect(status().isForbidden());mvc.perform(post(change).header("Authorization",guest).contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"favorita\",\"valor\":true}")).andExpect(status().isForbidden());
  mvc.perform(post(change).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"archivada\"}")).andExpect(status().isBadRequest());
 }
}
