#!/usr/bin/env python3
"""Audita backend/src/main/java/.../controllers buscando endpoints sin ninguna forma de
autorizacion:

  1. ESCRITURA (POST/PUT/PATCH/DELETE) sin @PreAuthorize/@Secured, ni de clase ni de
     metodo. Usado para el cierre de P8 (examen suspenso, 2026-09-16) -- ver la entrada
     correspondiente en OWASP-AUDIT.md, seccion A05:2021.
  2. LECTURA (GET), desde la revision final (2026-09-19, punto 5b): "la autorizacion de los
     GET quedo fuera del alcance del verificador". Un GET sin anotacion no es
     necesariamente un fallo -- puede validar el acceso dentro del metodo (el estudiante
     dueno, el panelista, el tutor) --, asi que aqui no basta con ver la anotacion: cada GET
     sin @PreAuthorize debe cumplir UNA de tres cosas, y el script lo comprueba:

       a. validar el acceso en el propio metodo (SubmissionAccessService, la identidad del
          token, isCurrentAppUserOrAdmin...): se detecta por las marcas de GUARDAS_EN_METODO;
       b. delegar en un servicio que valida: DELEGADOS_A_SERVICIO nombra el fichero y la
          marca que debe seguir apareciendo en el (si alguien la quita, el script falla);
       c. ser un catalogo institucional sin datos de personas: CATALOGOS, con la razon.

     Cualquier GET nuevo que no encaje en ninguna falla -- que es lo que se quiere.

Uso: python audit-endpoints-autorizacion.py [ruta a backend/src/main/java/.../controllers]
"""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else
            "backend/src/main/java/ec/edu/uteq/presustentaciones/controllers")
SERVICIOS = ROOT.parent / "services"

WRITE_VERBS = ("PostMapping", "PutMapping", "PatchMapping", "DeleteMapping")

# Endpoints deliberadamente públicos (mecanismo de autenticación/recuperación en sí
# mismo): no pueden exigir sesión previa. Documentados en OWASP-AUDIT.md.
# La clave es el nombre del método JAVA, no la ruta HTTP. El renombrado de P4
# (2026-09-18) cambió `recuperar` por `recover` y este script lo detectó como un
# endpoint sin autorización y sin justificación -- correctamente, porque para él
# era un nombre nuevo. La ruta no cambió: sigue siendo @PostMapping("/recuperar"),
# verificado contra el commit anterior al renombrado. Solo se actualiza la clave.
EXENTOS_CONOCIDOS = {
    ("AuthController", "login"), ("AuthController", "refresh"),
    ("AuthController", "logout"), ("AuthController", "recover"),
    ("AuthController", "reset"),
}

# --- GET ---------------------------------------------------------------------------------
# (a) marcas que, dentro del cuerpo del metodo, indican que se valida quien pregunta.
GUARDAS_EN_METODO = (
    "submissionAccessService.validateAccess",   # participante de la solicitud o permiso
    "validateAccess(",                          # helper local equivalente
    "validateAccessSubmission(",                # propietario de la solicitud o quien la revisa
    "isCurrentAppUserOrAdmin(",                 # el propio usuario o ADMIN
    "resolveAppUserId(",                        # la identidad sale del token, no de la URL
    "getAuthentication()",                      # idem: se filtra por el usuario autenticado
    "validateOwnAccountOrManager(",             # el propio usuario o quien gestiona el cronograma
)

# (b) el controlador delega y el servicio valida. Se comprueba que la marca siga en el servicio.
DELEGADOS_A_SERVICIO = {
    ("EvaluationPanelistController", "obtain"):
        ("EvaluationPanelistService.java", "submissionAccessService.validateAccess"),
    ("EvaluationPanelistController", "obtainPanel"):
        ("EvaluationPanelistService.java", "submissionAccessService.validateAccess"),
    ("ProposalController", "obtainBySubmission"):
        ("ProposalServiceImpl.java", "submissionAccessService.validateAccess"),
    ("ProposalController", "viewPdf"):
        ("ProposalServiceImpl.java", "submissionAccessService.validateAccess"),
    ("ProposalController", "verify"):
        ("ProposalServiceImpl.java", "submissionAccessService.validateAccess"),
}

# (c) catalogos o datos agregados sin datos de personas; los protege la sesion obligatoria de
# SecurityConfig (/api/v1/** authenticated), no un permiso concreto.
CATALOGOS = {
    ("RoomController", "list"): "catalogo de salas",
    ("RoomController", "listPaged"): "catalogo de salas",
    ("RubricController", "list"): "catalogo de rubricas",
    ("RubricController", "obtain"): "catalogo de rubricas",
    ("RubricController", "criteria"): "criterios de la rubrica",
    ("ScheduleController", "availability"): "franjas libres de un dia, sin datos de personas",
    ("ScheduleController", "verifyAvailability"): "true/false de una sala, sin datos de personas",
}


def metodos(verbos, text):
    """Rinde (metodo, verbo, linea 1-based, bloque de anotaciones+firma, cuerpo)."""
    lines = text.split("\n")
    for i, line in enumerate(lines):
        for verb in verbos:
            if not re.search(rf"@{verb}\b", line):
                continue
            # ventana hacia adelante desde la anotacion de mapping hasta la firma del metodo
            window, j = [], i
            while j < len(lines) and "public " not in lines[j]:
                window.append(lines[j])
                j += 1
            if j < len(lines):
                window.append(lines[j])
            firma = lines[j] if j < len(lines) else ""
            nombre = re.search(r"public\s+[\w<>\[\],\s\.\?]+?\s+(\w+)\s*\(", firma)
            # cuerpo: desde la firma hasta que se cierran las llaves
            cuerpo, depth, k, abierto = [], 0, j, False
            while k < len(lines):
                cuerpo.append(lines[k])
                depth += lines[k].count("{") - lines[k].count("}")
                abierto = abierto or "{" in lines[k]
                if abierto and depth <= 0:
                    break
                k += 1
            yield (nombre.group(1) if nombre else "?", verb, i + 1,
                   "\n".join(window), "\n".join(cuerpo))


total = 0
missing = []
total_get = 0
get_sin_anotacion = []

for f in sorted(ROOT.glob("*.java")):
    text = f.read_text(encoding="utf-8", errors="replace").replace("\r\n", "\n")

    idx_class = text.find("public class ")
    class_header = text[:idx_class] if idx_class != -1 else text
    idx_last_import = class_header.rfind("\nimport ")
    class_annot_zone = class_header[idx_last_import:] if idx_last_import != -1 else class_header
    class_level_auth = "@PreAuthorize" in class_annot_zone or "@Secured" in class_annot_zone

    m = re.search(r"class (\w+)", text)
    class_name = m.group(1) if m else f.stem

    for method_name, verb, linea, bloque, cuerpo in metodos(WRITE_VERBS, text):
        has_method_auth = "@PreAuthorize" in bloque or "@Secured" in bloque
        total += 1
        if not (has_method_auth or class_level_auth):
            missing.append((class_name, method_name, verb, linea))

    for method_name, verb, linea, bloque, cuerpo in metodos(("GetMapping",), text):
        total_get += 1
        if "@PreAuthorize" in bloque or "@Secured" in bloque or class_level_auth:
            continue
        get_sin_anotacion.append((class_name, method_name, linea, cuerpo))

print(f"Total endpoints de escritura (POST/PUT/PATCH/DELETE): {total}")
print(f"Sin ninguna anotacion de autorizacion: {len(missing)}")
print()

inesperados = []
for class_name, method_name, verb, line in missing:
    esperado = (class_name, method_name) in EXENTOS_CONOCIDOS
    etiqueta = "exento conocido (auth pre-login)" if esperado else "*** INESPERADO ***"
    print(f"  {class_name}.{method_name} ({verb}, L{line}) -- {etiqueta}")
    if not esperado:
        inesperados.append((class_name, method_name))

print()
if inesperados:
    print(f"FALLO: {len(inesperados)} endpoint(s) sin autorizacion y sin justificacion conocida.")
    sys.exit(1)
else:
    print("OK: todos los endpoints sin @PreAuthorize son exentos conocidos y documentados.")

# ---------------------------------------------------------------------------------- GET
print()
print(f"Total endpoints de lectura (GET): {total_get}")
print(f"Sin @PreAuthorize: {len(get_sin_anotacion)}")
print()

sin_justificar = []
for class_name, method_name, linea, cuerpo in get_sin_anotacion:
    clave = (class_name, method_name)
    if any(g in cuerpo for g in GUARDAS_EN_METODO):
        etiqueta = "valida el acceso en el metodo"
    elif clave in DELEGADOS_A_SERVICIO:
        fichero, marca = DELEGADOS_A_SERVICIO[clave]
        ruta = SERVICIOS / fichero
        if ruta.exists() and marca in ruta.read_text(encoding="utf-8", errors="replace"):
            etiqueta = f"delega en {fichero}, que valida"
        else:
            etiqueta = f"*** {fichero} ya no contiene '{marca}' ***"
            sin_justificar.append(clave)
    elif clave in CATALOGOS:
        etiqueta = f"catalogo ({CATALOGOS[clave]})"
    else:
        etiqueta = "*** INESPERADO ***"
        sin_justificar.append(clave)
    print(f"  {class_name}.{method_name} (GET, L{linea}) -- {etiqueta}")

print()
if sin_justificar:
    print(f"FALLO: {len(sin_justificar)} endpoint(s) GET sin autorizacion ni justificacion comprobable.")
    sys.exit(1)
print("OK: todo GET sin @PreAuthorize valida el acceso, delega en un servicio que lo valida o es un catalogo.")
