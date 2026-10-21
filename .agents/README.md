# Ponytail en Villa Serena

Instalación por repositorio de las seis skills de
[Ponytail](https://github.com/DietrichGebert/ponytail), versión **5.3.2**,
fijada al commit [`09c434ab73ca43761c5d8b0c5d341926c2fba710`](https://github.com/DietrichGebert/ponytail/tree/09c434ab73ca43761c5d8b0c5d341926c2fba710).
Las copias de `SKILL.md` conservan el contenido original. La revisión y sus huellas
SHA-256 están en [ponytail-source.json](ponytail-source.json); la licencia MIT del
autor se conserva en [PONYTAIL-LICENSE](PONYTAIL-LICENSE).

## Carga y uso

`AGENTS.md` indica leer la skill principal y usar el nivel `full` por defecto.
`CLAUDE.md` remite a `AGENTS.md`. Los acuerdos del proyecto y el español prevalecen
sobre preferencias genéricas del proveedor. Ponytail busca el cambio completo más
pequeño, conservando validaciones, seguridad y las comprobaciones necesarias.

Codex descubre las skills en `.agents/skills` al abrir el checkout que contiene
estos archivos. En su selector de skills se usan los nombres locales, sin el
prefijo del plugin `ponytail:`:

| Skill | Invocación en Codex | Uso |
|---|---|---|
| [ponytail](skills/ponytail/SKILL.md) | `$ponytail full` | Criterio de implementación; también `lite` o `ultra`. |
| [ponytail-review](skills/ponytail-review/SKILL.md) | `$ponytail-review` | Revisión de cambios, con alcance explícito. |
| [ponytail-audit](skills/ponytail-audit/SKILL.md) | `$ponytail-audit` | Auditoría del repositorio o área solicitada. |
| [ponytail-debt](skills/ponytail-debt/SKILL.md) | `$ponytail-debt` | Inventario de comentarios `shortcut:`. |
| [ponytail-gain](skills/ponytail-gain/SKILL.md) | `$ponytail-gain` | Resultados publicados por el autor; no mide este proyecto. |
| [ponytail-help](skills/ponytail-help/SKILL.md) | `$ponytail-help` | Ayuda de upstream, con las diferencias locales de esta guía. |

En otros agentes, solicitar la skill por nombre y leer el archivo enlazado.
La disponibilidad de comandos nativos depende de la herramienta; `AGENTS.md`
proporciona la carga de instrucciones del proyecto. Para desactivar el modo,
pedir «desactiva Ponytail» o «modo normal»; para reactivarlo, «usa Ponytail full».
Si el selector no refleja la instalación, abrir una nueva sesión en ese checkout.

## Modalidad de instalación

Estas skills son instrucciones versionadas del proyecto. Las secciones de la
ayuda upstream sobre nombres con prefijo, `/plugin`, variables de entorno,
configuración personal, hooks o panel corresponden al **plugin instalado por
usuario** y no configuran esta modalidad. Esta guía define el uso local.

La referencia de `ponytail-gain` a los benchmarks vive en
[la fuente fijada](https://github.com/DietrichGebert/ponytail/blob/09c434ab73ca43761c5d8b0c5d341926c2fba710/benchmarks/results/2026-10-07-agentic.md).
Sus cifras no acreditan ahorros en Villa Serena. Los demás archivos de upstream
pueden consultarse en el enlace al commit anterior.

La descarga se realizó con `skill-installer`, fijando `--ref` a ese SHA y
seleccionando los seis directorios `skills/ponytail*`. Actualizar los archivos,
la licencia y el manifiesto juntos mediante issue y PR; comprobar las huellas y
revisar las instrucciones nuevas antes de adoptar otra revisión.
