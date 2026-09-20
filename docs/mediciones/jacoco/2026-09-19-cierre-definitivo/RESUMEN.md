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
| **Líneas (LINE)** | 4022 | 4904 | **82.01 %** |
| **Ramas (BRANCH)** | 1483 | 2018 | **73.49 %** |

```
Tests run: 809, Failures: 0, Errors: 0, Skipped: 0
jacoco:check (jacoco-check) --- All coverage checks have been met.
BUILD SUCCESS
```

- **Pruebas:** 809
- **Fallos:** 0
- **Errores:** 0

## Regenerada de nuevo tras cerrar P7 (2026-09-19)

La prueba de integración del chatbot dejó de simular el servicio que decía probar y se le añadieron dos
casos: **804 → 806 pruebas**. La cobertura no se movió (`ChatbotService` ya estaba ejercitado por otra
clase), así que las cifras de líneas y ramas son idénticas; lo que cambia es el conteo de pruebas, y
por eso se republica.

## Regenerada el mismo día tras el cierre de avisos de Javadoc

Esta carpeta se regeneró después de cerrar los avisos de `javadoc` (682 → 170). Ese trabajo es casi
todo comentarios, que no instrumenta JaCoCo, pero incluyó **10 constructores explícitos** en clases de
configuración que no llevan Lombok — ahí javadoc avisaba de un constructor por defecto que realmente no
existía en el fuente. Esas 3 líneas nuevas son código, están cubiertas, y por eso el total sube de
4901 a 4904 líneas instrumentadas y la cifra de 82,00 % a **82,01 %**.

Se sustituye el XML en vez de crear otra carpeta con la misma fecha: lo que documenta esta carpeta es
*la corrida de cierre del 2026-09-19*, y la corrida de cierre es esta. La anterior no se publicaba en
ningún sitio que no se haya actualizado en el mismo commit.

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

## Regenerada tras descubrir tres pruebas que nunca se ejecutaban (2026-09-20)

La revision final contrasto los **809** metodos `@Test` que cuenta el AST con las **806** pruebas de esta
corrida. La brecha eran tres pruebas de `PasswordPolicyValidatorTest` dentro de una clase `static` anidada
sin `@Nested`: JUnit no la descubre y Surefire excluye las clases internas, asi que **existian y no corrian**.
Se sacaron a `RegisterPasswordPolicyIntegrationTest` y ahora se ejecutan: **806 -> 809 pruebas, 0 fallos**.
La cobertura **no se movio** (`LINE` 4022/4904, `BRANCH` 1483/2018: el endpoint de registro ya lo ejercitaban
otras clases); lo que cambia es el conteo de pruebas, y por eso se republica. Las tres pasan, y se comprobo
que **pueden fallar**: al desactivar el rechazo de contrasenas comunes en `PasswordPolicyValidator`, la
primera se cae. `scripts/p2-pruebas-ejecutadas.py` comprueba desde entonces que toda prueba anotada corre.
