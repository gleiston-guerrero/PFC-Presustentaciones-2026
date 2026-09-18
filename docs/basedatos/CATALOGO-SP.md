# 🗄️ CATÁLOGO DE PROCEDIMIENTOS ALMACENADOS Y FUNCIONES SQL (PL/pgSQL)

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ
**Motor de BD:** PostgreSQL 15.x
**Migraciones Flyway:** [`V2__stored_procedures.sql`](../../backend/src/main/resources/db/migration/V2__stored_procedures.sql) (1-4), [`V3__stored_procedures_validacion_y_codigos.sql`](../../backend/src/main/resources/db/migration/V3__stored_procedures_validacion_y_codigos.sql) (5-6) — ver la decisión explícita de convención de carpetas en [`../adr/ADR-006-estrategia-hibrida-bd-sp.md`](../adr/ADR-006-estrategia-hibrida-bd-sp.md)

---

## ⚠️ Objetos duplicados detectados en auditoría (2026-08-29)

`V10__actualizar_procedimientos_fase3.sql` (cabecera interna aún dice `V3__...`, resto de una
renumeración de la fusión de ramas — ver nota de fusión del ítem 3 más abajo) **también** declara
`sp_calcular_promedio_evaluacion(BIGINT)` y `sp_generar_reporte_defensas(VARCHAR)` como `FUNCTION ...
RETURNS TABLE`, con **un solo parámetro** cada una (sin el `INOUT refcursor`). Como PostgreSQL
identifica una rutina por `(esquema, nombre, tipos de parámetros)`, esta firma de un solo parámetro
es distinta a la `PROCEDURE` de dos parámetros de `V2`/`V3` documentada abajo — no la reemplaza, sino
que **coexiste como una sobrecarga (overload) separada y sin uso**: el código Java (`@Procedure`/
`@NamedStoredProcedureQuery`, ver más abajo) invoca explícitamente la firma de dos parámetros, así que
sigue resolviendo contra la `PROCEDURE` correcta (confirmado: la suite de tests del backend pasa
completa, 61/61). Estas dos `FUNCTION` son objetos huérfanos en el esquema real, resultado de la
fusión de ramas del equipo — quedan documentadas aquí para que no se confundan con la firma que
realmente usa la aplicación; no se modifica `V10` porque es una migración Flyway ya aplicada
(alterarla invalidaría el checksum de `flyway_schema_history` en cualquier base de datos existente).

## ⚠️ Actualización de este documento (Fase 3)

Una versión anterior de este catálogo describía 4 procedimientos con nombres de tabla que
**nunca existieron** en el esquema real (`estudiantes`, `solicitudes`, `cronogramas`,
`salas`, `jurados` en vez de `estudiante`, `solicitud`, `cronograma`, `sala`,
`miembros_tribunal`), y recomendaba invocarlos vía `@Query(nativeQuery = true)` — que ni
siquiera es el mecanismo que la guía exige (`@Procedure` o `@NamedStoredProcedureQuery`).
Como nada de esto se había invocado nunca desde Java, el error nunca se manifestó (ver
`docs/observaciones/OBSERVACIONES.md`, OBS-03). Este documento se reescribe con la firma SQL
real, corregida, y **verificada end-to-end contra un backend corriendo en Docker** (no solo
leída del código) para cada uno de los 6 procedimientos originales, incluyendo los 2 nuevos que
completan las 5 categorías funcionales exigidas por la guía. **Actualización 2026-09-05:** se
añaden 4 rutinas más que existían en las migraciones y estaban conectadas o activas sin figurar
en este catálogo (ítems 7-10 más abajo) — el esquema real tiene 10 rutinas, no 6.

## 📌 Introducción y Estrategia Híbrida de Acceso a Datos

El sistema implementa una **estrategia de acceso a datos híbrida**:
1. **Spring Data JPA**: operaciones CRUD elementales sobre entidades individuales (`Usuario`, `Solicitud`, `Sala`, `Anteproyecto`, etc.) — columna `tipo_acceso = CRUD-ORM` en [`../trazabilidad/matriz.csv`](../trazabilidad/matriz.csv).
2. **Procedimientos y Funciones PL/pgSQL**: obligatorios para uniones multi-tabla, cálculos agregados, actualizaciones masivas, validaciones cruzadas y generación de códigos secuenciales — columna `tipo_acceso = SP`.

### 🛡️ Protección contra inyección SQL
Las 10 rutinas están parametrizadas nativamente (`IN`/`INOUT`), invocadas desde Java
mayoritariamente vía JPA 2.1 `@Procedure`/`@NamedStoredProcedureQuery` (9 de 10; la excepción,
`sp_obtener_estadisticas_tutores`, usa `@Query(nativeQuery = true)` sin parámetros — ver ítem 7)
— **cero** usos de
`createNativeQuery` con concatenación de cadenas en todo el backend (verificado con
[`../../scripts/audit-sql-dynamic.sh`](../../scripts/audit-sql-dynamic.sh), que además
rechaza automáticamente `EXECUTE IMMEDIATE`/`sp_executesql`/concatenación en cualquier
archivo `.sql` del repositorio).

### Nota técnica: por qué 4 de los 6 son `PROCEDURE` con `INOUT`/`REFCURSOR` y no `FUNCTION`

Probando la conexión real desde JPA se encontró que Hibernate invoca `@Procedure` y
`@NamedStoredProcedureQuery` con la sintaxis JDBC `{call proc(...)}` (CALL literal) — y
PostgreSQL **rechaza CALL para objetos `FUNCTION`** sin importar cuántos parámetros se
declaren (`"... is not a procedure. Hint: To call a function, use SELECT"`, o directamente
`"... does not exist"` si el número de parámetros no coincide). La única forma de que la
firma real en Postgres coincida con la sintaxis que Hibernate genera es declarar los
procedimientos como `PROCEDURE`:
- Retorno **escalar** (`sp_generar_codigo_expediente`, `sp_validar_conflicto_jurado`): parámetro `INOUT` para el valor de retorno.
- Retorno de **conjunto de filas** (`sp_calcular_promedio_evaluacion`, `sp_generar_reporte_defensas`): parámetro `INOUT ... refcursor`, mapeado en Java con `ParameterMode.REF_CURSOR` — y el método que lo invoca debe ser `@Transactional`, porque Postgres solo mantiene el cursor abierto dentro de la misma transacción que lo abrió.

---

## 📋 Catálogo de Procedimientos y Funciones

### 1. `sp_calcular_promedio_evaluacion` — categoría: cálculos agregados
* **Tipo:** `PROCEDURE` con parámetro `INOUT ... refcursor`
* **Propósito:** Calcula la nota final ponderada de una pre-sustentación (60% instructor + 40% promedio del tribunal por criterio de rúbrica) y persiste el resultado en `evaluaciones`.
* **Firma SQL real:**
```sql
CREATE OR REPLACE PROCEDURE presus.sp_calcular_promedio_evaluacion(
    IN p_solicitud_id BIGINT,
    INOUT p_resultado refcursor DEFAULT 'promedio_evaluacion_cursor'
)
```
* **Parámetros:** `p_solicitud_id` (IN, BIGINT) · `p_resultado` (INOUT, refcursor) — filas: `solicitud_id BIGINT`, `nota_final DOUBLE PRECISION`, `estado_resultado VARCHAR`.
* **Tablas que afecta:** lee `presus.evaluaciones_criterio` y `presus.evaluaciones`; escribe (`UPDATE`) `presus.evaluaciones`.
* **Invocación real desde Java** (`Evaluacion.java` + `EvaluacionRepository.java`):
```java
@NamedStoredProcedureQuery(
    name = "Evaluacion.calcularPromedioEvaluacion",
    procedureName = "presus.sp_calcular_promedio_evaluacion",
    resultSetMappings = "PromedioEvaluacionMapping",
    parameters = {
        @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_solicitud_id", type = Long.class),
        @StoredProcedureParameter(mode = ParameterMode.REF_CURSOR, name = "p_resultado", type = void.class)
    })
// repositorio:
@Procedure(name = "Evaluacion.calcularPromedioEvaluacion")
List<PromedioEvaluacionResult> calcularPromedioEvaluacion(@Param("p_solicitud_id") Long solicitudId);
```
* **Flujo real:** `EvaluacionServiceImpl.calcularPromedioSp()` (`@Transactional`), expuesto en `POST /api/v1/evaluaciones/{solicitudId}/calcular-promedio`.
* **Verificado real** (2026-08-17, contra Docker): `POST /api/v1/evaluaciones/1/calcular-promedio` → `200 {"solicitudId":1,"notaFinal":4.2,"estadoResultado":"REPROBADO"}`.
* **Prueba unitaria (2026-08-29):** `EvaluacionServiceImplTest` — cubre la fila base creada automáticamente si no existe, la reutilización si ya existe, y el caso en que el procedimiento no devuelve filas. Antes de esta fecha solo estaba verificado manualmente (brecha declarada explícitamente en `docs/mediciones/jacoco/COVERAGE.md`).

---

### 2. `sp_generar_reporte_defensas` — categoría: consultas multi-tabla
* **Tipo:** `PROCEDURE` con parámetro `INOUT ... refcursor`
* **Propósito:** Reporte consolidado de defensas por carrera, uniendo 6 tablas.
* **Firma SQL real:**
```sql
CREATE OR REPLACE PROCEDURE presus.sp_generar_reporte_defensas(
    IN p_carrera VARCHAR,
    INOUT p_resultado refcursor DEFAULT 'reporte_defensas_cursor'
)
```
* **Parámetros:** `p_carrera` (IN, VARCHAR) · `p_resultado` (INOUT, refcursor) — filas: `solicitud_id`, `estudiante_nombre`, `expediente`, `titulo_tema`, `estado_solicitud`, `fecha_defensa`, `sala_nombre`, `nota_final`.
* **Tablas que afecta (solo lectura):** `presus.solicitud`, `presus.estudiante`, `presus.usuarios`, `presus.estados_solicitud`, `presus.cronograma`, `presus.sala`, `presus.evaluaciones`.
* **Invocación real desde Java** (`Solicitud.java` + `SolicitudRepository.java`), mismo patrón `@NamedStoredProcedureQuery` + `ParameterMode.REF_CURSOR` que el anterior.
* **Flujo real:** `GET /api/v1/reportes/defensas?carrera=...` en `ReporteController` (`@Transactional(readOnly = true)`, requerido por el mismo motivo del refcursor).
* **Verificado real:** `GET /api/v1/reportes/defensas?carrera=Software` → `200`, con `expediente` y `notaFinal` ya calculados por los otros dos procedimientos, confirmando el cruce real entre los 6 SPs.
* **Prueba unitaria (2026-08-29):** `SolicitudServiceImplTest.testGenerarReporteDefensasSPMapeaCadaColumnaDeLaFilaCruda` — confirma que cada posición del `Object[]` crudo se mapea a la clave correcta (protege contra un cambio de orden de columnas en el SP que rompería el mapeo sin que ningún test lo detectara).

---

### 3. `sp_asignar_jurado_masivo` — categoría: actualizaciones masivas — ✅ conectado y probado
* **Tipo:** Procedimiento Almacenado PL/pgSQL (`PROCEDURE`)
* **Propósito:** Asigna (upsert) un docente como jurado de una solicitud, resolviendo el código de rol contra `roles_jurado`. La semántica "masiva" se logra invocándolo una vez por par `(solicitud, docente)` desde un único método de servicio `@Transactional`: si un par falla, se revierte también lo ya insertado del lote en esa misma llamada.
* **Corrección aplicada (14-18 ago 2026):** la versión original recibía `BIGINT[]` y escribía en una tabla `jurados(docente_id, solicitud_id, rol VARCHAR, ...)` que **no es la que usa la capa JPA real** — la entidad `Jurado.java` mapea a `miembros_tribunal(solicitud_id, docente_id, rol_jurado_id FK, ...)`. Se reescribió el procedimiento para escribir en `miembros_tribunal` resolviendo `rol_jurado_id`. También se detectó y corrigió que el `search_path` del rol de conexión (`JEAN`) no incluía el esquema `presus`, causando `relation "roles_jurado" does not exist` en tiempo de ejecución (`ALTER ROLE "JEAN" SET search_path TO presus, public;`).
* **Nota de fusión de ramas (18 ago 2026):** también se desarrolló en paralelo una variante con parámetros `BIGINT[]` (arreglo completo en una sola llamada). Se mantiene la firma escalar en el repositorio porque es la que quedó verificada end-to-end de forma reproducible, incluyendo un caso real de rollback (ver abajo); si se retoma la variante con arreglos, revalidar el binding de `Long[]` vía `@Procedure` antes de reemplazar esta.
* **Firma SQL real:**
```sql
CREATE OR REPLACE PROCEDURE presus.sp_asignar_jurado_masivo(
    p_solicitud_id BIGINT,
    p_docente_id BIGINT,
    p_rol_codigo VARCHAR
)
```
* **Parámetros:**
  - `p_solicitud_id` (BIGINT): ID de la solicitud.
  - `p_docente_id` (BIGINT): ID del docente.
  - `p_rol_codigo` (VARCHAR): código de `roles_jurado` (`'PRESIDENTE'`, `'VOCAL'`, `'SECRETARIO'`).
* **Tablas que afecta:** lee `presus.roles_jurado`; escribe (`INSERT ... ON CONFLICT DO UPDATE`) `presus.miembros_tribunal`.
* **Manejo de Errores:** `RAISE EXCEPTION` si el código de rol no existe en `roles_jurado`; la FK de `miembros_tribunal` rechaza `docente_id`/`solicitud_id` inexistentes.
* **Invocación real desde Java JPA** (`JuradoRepository.java`):
```java
@Procedure(procedureName = "sp_asignar_jurado_masivo")
void spAsignarJuradoMasivo(@Param("p_solicitud_id") Long solicitudId,
                            @Param("p_docente_id") Long docenteId,
                            @Param("p_rol_codigo") String rolCodigo);
```
Invocado desde `JuradoServiceImpl.asignarJuradoMasivo(List<Long>, List<Long>, String)`, anotado `@Transactional`, expuesto en `POST /api/v1/jurados/asignar-masivo` (roles `ADMIN`/`COORDINADOR`).
* **Prueba de control transaccional (verificada manualmente):** lote de 2 pares donde el primero es válido y el segundo viola la FK de `docente_id` → el `INSERT` del primer par se ejecuta pero, al fallar el segundo, Spring revierte la transacción completa; se confirmó que **ningún** registro del lote queda en `miembros_tribunal`.
* **Prueba unitaria (2026-08-29):** `JuradoServiceImplTest` — cubre el rechazo por longitud de arreglos distinta, que el procedimiento se invoca una vez por par, y que una excepción a mitad de lote detiene el `for` sin intentar los pares restantes (el rollback real de la fila ya insertada lo hace `@Transactional`, no el bucle Java).

---

### 4. `sp_firmar_acta_digital` — categoría: actualizaciones masivas
* **Tipo:** `PROCEDURE`
* **Propósito:** Firma digital multi-actor de actas, con auditoría de fecha y bitácora de observaciones por rol.
* **Firma SQL real:**
```sql
CREATE OR REPLACE PROCEDURE presus.sp_firmar_acta_digital(
    p_acta_id BIGINT,
    p_rol VARCHAR,
    p_observacion TEXT
)
```
* **Parámetros:** `p_acta_id` (IN, BIGINT) · `p_rol` (IN, VARCHAR — `PRESIDENTE`/`VOCAL_1`/`VOCAL_2`/`TUTOR`, alineado con la convención real del resto del backend) · `p_observacion` (IN, TEXT, opcional).
* **Tablas que afecta:** `UPDATE presus.actas` (columnas `firmada_*`, `fecha_firma_*`, y **agrega** una línea a `observaciones_acta` — un campo que la implementación Java anterior nunca escribía).
* **Invocación real desde Java** (`ActaRepository.java`):
```java
@Procedure(procedureName = "presus.sp_firmar_acta_digital")
void firmarActaDigital(@Param("p_acta_id") Long actaId, @Param("p_rol") String rol, @Param("p_observacion") String observacion);
```
* **Flujo real:** `ActaServiceImpl.firmarActa()` invoca el SP y luego `entityManager.refresh(acta)` para que el resto del flujo (cambio de estado a `COMPLETADA`, regeneración de PDF) vea lo que el procedimiento realmente persistió. Expuesto en `POST /api/v1/actas/firmar/{actaId}`.
* **Verificado real:** firma de PRESIDENTE → `200`, con `observacionesActa: "\n[PRESIDENTE]: Todo correcto"` confirmado en la respuesta.
* **Prueba unitaria (2026-08-29):** `ActaServiceImplTest` — cubre rol inválido, firma parcial (no completa la solicitud), firma completa (las 4 firmas → transición a `COMPLETADA` + regeneración real de PDF con iText contra un directorio temporal), y que un fallo en la notificación no interrumpe la firma.

---

### 5. `sp_validar_conflicto_jurado` — categoría: validaciones cruzadas *(nuevo, Fase 3)*
* **Tipo:** `PROCEDURE` con parámetro `INOUT` escalar
* **Propósito:** Antes de programar una defensa, verifica que ningún docente ya asignado como jurado de la solicitud tenga **otra** defensa programada en un horario que se solape — cruza `miembros_tribunal` con `cronograma`, dos tablas distintas.
* **Firma SQL real:**
```sql
CREATE OR REPLACE PROCEDURE presus.sp_validar_conflicto_jurado(
    IN p_solicitud_id BIGINT,
    IN p_docente_id BIGINT,
    IN p_fecha_inicio TIMESTAMP,
    IN p_duracion_min INTEGER,
    INOUT p_disponible BOOLEAN DEFAULT NULL
)
```
* **Tablas que afecta (solo lectura):** `presus.miembros_tribunal`, `presus.cronograma`.
* **Invocación real desde Java** (`Jurado.java` + `JuradoRepository.java`):
```java
@Procedure(name = "Jurado.validarConflictoJurado")
Boolean validarConflictoJurado(@Param("p_solicitud_id") Long solicitudId, @Param("p_docente_id") Long docenteId,
                                @Param("p_fecha_inicio") LocalDateTime fechaInicio, @Param("p_duracion_min") Integer duracionMin,
                                @Param("p_disponible") Boolean disponibleInicial);
```
* **Flujo real:** `CronogramaServiceImpl.crearCronograma()` lo llama para cada jurado ya asignado antes de guardar el cronograma; si algún docente tiene conflicto, se rechaza con un mensaje explícito.
* **Verificado real:** (a) creación de cronograma con 3 jurados sin conflictos previos → `200`; (b) prueba directa por SQL (`CALL` con un docente ya ocupado en un horario solapado) → `p_disponible = f`, confirmando también la rama de conflicto.
* **Prueba unitaria:** `CronogramaServiceImplTest.testCrearCronogramaFallaPorConflictoDeJurado` mockea la respuesta `Boolean.FALSE` del procedimiento y confirma que el servicio la traduce en el mensaje de conflicto esperado.

---

### 6. `sp_generar_codigo_expediente` — categoría: generación de códigos secuenciales *(nuevo, Fase 3)*
* **Tipo:** `PROCEDURE` con parámetro `INOUT` escalar
* **Propósito:** Genera el código de expediente de un estudiante (formato `EXP-<año>-NNNNN`) usando `nextval()` sobre una secuencia dedicada — atómico a nivel de motor, así que dos altas concurrentes nunca reciben el mismo código (a diferencia de calcular `MAX(id)+1` en Java, que sí tendría condición de carrera).
* **Firma SQL real:**
```sql
CREATE SEQUENCE IF NOT EXISTS presus.expediente_codigo_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE PROCEDURE presus.sp_generar_codigo_expediente(
    IN p_anio INTEGER,
    INOUT p_codigo VARCHAR DEFAULT NULL
)
```
* **Tablas que afecta:** ninguna tabla — solo la secuencia `presus.expediente_codigo_seq`.
* **Invocación real desde Java** (`Estudiante.java` + `EstudianteRepository.java`):
```java
@Procedure(name = "Estudiante.generarCodigoExpediente")
String generarCodigoExpediente(@Param("p_anio") Integer anio, @Param("p_codigo") String codigoInicial);
```
* **Flujo real:** `SolicitudServiceImpl.crearPerfilEstudiante()` lo llama al crear automáticamente el perfil de un estudiante nuevo.
* **Verificado real:** creación de solicitud de punta a punta → `expedienteCodigo: "EXP-2026-00001"` en la respuesta real del backend.
* **Prueba unitaria:** `SolicitudServiceImplTest.testCrearSolicitudPorUsuarioCreaPerfilEstudianteAutomaticamente` verifica que `crearPerfilEstudiante()` invoca `generarCodigoExpediente` y que el código devuelto por el procedimiento (no calculado en Java) queda en el estudiante guardado.

---

### 7. `sp_obtener_estadisticas_tutores` — categoría: consultas multi-tabla ⚠️ mecanismo pendiente de corregir
* **Tipo:** `FUNCTION` sin parámetros, `RETURNS TABLE` (definida en [`V10__actualizar_procedimientos_fase3.sql`](../../backend/src/main/resources/db/migration/V10__actualizar_procedimientos_fase3.sql))
* **Propósito:** Estadísticas consolidadas por tutor: tutorías activas, completadas y fases aprobadas, cruzando `docente`, `usuarios`, `tutores` y `tutoria_fases`.
* **Firma SQL real:**
```sql
CREATE OR REPLACE FUNCTION presus.sp_obtener_estadisticas_tutores()
RETURNS TABLE (
    tutor_docente_id BIGINT, tutor_nombre TEXT,
    tutorias_activas BIGINT, tutorias_completadas BIGINT, total_fases_aprobadas BIGINT
)
```
* **Tablas que afecta (solo lectura):** `presus.docente`, `presus.usuarios`, `presus.tutores`, `presus.tutoria_fases`.
* **Invocación real desde Java** (`TutorRepository.java`):
```java
@Query(value = "SELECT * FROM presus.sp_obtener_estadisticas_tutores()", nativeQuery = true)
List<Object[]> obtenerEstadisticasTutoresSp();
```
* **⚠️ Pendiente:** esta es la única de las 10 rutinas que **no** usa `@Procedure`/`@NamedStoredProcedureQuery` — sigue en `@Query(nativeQuery = true)`, que no es el mecanismo que exige la guía. No se convirtió en esta corrección porque cambiar a `@NamedStoredProcedureQuery` con `ParameterMode.REF_CURSOR` sobre una `FUNCTION RETURNS TABLE` (en vez de una `PROCEDURE` con `INOUT refcursor`, que es el único caso probado en este catálogo) requiere revalidar el mapeo contra un backend corriendo, y no fue posible ejecutar la suite de integración en esta pasada. Queda como tarea explícita, no como corrección silenciosa.
* **Flujo real:** `TutorServiceImpl.obtenerEstadisticasTutoresSP()` → `GET /api/v1/tutores/estadisticas` en `TutorController` (permiso `EVALUACION_RUBRICA_REGISTRAR`).
* **Prueba unitaria:** no confirmada en esta pasada — verificar si `TutorServiceImplTest` la cubre.

---

### 8. `sp_registrar_tutoria_avance` — categoría: actualizaciones masivas ✅ conectado y con mecanismo correcto
* **Tipo:** `PROCEDURE` (definida en [`V10__actualizar_procedimientos_fase3.sql`](../../backend/src/main/resources/db/migration/V10__actualizar_procedimientos_fase3.sql))
* **Propósito:** Valida y registra el avance de una fase de tutoría: rechaza si la tutoría ya está `COMPLETADA`, exige que la fase anterior esté `APROBADA` antes de aceptar la siguiente, e inserta o actualiza la fila de `tutoria_fases` correspondiente.
* **Firma SQL real:**
```sql
CREATE OR REPLACE PROCEDURE presus.sp_registrar_tutoria_avance(
    p_tutor_id BIGINT, p_numero_fase INT, p_archivo_pdf VARCHAR,
    p_tamano_bytes BIGINT, p_sha256 VARCHAR
)
```
* **Tablas que afecta:** lee y escribe (`INSERT`/`UPDATE`) `presus.tutoria_fases`; lee `presus.tutores`.
* **Invocación real desde Java** (`TutoriaFaseRepository.java`) — ya usa el mecanismo correcto:
```java
@org.springframework.data.jpa.repository.query.Procedure(procedureName = "presus.sp_registrar_tutoria_avance")
void spRegistrarTutoriaAvance(@Param("p_tutor_id") Long tutorId, @Param("p_numero_fase") Integer numeroFase,
                               @Param("p_archivo_pdf") String archivoPdf, @Param("p_tamano_bytes") Long tamanoBytes,
                               @Param("p_sha256") String sha256);
```
* **Flujo real:** `TutoriaServiceImpl.registrarAvanceSP()` → `POST /api/v1/tutorias/{tutorId}/registrar-avance` en `TutoriaController`.
* **Prueba unitaria:** no confirmada en esta pasada — verificar si `TutoriaServiceImplTest` la cubre.

---

### 9. `fn_auditoria_generica` — categoría: trigger de auditoría (no invocado desde Java)
* **Tipo:** `FUNCTION ... RETURNS TRIGGER` (definida en [`V15__auditoria.sql`](../../backend/src/main/resources/db/migration/V15__auditoria.sql))
* **Propósito:** Registra en `presus.auditoria` cada `INSERT`/`UPDATE`/`DELETE` sobre las tablas de mayor valor auditable (`usuarios`, `roles_usuario`, `permisos`, y por extensión el flujo de negocio crítico), guardando el antes/después completo de la fila vía `to_jsonb`. Nunca persiste el hash de contraseña. Identifica al actor leyendo el GUC de sesión `presus.usuario_actual`, fijado por `AuditoriaService.marcarActorActual()` antes de cada operación relevante.
* **No se invoca desde Java** — se dispara solo mediante los `CREATE TRIGGER` de `V15` (p. ej. `trg_auditoria_usuarios`, `trg_auditoria_roles_usuario`, `trg_auditoria_permisos`), por eso no aparece en ninguna columna `Tipo_Acceso = SP` de `matriz.csv`: es auditoría a nivel de motor, corre incluso si algo escribe directo a la base sin pasar por el backend.
* **Verificación:** disparar manualmente un `UPDATE presus.usuarios ...` y confirmar la fila resultante en `presus.auditoria`.

---

### 10. `fn_auditoria_rol_permisos` — categoría: trigger de auditoría (no invocado desde Java)
* **Tipo:** `FUNCTION ... RETURNS TRIGGER` (definida en [`V15__auditoria.sql`](../../backend/src/main/resources/db/migration/V15__auditoria.sql))
* **Propósito:** Variante de `fn_auditoria_generica` para `presus.rol_permisos`, cuya clave es compuesta (`rol_id`, `permiso_id`) sin columna `id`. Audita por `rol_id` (la unidad de negocio real: "a qué rol se le tocaron los permisos"), registrando `ASIGNAR_PERMISO`/`QUITAR_PERMISO` en `presus.auditoria`.
* **No se invoca desde Java** — se dispara solo mediante `trg_auditoria_rol_permisos` (`AFTER INSERT OR DELETE ON presus.rol_permisos`), definido en `V15`.
* **Verificación:** disparar manualmente un `INSERT`/`DELETE` sobre `presus.rol_permisos` y confirmar la fila resultante en `presus.auditoria` con acción `ASIGNAR_PERMISO`/`QUITAR_PERMISO`.

---

## 📊 Cobertura de las categorías funcionales exigidas por la guía

La guía enumera 6 categorías (multi-tabla, agregados, **reportes**, actualizaciones masivas,
validaciones cruzadas, códigos secuenciales); "reportes" y "consultas multi-tabla" se satisfacen
aquí con el **mismo** procedimiento porque `sp_generar_reporte_defensas` es, por definición, un
reporte que cruza 6 tablas — no hay dos procedimientos separados para esas dos filas, es una
única pieza de SQL sirviendo ambos requisitos, documentado así explícitamente para que quede
trazable en vez de implícito.

| Categoría (guía) | Procedimiento(s) | Estado | Prueba unitaria |
|---|---|---|---|
| Consultas multi-tabla / Reportes | `sp_generar_reporte_defensas` | ✅ Conectado y verificado | ✅ `SolicitudServiceImplTest` |
| Cálculos agregados | `sp_calcular_promedio_evaluacion` | ✅ Conectado y verificado | ✅ `EvaluacionServiceImplTest` |
| Actualizaciones masivas | `sp_asignar_jurado_masivo`, `sp_firmar_acta_digital` | ✅ Conectados y verificados | ✅ `JuradoServiceImplTest`, `ActaServiceImplTest` |
| Validaciones cruzadas | `sp_validar_conflicto_jurado` | ✅ Conectado y verificado (Fase 3) | ✅ `CronogramaServiceImplTest` |
| Generación de códigos secuenciales | `sp_generar_codigo_expediente` | ✅ Conectado y verificado (Fase 3) | ✅ `SolicitudServiceImplTest` |

**Total real en el esquema: 10 rutinas** — 6 documentadas arriba desde el inicio (1-6, todas conectadas
desde Java vía JPA 2.1 con `@Procedure`/`@NamedStoredProcedureQuery`, verificadas end-to-end contra
Docker y con prueba unitaria dedicada) + 4 que esta actualización (2026-09-05) añade al catálogo por
estar conectadas y sin documentar: `sp_obtener_estadisticas_tutores` y `sp_registrar_tutoria_avance`
(7-8, ambas invocadas desde Java — la primera todavía vía `@Query(nativeQuery = true)`, mecanismo
pendiente de corregir; ver ítem 7) y `fn_auditoria_generica`/`fn_auditoria_rol_permisos` (9-10,
triggers de motor que no se invocan desde Java, por lo que no tienen fila en `matriz.csv`).

**Nota de conteo (P11, evaluación integral 2026-09-17):** "10 rutinas" cuenta por **nombre** distinto,
que es lo que invoca Java. El esquema materializado real (`database/esquema.sql`) tiene **13 objetos**
(`grep -c "^CREATE \(OR REPLACE \)\?\(PROCEDURE\|FUNCTION\)" database/esquema.sql` → 13; 8 `PROCEDURE` +
5 `FUNCTION`), porque 3 de los 10 nombres conservan una sobrecarga adicional sin uso, documentada arriba
y en la advertencia "Objetos duplicados" al inicio de este archivo: las `FUNCTION` huérfanas de un
parámetro de `sp_calcular_promedio_evaluacion` y `sp_generar_reporte_defensas` (`V10`), y la variante
`BIGINT[]` de `sp_asignar_jurado_masivo` (nota de fusión de ramas, ítem 3). Ninguna de las tres se
invoca desde Java — PostgreSQL las resuelve por firma exacta y el código siempre llama la variante
documentada arriba —, así que no cuentan como rutinas adicionales "activas", pero sí son objetos reales
en el esquema y deben citarse como tales si se referencia el esquema (no el código Java) como fuente.
Antes de esta fecha, `sp_calcular_promedio_evaluacion` y `sp_generar_reporte_defensas` solo
estaban verificados manualmente (brecha que declaraba explícitamente `docs/mediciones/jacoco/COVERAGE.md`);
`sp_asignar_jurado_masivo` tampoco tenía prueba dedicada pese a que `JuradoServiceImplTest` ya
existía para otras responsabilidades de esa clase. Supera el mínimo de 6 exigido por el criterio P1.

**Pendiente explícito (no resuelto en esta corrección):** decidir cuál de las dos rutas paralelas
que calculan el promedio de evaluación se conserva — `EvaluacionServiceImpl.calcularPromedioSp()`
(expuesto en `POST /api/v1/evaluaciones/calcular-promedio/{solicitudId}`, sin llamadores en código
de producción, solo en tests) vs. `calcularPromedioSP()` (con P mayúscula, el que sí expone el
endpoint real de negocio). Ambas resuelven contra objetos SQL distintos (la `PROCEDURE` de dos
parámetros de `V2`/`V3` vs. las `FUNCTION` huérfanas de un parámetro de `V10`, ver advertencia al
inicio de este documento) y mantener las dos es confuso para quien audite el código. Requiere
decidir con el equipo cuál eliminar, no es un cambio mecánico.
