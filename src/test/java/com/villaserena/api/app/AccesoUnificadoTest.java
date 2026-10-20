package com.villaserena.api.app;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.villaserena.api.auth.*;
import com.villaserena.api.auth.dto.*;
import com.villaserena.api.huespedes.*;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.comun.ApiException;

class AccesoUnificadoTest {
 JdbcTemplate jdbc;EmpleadoRepository empleados;HuespedRepository huespedes;CodigoOtpRepository codigos;AuthService personal;AccesoHuespedService acceso;BCryptPasswordEncoder encoder;AccesoUnificadoService service;
 final Instant now=Instant.parse("2026-10-15T12:00:00Z");final String email="acceso-unitario@example.test";
 @BeforeEach void setup(){jdbc=mock(JdbcTemplate.class);empleados=mock(EmpleadoRepository.class);huespedes=mock(HuespedRepository.class);codigos=mock(CodigoOtpRepository.class);personal=mock(AuthService.class);acceso=mock(AccesoHuespedService.class);encoder=new BCryptPasswordEncoder(4);var p=mock(PropiedadesVillaSerena.class);when(p.login()).thenReturn(new PropiedadesVillaSerena.Login(5,15));Map<String,Object> control=new HashMap<>();control.put("intentos",0);control.put("bloqueado_hasta",null);when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(control);when(empleados.findByCorreoIgnoreCase(email)).thenReturn(Optional.empty());when(huespedes.findByCorreoIgnoreCase(email)).thenReturn(Optional.empty());service=new AccesoUnificadoService(jdbc,empleados,huespedes,codigos,encoder,Clock.fixed(now,ZoneOffset.UTC),personal,acceso,p);}
 void staff(String password){Empleado e=new Empleado();e.setId(80L);e.setContrasenaHash(encoder.encode(password));e.setEstado(EstadoEmpleado.ACTIVO);when(empleados.findByCorreoIgnoreCase(email)).thenReturn(Optional.of(e));when(personal.login(any())).thenReturn(new SesionEmpleado("staff-access","staff-refresh","Bearer",900,null));}
 void guest(String otp){Huesped h=new Huesped();h.setId(90L);when(huespedes.findByCorreoIgnoreCase(email)).thenReturn(Optional.of(h));CodigoOtp c=new CodigoOtp();c.setCodigoHash(encoder.encode(otp));c.setExpiraEn(now.plusSeconds(600));when(codigos.vigentes(90,now)).thenReturn(List.of(c));when(acceso.verificarCodigo(email,otp)).thenReturn(new Tokens("guest-access","guest-refresh","Bearer",900));}
 @Test void passwordConSeisDigitosNoSeInterpretaComoOtp(){staff("123456");assertEquals("staff-access",service.iniciar(email,"123456").accessToken());verify(personal,times(1)).login(any());verifyNoInteractions(acceso);}
 @Test void dosPerfilesPasswordNoFallaOtp(){staff("UnaClave88");guest("654321");assertEquals("staff-access",service.iniciar(email,"UnaClave88").accessToken());verifyNoInteractions(acceso);}
 @Test void dosPerfilesOtpNoFallaPassword(){staff("UnaClave88");guest("654321");assertEquals("guest-access",service.iniciar(email,"654321").accessToken());verifyNoInteractions(personal);}
 @Test void coincidenciaDobleNoConcedePrivilegiosNiConsumeCodigo(){staff("123456");guest("123456");assertThrows(ApiException.class,()->service.iniciar(email,"123456"));verifyNoInteractions(personal,acceso);verify(jdbc,never()).update(startsWith("UPDATE unified_access_control"),any(Object[].class));}
 @Test void falloDualCuentaUnaVezSinBloquearLosDosPerfiles(){staff("UnaClave88");guest("654321");assertThrows(ApiException.class,()->service.iniciar(email,"incorrecta"));verifyNoInteractions(personal,acceso);verify(jdbc,times(1)).update(eq("UPDATE unified_access_control SET attempts=?,locked_until=? WHERE email=?"),eq(1),isNull(),eq(email));}
 @Test void correoDesconocidoDevuelveMismoError(){var e=assertThrows(ApiException.class,()->service.iniciar(email,"incorrecta"));assertEquals(AccesoUnificadoService.invalido().getMessage(),e.getMessage());}
 @Test void reenviosConcurrentesCompartenUnSoloIntento()throws Exception{var svc=mock(AccesoUnificadoService.class);when(svc.iniciar(email,"valor")).thenReturn(new AccesoUnificadoService.Sesion("a","r","Bearer",900,null));var controller=new AccesoUnificadoController(svc);var key=UUID.randomUUID();try(var executor=Executors.newFixedThreadPool(6)){var jobs=new ArrayList<Future<AccesoUnificadoService.Sesion>>();for(int i=0;i<6;i++)jobs.add(executor.submit(()->controller.iniciar(new AccesoUnificadoController.Peticion(email,"valor"),key)));for(var job:jobs)assertEquals("a",job.get().accessToken());}verify(svc,times(1)).iniciar(email,"valor");assertThrows(ApiException.class,()->controller.iniciar(new AccesoUnificadoController.Peticion(email,"otro"),key));verifyNoMoreInteractions(svc);}
}
