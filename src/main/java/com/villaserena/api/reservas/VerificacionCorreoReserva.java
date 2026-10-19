package com.villaserena.api.reservas;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.notificaciones.OutboxService;
import com.villaserena.api.notificaciones.TipoNotificacion;

/** Email ownership for a public booking draft; never emits sign-in tokens. */
@Service
public class VerificacionCorreoReserva {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final OutboxService outbox;
    private final Clock clock;
    private volatile org.springframework.security.oauth2.jwt.NimbusJwtDecoder google;
    public VerificacionCorreoReserva(JdbcTemplate jdbc, PasswordEncoder encoder, OutboxService outbox, Clock clock) {
        this.jdbc=jdbc; this.encoder=encoder; this.outbox=outbox; this.clock=clock;
    }
    static String correo(String value) {
        String email=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        if(email.length()>150 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw ApiException.datosInvalidos("Introduce un correo válido.",List.of());
        return email;
    }
    private void lock(String email) { jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","verificacion-reserva:"+email); }
    private static Timestamp ts(Instant instant) { return Timestamp.from(instant); }
    private static ApiException invalido() { return new ApiException(HttpStatus.UNAUTHORIZED,"VERIFICACION_INVALIDA","El código no es válido o venció. Revisa el código y vuelve a intentarlo."); }
    private static ApiException limite() { return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"VERIFICACION_LIMITE","Espera antes de solicitar o probar otro código."); }
    private int fallos(String email,Instant now) { return jdbc.queryForObject("SELECT COALESCE(SUM(intentos),0)::int FROM reserva_correo_verificaciones WHERE correo=? AND origen='CODIGO' AND creado_en>?",Integer.class,email,ts(now.minusSeconds(900))); }
    private static String hash(String secret) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public Map<String,Object> actualizarCorreo(String address,UUID draft,String previous) {
        String email=correo(address),old=correo(previous);
        if(email.equals(old)) return Map.of("cambiado",false);
        lock(old);
        Instant now=clock.instant();
        // Revoke this draft's previous proof/challenge, never another booking or sign-in OTP.
        jdbc.update("UPDATE outbox SET estado='FALLIDO',ultimo_error='DESCARTADO_CAMBIO_CORREO',payload=payload-'codigo' WHERE estado='PENDIENTE' AND payload->>'proposito'='VERIFICAR_RESERVA' AND id IN (SELECT outbox_id FROM reserva_correo_verificaciones WHERE correo=? AND borrador=? AND consumido_en IS NULL)",old,draft);
        jdbc.update("UPDATE reserva_correo_verificaciones SET expira_en=LEAST(expira_en,?),comprobante_hash=NULL,comprobante_expira_en=NULL WHERE correo=? AND borrador=? AND consumido_en IS NULL",ts(now),old,draft);
        return Map.of("cambiado",true);
    }
    @Transactional
    public Map<String,Object> solicitar(String address,UUID draft,String name) {
        String email=correo(address); Instant now=clock.instant(); lock(email);
        // A retry of an unresolved send must reuse its outbox entry, not generate another code.
        boolean pending=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM reserva_correo_verificaciones v JOIN outbox o ON o.id=v.outbox_id WHERE v.correo=? AND v.borrador=? AND v.origen='CODIGO' AND v.verificado_en IS NULL AND v.expira_en>? AND o.estado='PENDIENTE')",Boolean.class,email,draft,ts(now)));
        if(pending) return Map.of("estado","SOLICITADO","envio","PENDIENTE","reenviarEn",60);
        if(fallos(email,now)>=5) throw limite();
        int recent=jdbc.queryForObject("SELECT COUNT(*)::int FROM reserva_correo_verificaciones WHERE correo=? AND origen='CODIGO' AND creado_en>?",Integer.class,email,ts(now.minusSeconds(900)));
        boolean cooldown=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM reserva_correo_verificaciones WHERE correo=? AND origen='CODIGO' AND creado_en>?)",Boolean.class,email,ts(now.minusSeconds(60)));
        if(cooldown || recent>=3) throw limite();
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1000000));
        String guest=name==null?"":name.trim(); if(guest.length()>150) throw ApiException.datosInvalidos("El nombre es demasiado largo.",List.of());
        var notice=outbox.encolar(TipoNotificacion.CORREO_OTP,email,Map.of("proposito","VERIFICAR_RESERVA","nombreHuesped",guest,"codigo",code,"minutos",10));
        jdbc.update("INSERT INTO reserva_correo_verificaciones(id,correo,borrador,codigo_hash,creado_en,expira_en,outbox_id) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),email,draft,encoder.encode(code),ts(now),ts(now.plusSeconds(600)),notice.getId());
        return Map.of("estado","SOLICITADO","mensaje","Solicitud recibida. Revisa tu correo para verificarlo.","reenviarEn",60);
    }
    @Transactional(noRollbackFor=ApiException.class)
    public Map<String,Object> verificar(String address,UUID draft,String code) {
        String email=correo(address); Instant now=clock.instant(); lock(email);
        if(fallos(email,now)>=5) throw limite();
        var rows=jdbc.queryForList("SELECT * FROM reserva_correo_verificaciones WHERE correo=? AND borrador=? AND origen='CODIGO' ORDER BY creado_en DESC LIMIT 1 FOR UPDATE",email,draft);
        if(rows.isEmpty()) throw invalido();
        var row=rows.getFirst();
        if(row.get("verificado_en")!=null || !((Timestamp)row.get("expira_en")).toInstant().isAfter(now) || !encoder.matches(code,(String)row.get("codigo_hash"))) {
            jdbc.update("UPDATE reserva_correo_verificaciones SET intentos=intentos+1 WHERE id=?",row.get("id")); throw invalido();
        }
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); String receipt=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("UPDATE reserva_correo_verificaciones SET verificado_en=?,comprobante_hash=?,comprobante_expira_en=? WHERE id=?",ts(now),hash(receipt),ts(now.plusSeconds(1800)),row.get("id"));
        return Map.of("verificado",true,"comprobante",receipt,"expiraEn",1800);
    }
    @Transactional(readOnly=true)
    public boolean estado(String address,UUID draft,String receipt) {
        if(receipt==null || !receipt.matches("[A-Za-z0-9_-]{43}")) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM reserva_correo_verificaciones WHERE correo=? AND borrador=? AND comprobante_hash=? AND verificado_en IS NOT NULL AND consumido_en IS NULL AND comprobante_expira_en>?)",Boolean.class,correo(address),draft,hash(receipt),ts(clock.instant())));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> informacion(String address,UUID draft,String receipt) {
        boolean verified=estado(address,draft,receipt);String email=correo(address);Instant now=clock.instant();
        var rows=jdbc.queryForList("SELECT v.creado_en,v.expira_en,o.estado AS envio FROM reserva_correo_verificaciones v JOIN outbox o ON o.id=v.outbox_id WHERE v.correo=? AND v.borrador=? AND v.origen='CODIGO' AND v.verificado_en IS NULL ORDER BY v.creado_en DESC LIMIT 1",email,draft);
        boolean requested=!rows.isEmpty() && ((Timestamp)rows.getFirst().get("expira_en")).toInstant().isAfter(now);
        long wait=requested?Math.max(0,Duration.between(now,((Timestamp)rows.getFirst().get("creado_en")).toInstant().plusSeconds(60)).getSeconds()):0;
        String envio=requested?String.valueOf(rows.getFirst().get("envio")):"NINGUNO";
        return Map.of("verificado",verified,"solicitado",requested && "ENVIADO".equals(envio),"envio",envio,"reenviarEn",wait);
    }
    @Transactional
    public Map<String,Object> verificarGoogle(String address,UUID draft,String idToken) {
        String client=System.getenv("GOOGLE_CLIENT_ID");
        if(client==null || client.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GOOGLE_NO_CONFIGURADO","La verificación de Google no está disponible.");
        final org.springframework.security.oauth2.jwt.Jwt jwt;
        try {
            if(google==null) synchronized(this) { if(google==null) google=org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build(); }
            jwt=google.decode(idToken);
            if(!Set.of("https://accounts.google.com","accounts.google.com").contains(jwt.getClaimAsString("iss")) || !jwt.getAudience().contains(client) || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) || jwt.getIssuedAt()==null || jwt.getIssuedAt().isBefore(clock.instant().minusSeconds(600)) || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(60))) throw new IllegalArgumentException();
        } catch(Exception e) { throw new ApiException(HttpStatus.UNAUTHORIZED,"GOOGLE_IDENTIDAD_INVALIDA","No se pudo validar el correo de Google."); }
        String email=correo(jwt.getClaimAsString("email"));
        if(!email.equals(correo(address))) throw new ApiException(HttpStatus.UNAUTHORIZED,"GOOGLE_IDENTIDAD_INVALIDA","El correo no coincide con Google.");
        // Google is authoritative for Gmail and verified Workspace domains; third-party addresses need a challenge.
        String hosted=jwt.getClaimAsString("hd");
        if(!email.endsWith("@gmail.com") && (hosted==null || hosted.isBlank())) return Map.of("verificado",false);
        lock(email);Instant now=clock.instant();byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String receipt=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO reserva_correo_verificaciones(id,correo,borrador,codigo_hash,creado_en,expira_en,verificado_en,comprobante_hash,comprobante_expira_en,origen) VALUES(?,?,?,?,?,?,?,?,?,'GOOGLE')",UUID.randomUUID(),email,draft,"NO_APLICA_GOOGLE",ts(now),ts(now.plusSeconds(600)),ts(now),hash(receipt),ts(now.plusSeconds(1800)));
        return Map.of("verificado",true,"comprobante",receipt,"expiraEn",1800);
    }
    @Transactional
    public UUID consumir(String address,String draft,String receipt) {
        if(draft==null || receipt==null || !receipt.matches("[A-Za-z0-9_-]{43}")) throw requerida();
        final UUID draftId; try { draftId=UUID.fromString(draft); } catch(IllegalArgumentException e) { throw requerida(); }
        String email=correo(address); lock(email);
        var ids=jdbc.queryForList("UPDATE reserva_correo_verificaciones SET consumido_en=? WHERE correo=? AND borrador=? AND comprobante_hash=? AND verificado_en IS NOT NULL AND consumido_en IS NULL AND comprobante_expira_en>? RETURNING id",UUID.class,ts(clock.instant()),email,draftId,hash(receipt),ts(clock.instant()));
        if(ids.size()!=1) throw requerida();
        return ids.getFirst();
    }
    /** Admite renovar el comprobante para el mismo correo y borrador, sin consumirlo otra vez. */
    @Transactional(readOnly=true)
    public boolean autorizaRecuperacion(UUID original, String draft, String receipt) {
        if (original == null || draft == null || receipt == null || !receipt.matches("[A-Za-z0-9_-]{43}")) return false;
        final UUID draftId;
        try { draftId=UUID.fromString(draft); } catch(IllegalArgumentException e) { return false; }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM reserva_correo_verificaciones original
                    JOIN reserva_correo_verificaciones actual
                      ON actual.correo=original.correo AND actual.borrador=original.borrador
                    WHERE original.id=? AND original.consumido_en IS NOT NULL
                      AND actual.borrador=? AND actual.comprobante_hash=?
                      AND actual.verificado_en IS NOT NULL AND actual.comprobante_expira_en>?
                )
                """, Boolean.class, original, draftId, hash(receipt), ts(clock.instant())));
    }
    private static ApiException requerida() { return new ApiException(HttpStatus.FORBIDDEN,"CORREO_NO_VERIFICADO","Verifica tu correo antes de crear la reserva."); }
}
