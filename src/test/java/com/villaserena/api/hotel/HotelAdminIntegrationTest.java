package com.villaserena.api.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.Empleado;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.estadia.CheckinService;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class HotelAdminIntegrationTest {

    private static final String RUTA = "/api/v1/admin/hotel";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokens;

    @Autowired
    PropiedadesVillaSerena propiedades;

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    RelojFijoConfig.RelojPrueba reloj;

    @MockitoBean
    PasarelaPagos pasarela;

    @Autowired AlmacenEnMemoria.Almacen almacen;

    String admin;
    String recepcion;
    long recepcionistaId;

    @BeforeEach
    void preparar() {
        admin = tokenEmpleado("sofia.morales@villaserena.test");
        recepcionistaId = empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow().getId();
        recepcion = tokenEmpleado("ana.perez@villaserena.test");
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    @Test
    void consultaYActualizaDatosGeneralesFiscalesYFotosConCoherenciaPublica() throws Exception {
        jdbc.update("INSERT INTO hotel_photos (object_key, sort_order) VALUES ('hotel/jardin.jpg', 2), ('hotel/fachada.jpg', 0)");

        String cuerpoInicial = mvc.perform(get(RUTA).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Villa Serena"))
                .andExpect(jsonPath("$.direccion").value("5a Avenida Norte 12, Antigua Guatemala, Sacatepéquez"))
                .andExpect(jsonPath("$.telefono").value("+502 7832 0000"))
                .andExpect(jsonPath("$.correo").value("reservas@villaserena.test"))
                .andExpect(jsonPath("$.nombreComercial").value("Hotel Villa Serena"))
                .andExpect(jsonPath("$.razonSocial").value("Villa Serena Hotelera, S.A."))
                .andExpect(jsonPath("$.nit").value("4817205-7"))
                .andExpect(jsonPath("$.direccionFiscal").value("5a Avenida Norte 12, Antigua Guatemala, Sacatepéquez"))
                .andExpect(jsonPath("$.fotos", hasSize(2)))
                .andExpect(jsonPath("$.fotos[0].clave").value("hotel/fachada.jpg"))
                .andExpect(jsonPath("$.fotos[0].orden").value(0))
                .andExpect(jsonPath("$.fotos[1].clave").value("hotel/jardin.jpg"))
                .andExpect(jsonPath("$.fotos[1].orden").value(2))
                .andExpect(jsonPath("$.horaCheckIn").value("15:00"))
                .andExpect(jsonPath("$.horaCheckOut").value("12:00"))
                .andExpect(jsonPath("$.politicaCancelacion").value(HotelService.POLITICA_CANCELACION))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> campos = JsonPath.read(cuerpoInicial, "$");
        assertThat(campos.keySet()).containsExactlyInAnyOrder(
                "nombre", "descripcion", "direccion", "telefono", "correo",
                "nombreComercial", "razonSocial", "nit", "direccionFiscal",
                "fotos", "horaCheckIn", "horaCheckOut", "politicaCancelacion", "revisionGaleria", "editorFotografico");

        String revision = mvc.perform(get(RUTA).header("Authorization", admin)).andReturn().getResponse().getHeader("ETag");
        almacen.guardarPublico("hotel/00000000-0000-0000-0000-000000000001.jpg", new byte[]{1}, "image/jpeg");
        almacen.guardarPublico("hotel/00000000-0000-0000-0000-000000000002.jpg", new byte[]{1}, "image/jpeg");

        String actualizacion = """
                {
                  "nombre": "  Villa Serena Boutique  ",
                  "descripcion": "  Hotel colonial renovado en Antigua Guatemala.  ",
                  "direccion": "  4a Calle Oriente 15, Antigua Guatemala  ",
                  "telefono": "  +502 7832 9999  ",
                  "correo": "  contacto@villaserena.gt  ",
                  "nombreComercial": "  Villa Serena Antigua  ",
                  "razonSocial": "  Inversiones Villa Serena, S.A.  ",
                  "nit": " 1234567-9 ",
                  "direccionFiscal": "  4a Calle Oriente 15, Zona 1, Antigua Guatemala  ",
                  "fotos": [
                    { "clave": "hotel/00000000-0000-0000-0000-000000000002.jpg", "orden": 1 },
                    { "clave": "hotel/00000000-0000-0000-0000-000000000001.jpg", "orden": 0 }
                  ]
                }
                """;

        mvc.perform(put(RUTA).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", revision).content(actualizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Villa Serena Boutique"))
                .andExpect(jsonPath("$.descripcion").value("Hotel colonial renovado en Antigua Guatemala."))
                .andExpect(jsonPath("$.direccion").value("4a Calle Oriente 15, Antigua Guatemala"))
                .andExpect(jsonPath("$.telefono").value("+502 7832 9999"))
                .andExpect(jsonPath("$.correo").value("contacto@villaserena.gt"))
                .andExpect(jsonPath("$.nombreComercial").value("Villa Serena Antigua"))
                .andExpect(jsonPath("$.razonSocial").value("Inversiones Villa Serena, S.A."))
                .andExpect(jsonPath("$.nit").value("1234567-9"))
                .andExpect(jsonPath("$.direccionFiscal").value("4a Calle Oriente 15, Zona 1, Antigua Guatemala"))
                .andExpect(jsonPath("$.fotos", hasSize(2)))
                .andExpect(jsonPath("$.fotos[0].clave").value("hotel/00000000-0000-0000-0000-000000000001.jpg"))
                .andExpect(jsonPath("$.fotos[0].orden").value(0))
                .andExpect(jsonPath("$.fotos[1].clave").value("hotel/00000000-0000-0000-0000-000000000002.jpg"))
                .andExpect(jsonPath("$.fotos[1].orden").value(1))
                .andExpect(jsonPath("$.horaCheckIn").value("15:00"))
                .andExpect(jsonPath("$.horaCheckOut").value("12:00"))
                .andExpect(jsonPath("$.politicaCancelacion").value(HotelService.POLITICA_CANCELACION));

        String base = propiedades.archivos().urlPublica();
        mvc.perform(get("/api/v1/publico/hotel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Villa Serena Boutique"))
                .andExpect(jsonPath("$.descripcion").value("Hotel colonial renovado en Antigua Guatemala."))
                .andExpect(jsonPath("$.ubicacion.direccion").value("4a Calle Oriente 15, Antigua Guatemala"))
                .andExpect(jsonPath("$.contacto.telefono").value("+502 7832 9999"))
                .andExpect(jsonPath("$.contacto.correo").value("contacto@villaserena.gt"))
                .andExpect(jsonPath("$.fotos", contains(base + "/hotel/00000000-0000-0000-0000-000000000001.jpg", base + "/hotel/00000000-0000-0000-0000-000000000002.jpg")))
                .andExpect(jsonPath("$.horaCheckIn").value("15:00"))
                .andExpect(jsonPath("$.horaCheckOut").value("12:00"));
    }

    @Test
    void hotelNoConfiguradoResponde404YPermiteConfiguracionReal() throws Exception {
        jdbc.update("DELETE FROM hotel_photos");
        jdbc.update("DELETE FROM hotel_configuration");

        mvc.perform(get(RUTA).header("Authorization", admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("HOTEL_NO_CONFIGURADO"));

        mvc.perform(get("/api/v1/publico/hotel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("HOTEL_NO_CONFIGURADO"));

        mvc.perform(post(RUTA + "/inicializacion").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(peticionValida("6-k", "[]").replaceAll(",\\s*\"fotos\": \\[\\]", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Villa Serena"))
                .andExpect(jsonPath("$.nit").value("6-K"))
                .andExpect(jsonPath("$.fotos", hasSize(0)));

        mvc.perform(get(RUTA).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("6-K"));

        mvc.perform(get("/api/v1/publico/hotel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Villa Serena"))
                .andExpect(jsonPath("$.fotos", hasSize(0)));
    }

    @Test
    void exigeAutenticacionYRolAdmin() throws Exception {
        String roomService = tokenEmpleado("rodrigo.aju@villaserena.test");
        String myl = tokenEmpleado("luis.garcia@villaserena.test");
        long huespedId = jdbc.queryForObject("""
                INSERT INTO guests (full_name, email, phone, nationality, document_type, document_number)
                VALUES ('Huésped', 'huesped.hotel@correo.test', '55550000', 'Guatemalteca', 'DPI', '1234567890101')
                RETURNING id""", Long.class);
        String huesped = emitirToken(() -> tokens.emitirParaHuesped(huespedId));

        mvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content(peticionValida("4817205-7", "[]")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        for (String noAutorizado : List.of(recepcion, roomService, myl, huesped)) {
            mvc.perform(get(RUTA).header("Authorization", noAutorizado))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
            mvc.perform(put(RUTA).header("Authorization", noAutorizado)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(peticionValida("4817205-7", "[]")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        }

        Empleado sofia = empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow();
        sofia.setDebeCambiarContrasena(true);
        empleados.flush();
        String adminTemporal = tokenEmpleado("sofia.morales@villaserena.test");
        mvc.perform(get(RUTA).header("Authorization", adminTemporal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_TEMPORAL"));
    }

    @Test
    void validaCamposObligatoriosNitCorreoFotosYRechazaPropiedadesNoEditables() throws Exception {
        List<String> invalidos = List.of(
                "{}",
                peticionValida("4817205-7", "[]").replace("\"Villa Serena\"", "\"   \""),
                peticionValida("4817205-7", "[]").replace("\"Descripción del hotel\"", "\"\""),
                peticionValida("4817205-7", "[]").replace("\"Villa Serena Hotelera, S.A.\"", "\"   \""),
                peticionValida("4817205-7", "[]").replace("\"reservas@villaserena.test\"", "\"correo-invalido\""),
                peticionValida("4817205-7", "[]").replace("\"reservas@villaserena.test\"", "\"sin-dominio@local\""),
                peticionValida("4817205-8", "[]"),
                peticionValida("CF", "[]"),
                peticionValida("cf", "[]"),
                peticionValida("4817205-7", "null"),
                peticionValida("4817205-7", "[{\"clave\":\"   \",\"orden\":0}]"),
                peticionValida("4817205-7", "[{\"clave\":\"hotel/a.jpg\",\"orden\":-1}]"),
                peticionValida("4817205-7", "[{\"clave\":\"incidencias/privada.jpg\",\"orden\":0}]"),
                peticionValida("4817205-7", "[{\"clave\":\"../secreto.jpg\",\"orden\":0}]"),
                peticionValida("4817205-7", "[{\"clave\":\"hotel/a.jpg\",\"orden\":0,\"extra\":true}]"),
                peticionConExtra("\"serie\":\"VS-B\""),
                peticionConExtra("\"correlativo\":99"),
                peticionConExtra("\"horaCheckIn\":\"14:00\""),
                peticionConExtra("\"horaCheckOut\":\"11:00\""),
                peticionConExtra("\"politicaCancelacion\":\"Otra política\"")
        );

        for (String cuerpo : invalidos) {
            mvc.perform(put(RUTA).header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }

        // Ninguna petición rechazada altera los datos existentes.
        mvc.perform(get(RUTA).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Villa Serena"))
                .andExpect(jsonPath("$.nit").value("4817205-7"));
    }

    @Test
    void conservaFacturasHistoricasYCorrelativoAlActualizarDatosFiscales() throws Exception {
        String primeraReserva = enEstadia("101", "primera.factura@correo.test");
        String checkout1 = mvc.perform(post("/api/v1/checkout/{c}", primeraReserva)
                        .header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comprador\":{\"nit\":\"CF\",\"nombreComprador\":\"Cliente Uno\"},\"pago\":{\"metodo\":\"EFECTIVO\"}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long factura1Id = ((Number) JsonPath.read(checkout1, "$.factura.id")).longValue();

        mvc.perform(put(RUTA).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Villa Serena Renovada",
                                  "descripcion": "Descripción actualizada",
                                  "direccion": "Nueva dirección física",
                                  "telefono": "+502 7900 1111",
                                  "correo": "nuevo@villaserena.test",
                                  "nombreComercial": "Nuevo Nombre Comercial",
                                  "razonSocial": "Nueva Razón Social, S.A.",
                                  "nit": "1234567-9",
                                  "direccionFiscal": "Nueva Dirección Fiscal 99",
                                  "fotos": []
                                }
                                """))
                .andExpect(status().isOk());

        // La factura histórica conserva los datos fiscales con los que fue emitida.
        mvc.perform(get("/api/v1/facturas/{id}", factura1Id).header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serie").value("VS-A"))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.hotel.nombreComercial").value("Hotel Villa Serena"))
                .andExpect(jsonPath("$.hotel.razonSocial").value("Villa Serena Hotelera, S.A."))
                .andExpect(jsonPath("$.hotel.nit").value("4817205-7"))
                .andExpect(jsonPath("$.hotel.direccionFiscal").value("5a Avenida Norte 12, Antigua Guatemala, Sacatepéquez"));

        // Una factura nueva usa los nuevos datos fiscales y continúa la misma serie/correlativo.
        String segundaReserva = enEstadia("102", "segunda.factura@correo.test");
        String checkout2 = mvc.perform(post("/api/v1/checkout/{c}", segundaReserva)
                        .header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comprador\":{\"nit\":\"CF\",\"nombreComprador\":\"Cliente Dos\"},\"pago\":{\"metodo\":\"EFECTIVO\"}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long factura2Id = ((Number) JsonPath.read(checkout2, "$.factura.id")).longValue();

        mvc.perform(get("/api/v1/facturas/{id}", factura2Id).header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serie").value("VS-A"))
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.hotel.nombreComercial").value("Nuevo Nombre Comercial"))
                .andExpect(jsonPath("$.hotel.razonSocial").value("Nueva Razón Social, S.A."))
                .andExpect(jsonPath("$.hotel.nit").value("1234567-9"))
                .andExpect(jsonPath("$.hotel.direccionFiscal").value("Nueva Dirección Fiscal 99"));
    }

    @Test
    void inicializacionConHistoriaNoModificaReservasPagosCuentasNiFacturas() throws Exception {
        String codigo = enEstadia("101", "historia.inicializacion@correo.test");
        mvc.perform(post("/api/v1/checkout/{c}", codigo).header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comprador\":{\"nit\":\"CF\",\"nombreComprador\":\"Cliente de prueba\"},\"pago\":{\"metodo\":\"EFECTIVO\"}}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices", Integer.class)).isPositive();
        var antes = new java.util.LinkedHashMap<String, List<Map<String, Object>>>();
        for (String tabla : List.of("reservations", "payments", "accounts", "invoices", "invoice_series", "hotel_photos")) {
            antes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
        }
        jdbc.update("DELETE FROM hotel_configuration");
        String cuerpo = peticionValida("4817205-7", "[]").replaceAll(",\\s*\"fotos\"\\s*:\\s*\\[\\]", "");
        mvc.perform(post(RUTA + "/inicializacion").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
        mvc.perform(get(RUTA).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("4817205-7"));
        for (var tabla : antes.entrySet()) {
            assertThat(jdbc.queryForList("SELECT * FROM " + tabla.getKey() + " ORDER BY id"))
                    .as("Historia conservada en %s", tabla.getKey()).isEqualTo(tabla.getValue());
        }
    }

    private static String peticionValida(String nit, String fotosJson) {
        return """
                {
                  "nombre": "Villa Serena",
                  "descripcion": "Descripción del hotel",
                  "direccion": "5a Avenida Norte 12, Antigua Guatemala",
                  "telefono": "+502 7832 0000",
                  "correo": "reservas@villaserena.test",
                  "nombreComercial": "Hotel Villa Serena",
                  "razonSocial": "Villa Serena Hotelera, S.A.",
                  "nit": "%s",
                  "direccionFiscal": "5a Avenida Norte 12, Antigua Guatemala",
                  "fotos": %s
                }
                """.formatted(nit, fotosJson);
    }

    private static String peticionConExtra(String campoExtra) {
        String base = peticionValida("4817205-7", "[]").trim();
        return base.substring(0, base.length() - 1) + ", " + campoExtra + "}";
    }

    private String enEstadia(String numero, String correo) {
        long huesped = jdbc.queryForObject("""
                INSERT INTO guests (full_name, email, phone, nationality, document_type, document_number)
                VALUES ('Huésped ' || ?, ?, '+502 5555 0000', 'Guatemalteca', 'DPI', '1234567890101')
                RETURNING id""", Long.class, numero, correo);
        long habitacion = jdbc.queryForObject("SELECT id FROM rooms WHERE number = ?", Long.class, numero);
        long tipo = jdbc.queryForObject("SELECT room_type_id FROM rooms WHERE id = ?", Long.class, habitacion);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huesped, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), 1, habitacion, recepcionistaId));
        checkinService.hacerCheckin(reserva.getCodigo(), recepcionistaId);
        return reserva.getCodigo();
    }

    private String tokenEmpleado(String correo) {
        Empleado empleado = empleados.findByCorreoIgnoreCase(correo).orElseThrow();
        return emitirToken(() -> tokens.emitirParaEmpleado(empleado));
    }

    private String emitirToken(java.util.function.Supplier<com.villaserena.api.auth.dto.Tokens> emisor) {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + emisor.get().accessToken();
        } finally {
            reloj.fijar(antes);
        }
    }
}
