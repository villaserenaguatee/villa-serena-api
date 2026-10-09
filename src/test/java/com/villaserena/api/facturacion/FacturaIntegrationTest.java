package com.villaserena.api.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
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

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.estadia.CheckinService;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Pruebas de la factura (OBJ-4B) a través del check-out real de Recepción. "Hoy" es el
 * lunes 5 de octubre de 2026; la serie VS-A empieza en 1 (datos iniciales).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class FacturaIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    FacturaService facturas;

    @Autowired
    FacturaConsultaService consultas;

    @Autowired
    FacturaPdf pdf;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokens;

    @Autowired
    RelojFijoConfig.RelojPrueba reloj;

    @Autowired
    AlmacenEnMemoria.Almacen almacen;

    @MockitoBean
    PasarelaStripe stripe;

    long recepcionistaId;
    String recepcion;

    @BeforeEach
    void preparar() {
        almacen.objetos().clear();
        recepcionistaId = empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow().getId();
        recepcion = token(() -> tokens.emitirParaEmpleado(empleados.findById(recepcionistaId).orElseThrow()));
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    @Test
    void dosCheckoutsSeguidosTienenNumerosConsecutivos() throws Exception {
        String primera = enEstadia("101", "uno@correo.test");
        String segunda = enEstadia("102", "dos@correo.test");
        checkout(primera, "CF", "Cliente Uno")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factura.serie").value("VS-A"))
                .andExpect(jsonPath("$.factura.numero").value(1));
        checkout(segunda, "4817205-7", "Empresa Dos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factura.numero").value(2))
                .andExpect(jsonPath("$.factura.comprador.nit").value("4817205-7"));
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM series_factura WHERE serie = 'VS-A'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void unaSolaFacturaPorCuenta() throws Exception {
        String codigo = enEstadia("101", "uno@correo.test");
        checkout(codigo, "CF", "Cliente Uno").andExpect(status().isOk());
        long cuenta = cuentaDe(codigo);
        assertThatThrownBy(() -> facturas.emitir(cuenta, "CF", "Otra vez"))
                .isInstanceOf(ApiException.class)
                .extracting("codigo").isEqualTo("FACTURA_EXISTENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facturas", Integer.class)).isEqualTo(1);
    }

    @Test
    void laFacturaTraeTodoElContenidoYNoLosCargosAnulados() throws Exception {
        String codigo = enEstadia("101", "carla@correo.test");
        cargo(codigo, "Lavandería", 2, "100.00");
        long anulado = cargo(codigo, "Minibar por error", 1, "50.00");
        mvc.perform(post("/api/v1/cuentas/{c}/cargos/{id}/anular", codigo, anulado).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Registrado por error\"}"))
                .andExpect(status().isOk());

        long id = facturaId(checkout(codigo, "CF", "Carla Salida").andExpect(status().isOk()));
        mvc.perform(get("/api/v1/facturas/{id}", id).header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.codigoReserva").value(codigo))
                .andExpect(jsonPath("$.emitidaEn").value(startsWith("2026-10-05T12:00:00")))
                .andExpect(jsonPath("$.hotel.razonSocial").value("Villa Serena Hotelera, S.A."))
                .andExpect(jsonPath("$.hotel.nit").value("4817205-7"))
                .andExpect(jsonPath("$.comprador.nombreComprador").value("Carla Salida"))
                .andExpect(jsonPath("$.cargos[*].concepto",
                        contains("Alojamiento Estándar Jardín (2 noches)", "Lavandería")))
                .andExpect(jsonPath("$.total").value(1500.00))
                .andExpect(jsonPath("$.pagos[0].metodo").value("EFECTIVO"))
                .andExpect(jsonPath("$.pagos[0].monto").value(1500.00))
                .andExpect(jsonPath("$.leyendaIva").value("IVA incluido"))
                .andExpect(jsonPath("$.leyendaLegal").value("Factura de demostración — no válida ante la SAT"));

        String texto = textoDelPdf(pdf.generar(consultas.detalle(id)));
        assertThat(texto)
                .contains("Hotel Villa Serena", "Villa Serena Hotelera, S.A.", "NIT 4817205-7",
                        "FACTURA VS-A No. 1", "05/10/2026 12:00", "Reserva: " + codigo, "NIT: CF",
                        "Carla Salida", "Alojamiento Estándar Jardín (2 noches)", "Lavandería", "Q 200.00",
                        "IVA incluido", "Q 1500.00", "Efectivo", "no válida ante la SAT")
                .doesNotContain("Minibar");
    }

    @Test
    void elCorreoConElPdfQuedaEnElOutbox() throws Exception {
        String codigo = enEstadia("101", "carla@correo.test");
        long id = facturaId(checkout(codigo, "CF", "Carla Salida"));
        assertThat(jdbc.queryForObject("""
                SELECT destinatario || ' ' || (payload ->> 'facturaId') || ' ' || (payload ->> 'serieNumero')
                FROM outbox WHERE tipo = 'CORREO_FACTURA'""", String.class))
                .isEqualTo("carla@correo.test " + id + " VS-A 1");
    }

    @Test
    void elPdfSeDescargaConUrlFirmadaYSeGuardaUnaSolaVez() throws Exception {
        String codigo = enEstadia("101", "carla@correo.test");
        long id = facturaId(checkout(codigo, "CF", "Carla Salida"));
        mvc.perform(get("/api/v1/facturas/{id}/pdf", id).header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(AlmacenEnMemoria.URL_FIRMADA + "facturas/factura-VS-A-1.pdf"))
                .andExpect(jsonPath("$.expiraEn").value(startsWith("2026-10-05T12:10:00")));
        byte[] guardado = almacen.objetos().get("facturas/factura-VS-A-1.pdf");
        assertThat(new String(guardado, 0, 4)).isEqualTo("%PDF");
        mvc.perform(get("/api/v1/facturas/{id}/pdf", id).header("Authorization", recepcion))
                .andExpect(status().isOk());
        assertThat(almacen.objetos()).hasSize(1);
        mvc.perform(get("/api/v1/facturas/{id}/pdf", 999_999).header("Authorization", recepcion))
                .andExpect(status().isNotFound());
    }

    @Test
    void elHuespedSoloVeSuFactura() throws Exception {
        String codigo = enEstadia("101", "carla@correo.test");
        long huesped = jdbc.queryForObject("SELECT id FROM huespedes WHERE correo = 'carla@correo.test'", Long.class);
        String suyo = token(() -> tokens.emitirParaHuesped(huesped));
        mvc.perform(get("/api/v1/app/reservas/{c}/factura", codigo).header("Authorization", suyo))
                .andExpect(status().isNotFound());

        long id = facturaId(checkout(codigo, "CF", "Carla Salida"));
        mvc.perform(get("/api/v1/app/reservas/{c}/factura", codigo).header("Authorization", suyo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/facturas/{id}", id).header("Authorization", suyo)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/facturas/{id}/pdf", id).header("Authorization", suyo)).andExpect(status().isOk());

        long otro = jdbc.queryForObject("""
                INSERT INTO huespedes
                    (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Otro', 'otro@correo.test', '1', 'Guatemalteca', 'DPI', '1') RETURNING id""", Long.class);
        String ajeno = token(() -> tokens.emitirParaHuesped(otro));
        mvc.perform(get("/api/v1/facturas/{id}", id).header("Authorization", ajeno)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/facturas/{id}/pdf", id).header("Authorization", ajeno))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/app/reservas/{c}/factura", codigo).header("Authorization", ajeno))
                .andExpect(status().isNotFound());
        String roomService = token(() -> tokens.emitirParaEmpleado(
                empleados.findByCorreoIgnoreCase("rodrigo.aju@villaserena.test").orElseThrow()));
        mvc.perform(get("/api/v1/facturas/{id}", id).header("Authorization", roomService))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinSerieElCheckoutNoSeHace() throws Exception {
        String codigo = enEstadia("101", "carla@correo.test");
        jdbc.update("DELETE FROM series_factura");
        checkout(codigo, "CF", "Carla Salida")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SERIE_NO_CONFIGURADA"));
        assertThat(jdbc.queryForObject("SELECT estado FROM reservas WHERE codigo = ?", String.class, codigo))
                .isEqualTo("EN_ESTADIA");
    }

    // ------------------------------------------------------------ apoyo

    /** Reserva de Recepción del 5 al 7 de octubre con check-in hecho. */
    private String enEstadia(String numero, String correo) {
        long huesped = jdbc.queryForObject("""
                INSERT INTO huespedes
                    (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Huésped ' || ?, ?, '+502 5555 0000', 'Guatemalteca', 'DPI', '1234567890101')
                RETURNING id""", Long.class, numero, correo);
        long habitacion = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = ?", Long.class, numero);
        long tipo = jdbc.queryForObject("SELECT tipo_habitacion_id FROM habitaciones WHERE id = ?", Long.class,
                habitacion);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huesped, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), 1, habitacion, recepcionistaId));
        checkinService.hacerCheckin(reserva.getCodigo(), recepcionistaId);
        return reserva.getCodigo();
    }

    private ResultActions checkout(String codigo, String nit, String nombre) throws Exception {
        String json = """
                {"comprador": {"nit": "%s", "nombreComprador": "%s"}, "pago": {"metodo": "EFECTIVO"}}"""
                .formatted(nit, nombre);
        return mvc.perform(post("/api/v1/checkout/{c}", codigo).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long cargo(String codigo, String concepto, int cantidad, String precio) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/cuentas/{c}/cargos", codigo).header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concepto\": \"%s\", \"cantidad\": %d, \"precioUnitario\": %s}"
                                .formatted(concepto, cantidad, precio)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private long cuentaDe(String codigo) {
        return jdbc.queryForObject("SELECT cu.id FROM cuentas cu JOIN reservas r ON r.id = cu.reserva_id "
                + "WHERE r.codigo = ?", Long.class, codigo);
    }

    private static long facturaId(ResultActions checkout) throws Exception {
        return ((Number) JsonPath.read(checkout.andReturn().getResponse().getContentAsString(), "$.factura.id"))
                .longValue();
    }

    private static String textoDelPdf(byte[] contenido) throws Exception {
        PdfReader lector = new PdfReader(contenido);
        StringBuilder texto = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina)).append('\n');
        }
        lector.close();
        return texto.toString();
    }

    /** El validador de JWT usa la hora real: el token se emite con ella. */
    private String token(java.util.function.Supplier<com.villaserena.api.auth.dto.Tokens> emitir) {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + emitir.get().accessToken();
        } finally {
            reloj.fijar(antes);
        }
    }
}
