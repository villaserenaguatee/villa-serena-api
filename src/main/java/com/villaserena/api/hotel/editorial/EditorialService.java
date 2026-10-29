package com.villaserena.api.hotel.editorial;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.config.PropiedadesVillaSerena;

@Service
public class EditorialService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AlmacenArchivos almacen;
    private final String urlPublica;
    private static final Set<String> DESTINOS = Set.of("/catalogo", "/nosotros", "/reservar/habitaciones", "/#habitaciones", "/#servicios", "/#contacto");

    public EditorialService(JdbcTemplate jdbc, ObjectMapper json, AlmacenArchivos almacen, PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.json = json;
        this.almacen = almacen;
        this.urlPublica = propiedades.archivos().urlPublica();
    }

    @Transactional(readOnly = true)
    public EditorialSitio borrador(String idioma) {
        idioma(idioma);
        configurado(false);
        var filas = consultarBorrador(idioma, false);
        return vista(idioma, filas.isEmpty() ? new Documento(0, null, List.of()) : filas.getFirst());
    }

    @Transactional(readOnly = true)
    public EditorialSitio publicado(String idioma) {
        idioma(idioma);
        var filas = jdbc.query("SELECT revision, published_at, content FROM hotel_editorial_publications WHERE language=? ORDER BY revision DESC LIMIT 1",
                (rs, i) -> new Documento(rs.getLong(1), rs.getTimestamp(2).toInstant(), leer(rs.getString(3))), idioma);
        if (filas.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENIDO_NO_PUBLICADO", "Todavía no hay contenido publicado en este idioma.");
        Documento actual = filas.getFirst();
        return new EditorialSitio(idioma, actual.revision(), actual.revision(), null, actual.fecha(), actual.elementos(), imagenes(actual.elementos()));
    }

    @Transactional
    public EditorialSitio guardar(String idioma, GuardarEditorialPeticion peticion) {
        idioma(idioma);
        configurado(true);
        validar(peticion.elementos());
        jdbc.update("INSERT INTO hotel_editorial_drafts(language,revision,content,updated_by) VALUES (?,0,'[]'::jsonb,?) ON CONFLICT(language) DO NOTHING",
                idioma, UsuarioActual.id());
        Documento actual = consultarBorrador(idioma, true).getFirst();
        if (actual.revision() != peticion.revision()) throw cambiado();
        jdbc.update("UPDATE hotel_editorial_drafts SET revision=revision+1,content=?::jsonb,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE language=?",
                json.writeValueAsString(peticion.elementos()), UsuarioActual.id(), idioma);
        return vista(idioma, consultarBorrador(idioma, false).getFirst());
    }

    @Transactional
    public EditorialSitio publicar(String idioma, long revision) {
        idioma(idioma);
        configurado(true);
        var filas = consultarBorrador(idioma, true);
        if (filas.isEmpty()) throw invalido("Guarda un borrador antes de publicarlo.");
        Documento actual = filas.getFirst();
        if (actual.revision() != revision) throw cambiado();
        var existente = jdbc.queryForList("SELECT revision FROM hotel_editorial_publications WHERE language=? AND revision=?", Long.class, idioma, revision);
        if (existente.isEmpty()) {
            validar(actual.elementos());
            jdbc.update("INSERT INTO hotel_editorial_publications(language,revision,content,published_by) VALUES (?,?,?::jsonb,?)",
                    idioma, revision, json.writeValueAsString(actual.elementos()), UsuarioActual.id());
        }
        return vista(idioma, actual);
    }

    private void configurado(boolean bloquear) {
        // Mismo orden de bloqueos que galería: configuración primero, borrador después.
        if (jdbc.queryForList("SELECT id FROM hotel_configuration WHERE id=1" + (bloquear ? " FOR SHARE" : ""), Long.class).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "HOTEL_NO_CONFIGURADO", "Configura el hotel antes de gestionar su contenido.");
    }

    private List<Documento> consultarBorrador(String idioma, boolean bloquear) {
        return jdbc.query("SELECT revision,updated_at,content FROM hotel_editorial_drafts WHERE language=?" + (bloquear ? " FOR UPDATE" : ""),
                (rs, i) -> new Documento(rs.getLong(1), rs.getTimestamp(2).toInstant(), leer(rs.getString(3))), idioma);
    }

    private EditorialSitio vista(String idioma, Documento documento) {
        var publicado = jdbc.query("SELECT revision,published_at FROM hotel_editorial_publications WHERE language=? ORDER BY revision DESC LIMIT 1",
                (rs, i) -> Map.entry(rs.getLong(1), rs.getTimestamp(2).toInstant()), idioma);
        return new EditorialSitio(idioma, documento.revision(), publicado.isEmpty() ? 0 : publicado.getFirst().getKey(), documento.fecha(),
                publicado.isEmpty() ? null : publicado.getFirst().getValue(), documento.elementos(), imagenes(documento.elementos()));
    }

    private List<ElementoEditorial> leer(String contenido) {
        return json.readValue(contenido, new TypeReference<List<ElementoEditorial>>() {});
    }

    private Map<String, String> imagenes(List<ElementoEditorial> elementos) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (var elemento : elementos) {
            if (elemento.fotoClave() != null) urls.put(elemento.fotoClave(), urlPublica + "/" + elemento.fotoClave());
            if (elemento.fotoClaves() != null) for (String clave : elemento.fotoClaves()) urls.put(clave, urlPublica + "/" + clave);
        }
        return urls;
    }

    private void validar(List<ElementoEditorial> elementos) {
        if (elementos == null || elementos.isEmpty() || elementos.size() > CatalogoEditorial.IDS.size()
                || elementos.stream().map(ElementoEditorial::id).distinct().count() != elementos.size())
            throw invalido("Revisa los elementos del documento; no pueden estar vacíos ni repetidos.");
        for (var elemento : elementos) {
            String id = elemento.id();
            if (!CatalogoEditorial.IDS.contains(id)) throw invalido("El elemento seleccionado no admite edición editorial.");
            if (id.endsWith(".imagen") || id.endsWith(".imagenes")) {
                var claves = elemento.fotoClaves() != null ? elemento.fotoClaves()
                        : elemento.fotoClave() != null ? List.of(elemento.fotoClave()) : null;
                int maximo = id.endsWith(".imagenes") ? 30 : 1;
                if (elemento.texto() != null || elemento.destino() != null || claves == null
                        || elemento.fotoClave() != null && elemento.fotoClaves() != null
                        || claves.size() > maximo || claves.stream().distinct().count() != claves.size()
                        || "identidad.logo.imagen".equals(id) && claves.isEmpty())
                    throw invalido("Revisa las fotografías de la sección y su orden.");
                for (String clave : claves) {
                    boolean registrada = clave != null && clave.startsWith("hotel/")
                            && jdbc.queryForObject("SELECT count(*) FROM hotel_photos WHERE object_key=?", Integer.class, clave) > 0;
                    if (clave == null || !registrada && !clave.matches("hotel/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"))
                        throw invalido("Selecciona una fotografía cargada para el hotel.");
                    boolean existe;
                    try { existe = almacen.existePublico(clave); }
                    catch (RuntimeException error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ALMACEN_NO_DISPONIBLE", "No se pudo comprobar la fotografía. Conservamos el contenido anterior."); }
                    if (!existe) throw invalido("La fotografía ya no está disponible.");
                }
            } else {
                String texto = elemento.texto();
                if (elemento.fotoClave() != null || elemento.fotoClaves() != null || texto == null || texto.isBlank() || texto.length() > 4000
                        || texto.indexOf('<') >= 0 || texto.indexOf('>') >= 0
                        || texto.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                    throw invalido("Introduce texto sin etiquetas ni contenido ejecutable.");
                if ((id.endsWith(".titulo") || id.endsWith(".subtitulo") || id.endsWith(".boton")) && texto.length() > 200)
                    throw invalido("Los títulos y botones admiten hasta 200 caracteres.");
                if (id.endsWith(".boton")) {
                    if (elemento.destino() == null || !DESTINOS.contains(elemento.destino())) throw invalido("Selecciona un destino permitido para el botón.");
                } else if (elemento.destino() != null) throw invalido("Este texto no admite un enlace.");
            }
        }
    }

    private static void idioma(String idioma) {
        if (!Set.of("es", "en").contains(idioma)) throw invalido("Selecciona español o inglés.");
    }

    private static ApiException cambiado() {
        return ApiException.conflicto("CONTENIDO_CAMBIADO", "Otro administrador modificó el borrador. Actualiza y revisa tus cambios antes de continuar.");
    }

    private static ApiException invalido(String mensaje) {
        return ApiException.datosInvalidos(mensaje, List.of(new DetalleError("elementos", mensaje)));
    }

    private record Documento(long revision, Instant fecha, List<ElementoEditorial> elementos) {}
}
