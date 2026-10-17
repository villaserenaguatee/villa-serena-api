package com.villaserena.api.piso;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import com.villaserena.api.comun.ApiException;
class DocumentosSegurosTest {
 byte[] pdf(boolean active)throws Exception{var out=new ByteArrayOutputStream();var document=new Document();var writer=PdfWriter.getInstance(document,out);document.open();document.add(new Paragraph("Documento local de verificacion"));if(active)writer.addJavaScript("app.alert('not executed');");document.close();return out.toByteArray();}
 @Test void aceptaDocumentoNormalSinModificarBytes()throws Exception{byte[] bytes=pdf(false),before=bytes.clone();assertDoesNotThrow(()->DocumentosSeguros.validarPdf(bytes));assertArrayEquals(before,bytes);}
 @Test void rechazaJavascriptSinEjecutarlo()throws Exception{assertThrows(ApiException.class,()->DocumentosSeguros.validarPdf(pdf(true)));}
 @Test void noAceptaSoloCabeceraYFinal(){assertThrows(ApiException.class,()->DocumentosSeguros.validarPdf("%PDF-1.7 fake %%EOF".getBytes()));}
}
