package com.villaserena.api.facturacion;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * PDF de la factura con OpenPDF (HU-REC-15, criterio 4): datos del hotel y fiscales,
 * serie y número, fecha y hora, comprador, reserva, cargos vigentes, total con "IVA
 * incluido" sin desglose, pagos y la leyenda de demostración.
 */
@Component
public class FacturaPdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font AVISO = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.DARK_GRAY);

    public byte[] generar(FacturaDetalle f) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.LETTER, 48, 48, 48, 48);
        PdfWriter.getInstance(documento, salida);
        documento.addTitle("Factura " + f.serie() + " " + f.numero());
        documento.open();

        FacturaDetalle.Hotel h = f.hotel();
        documento.add(new Paragraph(h.nombreComercial(), TITULO));
        documento.add(new Paragraph(h.razonSocial() + " · NIT " + h.nit(), NORMAL));
        documento.add(new Paragraph(h.direccionFiscal(), NORMAL));
        if (h.telefono() != null) {
            documento.add(new Paragraph("Tel. " + h.telefono() + " · " + h.correo(), NORMAL));
        }

        Paragraph encabezado = new Paragraph("FACTURA " + f.serie() + " No. " + f.numero(), TITULO);
        encabezado.setSpacingBefore(12);
        documento.add(encabezado);
        documento.add(new Paragraph("Fecha: " + FECHA.format(f.emitidaEn()), NORMAL));
        documento.add(new Paragraph("Reserva: " + f.codigoReserva(), NORMAL));
        documento.add(new Paragraph("NIT: " + f.comprador().nit() + " · Nombre: " + f.comprador().nombreComprador(),
                NORMAL));

        PdfPTable cargos = new PdfPTable(new float[] {6, 1.2f, 2, 2});
        cargos.setWidthPercentage(100);
        cargos.setSpacingBefore(12);
        encabezados(cargos, "Concepto", "Cant.", "Precio", "Monto");
        for (FacturaDetalle.Cargo c : f.cargos()) {
            celda(cargos, c.concepto(), NORMAL, Element.ALIGN_LEFT);
            celda(cargos, String.valueOf(c.cantidad()), NORMAL, Element.ALIGN_RIGHT);
            celda(cargos, quetzales(c.precioUnitario()), NORMAL, Element.ALIGN_RIGHT);
            celda(cargos, quetzales(c.monto()), NORMAL, Element.ALIGN_RIGHT);
        }
        PdfPCell etiquetaTotal = new PdfPCell(new Phrase("Total (" + FacturaDetalle.LEYENDA_IVA + ")", NEGRITA));
        etiquetaTotal.setColspan(3);
        etiquetaTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cargos.addCell(etiquetaTotal);
        celda(cargos, quetzales(f.total()), NEGRITA, Element.ALIGN_RIGHT);
        documento.add(cargos);

        PdfPTable pagos = new PdfPTable(new float[] {6, 2});
        pagos.setWidthPercentage(60);
        pagos.setHorizontalAlignment(Element.ALIGN_LEFT);
        pagos.setSpacingBefore(12);
        encabezados(pagos, "Pago", "Monto");
        for (FacturaDetalle.Pago p : f.pagos()) {
            celda(pagos, metodo(p), NORMAL, Element.ALIGN_LEFT);
            celda(pagos, quetzales(p.monto()), NORMAL, Element.ALIGN_RIGHT);
        }
        documento.add(pagos);

        Paragraph leyenda = new Paragraph(FacturaDetalle.LEYENDA_LEGAL, AVISO);
        leyenda.setSpacingBefore(18);
        documento.add(leyenda);
        documento.close();
        return salida.toByteArray();
    }

    private static void encabezados(PdfPTable tabla, String... titulos) {
        for (String titulo : titulos) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, NEGRITA));
            celda.setBackgroundColor(new Color(0xEE, 0xEE, 0xEE));
            tabla.addCell(celda);
        }
    }

    private static void celda(PdfPTable tabla, String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        tabla.addCell(celda);
    }

    private static String quetzales(BigDecimal monto) {
        return "Q " + monto.setScale(2).toPlainString();
    }

    private static String metodo(FacturaDetalle.Pago p) {
        return switch (p.metodo()) {
            case STRIPE -> "Tarjeta en línea (Stripe)";
            case CANAL -> "Pagado al canal";
            case EFECTIVO -> "Efectivo";
            case TARJETA -> "Tarjeta";
            case OTRO -> "Otro";
        };
    }
}
