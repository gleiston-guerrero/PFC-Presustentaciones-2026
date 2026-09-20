# ROLES-PRIVILEGIOS.md — Gestión de usuarios, roles y privilegios (nivel motor de BD)

## Qué existía antes de esto

Autorización de **aplicación** (RBAC vía tabla `roles_usuario`, `@PreAuthorize` en los
controllers, JWT — ver ADR-004) — sólida, pero orientada a "qué puede hacer un usuario
en la API". A nivel de **motor PostgreSQL**, todo el sistema se conectaba con un único
usuario con privilegios de superusuario (`DB_USERNAME`/`DB_PASSWORD` en `.env`, ver
`docker-compose.yml`). No existían roles diferenciados en el motor: cualquier conexión
podía crear, alterar o borrar cualquier tabla del esquema `presus`.

## Qué agrega esta migración

[`V11__roles_y_privilegios.sql`](../../backend/src/main/resources/db/migration/V11__roles_y_privilegios.sql)
crea dos roles de PostgreSQL con privilegios distintos según responsabilidad:

| Rol | Uso previsto | Privilegios |
|---|---|---|
| `presus_app` | Conexión del backend (Spring Boot) en tiempo de ejecución | `SELECT/INSERT/UPDATE/DELETE` en todas las tablas de `presus`, `EXECUTE` en funciones/procedimientos, uso de secuencias. **Sin DDL**: no puede `CREATE`/`ALTER`/`DROP` tablas. |
| `presus_readonly` | Reportería, BI, auditoría externa | Sólo `SELECT` en todas las tablas de `presus`. No puede insertar, actualizar, borrar ni ejecutar DDL. |
| (el rol de `DB_USERNAME` actual, ej. superusuario) | Migraciones de Flyway / administración de esquema | Sin cambios — sigue siendo el único con privilegios de DDL, ya que es quien ejecuta `V1`...`V5` al arrancar la aplicación. |

`ALTER DEFAULT PRIVILEGES` garantiza que las tablas/funciones creadas por migraciones
**futuras** (V12+) hereden automáticamente estos mismos privilegios para `presus_app` y
`presus_readonly`, sin necesidad de volver a otorgar permisos a mano.

Las contraseñas del archivo de migración (`presusAppDemo2026`, `presusReadonlyDemo2026`)
son credenciales de **desarrollo/demo**, al mismo nivel que `POSTGRES_PASSWORD` en
`docker-compose.yml`. Para producción real se rotarían y gestionarían como secretos
(mismo criterio que se aplica a `JWT_SECRET`, ver `.env.example`).

## Verificación (ejecutada 2026-08-21 contra el volumen de desarrollo real)

Migración aplicada sin errores contra la base de datos de desarrollo existente
(`presus`, con el rol administrador real del proyecto). Se comprobó el comportamiento
real de los dos roles nuevos:

```
presus_readonly → SELECT count(*) FROM roles_usuario;        →  4 (OK)
presus_readonly → INSERT INTO usuarios (...);                 →  ERROR: permission denied for table usuarios
presus_app      → INSERT INTO facultades (...) RETURNING id;  →  OK (fila creada y luego eliminada)
presus_app      → CREATE TABLE presus.tabla_intrusa (id int); →  ERROR: permission denied for schema presus
```

Confirma que la separación de privilegios funciona exactamente como se documenta arriba.

## Guion de demostración en vivo (para la sustentación)

Con el contenedor de PostgreSQL corriendo (`docker compose up -d postgres`):

```bash
# 1. Mostrar los roles existentes y que ya no hay un único superusuario para todo
docker exec -it amz-postgres psql -U <admin> -d BdPresustentaciones -c "\du"

# 2. Conectarse como rol de solo lectura y demostrar que SÍ puede leer
docker exec -it -e PGPASSWORD=presusReadonlyDemo2026 amz-postgres \
  psql -U presus_readonly -d BdPresustentaciones -c "SELECT count(*) FROM usuarios;"

# 3. Demostrar que el rol de solo lectura NO puede escribir (error esperado, es el punto)
docker exec -it -e PGPASSWORD=presusReadonlyDemo2026 amz-postgres \
  psql -U presus_readonly -d BdPresustentaciones -c "DELETE FROM usuarios WHERE id=1;"

# 4. Conectarse como el rol de la aplicación y demostrar que sí puede escribir datos...
docker exec -it -e PGPASSWORD=presusAppDemo2026 amz-postgres \
  psql -U presus_app -d BdPresustentaciones -c "SELECT count(*) FROM solicitud;"

# 5. ...pero NO puede alterar la estructura del esquema (error esperado, es el punto)
docker exec -it -e PGPASSWORD=presusAppDemo2026 amz-postgres \
  psql -U presus_app -d BdPresustentaciones -c "DROP TABLE presus.usuarios;"
```

Los pasos 3 y 5 deben terminar en `ERROR: permission denied` — ese error **es** la
evidencia de que los privilegios están correctamente separados por responsabilidad.

## Decisión consciente: el backend sigue conectándose con el rol administrador

Por el tiempo disponible antes de la sustentación, **no se reconectó el backend en
ejecución** para usar `presus_app` como su credencial real (eso implicaría separar la
conexión de Flyway —que necesita DDL— de la conexión de JPA/Hibernate —que no debería
necesitarlo— en `application.properties`, y probarlo de punta a punta). Los roles y
privilegios están creados, aplicados y verificados a nivel de motor de base de datos,
pero la aplicación en ejecución todavía no los usa por defecto. Se documenta como
mejora pendiente, no se oculta ni se presenta como ya integrado.
