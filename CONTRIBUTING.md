# Contribuir a Villa Serena API

Esta guía describe cómo trabajar una issue de la API, desde su preparación hasta el cierre. Consulta también [README.md](README.md), el contrato `openapi.yaml` cuando esté integrado en la rama de trabajo y las historias y reglas en `villa-serena-docs`.

Al participar en el proyecto, sigue el [Código de conducta](CODE_OF_CONDUCT.md).

## 1. Preparar la issue

Antes de programar o editar documentos:

1. Lee la descripción, los criterios de aceptación y las conversaciones de la issue.
2. Revisa el código o documento actual para confirmar qué falta y qué ya existe.
3. Identifica el objetivo (`OBJ-`), las historias de usuario (`HU-`) y las reglas relacionadas cuando apliquen.
4. Comprueba dependencias: contrato, migraciones, endpoints o PR de otro repositorio. Enlaza esas dependencias en la issue.
5. Asígnate la issue o comenta que la tomarás para evitar trabajo duplicado. Si el resultado esperado es ambiguo, acláralo antes de implementar esa parte.

Una issue lista para trabajar debe indicar el problema, el resultado esperado, el alcance y criterios verificables. Para errores, agrega pasos de reproducción, resultado actual y esperado. Si falta información, completa la issue antes de darla por resuelta.

Si el cambio afecta varios repositorios, utiliza una issue y un PR por repositorio y enlázalos entre sí. Los números de issue son propios de cada repositorio.

## 2. Crear una rama

El flujo obligatorio para todos los cambios es **rama de trabajo → PR a `develop` → validación de integración → PR de `develop` a `main`**. Esto incluye funcionalidades, correcciones, documentación y mantenimiento.

`develop` reúne el trabajo del equipo; `main` recibe únicamente entregas integradas y verificadas. Crea una rama por issue desde `develop`. No trabajes directamente en `develop` o `main` ni abras PR desde una rama de issue hacia `main`. Si `develop` todavía no existe en el repositorio, coordina su creación con el responsable antes de comenzar.

Con el árbol de trabajo limpio, este ejemplo parte de `develop` y atiende la issue #12:

```bash
git status
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feat/12-descripcion-corta
```

Conserva cualquier trabajo pendiente antes de cambiar de rama; no lo descartes ni lo mezcles con otra issue.

Usa `feat/<numero>-<descripcion>` para funcionalidades, `fix/...` para errores, `docs/...` para documentación o `chore/...` para mantenimiento. Las ramas existentes con nombres `obj...` pueden conservarse; vincula su PR con la issue.

## 3. Implementar y verificar

- Limita el cambio a los criterios de aceptación. Registra otros hallazgos en una issue aparte.
- Sigue las convenciones y versiones del repositorio. Evita refactors o actualizaciones de dependencias ajenos a la tarea.
- Coordina cambios a piezas compartidas, especialmente migraciones y contrato OpenAPI, con sus responsables.
- Agrega o ajusta pruebas cuando el comportamiento lo requiera. Recorre los criterios de aceptación y registra evidencia del resultado.
- Actualiza la documentación que cambie con el comportamiento.
- Mantén fuera de Git las claves, contraseñas, archivos `.env`, datos personales reales y archivos generados locales.

Si utilizas IA, dale la issue y las referencias necesarias, revisa su propuesta y verifica lo que produce. El responsable de la entrega comprueba el alcance, el diff y los resultados; una respuesta de la IA no sustituye las pruebas.

## Comprobaciones de la API

- Usa Java 21 y el Maven Wrapper del repositorio. Consulta el [README](README.md) y la guía de arranque en `villa-serena-docs` para preparar el entorno.
- Ejecuta `./mvnw test` (Windows: `mvnw.cmd test`). Las pruebas con Testcontainers necesitan Docker disponible. Indica si una comprobación no pudo ejecutarse y por qué.
- Para cambios de base de datos, valida las migraciones y ejecuta [scripts/probar-esquema.sql](scripts/probar-esquema.sql) según el README, en una base de desarrollo. Coordina la numeración de Flyway y no modifiques migraciones que ya se hayan aplicado en entornos compartidos; agrega una nueva.
- Si cambia un endpoint, coordina la actualización de `openapi.yaml` y de la documentación de endpoints y cobertura que exista en la rama de trabajo. Si el contrato todavía no está integrado, enlaza su PR como dependencia. Describe el impacto en web y móvil y enlaza sus tareas.
- Comprueba casos válidos, errores y permisos relevantes para la issue. Adjunta la petición y respuesta de ejemplo sin secretos.

## 4. Guardar y abrir el pull request

Revisa los archivos y agrega únicamente los correspondientes a la issue:

```bash
git diff
git diff --check
git status --short
git add CONTRIBUTING.md
git diff --cached
git commit -m "docs: agregar guia de contribucion (#12)"
git push -u origin HEAD
```

El archivo y mensaje son ejemplos: sustitúyelos por los de tu tarea. Usa mensajes que expliquen el cambio, como `feat: ... (#12)` o `fix: ... (#12)`.

Abre el PR de la issue con **base: `develop`** y **compare: tu rama de trabajo**. Incluye:

- Problema y comportamiento resultante.
- Issue relacionada y dependencias en otros repositorios.
- Criterios de aceptación cubiertos.
- Cómo lo probaste: comandos, resultados y capturas si hay cambios visuales.
- Limitaciones o criterios pendientes, si existen.

Puedes usar esta descripción:

```markdown
## Cambio
Qué problema resuelve y qué comportamiento queda.

## Issue
Relacionado con #12

## Validación
- [ ] Criterios de aceptación verificados.
- [ ] Comprobaciones aplicables ejecutadas; comandos y resultados adjuntos.
- [ ] Documentación actualizada cuando corresponde.

## Dependencias y pendientes
Enlaces a PR/issues relacionados o "Ninguno".
```

En el PR hacia `develop`, usa `Relacionado con #12` y detalla qué queda pendiente si la entrega es parcial. La issue permanece abierta hasta que el cambio validado llegue a `main`. En el PR de promoción hacia `main`, usa `Closes #12` por cada issue completamente resuelta; para otro repositorio, usa `Closes organizacion/repositorio#12`. Comprueba el cierre después del merge, sin asumir que GitHub lo hizo automáticamente.

## 5. Revisar, integrar y cerrar

### Integrar la issue en `develop`

Solicita revisión a otro integrante y atiende sus observaciones. Antes de fusionar, confirma que la base sea `develop`, las dependencias estén disponibles y los criterios de aceptación estén verificados. Integra el PR de la issue mediante **Squash and merge**.

Registra en la issue el PR fusionado y que está integrado en `develop`, pendiente de validación y promoción a `main`. El merge en `develop` no significa que la entrega esté terminada.

### Validar la integración

Prueba el estado actualizado de `develop`, con los cambios de las otras issues y repositorios necesarios para la entrega. Ejecuta las comprobaciones aplicables de esta guía y recorre el flujo afectado de principio a fin. Registra comandos, resultados y dependencias verificadas.

Si aparece una regresión, corrígela mediante otra rama y PR hacia `develop`, y repite las comprobaciones afectadas antes de promover. No promociones una entrega con criterios pendientes o dependencias sin integrar.

### Promover de `develop` a `main`

El responsable de la entrega abre un PR con **base: `main`** y **compare: `develop`**. Incluye las issues y PR que se entregan, evidencia de la validación de integración y cambios necesarios en otros repositorios. Revisa el diff completo: el PR promueve todos los cambios que haya en `develop` respecto de `main`.

Otro integrante revisa la entrega antes de fusionarla usando el método permitido por las reglas del repositorio. Conserva `develop` como rama permanente. Si la promoción usa squash, coordina la sincronización posterior de `main` hacia `develop` mediante PR para que las siguientes entregas partan de un historial coherente.

Después del merge en `main`, verifica el cierre de las issues completamente resueltas. Actualiza el seguimiento en `villa-serena-docs/17 - Avance del Proyecto.md` cuando corresponda, con referencias a los PR de implementación y promoción; no marques trabajo pendiente como terminado.

Si aparece un bloqueo, registra qué falla, evidencia, dependencia y trabajo pendiente en la issue. Conserva los avances y comunica el bloqueo al equipo.
