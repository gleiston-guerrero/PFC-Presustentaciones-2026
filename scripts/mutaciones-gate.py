#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Arnes de mutaciones del verificador: inyecta un defecto y exige que el gate lo vea.

POR QUE EXISTE
--------------
Un verificador que nunca se ha visto fallar no demuestra nada. La revision del
19-sep lo comprobo con 15 mutaciones y encontro que 10 sobrevivian: el gate era
fuerte comparando codigo contra codigo y ciego comparando documento contra
medicion. Cada correccion de entonces se probo a mano, una vez; este arnes lo
deja como algo que se puede volver a ejecutar, para que el siguiente cambio a un
detector no lo deje ciego sin que nadie se entere.

COMO FUNCIONA
-------------
Para cada mutacion: (1) aplica el cambio al archivo, (2) corre los detectores que
deberian verlo, (3) exige que ALGUNO salga distinto de 0, (4) restaura el
archivo byte a byte. Antes de mutar, exige que los detectores pasen SIN mutar
(si ya fallaran, "detectar" la mutacion no probaria nada). Si el texto a mutar ya
no existe en el archivo, la mutacion falla: un cambio que no se aplico no es una
mutacion que sobrevivio, es un arnes roto.

Uso:
    python scripts/mutaciones-gate.py --nb salida-nbconvert.json
    python scripts/mutaciones-gate.py --nb X --solo M05,M09

Sin --nb se omiten las mutaciones de la familia de rendimiento (necesitan la
salida del cuaderno, que tarda ~25 s en ejecutarse).

Sale con 1 si sobrevive alguna mutacion.
"""
import os
import subprocess
import sys

PY = sys.executable
ENV = {**os.environ, "PYTHONDONTWRITEBYTECODE": "1", "PYTHONIOENCODING": "utf-8"}

CIFRAS = ("cifras-publicadas", [PY, "scripts/cifras-publicadas.py"])
DOC = ("ev2-documental", [PY, "scripts/ev2-documental.py"])
AUTZ = ("audit-endpoints-autorizacion",
        [PY, "docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py"])
SPEL = ("p8-spel-vivo", [PY, "scripts/p8-spel-vivo.py"])
ETIQ = ("p9-etiqueta", [PY, "scripts/p9-etiqueta.py"])
EV1 = ("ev1-verificacion", [PY, "scripts/ev1-verificacion.py", "--rapido"])
EV4 = ("ev4-contribuciones", [PY, "scripts/ev4-contribuciones.py", "--check"])
P3 = ("p3-param-tautologicos", [PY, "scripts/p3-param-tautologicos.py"])
SCHED = "backend/src/main/java/ec/edu/uteq/presustentaciones/repositories/ScheduleRepository.java"

JSON_LH = "docs/mediciones/perf/lighthouse/prod-runs/desktop-run1.json"
EVID = "docs/mediciones/sus/re-aplicacion/evidencia/apps-script-historial-11-16.png"
FIG = "Informe-Final/figuras/fig-lighthouse-scores.png"
CTRL = "backend/src/main/java/ec/edu/uteq/presustentaciones/controllers/AppUserController.java"
PERM = "@permissionService.hasPermission(authentication, 'USUARIOS_GESTIONAR')"
T10 = "Informe-Final/secciones/10-evaluacion-empirica.tex"
SUSR = "docs/mediciones/sus/SUS-RESULTS.md"
HOST = "https://steadfast-success-production-2b60.up.railway.app/"

# Mutaciones con operacion especial en lugar de reemplazo de texto.
TAG, BORRAR, VIEJA = "@TAG@", "@BORRAR@", "@VIEJA@"

# (id, descripcion, archivo, viejo, nuevo, detectores, necesita el cuaderno)
MUTACIONES = [
    ("M01", "informe: cobertura de lineas 82.01 -> 85.01", "Informe-Final/secciones/01-resumenes.tex",
     "código llega a 82.01", "código llega a 85.01", [CIFRAS], False),
    ("M02", "informe: cobertura de ramas 73.49 -> 78.49", T10,
     "73.49\\,\\% de ramas (1,483/2,018)", "78.49\\,\\% de ramas (1,483/2,018)", [CIFRAS], False),
    ("M03", "informe: numero de pruebas 806 -> 860", "Informe-Final/secciones/08-diseno-arquitectura.tex",
     "806 pruebas automatizadas reales", "860 pruebas automatizadas reales", [CIFRAS], False),
    ("M04", "README: badge de cobertura 82.01 -> 88.01", "README.md",
     "coverage-82.01%25_lines", "coverage-88.01%25_lines", [CIFRAS], False),
    ("M05", "informe: media del SUS 52.83 -> 55.83", T10,
     "media 52.83/100", "media 55.83/100", [DOC], False),
    ("M06", "informe: IC 95 % del SUS, limite superior 59.51 -> 61.51", T10,
     "[46.16, 59.51]", "[46.16, 61.51]", [DOC], False),
    ("M07", "SUS-RESULTS: media 52,83 -> 57,83", SUSR,
     "**SUS = 52,83**", "**SUS = 57,83**", [DOC], False),
    ("M08", "informe: alfa de Cronbach 0.599 -> 0.899", T10,
     "de Cronbach $= 0.599$", "de Cronbach $= 0.899$", [DOC], False),
    ("M09", "SUS-RESULTS: decision de Holm 'no rechaza H0' -> 'rechaza H0'", SUSR,
     "| 0,4408 | no rechaza H0 |", "| 0,4408 | rechaza H0 |", [DOC], False),
    ("M10", "informe: 'ninguno significativo' -> 'ambos significativos'", T10,
     "0.441 y 0.608 respectivamente, ninguno significativo",
     "0.441 y 0.608 respectivamente, ambos significativos", [DOC], False),
    ("M11", "informe: p ajustado del SUS 0.441 -> 0.041 y 'tampoco significativo' -> 'significativo'", T10,
     "con $p$ ajustado de 0.441 --- tampoco significativo",
     "con $p$ ajustado de 0.041 --- significativo", [DOC], False),
    ("M12", "SUS-RESULTS: '0,441 tras Holm' -> '0,041 tras Holm'", SUSR,
     "**0,441 tras Holm**", "**0,041 tras Holm**", [DOC], False),
    ("M13", "informe: Holm de rendimiento, decision 'Significativo' -> 'No significativo'", T10,
     "Permutación, mediana (p50) & $1\\times10^{-5}$ & $0.025$ & $2\\times10^{-5}$ & Significativo",
     "Permutación, mediana (p50) & $1\\times10^{-5}$ & $0.025$ & $2\\times10^{-5}$ & No significativo",
     [DOC], True),
    ("M14", "informe: Holm de rendimiento, p ajustado 9.06e-11 -> 9.06e-3", T10,
     "$9.06\\times10^{-11}$ & Significativo", "$9.06\\times10^{-3}$ & Significativo", [DOC], True),
    ("M15", "k6/README: Holm de rendimiento, p ajustado de p95 2e-5 -> 2e-2", "k6/README.md",
     "| Permutación, diferencia de p95 | 1×10⁻⁵ | 0.05 | 2×10⁻⁵ |",
     "| Permutación, diferencia de p95 | 1×10⁻⁵ | 0.05 | 2×10⁻² |", [DOC], True),
    ("M16", "Lighthouse: una corrida versionada apunta a localhost", JSON_LH,
     HOST, "http://localhost:4200/", [DOC], False),
    ("M17", "informe: declara Lighthouse contra localhost en vez del despliegue publico", T10,
     "steadfast-success-production-2b60.up.railway.app", "localhost:4200", [DOC], False),
    ("M18", "Makefile: make bench-lh mide localhost por defecto", "Makefile",
     "LH_URL ?= " + HOST, "LH_URL ?= http://localhost:4200/", [DOC], False),
    ("M19", "la figura de Lighthouse del informe es una version vieja (la de 68/61)", FIG,
     VIEJA, None, [DOC], False),
    ("M20", "se borra un archivo de evidencia citado", EVID, BORRAR, None, [DOC], False),
    ("M21", "se mueve la etiqueta v1.1.0 (anotada) a otro commit", None, TAG, None, [ETIQ], False),
    ("M24", "VERIFICACION: fraccion de lineas vencida 4022/4904 -> 4019/4901", "VERIFICACION.md",
     "LINE **82,01 %** (4022/4904)", "LINE **82,01 %** (4019/4901)", [CIFRAS], False),
    ("M25", "P3: un Javadoc apilado sobre otro (la prosa del primero se pierde)", SCHED,
     "    /**\n     * Busca el/los registro(s) con submission id.",
     "    /** prosa que javadoc descarta */\n    /**\n     * Busca el/los registro(s) con submission id.", [P3], False),
    ("M26", "P3: un comentario Javadoc metido dentro de una consulta JPQL", SCHED,
     "          AND c.dateStart < :fin\n", "          AND c.dateStart < :fin\n          /** basura */\n", [P3], False),
    ("M27", "EV-1: una salida pegada en VERIFICACION.md queda obsoleta (Javadoc 778 -> 768)", "VERIFICACION.md",
     "metodos de interfaz): 778", "metodos de interfaz): 768", [EV1], False),
    ("M28", "EV-1: la tabla dice P3 Parcial y el resumen lo agrupa como Cumple", "VERIFICACION.md",
     "| P3 | ✅ Cumple |", "| P3 | 🟡 Parcial |", [EV1], False),
    ("M29", "EV-4: la tabla historica de CONTRIBUCIONES.md declara un commit de mas para un integrante", "CONTRIBUCIONES.md",
     "| Moncayo Loor, Xavier Alejandro | `XAML25 <xavierloor52@gmail.com>` (13) | 13 |",
     "| Moncayo Loor, Xavier Alejandro | `XAML25 <xavierloor52@gmail.com>` (13) | 14 |", [EV4], False),
    ("M30", "7.4: CONTRIBUTORS.md vuelve a decir que un companero se retiro de la carrera", "CONTRIBUTORS.md",
     "diseño de interfaz. No tiene ORCID", "diseño de interfaz. Se retiró de la carrera. No tiene ORCID", [DOC], False),
    ("M31", "7.4: CITATION.cff vuelve a decir que un companero ya no forma parte del programa", "CITATION.cff",
     "# orcid: no registrado a la fecha de esta version", "# orcid: no registrado -- ya no forma parte del programa", [DOC], False),
    ("M32", "7.4: la bitacora vuelve a decir que tres companeros reprobaron la materia", "docs/observaciones/BITACORA-COMMITS-2026-09-02.md",
     "no participan"+chr(10)+"en esta ronda de recuperación", "reprobaron"+chr(10)+"la materia en esta ronda de recuperación", [DOC], False),
    ("M22", "se retira el @PreAuthorize de un endpoint de escritura (POST)", CTRL,
     '    @PreAuthorize("' + PERM + '")\n    @Operation(summary = "Crear nuevo usuario (solo ADMIN)")',
     '    @Operation(summary = "Crear nuevo usuario (solo ADMIN)")', [AUTZ], False),
    ("M23", "el SpEL de @PreAuthorize apunta a un bean inexistente", CTRL,
     '"' + PERM + '"',
     '"@beanQueNoExiste.hasPermission(authentication, \'USUARIOS_GESTIONAR\')"', [SPEL], False),
]


def correr(det, nb):
    nombre, cmd = det
    cmd = list(cmd)
    if nombre == "ev2-documental" and nb:
        cmd += ["--nb", nb]
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                          errors="replace", env=ENV).returncode


def git(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8",
                       errors="replace")
    return r.stdout.strip()


def main():
    args = sys.argv[1:]
    nb = args[args.index("--nb") + 1] if "--nb" in args else None
    solo = set(args[args.index("--solo") + 1].split(",")) if "--solo" in args else None

    print("Linea base: los detectores tienen que pasar SIN mutar")
    for d in (CIFRAS, DOC, AUTZ, SPEL, P3, EV1, EV4, ETIQ):
        rc = correr(d, nb)
        print(f"  [{'OK  ' if rc == 0 else 'FAIL'}] {d[0]}")
        if rc != 0 and d is not ETIQ:
            print("\nEl arnes no puede mutar sobre una linea base que ya falla.")
            return 2
    # La etiqueta va por detras de HEAD mientras se trabaja: para que la mutacion M21
    # signifique algo, el arnes la compara contra HEAD igualada (ver M21 abajo).
    print()

    resultados = []
    for mid, desc, ruta, viejo, nuevo, dets, necesita_nb in MUTACIONES:
        if solo and mid not in solo:
            continue
        if necesita_nb and not nb:
            resultados.append((mid, desc, "OMITIDA", "necesita --nb"))
            continue
        original, previo = None, None
        try:
            if viejo == TAG:
                previo = git("rev-parse", "refs/tags/v1.1.0")
                # Se alinea primero la etiqueta con HEAD (estado sano) y luego se mueve
                # a otro commit: asi lo unico que cambia es el movimiento.
                subprocess.run(["git", "tag", "-f", "-a", "-m", "sano", "v1.1.0", "HEAD"],
                               capture_output=True, env=ENV)
                if correr(ETIQ, nb) != 0:
                    resultados.append((mid, desc, "ARNES", "la linea base de la etiqueta no pasa"))
                    continue
                subprocess.run(["git", "tag", "-f", "-a", "-m", "mutacion", "v1.1.0", "HEAD~3"],
                               capture_output=True, env=ENV)
            elif viejo == BORRAR:
                original = open(ruta, "rb").read()
                os.remove(ruta)
            elif viejo == VIEJA:
                original = open(ruta, "rb").read()
                vieja = subprocess.run(["git", "show", "a0dead6:" + ruta],
                                       capture_output=True).stdout
                if not vieja or vieja == original:
                    resultados.append((mid, desc, "ARNES", "no hay una version vieja distinta"))
                    continue
                open(ruta, "wb").write(vieja)
            else:
                original = open(ruta, "rb").read()
                txt = original.decode("utf-8")
                if "\r\n" in txt:
                    # Los fuentes del repositorio pueden ser CRLF: la mutacion se
                    # escribe con \n y se adapta, en vez de fallar por un salto de linea.
                    viejo, nuevo = viejo.replace("\n", "\r\n"), (nuevo or "").replace("\n", "\r\n")
                if viejo not in txt:
                    resultados.append((mid, desc, "ARNES", f"el texto a mutar ya no esta en {ruta}"))
                    continue
                open(ruta, "wb").write(txt.replace(viejo, nuevo, 1).encode("utf-8"))
            quienes = [d[0] for d in dets if correr(d, nb) != 0]
            resultados.append((mid, desc, "detectada" if quienes else "SOBREVIVE", ", ".join(quienes)))
        finally:
            if previo is not None:
                subprocess.run(["git", "update-ref", "refs/tags/v1.1.0", previo], capture_output=True)
            elif original is not None:
                open(ruta, "wb").write(original)

    print(f"{'id':<5}{'estado':<11}{'detector':<24}mutacion")
    for mid, desc, est, q in resultados:
        print(f"{mid:<5}{est:<11}{q[:22]:<24}{desc}")
    malos = [r for r in resultados if r[2] in ("SOBREVIVE", "ARNES")]
    print(f"\n{len([r for r in resultados if r[2] == 'detectada'])} detectada(s), "
          f"{len(malos)} sobreviven o arnes roto, "
          f"{len([r for r in resultados if r[2] == 'OMITIDA'])} omitida(s)")
    return 1 if malos else 0


if __name__ == "__main__":
    sys.exit(main())
