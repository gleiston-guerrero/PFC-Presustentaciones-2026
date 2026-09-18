#!/usr/bin/env python3
"""Audita backend/src/main/java/.../controllers buscando endpoints de escritura
(POST/PUT/PATCH/DELETE) sin ninguna forma de @PreAuthorize/@Secured, ni de clase ni
de metodo. Usado para el cierre de P8 (examen suspenso, 2026-09-16) -- ver la entrada
correspondiente en OWASP-AUDIT.md, seccion A05:2021.

Uso: python audit-endpoints-autorizacion.py [ruta a backend/src/main/java/.../controllers]
"""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else
            "backend/src/main/java/ec/edu/uteq/presustentaciones/controllers")

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

total = 0
missing = []

for f in sorted(ROOT.glob("*.java")):
    text = f.read_text(encoding="utf-8", errors="replace")

    idx_class = text.find("public class ")
    class_header = text[:idx_class] if idx_class != -1 else text
    idx_last_import = class_header.rfind("\nimport ")
    class_annot_zone = class_header[idx_last_import:] if idx_last_import != -1 else class_header
    class_level_auth = "@PreAuthorize" in class_annot_zone or "@Secured" in class_annot_zone

    m = re.search(r"class (\w+)", text)
    class_name = m.group(1) if m else f.stem

    lines = text.split("\n")
    for i, line in enumerate(lines):
        for verb in WRITE_VERBS:
            if not re.search(rf"@{verb}\b", line):
                continue
            # ventana hacia adelante desde la anotacion de mapping hasta la firma del metodo
            window, j = [], i
            while j < len(lines) and "public " not in lines[j]:
                window.append(lines[j])
                j += 1
            if j < len(lines):
                window.append(lines[j])
            block = "\n".join(window)
            has_method_auth = "@PreAuthorize" in block or "@Secured" in block
            covered = has_method_auth or class_level_auth

            method_name_match = re.search(r"public\s+[\w<>\[\],\s\.\?]+?\s+(\w+)\s*\(",
                                           lines[j] if j < len(lines) else "")
            method_name = method_name_match.group(1) if method_name_match else "?"

            total += 1
            if not covered:
                missing.append((class_name, method_name, verb, i + 1))

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
