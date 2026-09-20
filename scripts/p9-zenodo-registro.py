#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P9 -- Los metadatos del registro de Zenodo dicen lo mismo que el repositorio.

POR QUE EXISTE
--------------
La revision final dio P9 "cumple con reservas": el tarball depositado es bit a
bit el `git archive` del commit declarado, "pero los metadatos del registro
estan mal en tres ejes: apuntan a la cuenta antigua y a la v1.0.0, y no enlazan
el dataset". Cada uno de los tres se comprueba aqui contra la API publica de
Zenodo, no contra lo que este repositorio DICE haber puesto en el formulario:

  1. el repositorio de codigo del registro (`code:codeRepository`) es la cuenta
     vigente, la de `repository-code` de CITATION.cff
  2. ningun identificador relacionado apunta a la cuenta antigua ni a una version
     anterior; el que enlaza el codigo apunta a `tree/v1.1.0`
  3. el registro enlaza el DOI del dataset (`isSupplementedBy`)

Ademas: el campo Version es `v1.1.0` (ya fallo una vez como "v3") y la
descripcion no contiene el texto de una instruccion pegada por error (tambien
fallo una vez).

Los datos esperados salen de CITATION.cff, docs/ZENODO.md y docs/ZENODO-DATASET.md, no de
constantes aqui: si se escribieran en dos sitios, envejecerian en uno.

Uso:
    python scripts/p9-zenodo-registro.py            # consulta la API publica
    python scripts/p9-zenodo-registro.py --rapido   # sin red: avisa y sale 0

Sale con 1 si el registro contradice al repositorio, o si no se pudo consultar
(sin --rapido: un registro que no se puede leer no se puede dar por bueno).
"""
import io
import json
import re
import sys
import urllib.error
import urllib.request

sys.dont_write_bytecode = True

TAG = "v1.1.0"


def leer(ruta):
    return io.open(ruta, encoding="utf-8", errors="replace").read()


def esperado():
    cff = leer("CITATION.cff")
    repo = re.search(r'^repository-code:\s*"([^"]+)"', cff, re.M).group(1)
    # El registro que se comprueba es el que docs/ZENODO.md declara como el de esta version: asi,
    # al archivar una version nueva basta registrarla ahi (antes se leia de una descripcion de
    # CITATION.cff que hay que reescribir a mano y que envejecia).
    zen = leer("docs/ZENODO.md")
    doi_v = re.search(r"DOI de esta versi[oó]n\s*\|\s*\[10\.5281/zenodo\.(\d+)\]", zen).group(1)
    ds = leer("docs/ZENODO-DATASET.md")
    doi_ds = re.search(r"\*\*DOI \(esta versi[oó]n, v1\):\*\*\s*`10\.5281/zenodo\.(\d+)`", ds).group(1)
    return repo, doi_v, doi_ds


def registro(id_):
    req = urllib.request.Request(f"https://zenodo.org/api/records/{id_}",
                                 headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def main():
    rapido = "--rapido" in sys.argv
    repo, id_reg, id_ds = esperado()
    esp_tree = f"{repo}/tree/{TAG}"
    try:
        d = registro(id_reg)
    except (urllib.error.URLError, OSError, ValueError) as e:
        msg = f"no se pudo consultar https://zenodo.org/api/records/{id_reg}: {e}"
        if rapido:
            print(f"[WARN] {msg} -- omitido por --rapido")
            return 0
        print(f"[FAIL] {msg}")
        return 1

    m = d.get("metadata", {})
    print(f"  registro {id_reg}: version={m.get('version')!r}  fecha={m.get('publication_date')}")
    fallos = []

    if m.get("version") != TAG:
        fallos.append(f"el campo Version es {m.get('version')!r}, no {TAG!r}")

    cod = (m.get("custom") or {}).get("code:codeRepository")
    if cod != repo:
        fallos.append(f"eje 1: el repositorio de codigo del registro es {cod!r}; el vigente "
                      f"(CITATION.cff) es {repo!r}")

    rel = m.get("related_identifiers") or []
    for r in rel:
        ident = r.get("identifier", "")
        if "carla22072004" in ident:
            fallos.append(f"eje 2: un identificador relacionado apunta a la cuenta antigua: {ident}")
        elif re.search(r"/tree/v\d+\.\d+\.\d+$", ident) and not ident.endswith(f"/tree/{TAG}"):
            fallos.append(f"eje 2: un identificador relacionado apunta a otra version: {ident}")
    if not any(r.get("identifier") == esp_tree for r in rel):
        fallos.append(f"eje 2: ningun identificador relacionado apunta a {esp_tree}")

    if not any(id_ds in r.get("identifier", "") and r.get("relation") == "isSupplementedBy" for r in rel):
        fallos.append(f"eje 3: el registro no enlaza el dataset (isSupplementedBy 10.5281/zenodo.{id_ds})")

    desc = m.get("description", "")
    if re.search(r"bloque de docs/|ZENODO-METADATOS", desc):
        fallos.append("la descripcion contiene el texto de una instruccion pegada por error")

    for f in fallos:
        print(f"[FAIL] {f}")
    if fallos:
        print("Se corrige en zenodo.org > el registro > Editar (los metadatos se pueden editar sin "
              "subir una version nueva). Los valores exactos estan en docs/ZENODO.md.")
        return 1
    print(f"[OK] el registro de Zenodo coincide con el repositorio: cuenta vigente, {TAG}, "
          f"dataset 10.5281/zenodo.{id_ds} enlazado")
    return 0


if __name__ == "__main__":
    sys.exit(main())
