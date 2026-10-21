package com.villaserena.api.huespedes;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
class HuespedDatosTest {
 @Test void formatosYDatosLegitimos(){try(var f=Validation.buildDefaultValidatorFactory()){var v=f.getValidator();
  assertTrue(v.validate(new HuespedDatos("María O'Neill"," kim@example.test ","+502 5555-1234","Guatemalteca",TipoDocumento.DPI,"0012345678901")).isEmpty());
  assertTrue(v.validate(new HuespedDatos("José Pérez","kim@example.test","+1 (202) 555-0123","Otra",TipoDocumento.PASAPORTE,"AB0123456","US")).isEmpty());
  for(String dpi:new String[]{"123","12345678901234","123456789012A"})assertFalse(v.validate(new HuespedDatos("Kim","kim@example.test","+50255551234","GT",TipoDocumento.DPI,dpi)).isEmpty());
  for(String tel:new String[]{"123ABC789","123","+1234567890123456","++50255551234"})assertFalse(v.validate(new HuespedDatos("Kim","kim@example.test",tel,"GT",TipoDocumento.DPI,"0012345678901")).isEmpty());
  assertFalse(v.validate(new HuespedDatos("Kim","ki m@example.test","+50255551234","GT",TipoDocumento.DPI,"0012345678901")).isEmpty());
 }}

 @Test
 void nacionalidadOpcionalConLongitudLimitada() {
  try (var factory = Validation.buildDefaultValidatorFactory()) {
   var validator = factory.getValidator();
   for (String nacionalidad : new String[] {null, "", "   ", " Guatemalteca "}) {
    var principal = new HuespedDatos("Kim", "kim@example.test", "+50255551234", nacionalidad,
      TipoDocumento.DPI, "0012345678901");
    var adicional = new com.villaserena.api.reservas.dto.HuespedAdicionalPeticion("Kim",
      TipoDocumento.DPI, "0012345678901", nacionalidad);
    assertTrue(validator.validate(principal).isEmpty());
    assertTrue(validator.validate(adicional).isEmpty());
    String esperado = nacionalidad == null || nacionalidad.isBlank() ? null : "Guatemalteca";
    assertEquals(esperado, principal.nacionalidad());
    assertEquals(esperado, adicional.nacionalidad());
   }
   assertFalse(validator.validate(new HuespedDatos("Kim", "kim@example.test", "+50255551234",
     "x".repeat(61), TipoDocumento.DPI, "0012345678901")).isEmpty());
   assertFalse(validator.validate(new com.villaserena.api.reservas.dto.HuespedAdicionalPeticion("Kim",
     TipoDocumento.DPI, "0012345678901", "x".repeat(61))).isEmpty());
  }
 }
}
