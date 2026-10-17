package com.villaserena.api.config;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
class AbusoPublicoTest {
 @Test void limitaCuerpoSinContentLength()throws Exception{var f=new AbusoPublicoFilter(Clock.systemUTC());var req=new MockHttpServletRequest("POST","/api/v1/publico/contacto"){public long getContentLengthLong(){return -1;}};req.setContent(new byte[(int)AbusoPublicoFilter.MAX_BODY+1]);var res=new MockHttpServletResponse();f.doFilter(req,res,(a,b)->a.getInputStream().readAllBytes());assertEquals(413,res.getStatus());}
 @Test void limitaSinProcesarNiEnviarYNoConfiaEnForwarded() throws Exception {
  var f=new AbusoPublicoFilter(Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"),ZoneOffset.UTC));var pasos=new AtomicInteger();
  for(int i=0;i<4;i++){var req=new MockHttpServletRequest("POST","/api/v1/publico/contacto");req.setRemoteAddr("127.0.0.1");req.addHeader("X-Forwarded-For","192.0.2."+i);var res=new MockHttpServletResponse();f.doFilter(req,res,(a,b)->pasos.incrementAndGet());assertEquals(i<3?200:429,res.getStatus());if(i==3)assertEquals("900",res.getHeader("Retry-After"));}assertEquals(3,pasos.get());
 }
 @Test void cuentaLoginCompartidoPeroNoBloqueaLecturas(){var f=new AbusoPublicoFilter(Clock.systemUTC());for(int i=0;i<30;i++)assertTrue(f.admitir("login:local",30,60));assertFalse(f.admitir("login:local",30,60));assertTrue(f.admitir("login:otro",30,60));}
 @Test void ventanaVencidaPermiteUsoLegitimo(){var now=new java.util.concurrent.atomic.AtomicReference<>(Instant.EPOCH);Clock c=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};var f=new AbusoPublicoFilter(c);assertTrue(f.admitir("otp",1,60));assertFalse(f.admitir("otp",1,60));now.set(Instant.EPOCH.plusSeconds(60));assertTrue(f.admitir("otp",1,60));}
}
