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

EL ACOPLAMIENTO CON LA PROSA (revision final, 2026-09-21)
--------------------------------------------------------
La revision final senalo que "editando solo SUS-RESULTS.md aparecio un fallo de
credenciales en claro" y, tras mi primera respuesta ("no se reproduce"), lo
describio mejor: *la palabra espanola "clave" dispara un falso positivo en
cualquier prosa*. Tenia razon, y ahora se reproduce con una linea:
`la clave: 823pruebas` o `Contrasena: 2026-09-20` bastaban para que `make verify`
saliera 1. Mi prueba anterior no lo veia porque las ediciones que probe no
contenian esa palabra.

La causa: "clave" y "contrasena" son palabras COMUNES del espanol ("el caso clave:
reproducible2026"), y en un .md o .tex no hay forma de saber si son una clave
de configuracion o una frase. Ahora:

  - Las claves en ingles (`password`, `secret`, `api_key`...) cuentan en todas
    partes, prosa incluida: el hallazgo original (`spring.datasource.password=...`)
    estaba en un .md.
  - Las claves en espanol (`clave`, `contrasena`) solo cuentan en archivos de
    codigo/configuracion, o en prosa cuando el valor va ENTRECOMILLADO (eso si
    es un ejemplo literal, no una frase).
  - Un valor que es una fecha o una cifra con separadores (`2026-09-20`,
    `0.608/0.220`) no es una contrasena en ningun archivo.

`--autoprueba` fija esto con una tabla de casos, en los dos sentidos: los falsos
positivos de la prosa no disparan y las credenciales de verdad sigue disparando.

PROBADO CONTRA
--------------
Las dos formas del hallazgo original disparan; las cinco formas de codigo normal
que producian falsos positivos, no. Reintroducir cualquiera de las dos lineas
que senalo la revision hace fallar esta comprobacion.

Uso:
    python scripts/ev2-credenciales.py
    python scripts/ev2-credenciales.py --autoprueba   # la tabla de casos del detector

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


# Archivos de prosa: ahi una palabra espanola comun no es una clave de configuracion.
PROSA = (".md", ".tex")
CLAVES_ESPANOLAS = {"clave", "contraseña", "contrasena"}

# Un valor que es una fecha o una cifra con separadores no es una contrasena.
# Deliberadamente NO cubre los numeros sin separadores (`12345678`): esos si
# pueden ser una contrasena.
RE_NO_CREDENCIAL = re.compile(
    r"^(?:\d{4}-\d{2}-\d{2}[\dT:.Z+-]*|[\d.,:/%=<>+-]*[.,:/][\d.,:/%=<>+-]*|p=[\d.,/]+)$")


def evaluar(ruta, txt):
    """Hallazgos (linea, clave, valor) de un archivo."""
    hallazgos = []
    en_prosa = ruta.replace("\\", "/").lower().endswith(PROSA)
    for m in RE_ASIGNACION.finditer(txt):
        valor = m.group("entre") or m.group("suelto")
        if PLACEHOLDERS.match(valor) or not RE_TIENE_DIGITO.search(valor):
            continue
        if RE_NO_CREDENCIAL.match(valor):
            continue
        # En prosa, "clave"/"contrasena" son palabras del espanol: solo cuentan
        # con el valor entrecomillado (un ejemplo literal, no una frase).
        if en_prosa and m.group(1).lower() in CLAVES_ESPANOLAS and not m.group("entre"):
            continue
        if (ruta.replace("\\", "/"), valor) in CITADAS:
            continue
        hallazgos.append((txt[:m.start()].count("\n") + 1, m.group(1), valor))
    return hallazgos


# (archivo, texto, debe disparar?, por que)
AUTOPRUEBA = [
    # falsos positivos que causaban el acoplamiento con la prosa
    ("SUS-RESULTS.md", "La clave: 823pruebas es la cifra de cierre", False, "'clave' es una palabra comun"),
    ("SUS-RESULTS.md", "la clave = mediciones2026 del contraste", False, "idem, con ="),
    ("SUS-RESULTS.md", "El caso clave: reproducible2026 en CI", False, "idem, en mitad de una frase"),
    ("informe.tex", "Contraseña: 2026-09-20 fue la fecha", False, "una fecha no es una contrasena"),
    ("SUS-RESULTS.md", "clave: p=0.608/0.220", False, "un p no es una contrasena"),
    ("SUS-RESULTS.md", "clave: 0.608/0.220", False, "una cifra con separadores"),
    # lo que SIGUE detectandose
    ("k6/load-test.js", "password: 'admin123'", True, "forma entrecomillada original"),
    ("backend/INSTRUCCIONES.md", "spring.datasource.password=postgreAdmin19", True, "forma suelta original, en un .md"),
    ("README.md", "secret = jwtSecreto2026x", True, "clave en ingles en prosa"),
    ("README.md", "contraseña: 'Secreto123'", True, "clave en espanol, pero entrecomillada: ejemplo literal"),
    ("backend/config.properties", "clave=Secreto12345", True, "clave en espanol en un archivo de configuracion"),
    ("backend/app.py", "contraseña = Secreto12345", True, "idem, en codigo"),
    ("backend/app.py", "password=12345678", True, "una contrasena solo de digitos sigue contando"),
]


def autoprueba():
    malos = 0
    for ruta, texto, debe, porque in AUTOPRUEBA:
        dispara = bool(evaluar(ruta, texto))
        ok = dispara == debe
        malos += not ok
        print(f"  [{'OK  ' if ok else 'FAIL'}] {'dispara ' if dispara else 'no dispara'}  "
              f"{ruta}: {texto!r} -- {porque}")
    if malos:
        print(f"\n{malos} caso(s) del detector no dan lo esperado.")
        return 1
    print(f"\n[OK] {len(AUTOPRUEBA)} casos del detector: la prosa no dispara y las credenciales si.")
    return 0


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
    if "--autoprueba" in sys.argv:
        return autoprueba()
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
        for linea, clave, valor in evaluar(f, txt):
            hallazgos.append((f, linea, clave, valor))

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
