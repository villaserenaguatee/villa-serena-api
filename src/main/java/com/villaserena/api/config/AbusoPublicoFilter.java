package com.villaserena.api.config;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Límite adicional a los contadores transaccionales por cuenta. No confía en cabeceras del cliente. */
@Component
public class AbusoPublicoFilter extends OncePerRequestFilter {
    static final long MAX_BODY = 8L * 1024 * 1024;
    private static class Limitado extends ServletInputStream {
        final ServletInputStream original; long leidos; boolean exceso;
        Limitado(ServletInputStream original) { this.original=original; }
        private void contar(int n)throws IOException {if(n>0)leidos+=n;if(leidos>MAX_BODY){exceso=true;throw new IOException("Request body limit exceeded");}}
        public int read()throws IOException{if(exceso)throw new IOException("Request body limit exceeded");int n=original.read();contar(n<0?0:1);return n;}
        public int read(byte[] b,int off,int len)throws IOException{if(exceso)throw new IOException("Request body limit exceeded");int n=original.read(b,off,(int)Math.min(len,MAX_BODY-leidos+1));contar(n);return n;}
        public boolean isFinished(){return original.isFinished();}
        public boolean isReady(){return original.isReady();}
        public void setReadListener(ReadListener listener){original.setReadListener(listener);}
    }
    private final Clock clock;
    private final Map<String, Ventana> ventanas = new HashMap<>();
    private record Ventana(long fin, int usados) {}
    public AbusoPublicoFilter(Clock clock) { this.clock = clock; }
    synchronized boolean admitir(String clave, int maximo, long segundos) {
        long ahora = clock.instant().getEpochSecond();
        ventanas.entrySet().removeIf(e -> e.getValue().fin() <= ahora);
        Ventana anterior = ventanas.get(clave);
        if (anterior == null && ventanas.size() >= 10000) return false;
        if (anterior != null && anterior.usados() >= maximo) return false;
        ventanas.put(clave, new Ventana(anterior == null ? ahora + segundos : anterior.fin(), anterior == null ? 1 : anterior.usados() + 1));
        return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (req.getContentLengthLong() > 8L * 1024 * 1024) {
            res.setStatus(413); res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"codigo\":\"CONTENIDO_DEMASIADO_GRANDE\",\"mensaje\":\"El contenido supera el tamaño permitido.\"}"); return;
        }
        String ruta = req.getRequestURI(), grupo = null; int maximo = 0; long segundos = 60;
        if ("POST".equals(req.getMethod())) {
            if (ruta.equals("/api/v1/app/acceso/recuperacion/solicitar")) { grupo = "recuperacion-envio"; maximo = 6; segundos = 900; }
            else if (ruta.equals("/api/v1/app/acceso/recuperacion/confirmar")) { grupo = "recuperacion-confirmar"; maximo = 20; segundos = 900; }
            else if (ruta.equals("/api/v1/app/acceso/solicitar-codigo")) { grupo = "otp"; maximo = 3; segundos = 900; }
            else if (ruta.equals("/api/v1/publico/correo/solicitar")) { grupo = "verificar-reserva-envio"; maximo = 6; segundos = 900; }
            else if (ruta.equals("/api/v1/publico/correo/verificar")) { grupo = "verificar-reserva-codigo"; maximo = 30; }
            else if (ruta.equals("/api/v1/publico/correo/estado")) { grupo = "verificar-reserva-estado"; maximo = 120; }
            else if (ruta.equals("/api/v1/publico/correo/google")) { grupo = "verificar-reserva-google"; maximo = 20; }
            else if (ruta.equals("/api/v1/publico/contacto")) { grupo = "contacto"; maximo = 3; segundos = 900; }
            else if (ruta.equals("/api/v1/auth/login") || ruta.equals("/api/v1/app/acceso/iniciar-sesion") || ruta.equals("/api/v1/app/acceso/verificar-codigo")) { grupo = "login"; maximo = 30; }
        }
        if (grupo != null && !admitir(grupo + ":" + req.getRemoteAddr(), maximo, segundos)) {
            res.setStatus(429); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
            res.setHeader("Retry-After", Long.toString(segundos)); res.setHeader("Cache-Control", "no-store");
            res.getWriter().write("{\"codigo\":\"LIMITE_SOLICITUDES\",\"mensaje\":\"Demasiadas solicitudes. Espera antes de volver a intentarlo.\",\"detalles\":[]}");
            return;
        }
        var limited = new Limitado(req.getInputStream());
        var wrapper = new HttpServletRequestWrapper(req) {
            public ServletInputStream getInputStream(){return limited;}
            public java.io.BufferedReader getReader(){return new java.io.BufferedReader(new java.io.InputStreamReader(limited,java.nio.charset.StandardCharsets.UTF_8));}
        };
        try { chain.doFilter(wrapper, res); }
        catch(IOException|ServletException e){
            if(!limited.exceso||res.isCommitted())throw e;
            res.resetBuffer();res.setStatus(413);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"codigo\":\"CONTENIDO_DEMASIADO_GRANDE\",\"mensaje\":\"El contenido supera el tamaño permitido.\"}");
        }
    }
}
