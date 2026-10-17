package com.villaserena.api.app;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.villaserena.api.auth.*;
import com.villaserena.api.auth.dto.Tokens;
import com.villaserena.api.huespedes.*;
import com.villaserena.api.reservas.ReservaRepository;
import com.villaserena.api.notificaciones.OutboxService;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.comun.ApiException;
class AccesoLimitesTest {
 HuespedRepository guests;ReservaRepository reservations;CodigoOtpRepository codes;OutboxService outbox;TokenService tokens;AccesoHuespedService service;
 final Instant now=Instant.parse("2026-10-15T12:00:00Z");Huesped guest;BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
 @BeforeEach void setup(){guests=mock(HuespedRepository.class);reservations=mock(ReservaRepository.class);codes=mock(CodigoOtpRepository.class);outbox=mock(OutboxService.class);tokens=mock(TokenService.class);var p=mock(PropiedadesVillaSerena.class);when(p.login()).thenReturn(new PropiedadesVillaSerena.Login(5,15));service=new AccesoHuespedService(guests,reservations,codes,mock(DispositivoPushRepository.class),tokens,outbox,encoder,Clock.fixed(now,ZoneOffset.UTC),p);guest=new Huesped();guest.setId(91L);guest.setNombreCompleto("Nombre local");guest.setCorreo("otp-unit@example.test");when(guests.bloquearPorCorreo(guest.getCorreo())).thenReturn(Optional.of(guest));when(reservations.existsByHuespedId(91L)).thenReturn(true);}
 @Test void noReenviaDuranteLaEspera(){when(codes.countByHuespedIdAndCreadoEnAfter(91L,now.minusSeconds(60))).thenReturn(1L);service.solicitarCodigo(guest.getCorreo());verifyNoInteractions(outbox);verify(codes,never()).save(any());}
 @Test void noSuperaTresPorQuinceMinutos(){when(codes.countByHuespedIdAndCreadoEnAfter(91L,now.minusSeconds(900))).thenReturn(3L);service.solicitarCodigo(guest.getCorreo());verifyNoInteractions(outbox);verify(codes,never()).save(any());}
 @Test void generaUnSoloCodigoConDiezMinutosYHash(){service.solicitarCodigo(guest.getCorreo());var capture=org.mockito.ArgumentCaptor.forClass(CodigoOtp.class);verify(codes).save(capture.capture());assertEquals(now.plusSeconds(600),capture.getValue().getExpiraEn());assertTrue(capture.getValue().getCodigoHash().startsWith("$2"));verify(outbox,times(1)).encolar(any(),eq(guest.getCorreo()),anyMap());}
 @Test void codigoConsumidoNoPuedeReutilizarse(){var code=new CodigoOtp();code.setCodigoHash(encoder.encode("654321"));code.setExpiraEn(now.plusSeconds(600));when(codes.vigentes(91L,now)).thenAnswer(i->code.sirve(now)?List.of(code):List.of());when(tokens.emitirParaHuesped(91L)).thenReturn(new Tokens("local-access","local-refresh","Bearer",900));assertNotNull(service.verificarCodigo(guest.getCorreo(),"654321"));assertEquals(now,code.getUsadoEn());assertThrows(ApiException.class,()->service.verificarCodigo(guest.getCorreo(),"654321"));verify(tokens,times(1)).emitirParaHuesped(91L);}
 @Test void bloqueadoYDesconocidoNoRevelanExistencia(){guest.setOtpBloqueadoHasta(now.plusSeconds(900));var a=assertThrows(ApiException.class,()->service.verificarCodigo(guest.getCorreo(),"654321"));when(guests.bloquearPorCorreo("unknown@example.test")).thenReturn(Optional.empty());var b=assertThrows(ApiException.class,()->service.verificarCodigo("unknown@example.test","654321"));assertEquals(a.getCodigo(),b.getCodigo());assertEquals(a.getMessage(),b.getMessage());verifyNoInteractions(tokens);}
}
