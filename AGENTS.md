# AGENTS.md — Acuerdos de trabajo con IA en Villa Serena

> Esta es la plantilla compartida de instrucciones para los cinco repositorios. Su ubicación de uso es la raíz de cada repositorio; `CLAUDE.md` remite a ella mediante `@AGENTS.md`.

## 1. Proyecto y resultado esperado

Villa Serena es un PMS para un hotel boutique ficticio, desarrollado como proyecto final del curso de Desarrollo Web.

- **Hito:** sábado 10 de octubre de 2026. El flujo demostrable es reservar → check-in → estadía desde la app → operación → check-out con factura.
- **Entorno del hito:** PostgreSQL, Mailpit, MinIO, Prometheus y Grafana corren en Docker; el API, la web y la app corren en las computadoras del equipo.
- **Demostración:** huéspedes ficticios, Stripe en modo prueba y facturas sin validez ante la SAT.
- **Después del hito:** despliegue en VPS, Cloudflare para producción, CI/CD de despliegue y backups. Los workflows de colaboración en GitHub y un Cloudflare Tunnel temporal para pruebas entre integrantes pueden usarse durante el desarrollo.

## 2. Equipo, usuario y agente

El equipo dirige el proyecto y la persona que trabaja con el agente dirige la sesión. El agente aporta análisis, implementación y verificación dentro del trabajo solicitado.

| Rol | Responsabilidad |
|---|---|
| **Alex — Scrum Master** | Facilita el proceso, recibe inquietudes, preguntas y bloqueos, y ayuda al equipo a resolverlos. No reparte unilateralmente las tareas ni prescribe recetas de implementación. |
| **Kimberly — Product Owner** | Define las prioridades y tiene la decisión final sobre el alcance, el comportamiento y la aceptación del producto: cómo quedará el sistema. Las decisiones técnicas se acuerdan con quienes desarrollan para alcanzar esos objetivos. |
| **Equipo de desarrollo** | Toma las tareas disponibles, coordina cambios compartidos y acuerda las decisiones técnicas. Las asignaciones de los documentos son una referencia inicial y pueden cambiar. |

La comunicación es en español, clara y por bloques breves. Cada actualización aporta lo necesario para entender el avance, la decisión actual o un bloqueo. Los nombres técnicos siguen las convenciones del repositorio y del documento 14.

Los entornos del equipo son Windows (Kimberly y Josué), macOS (Pablo, Carlos y Hugo) y Linux (Alex). El agente adapta los comandos a la terminal y al sistema de la persona; las guías no imponen rutas locales ni instrucciones para crear carpetas. La guía 16, sección 1.1, contiene las variantes necesarias.

Las instrucciones y los prompts describen el **estado deseado**, sus restricciones y los criterios de aceptación. Los pasos detallados se reservan para procedimientos que los necesitan o cuando la persona los solicita.

## 3. Inicio de sesión y selección del trabajo

Al iniciar cada sesión, el agente revisa el repositorio, la rama actual, los cambios locales y las instrucciones aplicables. Actualiza las referencias con `git fetch` y determina cómo sincronizar sin perder trabajo:

- Una rama sin divergencias se actualiza con `git pull --ff-only` cuando corresponda.
- Una rama personal puede ponerse al día mediante rebase sobre su base de integración cuando su estado lo permita. Una rama compartida se sincroniza según el acuerdo del equipo, sin reescribir su historial unilateralmente.
- Los cambios locales se conservan y los conflictos se resuelven comprendiendo ambas partes. No se descarta trabajo ni se fuerza un push para lograr la sincronización.

Las issues se consultan mediante GitHub CLI (`gh`) o el complemento/plugin disponible. La revisión identifica la tarea solicitada o asignada, su estado, los criterios de aceptación, las dependencias y las prioridades, especialmente las marcadas como urgentes. La prioridad orienta la selección del trabajo; las dependencias y las indicaciones de la persona también cuentan.

El alcance de la issue se contrasta con la documentación vigente y el código existente. Si GitHub no está accesible, el agente informa esa limitación y avanza con la información verificable, sin asumir que conoce el estado remoto de las tareas.

La búsqueda y lectura de documentación se limita a lo necesario para la tarea: archivos, secciones y referencias pertinentes. El agente amplía esa lectura cuando una dependencia, discrepancia o falta de información lo requiere, sin examinar todo el repositorio de documentación por defecto. Así se conserva el contexto de la sesión y se evita consumir tokens con contenido ajeno al trabajo.

## 4. Autonomía y decisiones

El agente presenta un plan breve de los archivos o componentes que espera cambiar y continúa con la implementación autorizada. Presentar el plan no implica esperar una aprobación adicional.

Las decisiones técnicas rutinarias pueden resolverse con criterio propio, siguiendo las convenciones existentes. Esto incluye los ajustes de integración, las validaciones técnicas y las dependencias necesarias para completar la tarea, sin ampliar sus funcionalidades. Los supuestos relevantes quedan explicados en el resultado.

La consulta a la persona corresponde a cambios de alcance o criterios de aceptación, alternativas con consecuencias importantes, contradicciones que no se pueden resolver con evidencia y acciones destructivas o externas aún no autorizadas. Una autorización ya dada no se vuelve a solicitar para el mismo trabajo.

Los requisitos acordados por el equipo y las indicaciones explícitas de la sesión guían el trabajo. Los documentos y prompts aportan contexto; una receta antigua o una asignación personal no bloquea por sí sola una tarea autorizada. Las discrepancias que requieren una decisión se registran como issues, con las fuentes, el efecto y una propuesta, y se consultan con Alex para facilitar su resolución. Los cambios de producto corresponden a Kimberly.

## 5. Issues, ramas y pull requests

El trabajo sigue el ciclo **issue → rama de trabajo → implementación y verificación → PR → integración → cierre de la issue**.

- **`develop` es la rama de integración.** Las ramas de trabajo parten de ella y las PR se dirigen a ella. Si no existe, el agente propone crearla desde `main` antes de iniciar una tarea nueva; el trabajo ya iniciado se conserva y se acuerda cómo incorporarlo.
- **`main` se reserva para la entrega del sistema terminado.** No recibe cambios de desarrollo ni PR de tareas individuales. La entrega se integra mediante una PR desde `develop`.
- Los nombres de rama empiezan con el tipo de cambio: `feat/`, `fix/`, `chore/`, `docs/` o `refactor/`. Pueden incluir después la issue y el objetivo, por ejemplo `feat/42-obj3a-room-service`.
- Los commits describen el cambio en español con un tipo reconocible, por ejemplo `feat: agrega cola de pedidos de Room Service`.
- Las PR son acotadas y explican el problema resuelto, el resultado y cómo se comprobó. Referencian la issue correspondiente y se revisan antes de integrarse.
- Si `develop` es la rama predeterminada, puede usarse `Closes #42` para el cierre automático al integrar la PR. Si no lo es, la PR enlaza la issue y esta se cierra explícitamente después de verificar su integración en `develop`.

Las discrepancias, propuestas de cambio, sugerencias y bloqueos que necesitan seguimiento se publican como issues para mantener un registro común. Los detalles menores de implementación se explican en la PR o en la sesión, sin generar una issue por cada decisión rutinaria.

Para tareas independientes, el agente puede proponer **Git worktrees**, cada uno con su rama e issue. Esto permite mantener varias tareas disponibles sin mezclar cambios. Las tareas que comparten archivos o dependen unas de otras requieren coordinación antes de avanzar a la vez.

## 6. Acuerdos técnicos

| Pieza | Versión o convención |
|---|---|
| Backend | Spring Boot **4.1**, Java **21**, Maven |
| Base de datos | PostgreSQL **17**, Flyway |
| Web | Next.js **15** (App Router), React 19, TypeScript, Tailwind 4, shadcn/ui |
| App | React Native con Expo **SDK 54**, Expo Router |
| Gestor de paquetes JS | pnpm |

Estas versiones son la base acordada. Un cambio de versión se plantea como issue con su motivo e impacto; no se introduce como parte incidental de otra tarea. Next.js 15 es la versión principal de la web.

- **Fechas:** los instantes con hora se guardan en UTC; las fechas de estadía se conservan como fechas sin hora. La presentación y los cálculos del negocio usan `America/Guatemala` (AD-19).
- **Configuración:** los perfiles `local`, `dev` y `prod` separan los entornos. `local` corresponde a la computadora de cada integrante; `dev`, a pruebas compartidas; `prod`, al despliegue posterior al hito. La configuración aprovecha la estructura existente, sin imponer un nombre o extensión de archivo ni duplicar fuentes de configuración.
- **Arranque del API:** `OBJ-0B` prepara el proyecto mínimo y su configuración; `OBJ-0C` incorpora el esquema y los datos iniciales mediante Flyway. La verificación conjunta comprueba el arranque con las migraciones aplicadas. El proyecto base no espera al esquema para empezar.
- **Verificación:** cada tarea cumple los criterios de aceptación de su issue y los pasos de comprobación aplicables de su prompt. El informe final distingue lo verificado, lo pendiente y cualquier limitación del entorno.

## 7. Seguridad y cambios compartidos

Los secretos permanecen fuera del código versionado, de las conversaciones y de los logs. La configuración local utiliza archivos de entorno ignorados por Git; GitHub Actions utiliza secretos configurados en GitHub. Los archivos `.env.example` contienen nombres de variables y valores ficticios. El agente usa marcadores para valores sensibles y la persona configura las credenciales en su entorno.

Las piezas compartidas se mantienen como acuerdos del equipo, sin exclusividad por integrante:

| Pieza | Resultado esperado y coordinación |
|---|---|
| Migraciones de Flyway | Los cambios de esquema se coordinan mediante issues y PR para evitar colisiones de versiones. Las migraciones ya aplicadas se conservan; la evolución utiliza migraciones nuevas. |
| `openapi.yaml` | El contrato refleja los endpoints y DTO acordados. Los cambios consideran sus consumidores y se coordinan con API, web y app; cada frontend actualiza su copia y sus tipos. |
| Spring Security | La configuración existente se reutiliza y los cambios de rutas o permisos incluyen verificación de acceso. |
| Cliente de tiempo real de la web | Las pantallas reutilizan el cliente compartido, con una conexión por pestaña y suscripciones autorizadas. |

Las protecciones de seguridad siguen siendo obligatorias: nunca se publican secretos, se exponen tokens de sesión al código del navegador ni se omiten controles de acceso para completar una demostración.

## 8. Referencia por repositorio

### villa-serena-infra

El entorno local contiene `docker-compose.dev.yml`, PostgreSQL 17, Mailpit, MinIO, Prometheus y Grafana, junto con su configuración y `.env.example`. Para el hito, API, web y app se ejecutan fuera de Docker.

Comando de arranque: `docker compose -f docker-compose.dev.yml up -d`.

### villa-serena-api

Spring Boot 4.1, paquete raíz `com.villaserena.api` y módulos según el documento 14. Spring Boot 4 utiliza los starters `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server` y `spring-boot-starter-flyway`.

Los endpoints siguen `/api/v1/...`. Las respuestas utilizan DTO con los datos permitidos para cada rol. Las reglas del negocio se aplican en Spring y, cuando corresponde, en la base de datos.

Comando de arranque local: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, cuando el perfil esté configurado. Flyway aplica el esquema y los datos iniciales.

Para verificar el API desde las computadoras del equipo de frontend, el agente puede ofrecer un **Cloudflare Tunnel temporal**. Su uso se acuerda con la persona, mantiene la autenticación y limita la exposición a los servicios necesarios. La URL y las condiciones de acceso se comparten por el canal del equipo; no se publican credenciales en issues. El túnel se cierra al terminar las pruebas y no equivale a un despliegue de producción.

### villa-serena-web

Next.js 15 aplica el patrón **BFF**: las peticiones HTTP de la web al API pasan por Next.js y los tokens de sesión permanecen en cookies httpOnly, fuera del código accesible al navegador.

El **WebSocket es una excepción explícita**: el navegador conecta directamente con Spring mediante un ticket de un solo uso y 60 segundos obtenido a través del BFF. El JWT del personal permanece fuera del navegador (documento 14, sección 6.1).

Los tipos se generan con `openapi-typescript` desde la copia local de `openapi.yaml`.

Comando de arranque: `pnpm dev` (`http://localhost:3000`).

### villa-serena-movil

Expo SDK 54 y Expo Router. La app consume el API y WebSocket de Spring directamente, con su JWT guardado en `expo-secure-store`.

Las pruebas rápidas utilizan Expo Go de SDK 54, instalado desde `expo.dev/go`. Las notificaciones push se verifican con el development build.

Comando de arranque: `npx expo start`.

### villa-serena-docs

Contiene los acuerdos, el alcance, las historias de usuario y las referencias para implementar y comprobar el sistema. Las instrucciones para IA se mantienen alineadas con el trabajo mediante issues y PR y describen resultados esperados, sin convertir preferencias de implementación en restricciones absolutas.
