#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P9 -- La etiqueta v1.1.0 tiene que ser anotada y apuntar a HEAD.

La regla del evaluador es "lo que no este dentro de la etiqueta no existe".
Una etiqueta que va por detras de HEAD deja fuera commits que el equipo cree
entregados, y una etiqueta movida a mano a otro commit hace lo mismo sin ruido.

Hasta la revision del 19-sep, `make verify` solo AVISABA si la etiqueta no
estaba en HEAD. El evaluador la movio como mutacion y el verificador siguio en
verde: "mover la etiqueta (solo avisa)". Un aviso no detiene nada.

  python scripts/p9-etiqueta.py            # verificacion de cierre: sale 1
  python scripts/p9-etiqueta.py --rapido   # trabajo en local: avisa y sale 0

En trabajo local es normal ir por delante de la etiqueta (se mueve como ULTIMO
paso); por eso --rapido, igual que en el resto de verify.sh, degrada a aviso.
"""
import subprocess
import sys

TAG = "v1.1.0"


def git(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return r.returncode, r.stdout.strip()


def main():
    rapido = "--rapido" in sys.argv
    cod, tipo = git("cat-file", "-t", TAG)
    if cod != 0:
        print(f"[FAIL] no existe la etiqueta {TAG}")
        return 1
    if tipo != "tag":
        print(f"[FAIL] la etiqueta {TAG} es ligera ({tipo}); tiene que ser anotada")
        return 1
    _, tag_commit = git("rev-list", "-n1", TAG)
    _, head = git("rev-parse", "HEAD")
    _, n = git("rev-list", "--count", f"{TAG}..HEAD")
    print(f"  {TAG} -> {tag_commit[:7]}   HEAD -> {head[:7]}   desfase: {n}")
    if tag_commit == head:
        print("[OK] la etiqueta anotada apunta a HEAD: lo que se defiende es lo que esta etiquetado")
        return 0
    msg = (f"la etiqueta {TAG} va {n} commit(s) por detras de HEAD (o no es su ancestro): "
           f"lo que no esta dentro de la etiqueta no existe")
    if rapido:
        print(f"[WARN] {msg} -- se mueve como ultimo paso")
        return 0
    print(f"[FAIL] {msg}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
