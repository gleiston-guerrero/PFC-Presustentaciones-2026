#!/usr/bin/env python3
"""P4 -- Auditoria del contrato JSON entre el backend Java y el frontend Angular.

POR QUE EXISTE
--------------
El renombrado masivo de P4 (`49adaee`) tradujo nombres de campo del espanol al
ingles y uso `@JsonProperty` para preservar el nombre que Angular ya consumia.
Lo aplico de forma **inconsistente**: en la misma clase hay campos anotados y
campos renombrados sin anotar. Cada campo sin anotar es un contrato roto: el
backend emite `notaPanelist` y Angular lee `notaJurado`, que llega `undefined`.

La evaluacion integral del 2026-09-17 encontro esta clase de fallo a mano
(`evaluar-ponderado`). Las revisiones posteriores la corrigieron caso por caso,
sin una comprobacion sistematica -- por eso quedaron 23 campos rotos que este
script encontro el 2026-09-18.

QUE HACE
--------
1. Calcula, para cada DTO/entidad, el nombre JSON que **realmente** emite cada
   campo: el de `@JsonProperty("...")` si existe, y si no el nombre del campo
   Java (que es lo que hace Jackson por defecto).
2. Extrae cada `interface` de TypeScript del frontend con sus propiedades,
   resolviendo `extends` para heredar las del padre.
3. Empareja cada interfaz con el DTO que mejor la explica (mayor solapamiento
   de nombres) y reporta las propiedades que Angular declara y el DTO no emite.

El emparejamiento por solapamiento es una heuristica, no un analisis de rutas:
por eso se exige un minimo de 3 campos en comun antes de comparar, y se
mantiene abajo una lista explicita de propiedades que son de UI y nunca vienen
del backend.

LIMITACION DECLARADA
--------------------
Solo cubre lo que esta **tipado**. El frontend tiene ~45 metodos de servicio que
devuelven `Observable<any>`; para esos no hay interfaz que comparar y este
script no puede decir nada. No se declara como auditoria exhaustiva.

Uso:
    python scripts/p4-contrato-json.py            # solo desajustes
    python scripts/p4-contrato-json.py --todo     # tambien los pares que cuadran

Sale con codigo 1 si encuentra algun desajuste, para poder usarlo como gate.
"""
import os
import re
import sys

DTO_ROOTS = [
    "backend/src/main/java/ec/edu/uteq/presustentaciones/dto/",
    "backend/src/main/java/ec/edu/uteq/presustentaciones/entities/",
]
FRONT_ROOT = "Frontend/src"

FIELD_RE = re.compile(
    r'((?:@\w+(?:\([^)]*\))?\s*)*)private\s+[\w<>\[\],\.\? ]+\s+(\w+)\s*[;=]'
)
JSONPROP_RE = re.compile(r'@JsonProperty\(\s*"([^"]+)"')
IFACE_RE = re.compile(
    r'(?:export\s+)?interface\s+(\w+)(?:\s+extends\s+([\w,\s]+?))?\s*\{(.*?)\n\}',
    re.S,
)
PROP_RE = re.compile(r'^\s{2,}(\w+)\s*\??\s*:', re.M)

# Propiedades que viven solo en el cliente (menus, filtros de query, estado de
# UI) y que por definicion ningun DTO emite. Se excluyen para que el reporte no
# se llene de falsos positivos.
SOLO_UI = {
    "icon", "image", "desc", "permiso", "label", "isBot", "text",
    "selector", "standalone", "imports", "templateUrl", "styleUrls",
    "encapsulation",
    # FiltroActas viaja como query params (HttpParams), no como cuerpo JSON:
    # el backend los recibe via @RequestParam/Pageable, no via un DTO.
    "page", "size", "q", "desde", "hasta",
}

MIN_SOLAPE = 3


def dtos_del_backend():
    """nombre del DTO -> {nombre_json: nombre_campo_java}"""
    out = {}
    for root in DTO_ROOTS:
        if not os.path.isdir(root):
            continue
        for fn in sorted(os.listdir(root)):
            if not fn.endswith(".java"):
                continue
            txt = open(root + fn, encoding="utf-8", errors="replace").read()
            campos = {}
            for m in FIELD_RE.finditer(txt):
                jp = JSONPROP_RE.search(m.group(1))
                campos[jp.group(1) if jp else m.group(2)] = m.group(2)
            if campos:
                out[fn[:-5]] = campos
    return out


def interfaces_del_frontend():
    """nombre de la interfaz -> set de propiedades (con las heredadas)"""
    ifaces, extiende = {}, {}
    for dirpath, _, fns in os.walk(FRONT_ROOT):
        for fn in fns:
            if not fn.endswith(".ts"):
                continue
            txt = open(os.path.join(dirpath, fn),
                       encoding="utf-8", errors="replace").read()
            for m in IFACE_RE.finditer(txt):
                props = {p.group(1) for p in PROP_RE.finditer(m.group(3))}
                if not props:
                    continue
                ifaces[m.group(1)] = props
                if m.group(2):
                    extiende[m.group(1)] = [x.strip()
                                            for x in m.group(2).split(",")]
    for hijo, padres in extiende.items():
        for p in padres:
            if p in ifaces:
                ifaces[hijo] |= ifaces[p]
    return ifaces


def main():
    dtos = dtos_del_backend()
    ifaces = interfaces_del_frontend()
    if not dtos or not ifaces:
        print("ERROR: corre este script desde la raiz del repositorio.")
        return 2

    print(f"DTOs/entidades analizados: {len(dtos)}")
    print(f"Interfaces TypeScript analizadas: {len(ifaces)}")
    print()

    desajustes = 0
    for iname, props in sorted(ifaces.items()):
        if len(props) < MIN_SOLAPE:
            continue
        mejor, score = None, 0
        for dname, campos in dtos.items():
            s = len(props & set(campos))
            if s > score:
                mejor, score = dname, s
        if not mejor or score < MIN_SOLAPE:
            continue
        faltan = (props - set(dtos[mejor])) - SOLO_UI
        if faltan:
            desajustes += 1
            print(f"  [ROTO] {iname} <-> {mejor} (coinciden {score}/{len(props)})")
            for f in sorted(faltan):
                print(f"           Angular lee '{f}' y el DTO no lo emite")
        elif "--todo" in sys.argv:
            print(f"  [OK]   {iname} <-> {mejor} ({score}/{len(props)})")

    print()
    if desajustes:
        print(f"*** {desajustes} contratos rotos. Se corrigen agregando"
              ' @JsonProperty("<nombre que ya usa Angular>") al campo, sin'
              " tocar el frontend. ***")
        return 1
    print("Sin desajustes de contrato en lo que esta tipado.")
    print("Recordatorio: ~45 metodos del frontend devuelven Observable<any>"
          " y quedan fuera de esta comprobacion.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
