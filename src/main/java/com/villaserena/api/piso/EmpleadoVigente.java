package com.villaserena.api.piso;
import java.io.IOException;
import java.util.Objects;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.villaserena.api.config.JwtConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
/** Los cambios de rol/estado se aplican también a accesos emitidos anteriormente. */
@Component @Order(Ordered.LOWEST_PRECEDENCE-10)
public class EmpleadoVigente extends OncePerRequestFilter {
 private final JdbcTemplate jdbc;public EmpleadoVigente(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();if(auth instanceof JwtAuthenticationToken token&&"EMPLEADO".equals(token.getToken().getClaimAsString(JwtConfig.CLAIM_TIPO))){var jwt=token.getToken();var rows=jdbc.queryForList("SELECT rol,area,estado,acceso_revocado_en FROM empleados WHERE id=?",Long.parseLong(jwt.getSubject()));if(rows.isEmpty()||!"ACTIVO".equals(rows.getFirst().get("estado"))||(rows.getFirst().get("acceso_revocado_en") instanceof java.sql.Timestamp revoked && (jwt.getIssuedAt()==null||!jwt.getIssuedAt().isAfter(revoked.toInstant())))||!Objects.equals(rows.getFirst().get("rol"),jwt.getClaimAsString(JwtConfig.CLAIM_ROL))||!Objects.equals(rows.getFirst().get("area"),jwt.getClaimAsString(JwtConfig.CLAIM_AREA))){res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.setHeader("Cache-Control","no-store");res.getWriter().write("{\"codigo\":\"SESION_ACTUALIZAR\",\"mensaje\":\"Tu acceso cambió. Renueva la sesión.\"}");return;}}
  chain.doFilter(req,res);
 }
}
