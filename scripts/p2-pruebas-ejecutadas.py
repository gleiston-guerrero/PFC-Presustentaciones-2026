#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P2 -- Toda prueba anotada @Test se ejecuta de verdad.

POR QUE EXISTE
--------------
La revision final contrasto los 809 metodos @Test que cuenta el AST con las 806 pruebas de la
corrida de cierre y pidio "explicar la brecha". La brecha era un defecto real: tres pruebas de
`PasswordPolicyValidatorTest` estaban en una clase `static` anidada SIN `@Nested`. JUnit 5 no
descubre una clase estatica anidada y Surefire excluye por defecto las clases internas (`$`),
asi que esas pruebas existian, se contaban como pruebas del proyecto, y no se habian ejecutado
nunca. Un conteo de pruebas que incluye pruebas que no corren es una cifra que miente.

QUE COMPRUEBA
-------------
  estatico  (siempre, sin compilar): ninguna clase `static` anidada dentro de una clase de
            prueba contiene metodos @Test. Si los tiene y no es `@Nested`, no corre.
  ejecutado (si hay informes de Surefire, es decir, despues de una corrida de la suite): cada
            metodo @Test que hay en el codigo fuente aparece entre los casos que Surefire
            ejecuto. La cuenta de anotados y de ejecutados tiene que coincidir.

Uso:
    python scripts/p2-pruebas-ejecutadas.py

Sale con 1 si hay una prueba anotada que no corre.
"""
import glob
import io
import os
import re
import sys
import xml.etree.ElementTree as ET
import collections

sys.dont_write_bytecode = True

FUENTES = "backend/src/test/java"
INFORMES = "backend/target/surefire-reports"

RE_ANOT = r"@(?:[A-Za-z_.]+\.)?(?:Test|ParameterizedTest|RepeatedTest)\b"   # tambien @org.junit.jupiter.api.Test
RE_METODO = re.compile(RE_ANOT + r"[^\n]*\n(?:\s*@[^\n]*\n)*\s*(?:public |protected |private )?(?:static )?void\s+(\w+)\s*\(")
RE_ESTATICA = re.compile(r"^[ \t]+((?:@\w+(?:\([^)]*\))?\s+)*)(?:public\s+|protected\s+|private\s+)?static\s+class\s+(\w+)", re.M)


def fuentes():
    return sorted(glob.glob(os.path.join(FUENTES, "**", "*.java"), recursive=True))


def estatico():
    """Clases estaticas anidadas con @Test que JUnit no descubre."""
    malos = []
    for f in fuentes():
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        for m in RE_ESTATICA.finditer(txt):
            anot, nombre = m.group(1), m.group(2)
            # cuerpo de la clase anidada: desde su llave de apertura hasta la que la cierra
            i = txt.index("{", m.end())
            prof, j = 0, i
            while j < len(txt):
                prof += (txt[j] == "{") - (txt[j] == "}")
                j += 1
                if prof == 0:
                    break
            if re.search(RE_ANOT, txt[i:j]) and "@Nested" not in anot:
                malos.append((f.replace("\\", "/"), nombre, len(re.findall(RE_ANOT, txt[i:j]))))
    return malos


def ejecutado():
    """(anotados, ejecutados, faltan) contra los informes de Surefire."""
    if not os.path.isdir(INFORMES):
        return None
    corrieron = collections.defaultdict(set)
    for x in glob.glob(os.path.join(INFORMES, "TEST-*.xml")):
        for tc in ET.parse(x).getroot().iter("testcase"):
            base = tc.get("classname", "").split("$")[0].split(".")[-1]
            corrieron[base].add(re.sub(r"\(.*", "", tc.get("name", "")))
    anotados, faltan = 0, []
    for f in fuentes():
        clase = os.path.basename(f)[:-5]
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        for n in RE_METODO.findall(txt):
            anotados += 1
            if n not in corrieron.get(clase, set()):
                faltan.append(f"{clase}.{n}")
    ejecutados = sum(len(v) for v in corrieron.values())
    return anotados, ejecutados, faltan


def main():
    malos = estatico()
    for f, nombre, n in malos:
        print(f"[FAIL] {f}: la clase estatica anidada `{nombre}` tiene {n} metodo(s) @Test que JUnit no "
              f"descubre (sin @Nested no se ejecutan; sacala a su propio archivo)")
    if not malos:
        print("[OK] ninguna clase estatica anidada con @Test (todas las pruebas son descubribles)")

    r = ejecutado()
    if r is None:
        print("[WARN] sin informes de Surefire: solo se comprobo lo estatico "
              "(corre la suite y vuelve a ejecutar esto)")
        return 1 if malos else 0
    anotados, ejecutados, faltan = r
    print(f"  metodos @Test en el codigo: {anotados}   casos ejecutados por Surefire: {ejecutados}")
    for n in faltan[:20]:
        print(f"[FAIL] anotado y no ejecutado: {n}")
    if faltan:
        print(f"[FAIL] {len(faltan)} prueba(s) anotada(s) que la suite no ejecuto")
    else:
        print("[OK] toda prueba anotada @Test aparece entre las ejecutadas")
    return 1 if (malos or faltan) else 0


if __name__ == "__main__":
    sys.exit(main())
