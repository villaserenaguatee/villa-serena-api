package com.villaserena.api.notificaciones;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.comun.ApiException;

class ContactoPublicoTest {
 JdbcTemplate jdbc;JavaMailSender mail;ContactoPublicoController controller;String hash,estado;boolean creado;
 @BeforeEach void setup(){
  jdbc=mock(JdbcTemplate.class);mail=mock(JavaMailSender.class);var tm=mock(PlatformTransactionManager.class);when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());var config=mock(PropiedadesVillaSerena.class);when(config.notificaciones()).thenReturn(new PropiedadesVillaSerena.Notificaciones("Villa Serena <hotelvillaserena.gt@gmail.com>","","",5,1,10));var template=mock(TemplateEngine.class);when(template.process(eq("correo/contacto"),any(Context.class))).thenReturn("<p>Mensaje del visitante</p>");when(mail.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));
  when(jdbc.update(startsWith("INSERT INTO web_contact"),any(Object[].class))).thenAnswer(i->{if(creado)return 0;creado=true;Object[] args=(Object[])i.getRawArguments()[1];hash=(String)args[1];estado="ENVIANDO";return 1;});
  when(jdbc.queryForMap(anyString(),any(Object[].class))).thenAnswer(i->Map.of("huella",hash,"estado",estado));
  when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class))).thenAnswer(i->List.of(estado));
  when(jdbc.update(startsWith("UPDATE web_contact SET status='ENVIADO'"),any(Object[].class))).thenAnswer(i->{estado="ENVIADO";return 1;});
  when(jdbc.update(startsWith("UPDATE web_contact SET status=?"),any(Object[].class))).thenAnswer(i->{estado=(String)((Object[])i.getRawArguments()[1])[0];return 1;});
  controller=new ContactoPublicoController(jdbc,tm,mail,template,config,new ContactoCorreoConfig("operacion@example.test"));
 }
 ContactoPublicoController.Peticion datos(){return new ContactoPublicoController.Peticion("Visitante Ejemplo","visitor@example.test","Comprobación aislada; no se envía por SMTP.");}
 @Test void destinatarioYReplyToCorrectosSinDuplicar()throws Exception{var id=UUID.randomUUID();assertEquals("ENVIADO",controller.enviar(datos(),id).estado());assertEquals("ENVIADO",controller.enviar(datos(),id).estado());var message=ArgumentCaptor.forClass(MimeMessage.class);verify(mail,times(1)).send(message.capture());assertEquals("operacion@example.test",((InternetAddress)message.getValue().getAllRecipients()[0]).getAddress());assertEquals("visitor@example.test",((InternetAddress)message.getValue().getReplyTo()[0]).getAddress());assertEquals("Visitante Ejemplo",((InternetAddress)message.getValue().getReplyTo()[0]).getPersonal());}
 @Test void smtpInciertoNoPermiteOtroEnvio(){doThrow(new MailSendException("Resultado SMTP no confirmado")).when(mail).send(any(MimeMessage.class));var id=UUID.randomUUID();assertEquals("INCIERTO",controller.enviar(datos(),id).estado());assertEquals("INCIERTO",controller.enviar(datos(),id).estado());verify(mail,times(1)).send(any(MimeMessage.class));}
 @Test void reutilizarReferenciaConOtroTextoNoEnvia(){var id=UUID.randomUUID();controller.enviar(datos(),id);assertThrows(ApiException.class,()->controller.enviar(new ContactoPublicoController.Peticion("Visitante Ejemplo","visitor@example.test","Otro mensaje"),id));verify(mail,times(1)).send(any(MimeMessage.class));}
}
