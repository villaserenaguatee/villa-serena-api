package com.villaserena.api.hotel;

import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Un campo omitido conserva fotos; un null explícito sigue siendo inválido. */
public class FotosHotelDeserializer extends ValueDeserializer<List<FotoHotelPeticion>> {
    @Override
    public List<FotoHotelPeticion> deserialize(JsonParser parser, DeserializationContext context) {
        return context.readValue(parser, new TypeReference<List<FotoHotelPeticion>>() {});
    }

    @Override
    public List<FotoHotelPeticion> getAbsentValue(DeserializationContext context) {
        return null;
    }

    @Override
    public List<FotoHotelPeticion> getNullValue(DeserializationContext context) {
        return context.reportInputMismatch(List.class, "Fotos no admite null; omite el campo para conservar la galería.");
    }
}
