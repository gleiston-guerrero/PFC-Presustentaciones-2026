#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""EV-1 -- Los bloques de VERIFICACION.md se reproducen literalmente, y la tabla no se contradice.

POR QUE EXISTE
--------------
EV-1 pide "VERIFICACION.md con orden, salida literal y archivo por punto". La
revision final lo dio por "cumple con defectos": reprodujo ocho bloques
literalmente y **tres no reproducen por cifras obsoletas** (Javadoc 734/768 frente
a 777/778, "403 commits" frente a 436, "3 pruebas" frente a 5), ademas de que
"la tabla resumen se contradice a si misma en dos filas".

Es el mismo defecto que el resto de este proyecto: una salida pegada a mano
envejece en cuanto alguien toca el codigo, y nada avisa. Se arregla igual que los
demas: que la salida no la escriba una persona, y que se compruebe.

BLOQUES REPRODUCIBLES
---------------------
Un bloque de comandos precedido por una linea `<!-- ev1:run -->` se ejecuta, y el
bloque de codigo que le sigue tiene que ser IGUAL a lo que imprime (se ignoran los
espacios finales y las lineas en blanco; nada mas). Variantes de la marca:

    <!-- ev1:run -->                            siempre
    <!-- ev1:run needs=backend/target/surefire-reports -->
                                                solo si esa ruta existe (lo que deja
                                                una suite de pruebas); si no, se omite
    <!-- ev1:run slow -->                       se omite con --rapido

Los bloques SIN marca son salidas historicas fechadas (una corrida de Maven, la
medicion "antes"): registran lo que paso ese dia y no se reejecutan.

TABLA Y RESUMEN
---------------
La tabla del principio da el estado de P1-P12. La seccion "Resumen de honestidad"
lo agrupa por categoria. Tienen que decir lo mismo: cada P esta en la categoria
que la tabla le da, y ninguna falta ni se repite.

Uso:
    python scripts/ev1-verificacion.py                 # comprueba
    python scripts/ev1-verificacion.py --actualizar    # reescribe las salidas marcadas
    python scripts/ev1-verificacion.py --rapido        # omite las marcadas `slow`

Sale con 1 si un bloque marcado no reproduce, o si tabla y resumen se contradicen.
"""
import io
import os
import re
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True

DOC = "VERIFICACION.md"
ENV = {**os.environ, "PYTHONIOENCODING": "utf-8", "PYTHONDONTWRITEBYTECODE": "1"}
BASH = None
RE_MARCA = re.compile(r"^<!--\s*ev1:run(?P<opts>[^>]*)-->\s*$")


def normalizar(txt):
    """Ignora espacios finales, saltos CRLF y lineas en blanco: nada mas."""
    return [l.rstrip() for l in txt.replace("\r\n", "\n").split("\n") if l.strip()]


def bloques(lineas):
    """(indice_marca, opts, (ini_cmd, fin_cmd), (ini_out, fin_out)) por cada marca."""
    out, i = [], 0
    while i < len(lineas):
        m = RE_MARCA.match(lineas[i])
        if not m:
            i += 1
            continue
        marca, opts = i, m.group("opts").strip()
        fences = []
        j = i + 1
        while j < len(lineas) and len(fences) < 2:
            if lineas[j].startswith("```"):
                k = j + 1
                while k < len(lineas) and not lineas[k].startswith("```"):
                    k += 1
                fences.append((j, k))       # [j] abre, [k] cierra
                j = k
            j += 1
        if len(fences) < 2:
            raise SystemExit(f"[FAIL] {DOC}:{marca + 1}: la marca ev1:run no va seguida de "
                             f"un bloque de comandos y un bloque de salida")
        out.append((marca, opts, fences[0], fences[1]))
        i = fences[1][1] + 1
    return out


def localizar_bash():
    """Un bash de verdad. En Windows, `bash` a secas suele ser el de WSL (System32), que
    no tiene /bin/bash y devuelve un error de WSL en lugar de la salida del comando: en la
    primera version esto reescribio todas las salidas con ese error."""
    if os.name != "nt":
        return "bash"
    r = subprocess.run(["git", "--exec-path"], capture_output=True, text=True)
    if r.returncode == 0:
        # <Git>/mingw64/libexec/git-core -> <Git>/usr/bin/bash.exe
        raiz = os.path.abspath(os.path.join(r.stdout.strip(), "..", "..", ".."))
        for cand in (os.path.join(raiz, "usr", "bin", "bash.exe"), os.path.join(raiz, "bin", "bash.exe")):
            if os.path.isfile(cand):
                return cand
    hallado = shutil.which("bash")
    if hallado and "system32" not in hallado.lower():
        return hallado
    raise SystemExit("[FAIL] no se encontro un bash utilizable (el de WSL en System32 no sirve)")


def ejecutar(comandos):
    r = subprocess.run([BASH, "-c", comandos], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", env=ENV)
    return r.stdout + r.stderr


def main():
    global BASH
    BASH = localizar_bash()
    actualizar = "--actualizar" in sys.argv
    rapido = "--rapido" in sys.argv
    crudo = io.open(DOC, encoding="utf-8", newline="").read()
    fin_linea = "\r\n" if "\r\n" in crudo else "\n"
    lineas = crudo.replace("\r\n", "\n").split("\n")

    fallos, corridos, omitidos = [], 0, 0
    cambios = []                                   # (ini, fin, nuevas lineas de salida)
    for marca, opts, (c_ini, c_fin), (o_ini, o_fin) in bloques(lineas):
        need = re.search(r"needs=(\S+)", opts)
        if "slow" in opts.split() and rapido:
            omitidos += 1
            continue
        if need and not os.path.exists(need.group(1)):
            omitidos += 1
            continue
        comandos = "\n".join(lineas[c_ini + 1:c_fin])
        real = ejecutar(comandos)
        if "execvpe(/bin/bash) failed" in real or "WSL" in real[:80]:
            raise SystemExit("[FAIL] el shell que se uso es el de WSL: no se escribe ni se compara nada")
        esperado = "\n".join(lineas[o_ini + 1:o_fin])
        corridos += 1
        if normalizar(real) == normalizar(esperado):
            continue
        if actualizar:
            cambios.append((o_ini + 1, o_fin, [l.rstrip() for l in real.replace("\r\n", "\n").split("\n")
                                               if l.strip()]))
            continue
        primera = next((f"esperado: {a!r}\n         real:     {b!r}"
                        for a, b in zip(normalizar(esperado) + [""] * 3, normalizar(real) + [""] * 3)
                        if a != b), "distinto largo")
        fallos.append(f"{DOC}:{marca + 1}: la salida documentada no es la que imprime "
                      f"`{comandos.splitlines()[0][:60]}`\n         {primera}")

    if actualizar:
        for ini, fin, nuevas in sorted(cambios, reverse=True):
            lineas[ini:fin] = nuevas
        io.open(DOC, "w", encoding="utf-8", newline="").write(fin_linea.join(lineas))
        print(f"[OK] {len(cambios)} bloque(s) de salida reescritos desde la corrida real")
        return 0

    print(f"  {corridos} bloque(s) marcados ejecutados, {omitidos} omitido(s)")
    for f in fallos:
        print(f"[FAIL] {f}")
    if corridos < 5 and not rapido:
        fallos.append("pocos bloques marcados")
        print(f"[FAIL] solo {corridos} bloques marcados con ev1:run: la marca dejo de casar")

    fallos += tabla_y_resumen("\n".join(lineas))
    if fallos:
        return 1
    print("[OK] los bloques marcados de VERIFICACION.md reproducen literalmente, y la tabla "
          "coincide con el resumen")
    return 0


CATEGORIAS = {"✅ Cumple": "cumple", "🟡 Parcial": "parcial", "🔴 No cumple": "no"}


def tabla_y_resumen(txt):
    """La tabla P1-P12 del principio y la seccion 'Resumen de honestidad' tienen que coincidir."""
    tabla = {}
    for m in re.finditer(r"^\|\s*P(\d{1,2})\s*\|\s*([^|]+?)\s*\|", txt, re.M):
        n = int(m.group(1))
        if n not in tabla:                      # la primera tabla es la vigente
            tabla[n] = m.group(2)
    if len(tabla) != 12:
        print(f"[FAIL] la tabla del principio tiene {len(tabla)} filas P, no 12")
        return ["tabla incompleta"]

    a = txt.find("## Resumen de honestidad")
    if a < 0:
        print("[FAIL] falta la seccion 'Resumen de honestidad'")
        return ["falta el resumen"]
    b = txt.find("\n## ", a + 5)
    resumen = txt[a:b if b > 0 else None]

    fallos, vistos = [], {}
    for m in re.finditer(r"^- \*\*([^*]+)\*\*\s*—\s*((?:P\d+(?:,\s*)?)+)", resumen, re.M):
        cat = m.group(1).strip()
        for n in re.findall(r"P(\d+)", m.group(2)):
            n = int(n)
            if n in vistos:
                fallos.append(f"P{n} aparece dos veces en el resumen ('{vistos[n]}' y '{cat}')")
            vistos[n] = cat
    for n in range(1, 13):
        if n not in vistos:
            fallos.append(f"P{n} no aparece en el resumen")
        elif tabla[n].split(" con ")[0].split(" en ")[0].strip() != vistos[n].split(" con ")[0].strip():
            fallos.append(f"P{n}: la tabla dice '{tabla[n]}' y el resumen '{vistos[n]}'")
    for f in fallos:
        print(f"[FAIL] {f}")
    return fallos


if __name__ == "__main__":
    sys.exit(main())
