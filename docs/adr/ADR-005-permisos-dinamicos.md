# ADR-005: Control de Acceso Basado en Permisos Dinámicos (Base de Datos), no Roles Estáticos en Código

**Estado:** Aceptado
**Fecha:** 2026-08-16 (decisión tomada en el código; documentada como ADR el 2026-08-29 durante la
auditoría de reproducibilidad, al encontrar que no tenía registro propio pese a ser una decisión
arquitectónica real y ya implementada).
**Decisores:** Equipo de Seguridad y Backend UTEQ

## Contexto

ADR-004 (Seguridad OWASP) declaraba como control #1 "Control de acceso RBAC estricto en backend
(`@PreAuthorize`)", describiendo el enfoque original: cada endpoint anotado con
`@PreAuthorize("hasRole('ADMIN')")` o similar, con los roles permitidos fijos en el código fuente.
Este enfoque tiene un problema operativo real: cambiar qué rol puede hacer qué cosa (p. ej. permitir
que un Coordinador también gestione usuarios) requiere modificar código Java, recompilar y
redesplegar — no es viable para un sistema donde la universidad espera poder ajustar permisos desde
la propia aplicación ("Gestionar Roles" / "Gestionar Permisos", ya construidas en el frontend).

## Decisión

Reemplazar los `@PreAuthorize("hasRole(...)")`/`hasAnyRole(...)` fijos por un único punto de
verificación dinámico: `@PreAuthorize("@permisoService.tienePermiso(authentication, 'CODIGO_PERMISO')")`
en cada endpoint protegido. `PermissionService.tienePermiso()` consulta directamente
`presus.rol_permisos` ⋈ `presus.permisos` ⋈ `presus.usuarios` (ver
`PermissionRepository.usuarioTienePermiso`, SQL nativo) contra el email del usuario autenticado y el
código de permiso exigido — no contra el JWT, que deliberadamente **no** lleva permisos, solo
identidad, para que un cambio de permisos aplique de inmediato sin esperar a que el usuario vuelva a
iniciar sesión.

Migraciones Flyway: `V11__roles_y_privilegios.sql` (roles de conexión a BD, no confundir con roles de
aplicación), `V13__permisos_roles_dinamicos.sql` (tablas `permisos`/`rol_permisos`, semilla de 18
permisos), `V14` (ajustes). Endpoints reales: `PermissionController` (CRUD de permisos) y `RoleController`
(asignación de permisos a roles), consumidos por los módulos "Gestionar Roles"/"Gestionar Permisos"
del frontend.

## Consecuencias

- **Positivas:** cambiar qué rol tiene qué permiso es una operación de datos (vía la propia UI de
  administración), no un despliegue de código; un único punto de verificación (`PermissionService`)
  reemplaza decenas de anotaciones dispersas, reduciendo el riesgo de que un endpoint nuevo se
  olvide de proteger correctamente; auditable — `permisos`/`rol_permisos` son tablas normales,
  consultables y versionadas por Flyway, no constantes en el bytecode.
- **Negativas:** cada verificación de autorización ahora implica una consulta SQL adicional (`EXISTS`
  con 3 joins) en vez de una comparación en memoria contra el `Authentication` del contexto de
  seguridad — coste de rendimiento aceptado sin medirse formalmente en esta fase (no hay benchmark
  dedicado comparando ambos enfoques, a diferencia de la comparación caché fría/caliente de
  `k6/README.md`); un typo en el string `'CODIGO_PERMISO'` de un `@PreAuthorize` falla en tiempo de
  ejecución (deniega acceso), no en compilación, a diferencia de `hasRole()` con una constante Java.
- **Corrección de ADR-004 (2026-08-29):** el control #1 de ADR-004 ("`@PreAuthorize` con roles
  estáticos") ya no describe el estado real del código — el mecanismo vigente es el de esta ADR.
  ADR-004 no se reescribe (el registro histórico de la decisión original se conserva), pero se
  referencia aquí para que la documentación completa quede trazable.

Ver también: `docs/mediciones/sec/owasp/OWASP-AUDIT.md` (control A01, menciona el patrón de
verificación de propiedad además de rol/permiso).
