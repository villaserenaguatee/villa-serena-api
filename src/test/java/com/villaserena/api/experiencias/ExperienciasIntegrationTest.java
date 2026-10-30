package com.villaserena.api.experiencias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.*;
import com.villaserena.api.auth.*;
import com.villaserena.api.reservas.*;

@SpringBootTest @AutoConfigureMockMvc @Import({TestcontainersConfiguration.class,RelojFijoConfig.class}) @ActiveProfiles("test") @Transactional
class ExperienciasIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired TokenService tokens; @Autowired EmpleadoRepository employees; @Autowired ReservaService stays; @Autowired TransactionTemplate tx;
 @Autowired RelojFijoConfig.RelojPrueba clock;
 @Autowired jakarta.persistence.EntityManager entityManager;
 String admin,staff,guest,other,code,code2;long amenity,experience,slot;Instant start;LocalDate arrival;
 static final String ADMIN="/api/v1/admin/experiencias",STAFF="/api/v1/experiencias";
 @BeforeEach void setup() throws Exception {
  clock.fijar(Instant.now());arrival=LocalDate.now(clock).plusDays(5);start=arrival.plusDays(1).atTime(10,0).atZone(ZoneId.of("America/Guatemala")).toInstant();
  admin=token("sofia.morales@villaserena.test");staff=token("ana.perez@villaserena.test");
  long guestId=createGuest(),employee=db.queryForObject("SELECT id FROM employees WHERE email='ana.perez@villaserena.test'",Long.class),type=db.queryForObject("SELECT id FROM room_types WHERE name='Doble Superior'",Long.class);
  long otherId=createGuest();
  guest=tx.execute(s->"Bearer "+tokens.emitirParaHuesped(guestId).accessToken());other=tx.execute(s->"Bearer "+tokens.emitirParaHuesped(otherId).accessToken());
  code=tx.execute(s->stays.crear(SolicitudReserva.recepcion(guestId,type,arrival,arrival.plusDays(3),1,null,employee)).getCodigo());
  code2=tx.execute(s->stays.crear(SolicitudReserva.recepcion(guestId,type,arrival,arrival.plusDays(3),1,null,employee)).getCodigo());
  amenity=db.queryForObject("INSERT INTO hotel_amenities(name,description,schedule,location,sort_order,status) VALUES('Experiencia de prueba','','','',0,'ACTIVO') RETURNING id",Long.class);
  experience=id(write(ADMIN,admin,config("INMEDIATA","GRATUITA","SIN_CARGO",0,false,true),200));
  slot=id(write(ADMIN+"/"+experience+"/horarios",admin,"{\"inicio\":\""+start+"\",\"capacidad\":2,\"activo\":true,\"version\":0}",200));
 }
 @AfterEach void resetClock(){clock.reiniciar();}
 long createGuest(){return db.queryForObject("INSERT INTO guests(full_name,email,phone,nationality,document_type,document_number) VALUES('Huésped de prueba',?,'+502 5555 1234','Guatemalteca','DPI',?) RETURNING id",Long.class,UUID.randomUUID()+"@example.test",String.format("%013d",(UUID.randomUUID().getLeastSignificantBits()&Long.MAX_VALUE)%10000000000000L));}
 String token(String email){return tx.execute(s->"Bearer "+tokens.emitirParaEmpleado(employees.findByCorreoIgnoreCase(email).orElseThrow()).accessToken());}
 String root(String c){return "/api/v1/app/reservas/"+c+"/experiencias";}
 String config(String mode,String pricing,String payment,int price,boolean stay,boolean cancel){return """
 {"amenidadId":%d,"categoria":"Spa","activa":true,"modalidad":"%s","tarifa":"%s","precio":%d,"cobro":"%s","duracionMinutos":60,"requiereEstadia":%s,"cancelable":%s,"antelacionCancelacionMinutos":60,"version":0}
 """.formatted(amenity,mode,pricing,price,payment,stay,cancel);}
 String body(UUID key,int people){return "{\"clave\":\""+key+"\",\"horarioId\":"+slot+",\"personas\":"+people+"}";}
 String write(String path,String token,String body,int status) throws Exception{return mvc.perform(post(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();}
 long id(String json){return ((Number)JsonPath.read(json,"$.id")).longValue();}
 void change(String mode,String pricing,String payment,int price,boolean stay,boolean cancel)throws Exception{mvc.perform(put(ADMIN+"/"+experience).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(config(mode,pricing,payment,price,stay,cancel))).andExpect(status().isOk());}
 @Test void persistenciaIdempotenciaDuplicadosPropiedadYCupos()throws Exception{
  UUID key=UUID.randomUUID();long booked=id(write(root(code)+"/reservas",guest,body(key,2),200));assertThat(id(write(root(code)+"/reservas",guest,body(key,2),200))).isEqualTo(booked);
  write(root(code)+"/reservas",guest,body(key,1),409);write(root(code)+"/reservas",guest,body(UUID.randomUUID(),1),409);write(root(code2)+"/reservas",guest,body(UUID.randomUUID(),1),409);
  mvc.perform(get(root(code)+"/reservas").header("Authorization",guest)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(booked)).andExpect(jsonPath("$[0].total").value(0));
  mvc.perform(get(root(code)+"/reservas").header("Authorization",other)).andExpect(status().isNotFound());write(root(code2)+"/reservas/"+booked+"/cancelar",guest,"{\"version\":0,\"motivo\":\"Prueba\"}",404);
  mvc.perform(get(ADMIN).header("Authorization",guest)).andExpect(status().isForbidden());mvc.perform(get(root(code))).andExpect(status().isUnauthorized());
  assertThat(db.queryForObject("SELECT count(*) FROM experience_bookings WHERE id=?",Integer.class,booked)).isEqualTo(1);
 }
 @Test void aprobacionRechazoYCargoUnicoConReversoAtomico()throws Exception{
  change("APROBACION","POR_PERSONA","CUENTA",25,true,true);
  write(root(code)+"/reservas",guest,body(UUID.randomUUID(),1),409);
  db.update("UPDATE reservations SET status='EN_ESTADIA' WHERE code=?",code);
  entityManager.clear();
  long booked=id(write(root(code)+"/reservas",guest,body(UUID.randomUUID(),2),200));assertThat(db.queryForObject("SELECT charge_id FROM experience_bookings WHERE id=?",Long.class,booked)).isNull();
  write(STAFF+"/reservas/"+booked+"/decision",staff,"{\"version\":9,\"accion\":\"APROBAR\"}",409);
  String approved=write(STAFF+"/reservas/"+booked+"/decision",staff,"{\"version\":0,\"accion\":\"APROBAR\"}",200);long charge=((Number)JsonPath.read(approved,"$.cargoId")).longValue();
  write(STAFF+"/reservas/"+booked+"/decision",staff,"{\"version\":0,\"accion\":\"APROBAR\"}",200);assertThat(db.queryForObject("SELECT total FROM charges WHERE id=?",java.math.BigDecimal.class,charge)).isEqualByComparingTo("50.00");
  String cancel="{\"version\":1,\"motivo\":\"Cambio de planes\"}";
  db.update("UPDATE accounts SET status='CERRADA' WHERE reservation_id=(SELECT id FROM reservations WHERE code=?)",code);
  write(root(code)+"/reservas/"+booked+"/cancelar",guest,cancel,409);
  assertThat(db.queryForObject("SELECT status FROM experience_bookings WHERE id=?",String.class,booked)).isEqualTo("CONFIRMADA");
  assertThat(db.queryForObject("SELECT status FROM charges WHERE id=?",String.class,charge)).isEqualTo("VIGENTE");
  db.update("UPDATE accounts SET status='ABIERTA' WHERE reservation_id=(SELECT id FROM reservations WHERE code=?)",code);
  write(root(code)+"/reservas/"+booked+"/cancelar",guest,cancel,200);write(root(code)+"/reservas/"+booked+"/cancelar",guest,cancel,200);
  assertThat(db.queryForObject("SELECT status FROM charges WHERE id=?",String.class,charge)).isEqualTo("ANULADO");
  long next=id(write(root(code)+"/reservas",guest,body(UUID.randomUUID(),1),200));write(STAFF+"/reservas/"+next+"/decision",staff,"{\"version\":0,\"accion\":\"RECHAZAR\"}",409);write(STAFF+"/reservas/"+next+"/decision",staff,"{\"version\":0,\"accion\":\"RECHAZAR\",\"motivo\":\"No disponible\"}",200);
 }
 @Test void cancelacionRestringidaEInactivosFechasYRevisiones()throws Exception{
  change("INMEDIATA","GRATUITA","SIN_CARGO",0,false,false);long b=id(write(root(code)+"/reservas",guest,body(UUID.randomUUID(),1),200));write(root(code)+"/reservas/"+b+"/cancelar",guest,"{\"version\":0,\"motivo\":\"Prueba\"}",409);
  mvc.perform(get(root(code)+"/"+experience+"/disponibilidad").param("fecha",arrival.plusDays(2).toString()).header("Authorization",guest)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(put(ADMIN+"/"+experience+"/horarios/"+slot).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"inicio\":\""+start+"\",\"capacidad\":1,\"activo\":true}")).andExpect(status().isOk());
  db.update("UPDATE hotel_amenities SET status='INACTIVO' WHERE id=?",amenity);write(root(code2)+"/reservas",guest,body(UUID.randomUUID(),1),409);
  db.update("UPDATE reservations SET status='FINALIZADA' WHERE code=?",code);write(root(code)+"/reservas",guest,body(UUID.randomUUID(),1),409);
 }
 @Test void coordinacionYAnticipoNuncaSimulanPagoNiReserva()throws Exception{
  change("COORDINACION","POR_RESERVA","ANTICIPADO",100,false,true);write(root(code)+"/reservas",guest,body(UUID.randomUUID(),1),409);
  String q="{\"clave\":\""+UUID.randomUUID()+"\",\"comentario\":\"Solicito disponibilidad\"}";long inquiry=id(write(root(code)+"/"+experience+"/consultas",guest,q,200));assertThat(id(write(root(code)+"/"+experience+"/consultas",guest,q,200))).isEqualTo(inquiry);
  write(STAFF+"/consultas/"+inquiry+"/respuesta",guest,"{\"respuesta\":\"Prueba\"}",403);write(STAFF+"/consultas/"+inquiry+"/respuesta",staff,"{\"respuesta\":\"Coordine su anticipo con Recepción\"}",200);
  mvc.perform(get(root(code)+"/consultas").header("Authorization",guest)).andExpect(status().isOk()).andExpect(jsonPath("$[0].estado").value("RESPONDIDA"));assertThat(db.queryForObject("SELECT count(*) FROM experience_bookings WHERE slot_id=?",Integer.class,slot)).isZero();
 }
 @Test void plazoDeCancelacionYCambiosDeHorarioRespetanLasReservasExistentes()throws Exception{
  long booked=id(write(root(code)+"/reservas",guest,body(UUID.randomUUID(),2),200));
  mvc.perform(put(ADMIN+"/"+experience+"/horarios/"+slot).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"inicio\":\""+start+"\",\"capacidad\":1,\"activo\":true}")).andExpect(status().isConflict());
  write(ADMIN+"/"+experience+"/horarios",admin,"{\"version\":0,\"inicio\":\""+start.plusSeconds(1800)+"\",\"capacidad\":2,\"activo\":true}",409);
  mvc.perform(get(root(code)+"/reservas").header("Authorization",guest)).andExpect(jsonPath("$[0].cancelable").value(true));
  clock.fijar(start.minusSeconds(3600));
  mvc.perform(get(root(code)+"/reservas").header("Authorization",guest)).andExpect(jsonPath("$[0].cancelable").value(false));
  write(root(code)+"/reservas/"+booked+"/cancelar",guest,"{\"version\":0,\"motivo\":\"Fuera de plazo\"}",409);
  assertThat(db.queryForObject("SELECT status FROM experience_bookings WHERE id=?",String.class,booked)).isEqualTo("CONFIRMADA");
 }
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED) void dosSolicitudesSimultaneasNoSuperanElUltimoCupo()throws Exception{
  db.update("UPDATE experience_slots SET capacity=1 WHERE id=?",slot);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)){
   var tasks=new ArrayList<Future<Integer>>();for(String lodging:List.of(code,code2))tasks.add(executor.submit(()->{ready.countDown();start.await(10,TimeUnit.SECONDS);return mvc.perform(post(root(lodging)+"/reservas").header("Authorization",guest).contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID(),1))).andReturn().getResponse().getStatus();}));
   assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();assertThat(List.of(tasks.get(0).get(20,TimeUnit.SECONDS),tasks.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
   assertThat(db.queryForObject("SELECT sum(people) FROM experience_bookings WHERE slot_id=?",Integer.class,slot)).isEqualTo(1);
  }finally{db.update("DELETE FROM experience_bookings WHERE slot_id=?",slot);db.update("DELETE FROM experience_slots WHERE experience_id=?",experience);db.update("DELETE FROM experiences WHERE id=?",experience);db.update("DELETE FROM hotel_amenities WHERE id=?",amenity);for(String lodging:List.of(code,code2)){long rid=db.queryForObject("SELECT id FROM reservations WHERE code=?",Long.class,lodging);db.update("DELETE FROM charges WHERE account_id IN (SELECT id FROM accounts WHERE reservation_id=?)",rid);db.update("DELETE FROM accounts WHERE reservation_id=?",rid);db.update("DELETE FROM reservation_nights WHERE reservation_id=?",rid);db.update("DELETE FROM reservations WHERE id=?",rid);}}
 }
}
