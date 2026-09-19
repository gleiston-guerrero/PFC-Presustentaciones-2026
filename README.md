# Sistema de Gestión de Pre-Sustentaciones UTEQ

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.21988563.svg)](https://doi.org/10.5281/zenodo.21988563)
[![Version](https://img.shields.io/badge/version-v1.1.0-blue.svg)](https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026)
[![CI](https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026/actions/workflows/ci.yml/badge.svg)](https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![JaCoCo Coverage](https://img.shields.io/badge/coverage-82.01%25_lines-brightgreen.svg)](docs/mediciones/jacoco/COVERAGE.md)
[![OWASP Top 10](https://img.shields.io/badge/OWASP-5%2F6_controles_con_evidencia-yellow.svg)](docs/mediciones/sec/owasp/OWASP-AUDIT.md)

Sistema web para la automatización, gestión y evaluación de pre-sustentaciones de trabajos de titulación de la **Universidad Técnica Estatal de Quevedo (UTEQ)**.

🌐 **Despliegue público real:** https://steadfast-success-production-2b60.up.railway.app —
`/actuator/health` responde 200; es la URL contra la que se corrieron las 6 mediciones Lighthouse
(ver la tabla más abajo).

## Integrantes / Autores
- Alava Alvarado, Jean Pierre
- Moncayo Loor, Xavier Alejandro
- Zamora Arias, Carla Esthefania
- Barreto Rosado, Heider Dominick

*(Para los roles específicos CRediT, ver `CONTRIBUTORS.md` y `Informe-Final`).*

---

## Requisitos Previos

Verificados de verdad en la máquina donde se generó esta evidencia (no una lista aspiracional) — ver
[`docs/entorno/versions.txt`](docs/entorno/versions.txt) para las versiones exactas y
[`scripts/gen-versions.sh`](scripts/gen-versions.sh) para regenerarlas. Solo el bloque "Despliegue
Rápido" (Docker + Node) es obligatorio para levantar el sistema; el resto solo hace falta para
`make all`/pasos individuales de verificación.

| Herramienta | Para qué | Versión usada al generar esta evidencia |
|---|---|---|
| **Docker Engine + Docker Compose** | Levantar Postgres/Redis/backend/nginx | 29.6.1 / v5.3.0 |
| **Node.js + npm** | Compilar el frontend Angular (`ng build`) | v24.12.0 / 11.6.2 |
| **JDK 17+** | Compilar el backend (Maven Wrapper trae Maven, no necesitas instalarlo aparte) | OpenJDK 21 (compila con `--release 17`, compatible) |
| **GNU Make** | Correr los objetivos `make *` documentados abajo | **No viene con Git Bash / Git for Windows** — instalar con `choco install make`, usar WSL, o ejecutar manualmente el comando de cada objetivo del [`Makefile`](Makefile) (cada uno es una sola línea de shell) |
| **k6** | `make bench` (pruebas de carga) | v2.2.0 |
| **LaTeX con `latexmk`** (MiKTeX o TeX Live) | `make pdf` (compila el Informe Final y el SRS) | MiKTeX, latexmk 4.88 |
| **Python 3 + matplotlib** | `make docs` (regenera las figuras de `docs/mediciones/perf/figuras/`) | 3.14.0 |
| **`gh` CLI** (opcional) | Solo si vas a interactuar con GitHub Issues/PRs desde la terminal | — |

**Nota Windows:** todos los comandos de este README se probaron en Git Bash (MINGW64) en Windows 11.
`make` específicamente **no está instalado por defecto** ahí — si `make all` falla con
`command not found`, instala GNU Make (`choco install make` con [Chocolatey](https://chocolatey.org/),
o usa WSL) o ejecuta el cuerpo de cada objetivo del `Makefile` directamente en la terminal. Si `make all`
falla por otra razón en Windows (ruta con espacios, conflicto de puerto 5432, etc.), ver
[`docs/entorno/TROUBLESHOOTING.md`](docs/entorno/TROUBLESHOOTING.md) — las tres causas reales que
encontramos ahí ya están corregidas o tienen solución documentada.

---

## Instrucciones de Ejecución del Proyecto (Despliegue Rápido)

Para levantar el entorno completo, el proyecto utiliza **Docker**.
La arquitectura incluye:
- **PostgreSQL**: Base de datos relacional (puerto 5432).
- **Redis**: Caché en memoria (puerto 6379).
- **Backend (Spring Boot)**: Se conecta a PostgreSQL y Redis. Utiliza **Flyway** para aplicar migraciones de base de datos automáticamente al arrancar.
- **Frontend (Angular)**: Servido por nginx.

### Orden exacto de comandos para ejecución local:

```bash
# 1. Clonar el repositorio
git clone https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026.git
cd PFC-Presustentaciones-2026

# 2. Configurar variables de entorno (incluye PostgreSQL y Redis)
cp .env.example .env
# Editar .env y reemplazar JWT_SECRET con el resultado de: openssl rand -hex 32

# 3. Construir el frontend (Angular via Node/npm)
cd Frontend
npm install
npx ng build --configuration production
cd ..

# 4. Compilar el backend y ejecutar pruebas (Java 17+ y Maven Wrapper)
cd backend
./mvnw clean test
./mvnw package -DskipTests
cd ..

# 5. Instrucciones de Docker: Levantar la infraestructura completa
docker compose up -d --build
```

El sistema estará disponible en:
- **Frontend Angular (servido por nginx):** `http://localhost`
- **API REST Backend versionada:** `http://localhost:8080/api/v1` (proxied también en `http://localhost/api/v1` vía nginx)
- **Documentación Swagger / OpenAPI 3.0:** `http://localhost:8080/swagger-ui/index.html`
- **Estado de salud (y verificación de Flyway):** `http://localhost:8080/actuator/health`

**Verificación completa de reproducibilidad (Criterio R1):** los pasos 1-2 de arriba (clonar y
crear `.env`) más `make all` ejecutan **todo** el pipeline — build, contenedores, espera de
migraciones, tests con JaCoCo, benchmarks k6, auditoría de seguridad, regeneración de figuras/
trazabilidad, y compilación del PDF final — terminando con código de salida 0 si todo funcionó:

```bash
cp .env.example .env
# Editar .env y reemplazar JWT_SECRET con el resultado de: openssl rand -hex 32
make all
```

**Video de Reproducibilidad:** ✅ grabado — [ver en Google Drive](https://drive.google.com/file/d/1Qi5-PW55kQmecrN3RNWKiIcxRcNXCQDR/view).
Ver [`docs/entorno/VIDEO-DEMO-SCRIPT.md`](docs/entorno/VIDEO-DEMO-SCRIPT.md) para el guion que se
siguió al grabarlo.

`make all` está verificado con éxito de punta a punta (2026-08-31: 109/109 tests, k6, auditoría,
trazabilidad y PDF final, exit 0; la suite creció desde entonces a 806 tests, ver
[`COVERAGE.md`](docs/mediciones/jacoco/COVERAGE.md)) — detalle completo de la verificación y de un bug real de migración que encontró en el
camino en [`docs/entorno/TROUBLESHOOTING.md`](docs/entorno/TROUBLESHOOTING.md).

## Cómo Compilar el Informe Académico

El informe final y el SRS son documentos LaTeX versionados en el repositorio. Para compilarlos a PDF, necesitas el **motor utilizado para generar el informe**: **LaTeX con `latexmk`** (parte de distribuciones como MiKTeX o TeX Live).

### Requisitos y Dependencias del Informe:
- **Motor / Compilador**: `latexmk`
- **Dependencias**: MiKTeX o TeX Live (con paquetes estándar de LaTeX).
- **Archivo principal del informe**: `Informe-Final/informe-final.tex`
- **Estructura de carpetas**:
  - `Informe-Final/`: Raíz del informe final.
  - `Informe-Final/secciones/`: Archivos `.tex` de cada capítulo.
  - `docs/requisitos/`: Raíz del documento de requisitos (SRS).

### Orden exacto de comandos para compilar:

**1. Comando para compilar y generar el PDF del Informe Final:**
```bash
cd Informe-Final
latexmk -pdf -interaction=nonstopmode -halt-on-error informe-final.tex
```
*(El archivo generado será `Informe-Final/informe-final.pdf`)*

**2. Comando para compilar y generar el PDF del SRS:**
```bash
cd docs/requisitos
latexmk -pdf -interaction=nonstopmode -halt-on-error SRS-v1.0.1.tex
```
*(El archivo generado será `docs/requisitos/SRS-v1.0.1.pdf`)*

Ambos PDF ya están compilados y versionados en el repositorio — solo hace falta recompilar si editas el `.tex` correspondiente. `latexmk` es incremental: si no hay cambios, termina de inmediato sin recompilar.

## Comandos Disponibles

`make all` corre todo en orden (`build → up → wait-backend → test → bench → audit → docs → pdf`), pero
cada paso también se puede correr por separado — útil para verificar un paso puntual, o si `make` no
está disponible (ver "Requisitos Previos" arriba: en ese caso, corre el comando de la columna derecha
directamente).

| `make ...` | Qué hace | Comando equivalente sin `make` |
|---|---|---|
| `up` | Levanta Postgres, Redis, backend y nginx con Docker Compose | `docker compose up -d --build` |
| `down` / `clean` | Detiene los contenedores (`clean` además borra volúmenes) | `docker compose down` / `docker compose down -v --remove-orphans` |
| `build` | Compila el backend y el build de producción del frontend | `cd backend && ./mvnw -q compile && cd ../Frontend && npx ng build --configuration production` |
| `test` | Corre la suite de tests del backend (genera el reporte JaCoCo) | `cd backend && ./mvnw test` |
| `wait-backend` | Espera a que `/actuator/health` responda `UP` (confirma que las migraciones Flyway se aplicaron) tras `make up` | `curl -sf http://localhost:8080/actuator/health` en un loop hasta ver `"status":"UP"` |
| `bench` | Corre `k6/load-test.js` contra el backend levantado | `cd k6 && BASE_URL=http://localhost:8080/api/v1 k6 run --quiet --summary-export=runN-summary.json load-test.js` (backend debe estar arriba primero) |
| `audit` | SpotBugs/find-sec-bugs (SQL dinámico) + `npm audit` del frontend | `./scripts/audit-sql-dynamic.sh && cd Frontend && npm audit` |
| `docs` | Regenera las figuras de rendimiento y valida la matriz de trazabilidad (8 comprobaciones V1–V8: columnas, vocabulario de estado, paridad SRS↔matriz en ambos sentidos, resolución de endpoints por ruta completa, existencia de pruebas e historias en disco) | `python scripts/gen-figuras.py && ./scripts/validate-traceability.sh` |
| `pdf` | Compila `Informe-Final/informe-final.tex` a PDF (el SRS se compila aparte, ver "Cómo Compilar el Informe Académico" arriba) | `cd Informe-Final && latexmk -pdf -interaction=nonstopmode -halt-on-error informe-final.tex` |

Para re-correr el escaneo OWASP ZAP (no cubierto por `make audit`, requiere Docker):
`cd docs/mediciones/sec/zap && docker run --rm -v "$(pwd):/zap/wrk:rw" -t zaproxy/zap-stable zap.sh -cmd -autorun /zap/wrk/zap.yaml` (con el stack de `make up` corriendo). **En Git Bash/MINGW64 en Windows**, antepone `MSYS_NO_PATHCONV=1` a ese comando — si no, Git Bash reescribe la ruta `/zap/wrk/...` del argumento como si fuera una ruta de Windows y el contenedor no encuentra el archivo.

## Despliegue Público (Producción)

**Estado:** desplegado y verificado en Railway (2026-09-17).

- **Frontend:** https://steadfast-success-production-2b60.up.railway.app — HTTPS válido, carga y el login
  conecta correctamente con el backend real (verificado en navegador, sin bloqueo de CORS).
- **Backend:** https://pfc-presustentaciones-2026-production.up.railway.app —
  `/actuator/health` responde `200 {"status":"UP"}` con DB (PostgreSQL), Redis, disco y ping en `UP`.
- **Lighthouse contra esta URL real** (no `localhost`): ver
  [`docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md`](docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md) —
  Performance 94/100 desktop y 81/100 mobile (ambos ≥80), Accessibility/Best Practices/SEO en 100/100 en
  los dos perfiles.
- Procedimiento completo y detalle técnico del despliegue en
  [`docs/despliegue/DEPLOYMENT.md`](docs/despliegue/DEPLOYMENT.md).

**Usuarios de demostración** (para que el tribunal entre sin registrarse): existe un usuario **Administrador**
(`admin@uteq.edu.ec`) verificado funcionando contra el despliegue real el 2026-09-17 (login real, no
simulado). Su contraseña, y las de los demás roles de demostración (Coordinador, Docente, Estudiante), se
entregan al tribunal junto con el enlace de despliegue en el informe de entrega — no se publican en este
README para evitar dejarlas expuestas en el historial público del repositorio.

---

## Resumen de Entregables y Documentación

| Componente | Ubicación en el Repositorio | Descripción |
|---|---|---|
| **Observaciones 1A & 1B** | [`docs/observaciones/OBSERVACIONES.md`](docs/observaciones/OBSERVACIONES.md) | Criterios, decisiones y hashes de commits de corrección. Etiquetas `v0.7.0` y `v0.7.1`. |
| **Catálogo de SP / SQL** | [`docs/basedatos/CATALOGO-SP.md`](docs/basedatos/CATALOGO-SP.md) | Documentación de procedimientos almacenados PL/pgSQL y funciones puras. |
| **Especificación SRS v1.0.1** | [`docs/requisitos/SRS-v1.0.1.pdf`](docs/requisitos/SRS-v1.0.1.pdf) / [`.tex`](docs/requisitos/SRS-v1.0.1.tex) / [`.docx`](docs/requisitos/SRS-v1.0.1.docx) | ISO/IEC/IEEE 29148:2018. **90 requisitos: 64 funcionales y 26 no funcionales**, cada uno con enunciado normativo, *rationale*, criterio de aceptación, método de verificación y estado. 12 HU ([`historias/`](docs/requisitos/historias/)) + 12 CU Cockburn ([`casos-de-uso/`](docs/requisitos/casos-de-uso/)); los requisitos sin historia declaran su origen real (migración, ADR, control OWASP, reglamento u observación docente). Responde a la revisión del docente-director sobre la v1.0.0 — ver §12.1 del documento y [`CHANGELOG-REQ.md`](docs/requisitos/CHANGELOG-REQ.md). Versiones anteriores en [`historico/`](docs/requisitos/historico/). |
| **Matriz Trazabilidad** | [`docs/trazabilidad/matriz.csv`](docs/trazabilidad/matriz.csv) | **90 filas, una por requisito**, con 11 columnas: ID → Título → Fuente → Módulo → Endpoint → Prioridad MoSCoW → Método de verificación → Caso de prueba → Estado → Evidencia → Observaciones. La columna `Estado` usa el vocabulario cerrado de 4 valores del SRS (Verificado / Implementado / Planificado / No cumplido). **36 de 50 requisitos *Must* verificados (72%)**; los 14 restantes se enumeran uno por uno, con lo que falta en cada uno, en §10.3 del SRS. Estas cifras **no se cuentan a mano**: las imprime `validate-traceability.sh`, y si el SRS y la matriz dejan de coincidir el validador falla y la cifra no llega a publicarse. |
| **Checklist INCOSE + elicitación** | [`docs/checklists/INCOSE-REQUIREMENTS.md`](docs/checklists/INCOSE-REQUIREMENTS.md) / [`docs/requisitos/elicitacion/`](docs/requisitos/elicitacion/) | 9 características INCOSE × 12 RF + 6 de conjunto; evidencia real de técnicas de elicitación (2/5 con evidencia documentada). |
| **Bitácora de requisitos** | [`docs/requisitos/CHANGELOG-REQ.md`](docs/requisitos/CHANGELOG-REQ.md) | Cambios entre SRS v0.9.0-rc y v1.0.0, tasa de estabilidad calculada. |
| **Despliegue & Docker** | [`Makefile`](Makefile) / [`docker-compose.yml`](docker-compose.yml) | `make up/down/restart/logs/ps/clean` + `make build/test/bench/audit/docs/pdf`, y **`make all`**: el objetivo de reproducibilidad end-to-end del Criterio R1 (Fase 10) — desde una clonación limpia levanta todos los contenedores, espera a que las migraciones Flyway se apliquen, corre tests + benchmarks + auditoría + reportes, y compila el PDF final, saliendo con código 0 solo si todo funcionó. Verificado real: `docker compose down -v && docker compose up -d --build` sobre un volumen de Postgres limpio llega a healthy, y `make pdf` compila el informe de 40 páginas sin errores. Imágenes ancladas por digest SHA-256. Guion para el video de demostración: [`docs/entorno/VIDEO-DEMO-SCRIPT.md`](docs/entorno/VIDEO-DEMO-SCRIPT.md) (✅ [video grabado](https://drive.google.com/file/d/1Qi5-PW55kQmecrN3RNWKiIcxRcNXCQDR/view)). |
| **Despliegue en producción** | [`docs/despliegue/`](docs/despliegue/) | `DEPLOYMENT.md` (proveedor y procedimiento), `RUNBOOK.md` (arranque/apagado/rotación), `BACKUP.md` (respaldo diario automatizado vía GitHub Actions). Estado real: **desplegado en Railway y verificado** (2026-09-17) — ver [Despliegue Público](#despliegue-público-producción) arriba. |
| **Procedencia de datos** | [`docs/mediciones/DATA-PROVENANCE.md`](docs/mediciones/DATA-PROVENANCE.md) | Mapea cada cifra citada en el informe a su archivo crudo y comando de origen. |
| **Versiones del entorno** | [`docs/entorno/versions.txt`](docs/entorno/versions.txt) | Versiones exactas de Docker, JDK, Node, Angular CLI y k6 usadas para generar la evidencia. Regenerable con [`scripts/gen-versions.sh`](scripts/gen-versions.sh); el job `entorno` de [`ci.yml`](.github/workflows/ci.yml) publica su propia copia como artefacto en cada push. |
| **Checklists metodológicos** | [`docs/checklists/`](docs/checklists/) | Autoevaluación honesta contra Ralph 2021 (General + Engineering Research + Benchmarking), PRISMA 2020 (evaluado contra el procedimiento de búsqueda real del capítulo de trabajos relacionados), Runeson & Höst (no aplica — el proyecto es DSR+GQM, no estudio de caso), INCOSE y FAIR. |
| **DOI del software (Zenodo)** | [`docs/ZENODO.md`](docs/ZENODO.md) / [`docs/ZENODO-DATASET.md`](docs/ZENODO-DATASET.md) | ✅ **Archivado en Zenodo** — versión actual `v1.0.1`: [10.5281/zenodo.22445216](https://doi.org/10.5281/zenodo.22445216) (licencia MIT, cierre real de la Entrega Final). Versión anterior `v1.0.0`: [10.5281/zenodo.21988564](https://doi.org/10.5281/zenodo.21988564). DOI de concepto (siempre resuelve a la última versión): [10.5281/zenodo.21988563](https://doi.org/10.5281/zenodo.21988563). Dataset de mediciones: [10.5281/zenodo.22398713](https://doi.org/10.5281/zenodo.22398713) (CC-BY 4.0, `docs/ZENODO-DATASET.md`). |
| **Scripts de análisis** | [`scripts/`](scripts/) | Notebooks reales y ejecutados (`perf-analysis.ipynb`, `sus-analysis.ipynb`) + `validate-traceability.sh`, `audit-sql-dynamic.sh`, `gen-figuras.py`. |
| **Pruebas de Carga k6** | [`k6/`](k6/) | Script k6, 5 corridas independientes válidas (`run3`-`run7`; `run1`/`run2` se conservan versionadas como evidencia de una corrida inválida real, no se usan en las estadísticas — ver [`k6/README.md`](k6/README.md)) y análisis estadístico caché fría/caliente (Wilcoxon, IC 95%, tamaño de efecto). |
| **Auditoría OWASP** | [`docs/mediciones/sec/owasp/OWASP-AUDIT.md`](docs/mediciones/sec/owasp/OWASP-AUDIT.md) | Revisión manual de 6 controles OWASP Top 10 + herramientas automáticas reales: OWASP ZAP (0 FAIL, 0 High; 5 hallazgos corregidos en total — 4 headers + 1 `@angular/core` vulnerable), SpotBugs/find-sec-bugs (0 SQL dinámico, 1 timing-attack corregido), `npm audit` (14 deps vulnerables, todas en tooling de build de íconos — bajó de 41 tras actualizar Angular). |
| **Escaneo OWASP ZAP** | [`docs/mediciones/sec/zap/`](docs/mediciones/sec/zap/) | Reporte HTML/JSON de la corrida real (plan de automatización `zap.yaml`), re-verificada 2026-08-29; corrida anterior conservada como `*.PREVIOUS.*`. |
| **Análisis estático** | [`docs/mediciones/sec/static-analysis/STATIC-ANALYSIS.md`](docs/mediciones/sec/static-analysis/STATIC-ANALYSIS.md) | SpotBugs + find-sec-bugs: 233 hallazgos reales (actualizado 2026-08-29; 189 en la corrida del 17-08), 0 de SQL dinámico. |
| **Usabilidad SUS** | [`docs/mediciones/sus/SUS-RESULTS.md`](docs/mediciones/sus/SUS-RESULTS.md) | **Resultado de cierre — ronda del 2026-09-18**, aplicada en un formulario alojado por un tercero que sella cada respuesta con la hora de su propio servidor: media SUS **52,83/100**, DE **12,06**, IC 95% **[46,16, 59,51]**, **n=15**, las 15 con consentimiento individual explícito. Por debajo del promedio de la industria (68, Bangor et al.) — resultado desfavorable, reportado como tal. Datos sin editar en [`re-aplicacion/`](docs/mediciones/sus/re-aplicacion/), reproducible con `scripts/sus-ingesta.py`. **Ronda anterior en papel, retractada:** de sus 15 hojas solo **4 tienen fecha de aplicación verificable**; las otras 11 llevan una fecha que no se sostiene y **no contribuyen a ninguna cifra de usabilidad publicada** (aparecen solo en la comprobacion de si los puntajes del papel fueron inventados, que es una afirmacion sobre ellas; esa conclusion se sostiene igual sin ellas) — se conservan versionadas y sin modificar en [`respuestas-crudas/`](docs/mediciones/sus/respuestas-crudas/) y [`sus-respuestas.csv`](docs/mediciones/sus/sus-respuestas.csv), declaradas pendientes de confirmar, no borradas. |
| **Lighthouse Frontend** | [`docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md`](docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md) | 6 corridas reales contra el **despliegue público real** (3 desktop + 3 mobile, 2026-09-17, no `localhost`): Performance **94/100 desktop, 81/100 mobile** (ambos ≥80 ✅), Accessibility 100, Best Practices 100, SEO 100 en los dos perfiles. Los 4 umbrales de la guía se cumplen. Mediciones anteriores contra `localhost` (2026-08-17 a 2026-09-06, con la investigación real de rendimiento que motivó las optimizaciones de imagen/caché/SEO) se conservan como histórico en el reporte — no satisfacían el criterio de "contra el despliegue público". |
| **Arquitectura C4** | [`docs/arquitectura/README.md`](docs/arquitectura/README.md) | Diagramas de Arquitectura Modelo C4 (Niveles 1 Contexto, 2 Contenedores, 3 Componentes). |
| **Registros ADR** | [`docs/adr/`](docs/adr/) | 7 Registros de Decisiones Arquitectónicas (ADR-001 a ADR-007). ADR-005 documenta el control de acceso por permisos dinámicos que reemplazó al RBAC estático descrito originalmente en ADR-004 (seguridad OWASP). ADR-006 (separación CRUD/SP) y ADR-007 (despliegue) se renumeraron el 2026-09-01 para coincidir con los temas específicos que la guía de la Entrega Final exige en esos dos números — mismo contenido, solo cambió el orden. |
| **Taxonomía CRediT** | [`CONTRIBUTORS.md`](CONTRIBUTORS.md) | Asignación explícita de roles CRediT para los 4 integrantes del grupo universitario. |
| **Citación & Licencia** | [`CITATION.cff`](CITATION.cff) / [`LICENSE`](LICENSE) | Archivo de citación CFF válido y Licencia Open Source MIT. |
| 📖 **Diccionario Datos** | [`docs/mediciones/DATA-DICTIONARY.md`](docs/mediciones/DATA-DICTIONARY.md) | Explicación detallada de variables medidas en pruebas empíricas. |
| **Documento Ético** | [`docs/etica/`](docs/etica/) | Principios bioéticos ([`ETHICS.md`](docs/etica/ETHICS.md)), plantilla de consentimiento informado ([`consentimientos/`](docs/etica/consentimientos/)) y declaración de uso de IA ([`ai-disclosure.md`](docs/etica/ai-disclosure.md)). |
| **Colección Postman** | [`docs/postman/PFC-Collection.json`](docs/postman/PFC-Collection.json) | Colección v2.1 con 27 peticiones HTTP RESTful (éxito, validación 400, autorización 401/403, no encontrado 404), verificada en la Fase 10 contra el backend real corriendo en Docker — no solo contra el código. Corrigió rutas que nunca existieron en una versión anterior (`/asignar-masivo`, `/{id}/firmar`, `/{id}/calcular-promedio`, `/reportes/defensas`, `POST /api/solicitudes` sin path) y el prefijo de versión `/api/v1/` faltante en todas las peticiones. **Re-verificada 2026-08-30 (auditoría de reproducibilidad):** las 27 peticiones se ejecutaron realmente contra el backend con el dataset de 1M+ registros cargado — 25/27 coincidían, 2 no: dos pruebas "sin token" nombradas "(Error 403)" en realidad reciben 401 desde el fix real de `GlobalExceptionHandler` (403 queda solo para autenticado-sin-rol), y tres pruebas "No Encontrado (Error 404)" usaban el ID `999` como "seguro que no existe" — pero **sí existe** en el dataset de 1M filas (verificado: `GET .../999` devolvía `200` con un registro real). El backend está correcto en los 5 casos; se corrigió la colección (nombres y el ID de prueba a `999999999`, confirmado inexistente), no el backend. |
| **Informe Final (académico, 12 capítulos)** | [`Informe-Final/informe-final.pdf`](Informe-Final/informe-final.pdf) | Documento IMRaD ampliado: SRS, DSR+GQM, arquitectura, evaluación empírica, discusión, amenazas a la validez, CRediT, 32 referencias verificadas (19 de alto impacto). Es el informe vigente para la defensa. |
| **Informe Técnico (Unidad IV, histórico)** | [`Informe-UNIDAD-4-PRESUS/`](Informe-UNIDAD-4-PRESUS/) | Informe técnico de la Unidad IV en `.docx`/`.tex`, anterior al Informe Final. |
