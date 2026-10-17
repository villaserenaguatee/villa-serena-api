package com.villaserena.api.piso;
import org.springframework.context.annotation.*;
/** La lectura privada utiliza la misma cadena JWT que el resto del API. */
@Configuration @Profile("demo")
public class ArchivosLecturaSecurity {}
