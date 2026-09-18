# Tabla de retención y supresión de datos personales

**Base legal general:** Ley Orgánica de Protección de Datos Personales del Ecuador (LOPDP).
**Ámbito:** todas las categorías de dato personal que trata `Sistema de Pre-Sustentaciones UTEQ`.
Documento exigido por RNF-19 (`docs/requisitos/SRS-v1.0.1.tex`) y referenciado desde
[`ETHICS.md`](ETHICS.md).

## Tabla de retención

| Categoría de dato | Ejemplos de campos | Finalidad | Base legal | Período de conservación | ¿Suprimible a solicitud del titular? |
|---|---|---|---|---|---|
| Datos de cuenta | `nombre`, `apellido`, `email`, `telefono`, `rol` | Identificación y acceso al sistema | Ejecución de la relación académica (LOPDP art. 7) | Mientras la cuenta esté activa | **Sí, por seudonimización** — ver §2. No se borra la fila (rompería el expediente enlazado); se reemplazan los campos identificables. |
| Expediente académico | código de expediente, carrera, semestre, historial de estados de la solicitud/anteproyecto | Constancia del proceso de titulación | Obligación legal (normativa de educación superior sobre registros académicos) | **Permanente** | **No.** Es justo lo que la normativa obliga a conservar; seudonimizar la cuenta del titular no borra el expediente, solo deja de identificarlo por nombre. |
| Calificaciones y actas | notas, actas de sustentación firmadas | Constancia legal del resultado de titulación | Obligación legal | **Permanente** | **No.** Documento con valor legal — mismo criterio que ya declara `docs/basedatos/PLAN-RESPALDOS-RECUPERACION.md` §1.3 para estas tablas. |
| Bitácora de auditoría (`presus.auditoria`) | tabla, registro afectado, acción, usuario, fecha, diff antes/después (sin contraseña, RNF-18) | Trazabilidad y seguridad de la información | Interés legítimo (seguridad, RNF-18) | **2 años** desde la fecha del evento | No individualmente (borrarla a demanda violaría RNF-18); se depura **automáticamente** por antigüedad, nunca por solicitud puntual — ver §3. |
| Archivos cargados | anteproyectos PDF, PDF de correcciones de tutoría | Soporte documental del proceso de titulación | Ejecución de la relación académica | Mientras exista el expediente asociado | **No**, independientemente del expediente — están enlazados a él por el mismo motivo que las calificaciones. |
| Notificaciones | mensajes internos del sistema | Aviso operativo | Interés legítimo | 6 meses (declarado; **sin depuración automática construida todavía** — ver nota) | Sí, de bajo riesgo — no requieren procedimiento especial. |
| Consentimientos informados firmados | formularios de evaluación de usabilidad (SUS) | Constancia de consentimiento explícito | Consentimiento del participante | **Mínimo 2 años** | No durante el período de conservación. |

## 1. Qué NO es suprimible, y por qué

El expediente académico, las calificaciones y las actas de sustentación tienen valor legal: la
normativa de educación superior obliga a la institución a poder acreditar, años después, que un
estudiante concreto sustentó y aprobó (o no) su trabajo de titulación. Suprimir esa información a
solicitud del titular pondría a la universidad en incumplimiento de una obligación distinta y de
mayor jerarquía que el derecho de supresión. Es exactamente el caso que la LOPDP contempla como
excepción al derecho de supresión cuando existe una obligación legal de conservación.

## 2. Procedimiento de supresión (RF: `POST /api/usuarios/{id}/solicitar-supresion`)

Para reconciliar el derecho de supresión con la obligación de conservar el expediente, la
resolución aceptada **seudonimiza**, no borra:

- `nombre`, `apellido` → marcador no identificable (`"Usuario suprimido"`, `"#<id>"`).
- `email` → `suprimido-<id>@presustentaciones.invalid` (deja de ser una dirección real, pero
  sigue siendo único y válido como clave de fila).
- `telefono` → `NULL`.
- La cuenta se desactiva (`activo = false`).

El **id** del usuario nunca cambia, así que el expediente académico (solicitudes, actas,
evaluaciones) sigue enlazado correctamente — solo deja de estar asociado a un nombre o correo
identificable. Implementación: `ErasureDataService#resolver` (backend).

**Paradoja resuelta:** el registro de la propia solicitud (`presus.solicitud_supresion`) no
guarda ningún dato personal del titular, solo su `usuario_id` — que sigue siendo una referencia
válida después de la seudonimización, no el dato suprimido en sí. Ver
`SupresionDatosServiceTest#elRegistroDeLaSolicitudNuncaContieneElDatoSuprimido`.

Una solicitud puede **rechazarse** en vez de aceptarse (p. ej. si el titular tiene un proceso de
titulación en curso que exige poder identificarlo mientras dure) — el rechazo también queda
registrado, con motivo, pero no toca la cuenta.

## 3. Depuración automática de la bitácora (RNF-18 ↔ RNF-19)

**La tensión real:** RNF-18 exige que la bitácora de auditoría no sea modificable ni eliminable
**desde la aplicación** (ninguna operación de la API puede tocarla). RNF-19 exige depurar
automáticamente lo que exceda el período de retención declarado arriba (2 años). Una vía de
borrado expuesta como endpoint rompería RNF-18 para cerrar RNF-19.

**Resolución:** la depuración corre como tarea programada del sistema
(`DepuracionBitacoraScheduler`, `@Scheduled`, sin ningún controlador ni endpoint asociado),
igual que `BackupScheduler` ya hace para la retención de respaldos. No es una vía por la que un
usuario -ni siquiera un Administrador vía API- pueda borrar entradas a demanda: es la política de
retención, ya declarada en este documento, aplicándose sola, mensualmente. Cada corrida deja
traza verificable en `presus.depuracion_bitacora_log` (cuántas entradas borró, hasta qué fecha) —
consultable directamente en la base, no vía API, por el mismo motivo que la propia bitácora no lo
es.

## 4. Lo que esta fase deliberadamente no construyó

- **Depuración automática de notificaciones** (6 meses declarados arriba): el criterio de RNF-19
  que sí exige automatización es el de la bitácora; se prioriza ese. Queda declarado el período,
  no construida la tarea — no se marca como si ya existiera.
- **Activación de un envío real de correo para notificar al titular** cuando su solicitud de
  supresión se resuelve: no forma parte del criterio de aceptación de RNF-19 tal como está
  redactado en el SRS, y se deja fuera para no ampliar el alcance de esta fase sin que el
  requisito lo pida.
