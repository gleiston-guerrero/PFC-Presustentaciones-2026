#!/usr/bin/env python3
"""P11 -- Comprueba que toda clase Java citada en el informe exista de verdad.

POR QUE
-------
La evaluacion del 2026-09-02 y la del 2026-09-17 senalan lo mismo: el informe
cita clases que ya no existen tras un renombrado (`UsuarioController`,
`EvaluacionController`). El chequeo que habia en `verify.sh` buscaba nueve
raices en espanol concretas, asi que solo veia el caso que ya se conocia. Con
esa lista quedaron 22 citas muertas sin detectar -- unas preexistentes
(`AnteproyectoController`, `AuditoriaService`, `PermisoService`) y otras
producidas por el renombrado de P4 (`EstadoTiempoRealController`,
`SupresionDatosService`).

Este script invierte la comprobacion: en vez de buscar nombres viejos
conocidos, toma **cada** identificador con forma de clase Java citado en el
informe o el SRS y verifica que exista el `.java` correspondiente. Asi detecta
tambien los renombrados que nadie anticipo.

EXCEPCION DELIBERADA
--------------------
La matriz de trazabilidad nombra a proposito un test que no existe, y lo dice
en el mismo parentesis: "ninguna (SalaServiceImplTest.java no existe)". Esa
cita es correcta -- afirma una ausencia. Se reconoce por el texto
"<Nombre>.java no existe" y no se cuenta como error.

Uso:
    python scripts/p11-citas-clases.py            # lista las citas muertas
    python scripts/p11-citas-clases.py --count    # solo el numero, para verify.sh

Sale con 1 si encuentra alguna.
"""
import glob
import os
import re
import sys

SUFIJOS = (
    "Controller", "ServiceImpl", "Service", "Repository", "Scheduler",
    "ServiceImplTest", "ControllerTest", "IntegrationTest", "ApplicationTests",
)
CIT = re.compile(r"\b[A-Z][A-Za-z0-9]*(?:" + "|".join(SUFIJOS) + r")\b")

FUENTES = ["Informe-Final/secciones/*.tex", "docs/requisitos/*.tex"]


def main():
    reales = {os.path.basename(p)[:-5]
              for p in glob.glob("backend/src/**/*.java", recursive=True)}
    if not reales:
        print("ERROR: corre este script desde la raiz del repositorio.")
        return 2

    malas = {}
    for patron in FUENTES:
        for f in glob.glob(patron):
            txt = open(f, encoding="utf-8", errors="replace").read()
            for m in CIT.finditer(txt):
                n = m.group(0)
                if n in reales:
                    continue
                # el documento declara explicitamente que ese archivo no existe
                if f"{n}.java" in txt and "no existe" in txt:
                    ventana = txt[max(0, txt.find(n) - 40): txt.find(n) + 80]
                    if "no existe" in ventana:
                        continue
                malas.setdefault(n, set()).add(os.path.basename(f))

    if "--count" in sys.argv:
        print(len(malas))
        return 1 if malas else 0

    if not malas:
        print(f"OK: las {len(reales)} clases del backend estan al dia;"
              " ninguna cita del informe apunta a una clase inexistente.")
        return 0
    print(f"{len(malas)} clase(s) citadas en el informe que NO existen:\n")
    for n in sorted(malas):
        print(f"  {n:46s} {', '.join(sorted(malas[n]))}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
