# Verificación del arranque y la base operativa (OBJ-0B, issue #10)

Fecha: 9 de octubre de 2026. Ejecutado en local (macOS, JDK 21, Docker Desktop)
con los servicios de `villa-serena-infra` arriba y el perfil `demo` activo.

## Cómo repetirla

```bash
# 1. Servicios de apoyo
cd villa-serena-infra && docker compose -f docker-compose.dev.yml up -d

# 2. API (el .env necesita SPRING_PROFILES_ACTIVE=demo y los tres hashes;
#    ver .env.example)
cd ../villa-serena-api && ./mvnw spring-boot:run
```

## Resultados

| Criterio | Comprobación | Resultado |
|---|---|---|
| 1 | Arranque y Flyway | `Successfully validated 6 migrations` y `Started ApiApplication`. Sobre una base nueva el mensaje es `Successfully applied 6 migrations`; en una ya migrada, Flyway valida las mismas 6 sin reparar nada. |
| 1b | `select count(*) from habitaciones;` | `12` |
| 2 | `GET /actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` |
| 3 | `GET /actuator/prometheus` | 425 líneas de métricas |
| 3 | Prometheus (http://localhost:9090/targets) | `villa-serena-api -> up`, sobre `http://host.docker.internal:8080/actuator/prometheus` |
| 4 | `GET /swagger-ui.html` | 302 a `/swagger-ui/index.html`, que abre |
| 5 | Ruta inexistente protegida | `{"codigo":"NO_AUTENTICADO","mensaje":"Debes iniciar sesión para continuar.","detalles":[]}` |
| 5 | Ruta inexistente pública | `{"codigo":"NO_ENCONTRADO","mensaje":"No se encontró el recurso solicitado.","detalles":[]}` |

El criterio 5 da dos respuestas distintas a propósito: en una ruta protegida,
Spring Security responde antes de saber si existe, y eso es lo correcto, porque
una ruta inexistente no debe revelar nada a quien no tiene sesión. Ambas usan el
formato único `{codigo, mensaje, detalles}` en español.

## Cambios que hizo falta completar

- `spring.jpa.hibernate.ddl-auto` pasó de `none` a `validate`, como pide el
  prompt: Hibernate sigue sin tocar las tablas, pero ahora comprueba al arrancar
  que las entidades correspondan al esquema de Flyway y falla si algo no cuadra.
  El arranque de esta verificación pasó esa comprobación con todas las entidades
  del proyecto.
- `server.port` quedó explícito (8080), configurable con `SERVER_PORT`.

## Una diferencia con el prompt, a propósito

El prompt de OBJ-0B pide la zona horaria de Jackson en UTC. No se aplicó: el
contrato (`openapi.yaml`, verificado en #12) muestra las fechas con el
desplazamiento local, por ejemplo `2026-10-02T14:30:00-06:00` en
`HistorialOperacion`. Poner Jackson en UTC reescribiría esas respuestas a `Z` y
dejaría de cumplirse el contrato, que es la superficie que consumen la web y la
app. La hora de negocio sigue centralizada en el bean `Clock` de
`RelojConfig` (America/Guatemala, AD-19) y la base guarda todo en UTC
(`hibernate.jdbc.time_zone`). Si el equipo prefiere UTC en las respuestas, hay
que cambiarlo primero en el contrato.
