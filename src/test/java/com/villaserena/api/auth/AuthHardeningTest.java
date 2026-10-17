package com.villaserena.api.auth;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.auth.dto.LoginPeticion;
class AuthHardeningTest {
 @Test void erroresGenericosYHashesParaCorreoAusente(){var repo=mock(EmpleadoRepository.class);var tokens=mock(TokenService.class);var encoder=spy(new BCryptPasswordEncoder(4));var p=mock(PropiedadesVillaSerena.class);when(p.login()).thenReturn(new PropiedadesVillaSerena.Login(5,15));var now=Instant.parse("2026-10-15T12:00:00Z");var service=new AuthService(repo,tokens,encoder,Clock.fixed(now,ZoneOffset.UTC),p);when(repo.findByCorreoIgnoreCase("unknown@example.test")).thenReturn(Optional.empty());var a=assertThrows(ApiException.class,()->service.login(new LoginPeticion("unknown@example.test","incorrecta")));var empleado=new Empleado();empleado.setBloqueadoHasta(now.plusSeconds(900));when(repo.findByCorreoIgnoreCase("locked@example.test")).thenReturn(Optional.of(empleado));var b=assertThrows(ApiException.class,()->service.login(new LoginPeticion("locked@example.test","incorrecta")));assertEquals(a.getCodigo(),b.getCodigo());assertEquals(a.getMessage(),b.getMessage());verify(encoder,times(2)).matches(eq("incorrecta"),anyString());verifyNoInteractions(tokens);}
 @Test void repositoriosBloqueanFilasEnConsumo()throws Exception{assertNotNull(EmpleadoRepository.class.getMethod("findByCorreoIgnoreCase",String.class).getAnnotation(org.springframework.data.jpa.repository.Lock.class));assertNotNull(RefreshTokenRepository.class.getMethod("findByTokenHash",String.class).getAnnotation(org.springframework.data.jpa.repository.Lock.class));}
}
