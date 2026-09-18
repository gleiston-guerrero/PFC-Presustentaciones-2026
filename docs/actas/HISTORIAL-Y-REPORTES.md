# Módulo de Reportes + Gestión e Historial de Actas

> Añadido en la rama `feature/reportes-y-historial-actas`. Migración **V19**.
> Extiende lo existente (no reemplaza): el flujo de firma del acta, la auditoría
> genérica de V15 y el `ReportController` de PDFs siguen igual.

## 1. Reportes (COORDINADOR / ADMINISTRADOR)

Permiso: **`REPORTES_VER`** (id 18, de V13 — ADMIN y COORDINADOR ya lo tenían).
Todo se calcula con `COUNT` / `GROUP BY` en PostgreSQL vía JPQL — nunca se cargan
tablas completas en memoria. Se añadió `ReportService` / `ReportServiceImpl`
y se **extendió** `ReportController` (los endpoints PDF previos no se tocaron).

| Método | Endpoint | Descripción | Filtros |
|---|---|---|---|
| GET | `/api/v1/reportes/resumen` | Resumen general (KPIs + series) | `desde`, `hasta`, `carrera` |
| GET | `/api/v1/reportes/solicitudes-por-estado` | Solicitudes agrupadas por estado | `desde`, `hasta`, `carrera` |
| GET | `/api/v1/reportes/sustentaciones-por-periodo` | Pre-sustentaciones por período académico | `desde`, `hasta` |
| GET | `/api/v1/reportes/actas` | Actas por estado + pendientes de firma | `desde`, `hasta` |
| GET | `/api/v1/reportes/actividad-docente` | Por docente: como jurado, como tutor, actas firmadas | — |
| GET | `/api/v1/reportes/por-carrera` | Total / completadas / rechazadas por carrera | — |

Fechas en formato ISO `yyyy-MM-dd`. Un docente/estudiante recibe **403**.

Frontend: `components/reportes/reportes.component.ts` → ruta `/dashboard/reportes`
(`roleGuard(['ADMIN','COORDINADOR'])`). Barras CSS puras + tablas; reutiliza los
botones de descarga de PDF ya existentes.

## 2. Gestión e historial de actas

### 2.1 Estado del acta (nuevo)

El acta no tenía estado explícito (se derivaba de `firmada_*`). V19 agrega el
catálogo **`presus.estados_acta`** y la columna **`presus.actas.estado_id`**
(mismo patrón que `estados_solicitud` / `estados_academicos`).

```
GENERADA ─▶ REVISADA ─▶ FINALIZADA
   │           │
   ├─▶ OBSERVADA ◀─┘   (OBSERVADA ─▶ REVISADA | GENERADA)
   └────────────────▶ ANULADA   (terminal; el ADMIN puede anular desde cualquier estado)
```

- Backfill de V19: acta con `firmada = true` → **FINALIZADA**; el resto → **GENERADA**.
- Cuando la última firma completa el acta, `MinutesServiceImpl.firmarActa` la pasa a
  **FINALIZADA** y registra el evento (además de dejar la solicitud en `COMPLETADA`,
  que ya hacía).
- `OBSERVADA` y `ANULADA` exigen **motivo**.

### 2.2 Endpoints (extienden `MinutesController`, prefijo `/api/v1/actas`)

| Método | Endpoint | Permiso | Notas |
|---|---|---|---|
| GET | `/mis-actas` | `ACTAS_VER_PROPIAS` (DOCENTE, COORD, ADMIN) | Solo actas donde el usuario es tutor o jurado. Paginado. |
| GET | `/buscar` | `ACTAS_VER` (COORD, ADMIN) | Filtros: `estado`, `carrera`, `desde`, `hasta`, `q`. Paginado. |
| GET | `/{id}` | `isAuthenticated()` + control en el service | Detalle. Un participante no autorizado recibe **403** (anti-IDOR). |
| GET | `/{id}/historial` | `ACTA_HISTORIAL_VER` (DOCENTE, COORD, ADMIN) | Timeline. Mismo control de acceso que el detalle. |
| PATCH | `/{id}/estado` | `ACTA_ESTADO_CAMBIAR` (COORD, ADMIN) | Body `{ nuevoEstado, motivo }`. Valida la transición. |

Endpoints previos (`/generar`, `/firmar`, `/descargar`, `/ver`, `GET /`,
`/solicitud/{id}`, `DELETE /{id}`) **sin cambios**. Se corrigió de paso que el
permiso `ACTAS_VER` (usado por `GET /`) no existía en el catálogo: V19 lo crea
y lo asigna a ADMIN y COORDINADOR.

### 2.3 Control de acceso (backend, no solo UI)

`MinutesServiceImpl.validarAcceso(acta)`:
1. `ROLE_ADMIN` → acceso total.
2. `ACTAS_VER` / `ACTAS_GESTIONAR` (permiso dinámico) → COORDINADOR/ADMIN.
3. En otro caso: solo si el usuario es el **estudiante dueño**, un **jurado** o el
   **tutor** de esa solicitud. Si no, `RuntimeException` → el controller responde **403**.

Ejemplo bloqueado: `GET /api/v1/actas/100` cuando el acta 100 es de otro docente → 403
aunque se conozca el ID.

## 3. Historial / auditoría (trazabilidad persistente)

Dos capas, ambas en PostgreSQL:

1. **`presus.historial_estados_acta`** (V19) — timeline de dominio que escribe
   `MinutesServiceImpl`. Columnas: `acta_id`, `estado_anterior_id`, `estado_nuevo_id`,
   `usuario_id`, `rol_usuario`, `accion` (`CREAR` / `CAMBIO_ESTADO` / `FIRMA_COMPLETA`),
   `comentario`, `fecha_cambio`. FKs a `actas`, `estados_acta`, `usuarios`.
   V19 siembra un evento `CREAR` por cada acta existente.
2. **`presus.auditoria`** (V15) — el trigger `trg_auditoria_actas` sigue capturando
   todo `INSERT/UPDATE/DELETE` sobre `actas` con `to_jsonb(OLD/NEW)`, como respaldo a
   nivel de base si algo escribe sin pasar por el backend.

Ejemplo real de respuesta de `GET /api/v1/actas/2/historial`:

```json
[
  { "accion": "CAMBIO_ESTADO", "estadoAnterior": "FINALIZADA", "estadoNuevo": "ANULADA",
    "usuarioEmail": "admin@uteq.edu.ec", "rolUsuario": "ADMIN",
    "comentario": "...", "fecha": "2026-09-01T21:50:58" },
  { "accion": "CREAR", "estadoAnterior": null, "estadoNuevo": "FINALIZADA",
    "usuarioNombre": "Sistema", "comentario": "Registro histórico ... V19",
    "fecha": "2026-08-21T00:00:00" }
]
```

Frontend: `components/actas/historial-acta/historial-acta.component.ts` (timeline),
ruta `/dashboard/actas/:id/historial`.

## 4. Permisos nuevos (V19)

| id | código | ADMIN | COORDINADOR | DOCENTE |
|----|--------|:---:|:---:|:---:|
| 22 | `ACTAS_VER` | ✔ | ✔ | |
| 23 | `ACTAS_VER_PROPIAS` | ✔ | | ✔ |
| 24 | `ACTA_HISTORIAL_VER` | ✔ | ✔ | ✔ |
| 25 | `ACTA_ESTADO_CAMBIAR` | ✔ | ✔ | |
| 26 | `ACTAS_GESTIONAR` | ✔ | | |

Editables desde *Gestionar Permisos* (sistema dinámico de V13).

## 5. Navegación

| Rol | Entradas nuevas en el dashboard |
|---|---|
| DOCENTE | **Mis Actas** (`/dashboard/actas/mis-actas`) |
| COORDINADOR | **Reportes**, **Actas** (`/dashboard/actas/gestion`) |
| ADMINISTRADOR | **Reportes**, **Gestión de Actas** |

Todas protegidas con `roleGuard` en el frontend **y** con `@PreAuthorize` +
control por propiedad en el backend.

## 6. Tests

- `ReportServiceImplTest` (nuevo, 4 casos): mapeo `Object[]`→DTO, relleno de estados
  faltantes con 0, combinación de actividad por docente, cálculo de totales/en proceso.
- `MinutesServiceImplTest` (ampliado, +7 casos, se conservan los 15 previos):
  IDOR en `obtenerDetalle`, acceso de COORDINADOR vía permiso, `cambiarEstado` registra
  historial, transición no permitida, motivo obligatorio, historial ordenado,
  delegación de `listarMisActas`.
- Suite completa: **129 → 140 tests, 0 fallos**.
