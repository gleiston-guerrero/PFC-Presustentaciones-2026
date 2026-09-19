#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Busca contrasenas escritas en claro en los archivos versionados.

POR QUE
-------
La revision del 18-sep senalo dos: `admin123` en k6/load-test.js:28 y
`postgreAdmin19` en backend/INSTRUCCIONES.md:64. Ninguna era de produccion --
la primera era la cuenta sembrada del perfil `dev`, la segunda la base local de
Docker-- pero ese argumento no lo puede comprobar quien lee el repositorio: una
cadena con pinta de credencial se lee como una credencial.

Las dos se retiraron. Esto existe para que no vuelvan, y revisa solo los
archivos que git rastrea, que son los que ve cualquiera que clone.

QUE CUENTA COMO HALLAZGO
------------------------
Un valor literal asignado a una clave de aspecto credencial, en cualquiera de
sus dos formas: entrecomillado (`password: 'admin123'`) o suelto al estilo
.properties (`spring.datasource.password=postgreAdmin19`). Hacen falta las dos,
porque los dos hallazgos originales tenian una forma distinta cada uno.

Ademas el valor tiene que contener algun digito: uno sin ninguno casi nunca es
una contrasena real y casi siempre es una palabra de prueba.

QUE NO CUENTA, Y POR QUE
------------------------
- Expresiones, no literales. Una primera version aceptaba cualquier valor y
  marco 28 sitios, todos codigo normal (`token = jwtService.generateToken(...)`,
  `token = localStorage.getItem(...)`). Un detector que grita en falso se
  termina ignorando, que es el mismo defecto que se esta corrigiendo: un [OK]
  que nadie cree no verifica nada.
- `.env.example`: es una plantilla; su proposito es declarar que variables hay
  que definir, y sus valores no abren nada.
- `src/test/` y `*.spec.ts`: fixtures sinteticos, necesarios para que la prueba
  corra, que no autentican contra ningun sistema.
- Lo que este en CITADAS, una lista explicita. Antes esto era una busqueda de
  frases ("se retiro", "hallazgo") alrededor del hallazgo, y resulto demasiado
  laxa: un archivo que mencionara "hallazgo" en otro parrafo silenciaba una
  credencial real. Se comprobo que lo hacia. Una lista explicita no puede
  tragarse nada sin que quede escrito.

PROBADO CONTRA
--------------
Las dos formas del hallazgo original disparan; las cinco formas de codigo normal
que producian falsos positivos, no. Reintroducir cualquiera de las dos lineas
que senalo la revision hace fallar esta comprobacion.

Uso:
    python scripts/ev2-credenciales.py

Sale con 1 si encuentra una credencial nueva en claro.
"""
import re
import subprocess
import sys

# Asignacion de un valor literal a una clave de aspecto credencial.
#
# Hacen falta las dos formas. Exigir comillas parecia lo prudente, pero deja
# fuera justo la forma del hallazgo original --
# `spring.datasource.password=postgreAdmin19` en un bloque .properties no lleva
# ninguna. Se comprobo: con solo la variante entrecomillada, reintroducir esa
# linea no disparaba nada.
RE_ASIGNACION = re.compile(
    r"""(?ix)
    \b(pass(?:word|wd)?|clave|contrase[nñ]a|secret|api[_-]?key)\b
    \s*[:=]\s*
    (?:
        (?P<q>["'])(?P<entre>[^"'\n]{6,})(?P=q)   # "valor" o 'valor'
      |
        (?P<suelto>[^\s"'`(){}\[\],;<>\n]{6,})  # valor=suelto, estilo .properties
    )
    """)

# Valores que encajan en el patron pero no son una credencial.
PLACEHOLDERS = re.compile(
    r"""(?ix)
    ^( \$ | \{ | %                      # ${VAR}, {{x}}, %VAR%
     | __ENV | process\.env | os\.environ
     | null | none | true | false | undefined
     | tu_ | your_ | change | generar_ | xxx | \.\.\.
     | <.*>                             # <pon-aqui-la-clave>
     | [A-Z_]{4,}$                      # DB_PASSWORD, JWT_SECRET
     | encode\( | encoder | hash | bcrypt | \$2[aby]\$
     | token$ | secret$ | password$ | clave$
     | dummy | fake | sample | ejemplo | placeholder
     )""")

# Un valor sin un solo digito casi nunca es una contrasena real.
RE_TIENE_DIGITO = re.compile(r"\d")

# Credenciales citadas a proposito dentro del relato de su propia retirada.
#
# Esto era, en una primera version, una busqueda de frases ("se retiro",
# "hallazgo"...) en una ventana alrededor del hallazgo. Era demasiado laxa: un
# archivo que mencionara "hallazgo" en cualquier otro parrafo silenciaba una
# credencial real, y se comprobo que lo hacia. Una lista explicita no puede
# tragarse nada sin que quede escrito aqui quien la puso y por que.
CITADAS = {
    # (archivo, valor): razon
    ("VERIFICACION.md", "admin123"):
        "citada como ejemplo en el relato de su propia retirada (seccion Seguridad)",
    ("VERIFICACION.md", "postgreAdmin19"):
        "citada como ejemplo de la forma sin comillas, en la misma seccion",
}

EXENTOS = {
    ".env.example": "plantilla: su proposito es declarar que variables existen",
    "scripts/ev2-credenciales.py": "este mismo detector",
    "common-passwords.txt": "lista de contrasenas prohibidas por RNF-06",
    "src/test/": "fixtures sinteticos: no autentican contra nada",
    ".spec.ts": "fixtures sinteticos: no autentican contra nada",
}

EXT = (".java", ".js", ".ts", ".py", ".md", ".yml", ".yaml", ".properties",
       ".sh", ".sql", ".json", ".tex")


def rastreados():
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                         encoding="utf-8", errors="replace").stdout
    return [f for f in out.splitlines() if f.strip().endswith(EXT)]


def exento(ruta):
    for clave, razon in EXENTOS.items():
        if clave in ruta.replace("\\", "/"):
            return razon
    return None


def main():
    hallazgos = []
    revisados = 0
    for f in rastreados():
        if exento(f):
            continue
        revisados += 1
        try:
            txt = open(f, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        for m in RE_ASIGNACION.finditer(txt):
            valor = m.group("entre") or m.group("suelto")
            if PLACEHOLDERS.match(valor) or not RE_TIENE_DIGITO.search(valor):
                continue
            if (f.replace("\\", "/"), valor) in CITADAS:
                continue
            linea = txt[:m.start()].count("\n") + 1
            hallazgos.append((f, linea, m.group(1), valor))

    print(f"Archivos versionados revisados: {revisados}")
    for clave, razon in EXENTOS.items():
        print(f"  exento: {clave}  ({razon})")
    for (arch, _), razon in CITADAS.items():
        print(f"  citada en {arch}: {razon}")
    print()

    if not hallazgos:
        print("[OK] ninguna credencial en claro en los archivos versionados.")
        return 0

    print(f"*** {len(hallazgos)} posible(s) credencial(es) en claro ***")
    for f, l, clave, valor in hallazgos:
        # No se reimprime el valor: el informe de un secreto no deberia filtrarlo.
        oculto = valor[:2] + "*" * max(0, len(valor) - 2)
        print(f"   {f}:{l}  {clave} = {oculto}  ({len(valor)} caracteres)")
    print("\nPasalo al entorno y documenta la variable en .env.example.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
