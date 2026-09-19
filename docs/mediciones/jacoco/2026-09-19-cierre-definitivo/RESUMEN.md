# Corrida de cierre definitiva — 2026-09-19

Esta carpeta es la **fuente canónica** de la cifra de cobertura que publican el informe, el README y el
SRS. `scripts/cifras-publicadas.py` la lee directamente y hace fallar `make verify` si algún documento
vigente publica un número que este `jacoco.xml` no respalda.

## Cómo se generó

```bash
docker compose up -d db redis
cd backend && ./mvnw clean test
```

Una sola sesión de ejecución (`sessioninfo` en el XML: 1), sobre PostgreSQL y Redis reales en Docker,
sin nada acumulado de corridas anteriores. JaCoCo corre en la fase `test` vía `jacoco-maven-plugin`,
que además aplica la regla `jacoco:check` (≥70 % en líneas y en ramas).

## Resultado

| Métrica | Cubierto | Total | Porcentaje |
|---|---:|---:|---:|
| **Líneas (LINE)** | 4019 | 4901 | **82.00 %** |
| **Ramas (BRANCH)** | 1483 | 2018 | **73.49 %** |

```
Tests run: 804, Failures: 0, Errors: 0, Skipped: 0
jacoco:check (jacoco-check) --- All coverage checks have been met.
BUILD SUCCESS
```

- **Pruebas:** 804
- **Fallos:** 0
- **Errores:** 0

## Por qué se volvió a correr

La revisión del 18-sep encontró conviviendo en los documentos vigentes tres cifras de cobertura
(82,10 / 82,03 / 82,96) y tres conteos de pruebas (559 / 801 / 804). Ninguna era inventada —cada una era
la cifra real de *alguna* corrida— pero publicadas a la vez son una contradicción. Se corrió de nuevo
desde cero, se unificó todo contra este XML, y se automatizó la comprobación para que no vuelva a pasar.

## Relación con las corridas anteriores

`2026-09-17-corrida-limpia-unica-sesion/` y `2026-09-18-corrida-limpia-reproduccion/` dieron ambas
82,03 % / 73,49 % sobre **4897** líneas instrumentadas. Aquí son **4901**: las 4 líneas de diferencia
son código que agregaron las correcciones posteriores al 18-sep. No cambió el método de medición, y esas
carpetas se conservan sin borrar.
