#!/usr/bin/env python3
"""P4 -- Congela el contrato de cable ANTES de renombrar identificadores.

EL ERROR QUE ESTE SCRIPT EVITA
------------------------------
El renombrado de `49adaee` cambio nombres de campo Java y confio en agregar
`@JsonProperty` a mano donde hiciera falta. Se aplico de forma inconsistente y
dejo 23 contratos JSON rotos (ver OBS-50), mas 18 `@RequestParam` (OBS-39).
La causa es estructural: si el nombre que viaja por HTTP es *implicito* (sale
del nombre del identificador Java), entonces renombrar el identificador cambia
el contrato en silencio.

La solucion es invertir el orden: **primero se hace explicito todo nombre que
viaja por el cable, y despues se renombra**. Una vez que cada campo lleva su
`@JsonProperty("nombreActual")`, cada query param su `@RequestParam(name=...)`
y cada variable de ruta su `@PathVariable("...")`, renombrar el identificador
Java ya no puede cambiar lo que ve el frontend.

QUE HACE
--------
1. `dto/` y `entities/`: a cada campo `private` sin `@JsonProperty` le agrega
   `@JsonProperty("<nombre actual del campo>")`. Es un no-op semantico hoy
   (Jackson ya usaba ese nombre) y una barrera manana.
2. Controladores: a cada `@RequestParam` y `@PathVariable` sin nombre
   explicito le agrega el nombre actual del parametro.

QUE NO TOCA
-----------
- Campos con `@JsonIgnore`: agregarles `@JsonProperty` cambiaria el
  comportamiento de serializacion (Jackson las combina de forma no obvia).
- Constantes (`static final`).
- Cualquier cadena de texto: nombres de columna, JPQL, rutas.

Uso:  python scripts/p4-congelar-contrato.py [--dry-run]
"""
import os
import re
import sys

B = "backend/src/main/java/ec/edu/uteq/presustentaciones/"
DRY = "--dry-run" in sys.argv

FIELD = re.compile(
    r'((?:^[ \t]*@\w+(?:\([^)]*\))?[ \t]*\r?\n)*)'   # anotaciones previas
    r'([ \t]*)private\s+(?!static\b)([\w<>\[\],\.\? ]+?)\s+(\w+)\s*([;=])',
    re.M,
)
IMPORT = "import com.fasterxml.jackson.annotation.JsonProperty;"


def congelar_campos():
    tot = 0
    for root in ("dto/", "entities/"):
        d = B + root
        for fn in sorted(os.listdir(d)):
            if not fn.endswith(".java"):
                continue
            p = d + fn
            txt = open(p, encoding="utf-8", errors="replace").read()
            n = 0

            def sub(m):
                nonlocal n
                ann, ind, tipo, nombre, fin = m.groups()
                if "@JsonProperty" in ann or "@JsonIgnore" in ann:
                    return m.group(0)
                n += 1
                return f'{ann}{ind}@JsonProperty("{nombre}")\n{ind}private {tipo} {nombre}{fin}'

            nuevo = FIELD.sub(sub, txt)
            if not n:
                continue
            if IMPORT not in nuevo:
                nuevo = re.sub(r'^(package [^\n]+\n)', r'\1\n' + IMPORT + '\n',
                               nuevo, count=1, flags=re.M)
            if not DRY:
                open(p, "w", encoding="utf-8").write(nuevo)
            print(f"  {root}{fn}: {n} campos congelados")
            tot += n
    return tot


def congelar_params():
    tot = 0
    for dp, _, fns in os.walk(B + "controllers"):
        for fn in sorted(fns):
            if not fn.endswith(".java"):
                continue
            p = os.path.join(dp, fn)
            txt = open(p, encoding="utf-8", errors="replace").read()
            n = 0

            def sub(m):
                nonlocal n
                anot, args, resto, nombre = m.group(1), m.group(2) or "", m.group(3), m.group(4)
                # ya tiene nombre explicito -> no tocar
                if re.search(r'name\s*=|value\s*=|\(\s*"', args):
                    return m.group(0)
                n += 1
                if args.strip("()").strip():
                    nuevos = f'(name = "{nombre}", {args.strip("()").strip()})'
                else:
                    nuevos = f'("{nombre}")'
                return f'{anot}{nuevos}{resto}{nombre}'

            nuevo = re.sub(
                r'(@(?:RequestParam|PathVariable))(\([^)]*\))?'
                r'((?:\s+(?:final\s+)?[\w<>\[\],\.\? ]+?)\s+)(\w+)\b',
                sub, txt)
            if n and not DRY:
                open(p, "w", encoding="utf-8").write(nuevo)
            if n:
                print(f"  controllers/{fn}: {n} parametros congelados")
                tot += n
    return tot


if __name__ == "__main__":
    if DRY:
        print(">>> DRY RUN, no se escribe nada\n")
    print("=== Campos JSON (dto/ + entities/) ===")
    a = congelar_campos()
    print(f"\n=== Query params y variables de ruta (controllers/) ===")
    b = congelar_params()
    print(f"\nTotal: {a} campos + {b} parametros = {a + b} nombres ahora explicitos")
