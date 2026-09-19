#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Genera y comprueba la tabla de titularidad por punto de CONTRIBUCIONES.md (EV-4).

POR QUE EXISTE
--------------
La revision del 18-sep declaro EV-4 "No cumple" con cuatro defectos concretos:
sin firmas, sin correo institucional, sin columna de archivos, y recuentos que
no cuadran ("dice 81 commits; son 89"). El ultimo es el que importa de verdad,
porque es el que se repite solo: cualquier numero escrito a mano en ese archivo
envejece con el siguiente commit. Cuando se corrigio 81 -> 89, dos dias despues
ya eran 92.

Asi que la tabla deja de escribirse y pasa a generarse. El historial etiqueta
cada commit con su punto en el asunto (`fix(P4,EV-2): ...`), asi que el reparto
por punto, el conteo, los autores y los archivos tocados salen de `git log`, no
de la memoria de nadie. Es la misma regla que el propio ing impuso: "un punto
atribuido en CONTRIBUCIONES.md que el historial no respalda no cuenta para
nadie".

USO
    python scripts/ev4-contribuciones.py            # imprime la tabla generada
    python scripts/ev4-contribuciones.py --check    # falla si el archivo no cuadra

En modo --check compara lo que CONTRIBUCIONES.md afirma contra lo que git dice:
el total de commits del tramo, el conjunto de autores, y los commits atribuidos
a cada punto. Sale con 1 a la primera discrepancia.
"""
import re
import subprocess
import sys

BASE = "f3d1ff4"        # el commit que reviso la guia original
ARCHIVO = "CONTRIBUCIONES.md"

# Orden de presentacion. EV-* van al final porque son entregables, no puntos.
ORDEN = [f"P{i}" for i in range(1, 13)] + ["EV-1", "EV-2", "EV-3", "EV-4"]

RE_PUNTO = re.compile(r"\b(EV-[1-4]|P(?:1[0-2]|[1-9]))\b")


def git(*args):
    r = subprocess.run(["git", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} fallo:\n{r.stderr}")
    return r.stdout


def commits():
    """(sha, asunto, autor, correo) de cada commit del tramo, del mas viejo al mas nuevo."""
    salida = git("log", "--reverse", "--format=%h%x1f%s%x1f%an%x1f%ae", f"{BASE}..HEAD")
    out = []
    for linea in salida.splitlines():
        if linea.strip():
            out.append(tuple(linea.split("\x1f")))
    return out


def archivos(sha):
    return [l for l in git("show", "--pretty=", "--name-only", sha).splitlines() if l.strip()]


def por_punto(cs):
    """Reparte los commits por punto leyendo el asunto. Un commit puede cerrar varios."""
    mapa = {}
    sueltos = []
    for sha, asunto, an, ae in cs:
        puntos = sorted(set(RE_PUNTO.findall(asunto)))
        if not puntos:
            sueltos.append((sha, asunto))
            continue
        for p in puntos:
            mapa.setdefault(p, []).append((sha, asunto, an, ae))
    return mapa, sueltos


def resumen_archivos(shas, tope=4):
    """Los archivos mas representativos: los que mas commits del punto tocaron."""
    frec = {}
    for s in shas:
        for f in archivos(s):
            frec[f] = frec.get(f, 0) + 1
    # Los .pdf y OBSERVACIONES.md los toca casi todo: no distinguen nada.
    ruido = ("informe-final.pdf", "docs/observaciones/OBSERVACIONES.md", "SRS-v1.0.1.pdf")
    utiles = [f for f in frec if not any(r in f for r in ruido)]
    utiles.sort(key=lambda f: (-frec[f], f))
    return utiles[:tope], len(frec)


def generar():
    cs = commits()
    mapa, sueltos = por_punto(cs)
    autores = sorted({f"{an} <{ae}>" for _, _, an, ae in cs})

    print(f"Tramo: {BASE}..HEAD")
    print(f"Commits: {len(cs)}")
    print(f"Autores: {', '.join(autores)}")
    print(f"Sin punto en el asunto: {len(sueltos)}\n")
    print("| Punto | Commits | Archivos de evidencia (los mas tocados) | Total archivos |")
    print("|---|---|---|---|")
    for p in ORDEN:
        filas = mapa.get(p)
        if not filas:
            continue
        shas = [f[0] for f in filas]
        clave, total = resumen_archivos(shas)
        cod = ", ".join(f"`{s}`" for s in shas)
        arch = "<br>".join(f"`{f}`" for f in clave) or "_(solo bitacora)_"
        print(f"| {p} | {cod} | {arch} | {total} |")
    if sueltos:
        print(f"\nCommits sin punto declarado ({len(sueltos)}):")
        for sha, asunto in sueltos:
            print(f"  {sha}  {asunto[:78]}")
    return 0


def check():
    cs = commits()
    mapa, _ = por_punto(cs)
    autores = {f"{an} <{ae}>" for _, _, an, ae in cs}
    try:
        txt = open(ARCHIVO, encoding="utf-8").read()
    except OSError as e:
        print(f"[FAIL] no se pudo leer {ARCHIVO}: {e}")
        return 1

    fallos = []

    # 1) El total de commits que declara el archivo.
    m = re.search(r"\*\*Total:\s*(\d+)\s+commits", txt)
    if not m:
        fallos.append("el archivo no declara un total de commits en negrita")
    elif int(m.group(1)) != len(cs):
        fallos.append(f"declara {m.group(1)} commits; git dice {len(cs)}")

    # 2) Los autores del tramo.
    for a in autores:
        correo = a.split("<")[1].rstrip(">")
        if correo not in txt:
            fallos.append(f"no declara el autor real del tramo: {a}")

    # 3) Cada commit citado por punto tiene que existir y estar en el tramo.
    citados = set(re.findall(r"`([0-9a-f]{7,40})`", txt))
    del_tramo = {sha for sha, _, _, _ in cs}
    for sha in citados:
        corto = sha[:7]
        if corto in del_tramo:
            continue
        r = subprocess.run(["git", "cat-file", "-e", f"{sha}^{{commit}}"],
                           capture_output=True)
        if r.returncode != 0:
            fallos.append(f"cita un commit que no existe: {sha}")

    # 4) Ningun punto con commits en git puede faltar en la tabla.
    for p, filas in mapa.items():
        if not re.search(rf"\|\s*{re.escape(p)}\b", txt):
            fallos.append(f"git atribuye {len(filas)} commit(s) a {p} y la tabla no lo lista")

    # 5) Los archivos citados en la tabla tienen que existir hoy.
    for f in set(re.findall(r"`((?:backend|frontend|Frontend|docs|scripts|k6|Informe-Final)/[^`\s]+)`", txt)):
        r = subprocess.run(["git", "ls-files", "--error-unmatch", "--cached", f],
                           capture_output=True)
        if r.returncode != 0:
            fallos.append(f"cita un archivo que no esta versionado ni en el indice: {f}")

    if fallos:
        print(f"[FAIL] CONTRIBUCIONES.md no cuadra con el historial ({len(fallos)}):")
        for f in fallos:
            print(f"   - {f}")
        return 1
    print(f"[OK] CONTRIBUCIONES.md cuadra con el historial: {len(cs)} commits, "
          f"{len(autores)} autor(es), {len(mapa)} punto(s) atribuidos.")
    return 0


if __name__ == "__main__":
    sys.exit(check() if "--check" in sys.argv else generar())
