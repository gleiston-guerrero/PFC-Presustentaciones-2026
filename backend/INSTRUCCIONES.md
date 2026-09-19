# 🔐 Proyecto UTEQ Pre-Sustentaciones - Spring Security + JWT

## ✅ ¿Qué se ha corregido?

Este es tu proyecto COMPLETO con Spring Security y JWT funcionando correctamente.

### Archivos Corregidos:

**13 Repositorios** (UUID → Long):
- MinutesRepository
- ProposalRepository
- ScheduleRepository
- TeacherRepository
- StudentRepository
- EvaluationRepository
- PanelistRepository
- NotificationRepository
- RubricRepository
- RoomRepository
- SubmissionRepository
- TutorRepository
- AppUserRepository

**6 Servicios** (UUID → Long, campos corregidos):
- IAppUserService + AppUserServiceImpl
- NotificationService + NotificationServiceImpl
- SubmissionService + SubmissionServiceImpl

**1 Controlador**:
- AppUserController (UUID → Long)

**Archivos de Seguridad Nuevos** (7 archivos):
- `security/jwt/JwtTokenProvider.java` - Generación y validación JWT
- `security/jwt/JwtAuthenticationFilter.java` - Filtro de autenticación
- `security/service/CustomUserDetailsService.java` - Carga de usuarios
- `security/dto/LoginRequest.java` - DTO de login
- `security/dto/LoginResponse.java` - DTO de respuesta
- `config/SecurityConfig.java` - Configuración de seguridad
- `controllers/AuthController.java` - Endpoints de autenticación

---

## 🚀 Cómo Usar Este Proyecto

### Paso 1: Abrir en tu IDE

1. **Extrae el ZIP completo**
2. **Abre tu IDE** (IntelliJ, Eclipse, VS Code)
3. **Importa el proyecto** como proyecto Maven existente
4. **Espera** a que descargue las dependencias (1-2 minutos)

### Paso 2: Verificar PostgreSQL

Asegúrate de que PostgreSQL esté corriendo y que la base de datos exista:

```sql
CREATE DATABASE presusDb;
```

La configuración en `application.properties` es:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/presusDb
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

> **Las credenciales no se escriben aqui.** `DB_USERNAME` y `DB_PASSWORD` salen de `.env`, que no esta
> versionado; copia [`.env.example`](../.env.example) a `.env` y pon ahi los valores de tu maquina.
> Docker Compose lo carga solo, y el target `test` del `Makefile` tambien.
>
> Una version anterior de este archivo traia la contrasena de la base local escrita en claro. Era una
> credencial de desarrollo, no de produccion, pero una credencial escrita en un repositorio publico se
> lee como una credencial: el lector no tiene forma de saber que no lo es. Se retira por eso.

### Paso 3: Ejecutar la Aplicación

1. **Ejecuta** `PreSustentacionesApplication.java`
2. **Espera** a ver: `Started PreSustentacionesApplication in X.XXX seconds`
3. ✅ **La aplicación está lista!**

---

## 📮 Probar en Postman

### 1️⃣ Registrar Usuario

**POST** `http://localhost:8080/api/auth/register`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
    "nombre": "Juan",
    "apellido": "Pérez",
    "email": "juan.perez@uteq.edu.ec",
    "password": "password123",
    "rol": "ESTUDIANTE"
}
```

**Respuesta:**
```
"Usuario registrado exitosamente"
```

---

### 2️⃣ Login

**POST** `http://localhost:8080/api/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
    "email": "juan.perez@uteq.edu.ec",
    "password": "password123"
}
```

**Respuesta:**
```json
{
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "type": "Bearer",
    "id": 1,
    "email": "juan.perez@uteq.edu.ec",
    "nombre": "Juan Pérez",
    "rol": "ESTUDIANTE"
}
```

⚠️ **COPIA EL TOKEN!**

---

### 3️⃣ Usar Endpoint Protegido

**GET** `http://localhost:8080/api/usuarios`

**Headers:**
```
Authorization: Bearer TU_TOKEN_AQUI
```

**Respuesta:**
```json
[
    {
        "id": 1,
        "nombre": "Juan",
        "apellido": "Pérez",
        "email": "juan.perez@uteq.edu.ec",
        "rol": "ESTUDIANTE",
        "activo": true
    }
]
```

---

## 📁 Estructura del Proyecto

```
presustentaciones-uteq-fixed/
├── pom.xml                          (Con Spring Security + JWT)
├── application.properties           (Con configuración JWT)
├── src/main/java/ec/edu/uteq/presustentaciones/
│   ├── controllers/
│   │   ├── AuthController.java     ⭐ NUEVO
│   │   ├── AppUserController.java  ✅ CORREGIDO
│   │   └── ... (otros 6 controladores originales)
│   ├── services/
│   │   ├── IAppUserService.java         ✅ CORREGIDO
│   │   ├── AppUserServiceImpl.java      ✅ CORREGIDO
│   │   ├── NotificationService.java     ✅ CORREGIDO
│   │   ├── NotificationServiceImpl.java ✅ CORREGIDO
│   │   ├── SubmissionService.java        ✅ CORREGIDO
│   │   ├── SubmissionServiceImpl.java    ✅ CORREGIDO
│   │   └── ... (otros servicios originales)
│   ├── repositories/
│   │   ├── AppUserRepository.java       ✅ CORREGIDO (13 en total)
│   │   └── ... (todos corregidos UUID → Long)
│   ├── entities/
│   │   └── ... (13 entidades originales SIN cambios)
│   ├── security/                    ⭐ CARPETA NUEVA
│   │   ├── jwt/
│   │   │   ├── JwtTokenProvider.java           ⭐ NUEVO
│   │   │   └── JwtAuthenticationFilter.java    ⭐ NUEVO
│   │   ├── service/
│   │   │   └── CustomUserDetailsService.java   ⭐ NUEVO
│   │   └── dto/
│   │       ├── LoginRequest.java               ⭐ NUEVO
│   │       └── LoginResponse.java              ⭐ NUEVO
│   ├── config/
│   │   └── SecurityConfig.java      ⭐ NUEVO
│   └── PreSustentacionesApplication.java (SIN cambios)
```

---

## 🎯 Endpoints Disponibles

### 🔓 Públicos (No requieren token)
- `POST /api/auth/register` - Registrar usuario
- `POST /api/auth/login` - Iniciar sesión

### 🔒 Protegidos (Requieren token)
- `GET /api/usuarios` - Listar usuarios
- `GET /api/usuarios/{id}` - Obtener usuario
- `PUT /api/usuarios/{id}` - Actualizar usuario
- `PATCH /api/usuarios/{id}/activar` - Activar usuario
- `PATCH /api/usuarios/{id}/desactivar` - Desactivar usuario
- `DELETE /api/usuarios/{id}` - Eliminar usuario
- `GET /api/solicitudes` - Listar solicitudes
- `GET /api/cronogramas` - Listar cronogramas
- ... (todos los demás endpoints)

---

## ⚙️ Configuración JWT

`jwt.secret` se lee desde la variable de entorno `JWT_SECRET` (sin valor por defecto en el código:
la app no arranca si falta). Genera uno y colócalo en tu `.env` local (nunca en `application.properties`
ni en el repositorio):

```bash
openssl rand -hex 32
```

```bash
# .env (copiado desde .env.example, ignorado por git)
JWT_SECRET=<pega aquí el valor generado>
JWT_EXPIRATION=86400000
# 86400000 ms = 24 horas
```

⚠️ **Importante:** cada entorno (dev, CI, producción) debe usar su propio `JWT_SECRET`, nunca compartido ni versionado.

---

## 🔍 Verificar que Todo Funciona

### ✅ Checklist:

- [ ] Proyecto abre sin errores en el IDE
- [ ] PostgreSQL está corriendo
- [ ] Base de datos `presusDb` existe
- [ ] Maven descargó todas las dependencias
- [ ] La aplicación inicia sin errores
- [ ] Endpoint de registro funciona (POST /api/auth/register)
- [ ] Endpoint de login funciona (POST /api/auth/login)
- [ ] Endpoints protegidos requieren token
- [ ] Token válido da acceso a endpoints protegidos

---

## 🐛 Solución de Problemas

### Error: "Application run failed"
**Solución:** Revisa que PostgreSQL esté corriendo

### Error: "Cannot find symbol"
**Solución:** Recarga Maven (Clic derecho → Maven → Reload Project)

### Error 401 en endpoints
**Solución:** Verifica que el header sea: `Authorization: Bearer TOKEN`

### Error: "Email ya registrado"
**Solución:** Usa otro email o borra la base de datos

---

## 📊 Resumen de Cambios

| Aspecto | Antes | Después |
|---------|-------|---------|
| Tipo de ID | UUID | Long |
| Seguridad | ❌ No implementada | ✅ Spring Security + JWT |
| Autenticación | ❌ No | ✅ Login/Register |
| Endpoints | 🔓 Públicos | 🔒 Protegidos con JWT |
| API JJWT | ❌ Incompatible | ✅ 0.12.5 |

---

## 🎓 Próximos Pasos

1. ✅ Prueba todos los endpoints en Postman
2. ✅ Crea usuarios con diferentes roles (ESTUDIANTE, DOCENTE, ADMIN)
3. ✅ Implementa autorización por roles con `@PreAuthorize`
4. ✅ Agrega validaciones adicionales
5. ✅ Conecta con tu frontend

---

**¡Tu proyecto está 100% funcional y listo para usar! 🎉**

Para cualquier duda, revisa los archivos de ejemplo en la carpeta `security/`.
