# Corrida limpia del 2026-09-18 — reproduccion independiente

**Comando exacto:**

```bash
set -a; . ./.env; set +a
cd backend && ./mvnw clean test
```

Con `docker compose up -d postgres redis` levantado (Postgres y Redis reales, no
embebidos). El `clean` es lo que importa: borra `backend/target/` y con el
`jacoco.exec` anterior, de modo que el reporte sale de **una sola sesion**.

## Resultado

```
Tests run: 804, Failures: 0, Errors: 0, Skipped: 0
jacoco:check (jacoco-check) --- All coverage checks have been met.
BUILD SUCCESS -- Total time: 01:02 min -- 2026-09-18T11:12:42-05:00
```

| Contador | Cubierto / Total | % |
|---|---|---|
| LINE | 4017 / 4897 | **82.03 %** |
| BRANCH | 1483 / 2018 | **73.49 %** |
| INSTRUCTION | 20997 / 26205 | 80.13 % |
| METHOD | 805 / 1013 | 79.47 % |
| CLASS | 81 / 87 | 93.10 % |

`sessioninfo` en el XML: **1**.

## Por que se versiona esta corrida si ya existe la del 17-sep

La evaluacion integral del 2026-09-17 objeto tres cosas de P2:

1. *"el XML acumula 71 sesiones (no es una corrida limpia)"* — aqui son **1**.
2. *"15 pruebas fallan por falta de base de datos"* — aqui **804 pruebas, 0 fallos**,
   contra Postgres y Redis reales.
3. *"no hay regla `check` que imponga el 70 %"* — existe (`jacoco-check` en
   `backend/pom.xml`, minimo 0.70 en lineas y ramas) y aqui **pasa**, no solo esta
   declarada.

La corrida de `2026-09-17-corrida-limpia-unica-sesion/` ya respondia a esto. Esta
carpeta no la reemplaza: la **reproduce de forma independiente un dia despues**,
en otra sesion y tras otros commits, y da **exactamente las mismas cifras**
(4017/4897 y 1483/2018, al dígito). Es decir, la cobertura reportada no depende
de la corrida particular que se haya versionado.

El valor de ramas coincide ademas, cifra por cifra, con el que el propio
ingeniero calculo sumando los contadores del XML versionado: 1483/2018 = 73.49 %.
