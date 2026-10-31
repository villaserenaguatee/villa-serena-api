# Seguridad en CI/CD — issue #111

`API CI` se ejecuta en PR y pushes a `main`, `develop` y `dev`. Conserva las
pruebas y la comprobación del contrato. `./mvnw verify` genera cobertura JaCoCo
en `target/site/jacoco/jacoco.xml` y su informe HTML.

SonarQube Cloud Free analiza únicamente la rama predeterminada de GitHub y las
PR dirigidas a ella. El workflow obtiene su nombre de
`github.event.repository.default_branch`; las demás ramas conservan pruebas y
Trivy, con un aviso de que Sonar queda fuera de alcance. GitHub tiene `main` como
predeterminada al configurar esta integración; `develop` sigue siendo la rama de
integración del equipo. Por tanto, una PR de trabajo hacia `develop` no recibe
análisis Sonar; la PR de entrega hacia `main` sí lo recibe.
Para la issue #111 se autorizó excepcionalmente una PR directa hacia `main`,
después de que `develop` se integrara y eliminara mediante la PR #110.

Trivy analiza el POM, incluidas las dependencias transitivas; otro job construye
el Dockerfile de producción y analiza sus paquetes de sistema y las librerías
Java del JAR empaquetado. `trivy.yaml` bloquea CVE HIGH/CRITICAL,
incluso sin corrección disponible. Los errores del escáner también fallan el job.
Los informes JSON y la cobertura quedan como artefactos, incluso si falla un gate.

El CD continúa siendo manual y exclusivo de `main`: primero llama al mismo CI
(pruebas, Sonar y Trivy); luego construye la imagen ARM64 para EC2, la carga
localmente y la escanea. Solo publica esa misma imagen en GHCR si pasa el escaneo.
El despliegue depende de esa publicación. El escaneo de PR usa la arquitectura
del runner; el de CD comprueba la imagen ARM64 que efectivamente se despliega.

Para reproducir los gates de vulnerabilidades localmente con Trivy 0.76.1:

```bash
./mvnw -B verify
trivy fs --config trivy.yaml --output target/trivy-dependencies.json pom.xml
docker build -t villa-serena-api:scan .
trivy image --config trivy.yaml --output target/trivy-image.json villa-serena-api:scan
```

Cada escaneo devuelve `1` si encuentra HIGH/CRITICAL. Revisar sus reportes JSON;
no interpretar una compilación o una suite de pruebas exitosa como ausencia de CVE.

## Configurar SonarQube Cloud o Server

1. Importar el repositorio en Sonar y habilitar análisis basado en CI. Desactivar
   el análisis automático de SonarQube Cloud para evitar análisis duplicados.
2. En GitHub → Settings → Secrets and variables → Actions, configurar:
   - Secret `SONAR_TOKEN`: token con permiso de análisis sobre este proyecto.
   - Variable `SONAR_HOST_URL`: `https://sonarcloud.io` o URL HTTPS del servidor,
     accesible desde el runner de GitHub.
   - Variable `SONAR_PROJECT_KEY`: clave exacta del proyecto importado.
   - Variable `SONAR_ORGANIZATION`: clave de organización para Cloud; omitir en
     Server si no corresponde.
3. Confirmar que la Main Branch de Sonar coincida con la rama predeterminada de
   GitHub: cambiarla en GitHub no sincroniza por sí solo el proyecto de Sonar.
   El plan Free admite una rama principal y PR dirigidas a ella; no admite
   perfiles ni gates personalizados. Usar el perfil Java y el gate incorporado
   Sonar way; otros planes permiten personalizarlos. Revisar vulnerabilidades,
   inyecciones, secretos, security hotspots y code smells; los hotspots requieren
   revisión humana. Mantener cobertura de código nuevo y revisión de hotspots
   como condiciones del gate. No reducir umbrales para aprobar un cambio.
4. Ejecutar una PR interna hacia la rama predeterminada y verificar el análisis y la importación
   de cobertura. El scanner Maven espera hasta 300 segundos por el Quality Gate:
   un rechazo, timeout o configuración ausente falla `verify`.
5. En las reglas de protección de `develop` y `main`, exigir los checks `verify`
   y `container-security` de `API CI`. La configuración YAML por sí sola no impide
   que alguien integre una PR con checks fallidos.

Valores configurados para el API:

| Variable | Valor |
| --- | --- |
| `SONAR_HOST_URL` | `https://sonarcloud.io` |
| `SONAR_PROJECT_KEY` | `villaserenaguatee_villa-serena-api` |
| `SONAR_ORGANIZATION` | `villaserenaguatee` |

`SONAR_TOKEN` se guarda exclusivamente como secret de Actions. El primer análisis
de la rama principal debe completarse antes de validar sus PR, para que exista
la referencia de comparación en Sonar.

Las PR de forks y Dependabot ejecutan pruebas y Trivy, pero omiten Sonar con un
aviso porque no reciben el secret. Antes de integrar esos cambios, una persona
del equipo debe revisarlos y llevarlos a una rama interna para abrir una PR con
análisis Sonar. No usar `pull_request_target` para ejecutar código externo con
secretos. Los pushes de Dependabot también omiten Sonar; el CD manual ejecutado
por el equipo exige el token y el Quality Gate.

## Falsos positivos y excepciones

Priorizar actualizar la dependencia o imagen base. Para un falso positivo o una
aceptación temporal del riesgo, registrar una issue con CVE/regla, componente y
versión afectados, evidencia de aplicabilidad, mitigación, responsable y fecha
límite; obtener revisión del equipo mediante PR antes de integrar la excepción.

Trivy usa `.trivyignore.yaml`, inicialmente vacío. Una excepción debe limitarse
al paquete/ruta afectado y caducar, por ejemplo (valores ilustrativos):

```yaml
vulnerabilities:
  - id: CVE-2099-00001
    paths:
      - app/app.jar
    statement: "Issue #NNN: evidencia y mitigación; responsable: equipo API"
    expired_at: 2099-01-31
```

Usar la ruta que Trivy reporte para cada objetivo (filesystem o imagen); una
excepción de dependencias no implica automáticamente una excepción de contenedor.
Revalidar su necesidad antes del vencimiento. No agregar exclusiones globales,
`ignore-unfixed: true` ni `continue-on-error` para sortear el gate.

En Sonar, marcar un hallazgo como falso positivo o aceptado únicamente con la
misma evidencia e issue enlazada; revisar los hotspots individualmente. El perfil
y el gate se administran en Sonar, no mediante exclusiones generales del código.

Referencias: [Trivy Java](https://trivy.dev/docs/latest/guide/coverage/language/java/),
[SonarScanner Maven](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/scanners/sonarscanner-for-maven)
y [JaCoCo Maven](https://www.jacoco.org/jacoco/trunk/doc/maven.html).
Los límites de ramas y personalización están en los
[planes de SonarQube Cloud](https://docs.sonarsource.com/sonarqube-cloud/administering-sonarcloud/managing-subscription/subscription-plans).
