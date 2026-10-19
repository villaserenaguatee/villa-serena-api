package com.villaserena.api.huespedes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos del huésped principal; nacionalidad opcional (esquema {@code HuespedDatos}). */
public record HuespedDatos(
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 150) String nombreCompleto,
        @NotBlank(message = "El correo es obligatorio.") @Email(message = "El correo no tiene un formato válido.") @Size(max = 150) String correo,
        @NotBlank(message = "El teléfono es obligatorio.") @Size(max = 30) @jakarta.validation.constraints.Pattern(regexp="\\+?[0-9][0-9 ()-]*", message="El teléfono solo admite números, prefijo internacional y separadores.") String telefono,
        @Size(max = 60) String nacionalidad,
        @NotNull(message = "El tipo de documento es obligatorio.") TipoDocumento tipoDocumento,
        @NotBlank(message = "El número de documento es obligatorio.") @Size(max = 30) String numeroDocumento,
        @Size(max = 2) String paisEmisorPasaporte) {
    public HuespedDatos(String nombreCompleto,String correo,String telefono,String nacionalidad,TipoDocumento tipoDocumento,String numeroDocumento) {
        this(nombreCompleto,correo,telefono,nacionalidad,tipoDocumento,numeroDocumento,null);
    }
    public HuespedDatos {
        nacionalidad = nacionalidad == null || nacionalidad.isBlank() ? null : nacionalidad.trim();
        correo = correo == null ? null : correo.trim();
        telefono = telefono == null ? null : telefono.trim();
        numeroDocumento = numeroDocumento == null ? null : numeroDocumento.trim();
        paisEmisorPasaporte = tipoDocumento == TipoDocumento.PASAPORTE && paisEmisorPasaporte != null ? paisEmisorPasaporte.trim().toUpperCase(java.util.Locale.ROOT) : null;
    }
    @jakarta.validation.constraints.AssertTrue(message="El DPI debe contener exactamente 13 dígitos; el pasaporte debe contener letras y números, sin superar el almacenamiento de 30 caracteres.")
    public boolean isDocumentoValido() {
        if (tipoDocumento == null || numeroDocumento == null) return true;
        return numeroDocumento.matches(tipoDocumento == TipoDocumento.DPI ? "[0-9]{13}" : "[A-Za-z0-9]{1,30}");
    }
    @jakarta.validation.constraints.AssertTrue(message="Guatemala requiere +502 y exactamente 8 dígitos nacionales. Para otros países introduce un teléfono internacional válido, sin letras.")
    public boolean isTelefonoValido() {
        if (telefono == null) return true;
        if(telefono.length()>30 || !telefono.matches("\\+[1-9][0-9 ()-]*")) return false;
        String digits=telefono.replaceAll("[^0-9]", "");
        if(digits.startsWith("502")) return digits.length()==11;
        if(digits.length()>15) return false;
        try { var util=com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();return util.isValidNumber(util.parse(telefono,"ZZ")); }
        catch(com.google.i18n.phonenumbers.NumberParseException e) { return false; }
    }
    @jakarta.validation.constraints.AssertTrue(message="Selecciona el país emisor del pasaporte; es independiente de la nacionalidad.")
    public boolean isPaisEmisorValido() {
        return tipoDocumento != TipoDocumento.PASAPORTE || paisEmisorPasaporte != null && java.util.Set.of(java.util.Locale.getISOCountries()).contains(paisEmisorPasaporte);
    }
}
