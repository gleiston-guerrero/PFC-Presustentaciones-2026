#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P4 -- Cuantos metodos @Test siguen con palabras en espanol en su nombre.

POR QUE EXISTE
--------------
P4 pide nombres en ingles. `p4-nombres-espanol.py` mide identificadores de DOMINIO
(su lexico son terminos del sistema: solicitud, acta, docente...) y daba 0 %, pero el
evaluador cuenta tambien los nombres de los metodos de prueba, y esos eran frases en
espanol enteras: `saveEvaluationLanzaExcepcionSiLaSubmissionNoExists`. Por eso P4 salia
con 40 % en la lectura estricta aunque el lexico de dominio estuviera limpio, y
`verify.sh` lo reconocia con una advertencia cuya cifra estaba ESCRITA A MANO
("436 de 807"; el conteo real, con este diccionario, era 781 de 809).

Este script mide lo que faltaba: parte cada nombre de metodo @Test en palabras
(camelCase) y cuenta los que contienen alguna palabra del diccionario espanol->ingles
de `scripts/p4-diccionario-es-en.txt` (verbos de prueba -devuelve, lanza, rechaza-,
articulos, conectores y terminos comunes).

Limite declarado: es una lista finita. Una palabra espanola que no este en ella no se
ve; por eso la tolerancia es cero y el diccionario se amplia cuando aparece una.

Uso:
    python scripts/p4-tests-espanol.py           # resumen
    python scripts/p4-tests-espanol.py --lista   # cada nombre

Sale con 1 si queda algun metodo @Test con una palabra del diccionario.
"""
import glob
import io
import os
import re
import sys

sys.dont_write_bytecode = True

DICCIONARIO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "p4-diccionario-es-en.txt")

RE_TOK = re.compile(r"[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\d+")
RE_TEST = re.compile(r"@(?:Test|ParameterizedTest|RepeatedTest)\b[^\n]*\n(?:\s*@[^\n]*\n)*\s*"
                     r"(?:public |protected |private )?(?:static )?void\s+(\w+)\s*\(")

# Palabras del diccionario que tambien son inglesas o siglas: no cuentan como espanol.
INGLES = {"a", "e", "as", "mal", "par", "base", "total", "visible", "error", "final", "principal",
          "lista", "actual", "real", "general", "manual", "normal", "local", "no", "son", "sin",
          "sus", "hay", "sea", "ser", "tal", "una", "un", "cuenta", "dato", "solo", "es"}


def diccionario():
    es = {}
    for linea in io.open(DICCIONARIO, encoding="utf-8"):
        linea = linea.strip()
        if linea and not linea.startswith("#") and ":" in linea:
            k, v = linea.split(":", 1)
            es[k] = v
    return es


def main():
    es = diccionario()
    # Las palabras ambiguas (solo, no, sin...) SI cuentan cuando estan en la lista de espanol
    # de uso en nombres de prueba: las que son inglesas de verdad se excluyen a mano.
    ambiguas_ok = {"no", "sin", "son", "es", "hay", "sea", "ser", "solo", "una", "un", "cuenta", "dato", "tal", "sus"}
    excluir = INGLES - ambiguas_ok
    total, malos = 0, []
    for f in sorted(glob.glob("backend/src/test/java/**/*.java", recursive=True)):
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        for m in RE_TEST.finditer(txt):
            total += 1
            e = [t for t in RE_TOK.findall(m.group(1))
                 if t.lower() in es and t.lower() not in excluir and not t.isdigit()]
            if e:
                malos.append((os.path.basename(f), m.group(1), e))
    if total < 500 or len(es) < 300:
        print(f"[FAIL] solo {total} metodos @Test o {len(es)} palabras en el diccionario: la lectura dejo de casar")
        return 1
    print(f"metodos @Test: {total}   con palabras en espanol: {len(malos)} ({100 * len(malos) / total:.1f} %)   "
          f"diccionario: {len(es)} palabras")
    if malos:
        for arch, n, e in malos[:40]:
            print(f"   {arch}: {n}   <- {', '.join(sorted(set(x.lower() for x in e)))}")
        print(f"[FAIL] {len(malos)} metodo(s) @Test con nombre en espanol")
        return 1
    print("[OK] ningun metodo @Test con palabras del diccionario espanol")
    return 0


if __name__ == "__main__":
    sys.exit(main())
