package com.villaserena.api.facturacion;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.reservas.MetodoPago;

/**
 * Lectura de facturas ya emitidas (HU-REC-15, HU-REC-16 y HU-HUE-16): detalle,
 * acceso del huésped y descarga del PDF. Está separada de {@link FacturaService}
 * (la emisión) para que el check-out pueda reemplazar la emisión sin tocar esto.
 * <p>
 * El PDF no se genera al emitir: se arma al pedir la descarga (y al enviar el
 * correo), así el check-out no depende del almacenamiento remoto.
 */
@Service
public class FacturaConsultaService {

    /** Prefijo de los PDF en el bucket privado. */
    static final String PREFIJO_PDF = "facturas/presentacion-v2/";

    private final JdbcTemplate jdbc;
    private final AlmacenArchivos almacen;
    private final FacturaPdf pdf;
    private final Clock reloj;
    private final Duration vigenciaUrl;

    public FacturaConsultaService(JdbcTemplate jdbc, AlmacenArchivos almacen, FacturaPdf pdf, Clock reloj,
            PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.almacen = almacen;
        this.pdf = pdf;
        this.reloj = reloj;
        this.vigenciaUrl = Duration.ofMinutes(propiedades.archivos().minutosUrlFirmada());
    }

    /**
     * Detalle con el formato {@code FacturaDetalle}. Los datos fiscales salen de la
     * copia guardada al emitir; el nombre comercial, el teléfono y el correo, de la
     * configuración del hotel.
     */
    @Transactional(readOnly = true)
    public FacturaDetalle detalle(long id) {
        Cabecera c = jdbc.query("""
                SELECT f.id, s.series AS "serie", f.number AS "numero", f.issued_at AS "emitida_en", r.code AS "codigo", f.buyer_tax_id AS "nit_comprador", f.buyer_name AS "nombre_comprador", f.total,
                       f.trade_name AS "nombre_comercial", f.legal_name AS "razon_social", f.hotel_tax_id AS "nit_hotel", f.tax_address AS "direccion_fiscal", f.account_id AS "cuenta_id",
                       h.name AS "nombre", h.phone AS "telefono", h.email AS "correo" FROM invoices f
                JOIN invoice_series s ON s.id = f.series_id
                JOIN accounts cu ON cu.id = f.account_id
                JOIN reservations r ON r.id = cu.reservation_id
                LEFT JOIN hotel_configuration h ON h.id = 1
                WHERE f.id = ?""",
                (rs, i) -> new Cabecera(rs.getLong(1), rs.getString(2), rs.getInt(3), fecha(rs.getTimestamp(4)),
                        rs.getString(5), new FacturaDetalle.Comprador(rs.getString(6), rs.getString(7)),
                        rs.getBigDecimal(8),
                        new FacturaDetalle.Hotel(rs.getString(14), rs.getString(9), rs.getString(10),
                                rs.getString(11), rs.getString(12), rs.getString(15), rs.getString(16)),
                        rs.getLong(13)),
                id).stream().findFirst().orElseThrow(ApiException::noEncontrado);
        List<FacturaDetalle.Cargo> cargos = jdbc.query("""
                SELECT concept AS "concepto", quantity AS "cantidad", unit_price AS "precio_unitario", total  FROM charges
                WHERE account_id = ? AND status = 'VIGENTE' ORDER BY created_at, id""",
                (rs, i) -> new FacturaDetalle.Cargo(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4)),
                c.cuentaId());
        List<FacturaDetalle.Pago> pagos = jdbc.query("""
                SELECT method AS "metodo", amount AS "monto" FROM payments WHERE account_id = ? AND status = 'APROBADO'
                ORDER BY coalesce(approved_at, created_at), id""",
                (rs, i) -> new FacturaDetalle.Pago(MetodoPago.valueOf(rs.getString(1)), rs.getBigDecimal(2)),
                c.cuentaId());
        return new FacturaDetalle(c.id(), "EMITIDA", c.serie(), c.numero(), c.emitidaEn(), c.codigoReserva(),
                c.hotel(), c.comprador(), cargos, pagos, c.total(), FacturaDetalle.LEYENDA_IVA,
                FacturaDetalle.LEYENDA_LEGAL);
    }

    /** La factura de la reserva del huésped (GET /app/reservas/{codigo}/factura); ajena o sin factura: 404. */
    @Transactional(readOnly = true)
    public FacturaDetalle deReservaDelHuesped(String codigo, long huespedId) {
        return detalle(jdbc.queryForList("""
                SELECT f.id  FROM invoices f JOIN accounts cu ON cu.id = f.account_id
                JOIN reservations r ON r.id = cu.reservation_id
                WHERE r.code = ? AND r.guest_id = ?""", Long.class, codigo, huespedId)
                .stream().findFirst().orElseThrow(ApiException::noEncontrado));
    }

    /** El huésped solo accede a sus facturas; una ajena responde 404 (VIS-01). */
    @Transactional(readOnly = true)
    public void comprobarDuenoHuesped(long facturaId, long huespedId) {
        boolean propia = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM invoices f JOIN accounts cu ON cu.id = f.account_id
                               JOIN reservations r ON r.id = cu.reservation_id
                               WHERE f.id = ? AND r.guest_id = ?)""", Boolean.class, facturaId, huespedId);
        if (!propia) {
            throw ApiException.noEncontrado();
        }
    }

    /**
     * Enlace temporal al PDF del bucket privado. La primera vez genera el PDF y lo
     * guarda; después reutiliza el mismo archivo.
     */
    @Transactional
    public DescargaFactura descarga(long id) {
        String clave = asegurarPdf(id);
        return new DescargaFactura(almacen.urlFirmada(clave),
                OffsetDateTime.ofInstant(reloj.instant().plus(vigenciaUrl), reloj.getZone()));
    }

    /** El correo adjunta los mismos bytes persistidos que descargan Portal y Recepción. */
    @Transactional
    public byte[] contenidoPdf(long id) {
        return almacen.leerPrivado(asegurarPdf(id));
    }

    private String asegurarPdf(long id) {
        List<String> filas = jdbc.queryForList("SELECT pdf_key AS \"clave_pdf\" FROM invoices WHERE id = ? FOR UPDATE",
                String.class, id);
        if (filas.isEmpty()) {
            throw ApiException.noEncontrado();
        }
        String clave = filas.getFirst();
        // Revisión exclusivamente visual: conserva el archivo anterior bajo su clave original.
        if (clave == null || !clave.startsWith(PREFIJO_PDF)) {
            FacturaDetalle detalle = detalle(id);
            clave = PREFIJO_PDF + nombrePdf(detalle);
            try {
                almacen.guardarPrivado(clave, pdf.generar(detalle), "application/pdf");
            } catch (RuntimeException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ARCHIVOS_NO_DISPONIBLES",
                        "No se pudo preparar el PDF en este momento. Intenta de nuevo.");
            }
            jdbc.update("UPDATE invoices SET pdf_key = ? WHERE id = ?", clave, id);
        }
        return clave;
    }

    /** Nombre del archivo, por ejemplo {@code factura-VS-A-1.pdf}. */
    static String nombrePdf(FacturaDetalle f) {
        return "Factura-Villa-Serena-" + f.serie() + "-" + f.numero() + ".pdf";
    }

    private OffsetDateTime fecha(Timestamp t) {
        return OffsetDateTime.ofInstant(t.toInstant(), reloj.getZone());
    }

    private record Cabecera(long id, String serie, int numero, OffsetDateTime emitidaEn, String codigoReserva,
            FacturaDetalle.Comprador comprador, BigDecimal total, FacturaDetalle.Hotel hotel, long cuentaId) {
    }
}
