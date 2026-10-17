package com.villaserena.api.piso;
import java.util.*;
import org.openpdf.text.pdf.*;
import com.villaserena.api.comun.ApiException;
/** Validation for new check-in uploads; existing documents are never rewritten. */
public final class DocumentosSeguros {
 private DocumentosSeguros(){}
 private static ApiException invalid(){return ApiException.datosInvalidos("Envía un PDF válido, sin contraseña, acciones automáticas ni archivos adjuntos, de hasta 20 páginas.",List.of());}
 public static void validarPdf(byte[] bytes){
  try {
   PdfReader reader=new PdfReader(bytes);
   try {
    if(reader.isEncrypted()||reader.getNumberOfPages()<1||reader.getNumberOfPages()>20||reader.getXrefSize()>20000)throw invalid();
    var forbidden=Set.of("JS","JavaScript","AA","OpenAction","EmbeddedFiles","RichMedia","XFA");
    var actions=Set.of("/JavaScript","/Launch","/SubmitForm","/ImportData","/GoToR","/Rendition","/RichMediaExecute");
    var pending=new ArrayDeque<PdfObject>();
    for(int i=0;i<reader.getXrefSize();i++){var object=reader.getPdfObject(i);if(object!=null)pending.add(object);}
    Set<PdfObject> seen=Collections.newSetFromMap(new IdentityHashMap<>());
    while(!pending.isEmpty()){
     var object=pending.removeLast();if(!seen.add(object))continue;if(seen.size()>100000)throw invalid();
     if(object instanceof PdfDictionary d){
      for(String key:forbidden)if(d.contains(new PdfName(key)))throw invalid();
      var action=d.getAsName(PdfName.S);if(action!=null&&actions.contains(action.toString()))throw invalid();
      if(d.contains(new PdfName("EF")))throw invalid();
      for(var key:d.getKeys()){var child=d.get(key);if(child!=null&&!child.isIndirect())pending.add(child);}
     }
     else if(object instanceof PdfArray a)for(var child:a.getElements())if(child!=null&&!child.isIndirect())pending.add(child);
    }
   } finally {reader.close();}
  }catch(ApiException e){throw e;}catch(Exception e){throw invalid();}
 }
}
