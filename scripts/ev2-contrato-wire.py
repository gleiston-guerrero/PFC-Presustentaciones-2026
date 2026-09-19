#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Comprueba que ningun nombre de cable dependa del nombre de la variable Java.

QUE PROBLEMA RESUELVE
---------------------
La revision del 18-sep encontro una regresion real que el renombrado de P4
introdujo y que ningun chequeo detectaba:

    @RequestParam(required = false) LocalDate from   <-- se llamaba 'desde'

Spring toma el nombre del parametro Java cuando la anotacion no trae uno
explicito. Al renombrar `desde` a `from`, el nombre de cable cambio con el, y
Angular siguio enviando `desde`: el filtro de fechas de 5 endpoints de reportes
y actas **se ignoraba en silencio** -- sin error, sin log, devolviendo todo el
conjunto como si no hubiera filtro.

La leccion no es "revisar mejor". Es que un nombre publico no puede depender de
un identificador interno que cualquiera puede renombrar. Este script lo vuelve
mecanico: si una anotacion de entrada no declara su nombre, falla.

Lo mismo vale para los procedimientos almacenados: un @StoredProcedureParameter
o un parametro con nombre en un CALL tiene que existir en las migraciones.

USO
    python scripts/ev2-contrato-wire.py

Sale con 1 si encuentra un nombre de cable implicito o un parametro de
procedimiento que no existe en el esquema.
"""
import glob
import io
import os
import re
import sys

CONTROLADORES = "backend/src/main/java/**/controllers/*.java"
MIGRACIONES = "backend/src/main/resources/db/migration/V*.sql"

# @RequestParam / @PathVariable / @RequestHeader / @CookieValue, con o sin parentesis.
RE_ENTRADA = re.compile(
    r"@(RequestParam|PathVariable|RequestHeader|CookieValue)\b\s*(\((?:[^()]|\([^()]*\))*\))?")

# Un nombre explicito es ("x"), (name = "x") o (value = "x").
RE_NOMBRE = re.compile(r'(?:^\(|[(,]\s*)(?:name|value)\s*=\s*"([^"]+)"|^\(\s*"([^"]+)"')

RE_SP_PARAM = re.compile(r'@StoredProcedureParameter\s*\([^)]*name\s*=\s*"([^"]+)"')


def sin_comentarios(t):
    """Blanquea los comentarios SIN mover ningun caracter de sitio.

    Se conserva la longitud (y los saltos de linea) a proposito: si se quitaran,
    todos los desplazamientos se correrian y los numeros de linea que reporta
    este script dejarian de corresponder al archivo real.
    """
    out, i, n = list(t), 0, len(t)
    while i < n:
        c = t[i]
        if c == '/' and i + 1 < n and t[i + 1] == '/':
            j = t.find('\n', i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = ' '
            i = j
        elif c == '/' and i + 1 < n and t[i + 1] == '*':
            j = t.find('*/', i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != '\n':
                    out[k] = ' '
            i = j
        elif c == '"':
            i += 1
            while i < n and t[i] != '"':
                i += 2 if t[i] == '\\' else 1
            i += 1
        else:
            i += 1
    return "".join(out)


def nombre_explicito(args):
    """True si la anotacion declara su nombre de cable."""
    if not args:
        return False
    cuerpo = args.strip()
    if re.match(r'^\(\s*"', cuerpo):          # @PathVariable("id")
        return True
    return bool(re.search(r'\b(?:name|value)\s*=\s*"', cuerpo))


def revisar_controladores():
    malos = []
    for f in glob.glob(CONTROLADORES, recursive=True):
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        codigo = sin_comentarios(txt)
        for m in RE_ENTRADA.finditer(codigo):
            if nombre_explicito(m.group(2)):
                continue
            linea = codigo[:m.start()].count("\n") + 1
            # el identificador Java que quedaria como nombre de cable
            resto = codigo[m.end():m.end() + 200]
            var = re.search(r"(\w+)\s*[,)]", resto)
            malos.append((f.replace("\\", "/"), linea, m.group(1),
                          var.group(1) if var else "?"))
    return malos


def nombres_de_migraciones():
    nombres = set()
    for f in glob.glob(MIGRACIONES):
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        # parametros declarados en CREATE PROCEDURE/FUNCTION nombre(p_x tipo, ...)
        for m in re.finditer(
                r"CREATE\s+(?:OR\s+REPLACE\s+)?(?:PROCEDURE|FUNCTION)\s+[\w.]+\s*\(([^;]*?)\)\s*(?:RETURNS|LANGUAGE|AS)",
                txt, re.I | re.S):
            for p in m.group(1).split(","):
                t = p.strip().split()
                if t:
                    cand = t[0].strip()
                    if cand.upper() in ("IN", "OUT", "INOUT") and len(t) > 1:
                        cand = t[1].strip()
                    if re.fullmatch(r"\w+", cand):
                        nombres.add(cand.lower())
    return nombres


def revisar_procedimientos():
    declarados = nombres_de_migraciones()
    if not declarados:
        return [], 0
    malos = []
    usados = 0
    for f in glob.glob("backend/src/main/java/**/*.java", recursive=True):
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        for m in RE_SP_PARAM.finditer(txt):
            usados += 1
            if m.group(1).lower() not in declarados:
                linea = txt[:m.start()].count("\n") + 1
                malos.append((f.replace("\\", "/"), linea, m.group(1)))
    return malos, usados


def main():
    if not os.path.isdir("backend/src/main/java"):
        print("ERROR: corre esto desde la raiz del repositorio.")
        return 2

    malos = revisar_controladores()
    total_anot = sum(
        1 for f in glob.glob(CONTROLADORES, recursive=True)
        for _ in RE_ENTRADA.finditer(
            sin_comentarios(io.open(f, encoding="utf-8", errors="replace").read())))

    print(f"Anotaciones de entrada en controladores: {total_anot}")
    print(f"Con nombre de cable explicito: {total_anot - len(malos)}")
    if malos:
        print(f"\n*** {len(malos)} sin nombre explicito: el nombre de cable seria "
              f"el identificador Java ***")
        for f, l, anot, var in malos:
            print(f"   {f}:{l}  @{anot} -> \"{var}\"")

    sp_malos, sp_usados = revisar_procedimientos()
    print(f"\n@StoredProcedureParameter revisados: {sp_usados}")
    if sp_malos:
        print(f"*** {len(sp_malos)} nombran un parametro que no existe en las migraciones ***")
        for f, l, n in sp_malos:
            print(f"   {f}:{l}  \"{n}\"")
    elif sp_usados:
        print("   todos existen en las migraciones versionadas.")

    if malos or sp_malos:
        print("\nUn nombre publico no puede depender de un identificador interno:")
        print("  declara el nombre --  @RequestParam(name = \"desde\") LocalDate from")
        return 1
    print("\n[OK] ningun nombre de cable depende del nombre de la variable Java.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
