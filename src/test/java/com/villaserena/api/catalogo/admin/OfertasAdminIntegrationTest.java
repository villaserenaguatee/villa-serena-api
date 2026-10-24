package com.villaserena.api.catalogo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.CatalogoOfertas;
import com.villaserena.api.reservas.pagos.PasarelaPagos;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class OfertasAdminIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired CatalogoOfertas catalogo;
    @Autowired AlmacenEnMemoria.Almacen almacen;
    @MockitoBean PasarelaPagos pasarela;
    long tipo, uno, dos;
    String admin, recepcion;
    Map<String, Object> oferta;

    @BeforeEach
    void preparar() throws Exception {
        admin=token("sofia.morales@villaserena.test"); recepcion=token("ana.perez@villaserena.test");
        tipo=jdbc.queryForObject("INSERT INTO room_types(name,description,capacity,base_price) VALUES(?,'Tipo prueba',3,500) RETURNING id",Long.class,"Tipo " + UUID.randomUUID());
        uno=habitacion("X"+UUID.randomUUID().toString().substring(0,6));
        dos=habitacion("Y"+UUID.randomUUID().toString().substring(0,6));
        for(long id:List.of(uno,dos)) guardarFeatures(id).andExpect(status().isOk());
        almacen.objetos().put("tipos/prueba.png",new byte[]{1});
        mvc.perform(put("/api/v1/admin/tipos-habitacion/{id}/fotos",tipo).header("Authorization",admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fotos\":[{\"clave\":\"tipos/prueba.png\",\"principal\":true,\"orden\":0}]}"))
                .andExpect(status().isOk());
        oferta=new LinkedHashMap<>(Map.of("tipoHabitacionId",tipo,"nombre","Suite de prueba","descripcion","Contenido registrado",
                "piso",1,"capacidad",2,"superficieM2",30,"camas",Map.of("queen",1),"caracteristicas",List.of("Wi-Fi"),
                "preferencias",List.of("Descanso"),"precioPorNoche",650));
        oferta.put("publicada",true);oferta.put("orden",25);oferta.put("habitacionIds",List.of(uno,dos));
    }

    @Test
    void administraOfertaCompletaYPublicaUnaSolaTarjetaConFotosDelTipo() throws Exception {
        guardar(true).andExpect(status().isCreated()).andExpect(jsonPath("$.cantidadHabitaciones").value(2))
                .andExpect(jsonPath("$.cantidadHabitacionesTipo").value(2)).andExpect(jsonPath("$.camas.queen").value(1));
        var publicas=catalogo.catalogo().stream().filter(o->"suite-prueba".equals(o.get("slug"))).toList();
        assertThat(publicas).hasSize(1);
        var publica=publicas.getFirst();
        assertThat(publica).containsEntry("name","Suite de prueba").containsEntry("bookable",true);
        assertThat(publica.get("price")).isEqualTo(new BigDecimal("650.00"));
        assertThat(publica.get("image").toString()).endsWith("/tipos/prueba.png");
        assertThat(publica).doesNotContainKeys("published","bedCounts","pendingFeatures");
        oferta.put("nombre","Suite editada");oferta.put("descripcion","Descripción nueva");oferta.put("precioPorNoche",720);
        guardar(false).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Suite editada"));
        var actual=catalogo.catalogo().stream().filter(o->"suite-prueba".equals(o.get("slug"))).findFirst().orElseThrow();
        assertThat(actual).containsEntry("description","Descripción nueva").containsEntry("price",new BigDecimal("720.00"));
    }

    @Test
    void galeriaActualizadaEn82SeReflejaSinCopiarFotosALaOferta() throws Exception {
        guardar(true).andExpect(status().isCreated());
        almacen.objetos().put("tipos/nueva.png",new byte[]{2});
        mvc.perform(put("/api/v1/admin/tipos-habitacion/{id}/fotos",tipo).header("Authorization",admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fotos\":[{\"clave\":\"tipos/nueva.png\",\"principal\":true,\"orden\":0}]}"))
                .andExpect(status().isOk());
        var publica=catalogo.catalogo().stream().filter(o->"suite-prueba".equals(o.get("slug"))).findFirst().orElseThrow();
        assertThat(publica.get("image").toString()).endsWith("/tipos/nueva.png");
    }

    @Test
    void publicaYDespublicaSinBorrarContenidoNiVinculos() throws Exception {
        guardar(true).andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/admin/ofertas/suite-prueba/publicacion").header("Authorization",admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"publicada\":false}")).andExpect(status().isOk());
        assertThat(catalogo.catalogo()).noneMatch(o->"suite-prueba".equals(o.get("slug")));
        mvc.perform(get("/api/v1/admin/ofertas/suite-prueba").header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.habitacionIds.length()").value(2)).andExpect(jsonPath("$.publicada").value(false));
        mvc.perform(patch("/api/v1/admin/ofertas/suite-prueba/publicacion").header("Authorization",admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"publicada\":true}")).andExpect(status().isOk());
        assertThat(catalogo.catalogo()).anyMatch(o->"suite-prueba".equals(o.get("slug")));
    }

    @Test
    void precioDeOfertaCotizaIgualYReservasPreviasConservanImportesYSnapshot() throws Exception {
        guardar(true).andExpect(status().isCreated());
        LocalDate entrada=LocalDate.now(java.time.ZoneId.of("America/Guatemala")).plusDays(2),salida=entrada.plusDays(2);
        var disponible=catalogo.disponibles(entrada,salida,2).stream().filter(o->"suite-prueba".equals(o.get("ofertaSlug"))).findFirst().orElseThrow();
        assertThat((BigDecimal) disponible.get("total")).isEqualByComparingTo("1300");
        // La referencia histórica y su precio no se actualizan al editar la oferta comercial.
        var peticion=new com.villaserena.api.reservas.dto.ReservaWebPeticion(tipo,entrada,salida,2,
                new com.villaserena.api.huespedes.HuespedDatos("Huésped catálogo",UUID.randomUUID()+"@example.test",
                        "+50255555555",null,com.villaserena.api.huespedes.TipoDocumento.DPI,"1234567890101"),null);
        var creada=catalogo.crear("suite-prueba",peticion);
        long reserva=creada.getId();
        long guest=creada.getHuespedId();
        assertThat(creada.getTotal()).isEqualByComparingTo("1300");
        long asignada=jdbc.queryForObject("SELECT room_id FROM reservations WHERE id=?",Long.class,reserva);
        long libre=asignada==uno?dos:uno;
        String snapshot=jdbc.queryForObject("SELECT content::text FROM reservation_offers WHERE reservation_id=?",String.class,reserva);
        oferta.put("precioPorNoche",800);guardar(false).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT total FROM reservations WHERE id=?",BigDecimal.class,reserva)).isEqualByComparingTo("1300");
        assertThat(jdbc.queryForList("SELECT price FROM reservation_nights WHERE reservation_id=?",BigDecimal.class,reserva)).allSatisfy(precio -> assertThat(precio).isEqualByComparingTo("650"));
        assertThat(jdbc.queryForObject("SELECT content::text FROM reservation_offers WHERE reservation_id=?",String.class,reserva)).isEqualTo(snapshot);
        assertThat((BigDecimal) catalogo.disponibles(entrada,salida,2).stream().filter(o->"suite-prueba".equals(o.get("ofertaSlug")))
                .findFirst().orElseThrow().get("total")).isEqualByComparingTo("1600");
        mvc.perform(put("/api/v1/admin/habitaciones/{id}/caracteristicas",asignada).header("Authorization",admin)
                .contentType(MediaType.APPLICATION_JSON).content(features())).andExpect(status().isConflict());
        // Con ambas habitaciones reservadas ya no queda disponibilidad de esta oferta.
        jdbc.update("INSERT INTO reservations(code,guest_id,room_type_id,room_id,checkin_date,checkout_date,guest_count,status,channel,total) "
                + "VALUES('VS-Y8Y8Y8',?,?,?,?,?,2,'CONFIRMADA','RECEPCION',1300)",guest,tipo,libre,entrada,salida);
        assertThat(catalogo.disponibles(entrada,salida,2)).noneMatch(o->"suite-prueba".equals(o.get("ofertaSlug")));
    }

    @Test
    void rechazaVinculosInvalidosYConservaOfertaAlFallar() throws Exception {
        guardar(true).andExpect(status().isCreated());
        for(List<Long> ids:List.of(List.of(uno,uno),List.of(999999L),List.of(jdbc.queryForObject("SELECT min(id) FROM rooms WHERE room_type_id<>?",Long.class,tipo)))) {
            oferta.put("habitacionIds",ids);guardar(false).andExpect(status().isBadRequest());
        }
        oferta.put("habitacionIds",List.of(uno,dos));oferta.put("caracteristicas",List.of("Jacuzzi inexistente"));
        guardar(false).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_offer_rooms WHERE slug='suite-prueba'",Integer.class)).isEqualTo(2);
        assertThat(catalogo.catalogo().stream().filter(o->"suite-prueba".equals(o.get("slug"))).findFirst().orElseThrow().get("features"))
                .isEqualTo(List.of("Wi-Fi"));
    }

    @Test
    void permisosYValidacionesNoPermitenEscriturasDeOtrosRoles() throws Exception {
        for(String ruta:List.of("/api/v1/admin/ofertas","/api/v1/admin/habitaciones/"+uno+"/caracteristicas")) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta).header("Authorization",recepcion)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/admin/ofertas/suite-prueba").header("Authorization",recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(oferta))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/habitaciones/{id}/caracteristicas",uno).header("Authorization",recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(features())).andExpect(status().isForbidden());
        oferta.put("precioPorNoche",0);guardar(true).andExpect(status().isBadRequest());
        oferta.put("precioPorNoche",650);oferta.put("capacidad",4);guardar(true).andExpect(status().isBadRequest());
        oferta.put("capacidad",2);oferta.put("piso",2);guardar(true).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_offers WHERE slug='suite-prueba'",Integer.class)).isZero();
    }

    @Test
    void noPublicaSinGaleriaOVinculosPeroPuedeGuardarBorrador() throws Exception {
        oferta.put("habitacionIds",List.of());guardar(true).andExpect(status().isBadRequest());
        oferta.put("publicada",false);guardar(true).andExpect(status().isCreated());
        guardar(true).andExpect(status().isConflict());
        oferta.put("habitacionIds",List.of(uno));oferta.put("publicada",true);
        jdbc.update("DELETE FROM room_type_photos WHERE room_type_id=?",tipo);
        guardar(false).andExpect(status().isBadRequest());
    }

    @Test
    void despublicaOfertaAnteriorSinExigirCompletarDatosNiBorrarVinculos() throws Exception {
        long antes=jdbc.queryForObject("SELECT count(*) FROM catalog_offer_rooms WHERE slug='habitacion-estandar'",Long.class);
        mvc.perform(patch("/api/v1/admin/ofertas/habitacion-estandar/publicacion").header("Authorization",admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"publicada\":false}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_offer_rooms WHERE slug='habitacion-estandar'",Long.class)).isEqualTo(antes);
        assertThat(catalogo.catalogo()).noneMatch(o->"habitacion-estandar".equals(o.get("slug")));
    }

    @Test
    void cambiosDeHabitacionOEstadoDelTipoActualizanElegibilidadSinInventarCupo() throws Exception {
        guardar(true).andExpect(status().isCreated());
        jdbc.update("UPDATE rooms SET condition='FUERA_DE_SERVICIO' WHERE id IN (?,?)",uno,dos);
        assertThat(catalogo.catalogo().stream().filter(o->"suite-prueba".equals(o.get("slug"))).findFirst().orElseThrow().get("bookable")).isEqualTo(false);
        jdbc.update("UPDATE rooms SET condition='LIMPIA' WHERE id IN (?,?)",uno,dos);
        jdbc.update("UPDATE room_types SET capacity=1 WHERE id=?",tipo);
        assertThat(catalogo.catalogo().stream().filter(o->"suite-prueba".equals(o.get("slug"))).findFirst().orElseThrow().get("bookable")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administration_audit WHERE resource='ofertas' AND record_id=?",Integer.class,tipo)).isEqualTo(1);
    }

    @Test
    void caracteristicasHistoricasNoInventanAtributosNiExponenNotasInternas() throws Exception {
        jdbc.update("UPDATE room_features SET content=?::jsonb WHERE room_id=?",
                "{\"piso\":1,\"capacidad\":2,\"camas\":{\"queen\":1},\"features\":[\"Wi-Fi\"],\"notaInterna\":\"Pendiente\",\"jardin\":true}",uno);
        mvc.perform(get("/api/v1/admin/habitaciones/{id}/caracteristicas",uno).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.superficieM2").doesNotExist())
                .andExpect(jsonPath("$.notaInterna").doesNotExist()).andExpect(jsonPath("$.jardin").doesNotExist())
                .andExpect(jsonPath("$.caracteristicas[0]").value("Wi-Fi"));
    }

    private long habitacion(String numero) { return jdbc.queryForObject("INSERT INTO rooms(number,floor,room_type_id) VALUES(?,1,?) RETURNING id",Long.class,numero,tipo); }
    private String token(String email) { return "Bearer "+tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(email).orElseThrow()).accessToken(); }
    private String features() { return "{\"capacidad\":2,\"superficieM2\":30,\"camas\":{\"queen\":1},\"caracteristicas\":[\"Wi-Fi\"]}"; }
    private ResultActions guardarFeatures(long id) throws Exception { return mvc.perform(put("/api/v1/admin/habitaciones/{id}/caracteristicas",id)
            .header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(features())); }
    private ResultActions guardar(boolean crear) throws Exception { return mvc.perform((crear?post("/api/v1/admin/ofertas/suite-prueba"):put("/api/v1/admin/ofertas/suite-prueba"))
            .header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(oferta))); }
}
