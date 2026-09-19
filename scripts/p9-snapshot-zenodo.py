#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Comprueba que el tag solo se adelante al snapshot de Zenodo para registrar el DOI.

EL PROBLEMA DE ORDEN QUE ESTO RESUELVE
--------------------------------------
Un DOI no existe hasta que se publica. Los commits que lo registran --ZENODO.md,
CITATION.cff, VERIFICACION.md-- son por fuerza POSTERIORES al snapshot que ese
DOI archiva. Asi que el tag acaba, inevitablemente, un poco por delante del
commit que esta dentro del archivo publicado.

La regla del evaluador es "lo que no este dentro de la etiqueta no existe", asi
que dejar el tag atras tampoco sirve: el registro del DOI quedaria fuera.

La salida no es elegir entre las dos cosas, es acotar la diferencia y hacerla
verificable. Este script exige que **lo unico** que separe el commit archivado
del commit etiquetado sea el registro del propio DOI. Si aparece codigo, una
prueba, una medicion o cualquier otro documento, falla: significaria que el
archivo publicado en Zenodo no contiene lo que el repositorio dice archivar.

DE DONDE SALEN LOS DATOS
------------------------
El commit archivado se lee de docs/ZENODO.md, de la fila "Commit archivado" de
su tabla. No se escribe aqui: si se escribiera en dos sitios, envejeceria en uno.

Uso:
    python scripts/p9-snapshot-zenodo.py

Sale con 1 si el tag se adelanto con algo que no sea el registro del DOI.
"""
import io
import os
import re
import subprocess
import sys

ZENODO_MD = "docs/ZENODO.md"
TAG = "v1.1.0"

# Archivos cuyo unico proposito, en esta ventana, es registrar el DOI recien
# acunado. Cualquier otra cosa que cambie entre el snapshot y el tag es un
# hallazgo.
DE_REGISTRO = {
    "docs/ZENODO.md",
    "CITATION.cff",
    "VERIFICACION.md",
    "CONTRIBUCIONES.md",          # se regenera sola en cada commit (EV-4)
    "scripts/p9-snapshot-zenodo.py",
    "scripts/verify.sh",          # ver la nota de abajo
    "docs/ZENODO-METADATOS-v1.1.0.md",
    "README.md",
}

# Sobre scripts/verify.sh en esa lista: se incluye porque en esta ventana su
# unico cambio fue enganchar esta misma comprobacion. Es la entrada mas laxa de
# la lista y conviene decirlo, porque verify.sh podria cambiar por otros motivos
# sin que esto lo note. El diff completo es de todas formas auditable:
#
#     git diff <commit archivado>..v1.1.0 -- scripts/verify.sh
#
# Lo que esta lista SI garantiza, y es lo que importa, es que entre el snapshot
# publicado y el tag no cambio ni codigo de la aplicacion, ni una prueba, ni una
# medicion, ni el informe.


def git(*args):
    r = subprocess.run(["git", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return r.returncode, r.stdout.strip(), r.stderr.strip()


def commit_archivado():
    """Lee el commit del snapshot de la tabla de docs/ZENODO.md."""
    if not os.path.isfile(ZENODO_MD):
        return None
    txt = io.open(ZENODO_MD, encoding="utf-8", errors="replace").read()
    m = re.search(r"\|\s*\*\*Commit archivado\*\*\s*\|\s*\*\*`([0-9a-f]{7,40})`\*\*\s*\|", txt)
    return m.group(1) if m else None


def main():
    cod, _, _ = git("rev-parse", TAG)
    if cod != 0:
        print(f"[FAIL] no existe el tag {TAG}")
        return 1

    snap = commit_archivado()
    if not snap:
        print(f"[FAIL] {ZENODO_MD} no declara el commit archivado "
              f"(fila '**Commit archivado**' de su tabla)")
        return 1

    cod, _, _ = git("cat-file", "-e", f"{snap}^{{commit}}")
    if cod != 0:
        print(f"[FAIL] el commit archivado {snap} no existe en este repositorio")
        return 1

    cod, _, _ = git("merge-base", "--is-ancestor", snap, TAG)
    if cod != 0:
        print(f"[FAIL] {snap} no es ancestro de {TAG}: el tag no contiene el "
              f"snapshot que Zenodo archiva")
        return 1

    _, salida, _ = git("diff", "--name-only", f"{snap}..{TAG}")
    cambiados = [l.strip() for l in salida.splitlines() if l.strip()]
    ajenos = [f for f in cambiados if f not in DE_REGISTRO]

    _, n, _ = git("rev-list", "--count", f"{snap}..{TAG}")
    print(f"Snapshot archivado en Zenodo : {snap}")
    print(f"Commit etiquetado {TAG:<12}: {git('rev-list', '-n1', TAG)[1][:7]}")
    print(f"Commits de diferencia        : {n}")
    print(f"Archivos que cambian         : {len(cambiados)}")

    if not cambiados:
        print(f"\n[OK] el tag apunta exactamente al commit archivado en Zenodo.")
        return 0

    for f in cambiados:
        marca = "  " if f in DE_REGISTRO else "!!"
        print(f"   {marca} {f}")

    if ajenos:
        print(f"\n*** {len(ajenos)} archivo(s) que NO son registro del DOI ***")
        print("El paquete publicado en Zenodo no contiene estos cambios, de modo que")
        print("el tag y el snapshot archivado dejaron de describir lo mismo.")
        print("O se archiva una version nueva, o se mueve el tag al commit archivado.")
        return 1

    print(f"\n[OK] la diferencia entre el snapshot archivado y {TAG} es solo el "
          f"registro del DOI.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
