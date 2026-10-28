package com.villaserena.api.hotel.editorial;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditorialSitio(String idioma, long revision, long revisionPublicada,
        Instant actualizadoEn, Instant publicadoEn, List<ElementoEditorial> elementos, Map<String, String> fotografias) {}
