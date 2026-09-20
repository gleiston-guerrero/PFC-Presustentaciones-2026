#!/usr/bin/env python3
"""P4 -- Conteo de identificadores con palabras en espanol, con METODO DECLARADO.

POR QUE EXISTE ESTE SCRIPT
--------------------------
La evaluacion integral del 2026-09-17 reporto, por AST propio del ingeniero:
    tipos    121/339 (35.7%)   metodos 1325/1836 (72.2%)   [main+test]
    en src/main:  39.7% tipos,  52.0% metodos
El equipo habia reportado 1.8% de tipos y 0.1% de metodos con
`p4-rename-scan-fuente.py`, y la diferencia se dejo registrada como "disputa
numerica abierta".

La disputa esta RESUELTA y el equipo estaba equivocado. No era un problema de
parseo: el universo de tipos coincide exactamente (339 = 339). El problema era
el diccionario. `p4-rename-scan-fuente.py` usaba una lista curada a mano que
omitia justo los tokens en espanol mas frecuentes del codigo:

    por (56 apariciones)   estado/estados (46)   titulacion (18)
    autenticar (15)        como (13)             de (22)   con (12)
    reporte (14)           resumen (10)          fase/fases (12)

`por` por si solo aparece en 56 identificadores (`obtainPorId`,
`listPorEstado`, `searchPorSubmission`...), casi todos metodos. Un diccionario
que no lo incluye no puede ver el patron dominante de nombres del repositorio.

METODO DE ESTE SCRIPT
---------------------
1. Mismo universo de identificadores que `p4-rename-scan-fuente.py` (se
   reutilizan sus regex TYPE_RE/METHOD_RE), para que las cifras sean
   comparables commit a commit.
2. Cada identificador se parte en tokens por camelCase/digitos.
3. Cada token se compara contra un lexico explicito, definido abajo en este
   mismo archivo y ordenado alfabeticamente para que se pueda auditar a mano.
   No hay deteccion "magica" de idioma: si un token cuenta como espanol, esta
   escrito aqui.
4. Se reportan DOS definiciones, porque el criterio "nombre en espanol" no es
   unico y la diferencia entre ambas es material:
     - NUCLEO: solo sustantivos y verbos de dominio inequivocamente en espanol.
       Excluye palabras funcionales (por, de, con, y...) y tokens ambiguos
       entre ingles y espanol (actual, real, final, base, error, no, me).
     - AMPLIO: NUCLEO + palabras funcionales + ambiguos resueltos a favor del
       espanol por el contexto del repositorio (p. ej. `AppUserActualService`
       es "usuario actual", no "actual service").
   La cifra del ingeniero deberia caer dentro o cerca del rango AMPLIO.

Uso:
    python scripts/p4-nombres-espanol.py
    python scripts/p4-nombres-espanol.py --list      # lista cada identificador
    python scripts/p4-nombres-espanol.py --tokens    # tokens no clasificados
"""
import importlib.util
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

# --- Reutilizamos el universo de identificadores del script original -------
_spec = importlib.util.spec_from_file_location(
    "p4_fuente", HERE / "p4-rename-scan-fuente.py"
)
_fuente = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_fuente)

TOKEN_RE = re.compile(r"[A-Z]+(?![a-z])|[A-Z][a-z]+|[a-z]+|[0-9]+")

# --- Lexico: sustantivos y verbos de dominio, inequivocamente espanol ------
# Construido enumerando los 466 tokens distintos que realmente aparecen en
# backend/src/{main,test}/java y clasificandolos uno por uno. No es una lista
# aspiracional: cada entrada esta presente en el codigo.
NUCLEO = {
    "academico", "academicos", "acceso", "actividad", "activa", "activas",
    "activos", "administrativo", "aplicar", "archivo", "area", "areas",
    "auditadas", "autenticado", "autenticar", "automatico", "automaticamente",
    "avance", "barrido", "bases", "bitacora", "caido", "capturar", "catalogo",
    "celda", "codigo", "comentario", "completa", "completo", "comunes",
    "conexion", "coordinador", "corregido", "correr", "criterio", "criterios",
    "cumple", "datos", "defecto", "defensa", "defensas", "degradacion",
    "descargable", "descripcion", "describir", "desde", "detalle",
    "diferencial", "directorio", "disponible", "disponibles", "escribir",
    "esta", "estadisticas", "estado", "estados", "etapa", "evaluar",
    "existe", "existente", "explorar", "fase", "fases", "fecha", "fila",
    "fin", "firma", "firmantes", "firmas", "fisica", "formato", "forzar",
    "franjas", "gestion", "guardado", "guardados", "hasta", "horario",
    "humanizar", "ideas", "inicializar", "inicio", "institucionales",
    "integridad", "intervalo", "investigacion", "leer", "leida", "leidas",
    "leidos", "libre", "limpiar", "marcar", "masivo", "mensaje", "mensajes",
    "modificar", "nota", "nuevo", "observacion", "observaciones", "origen",
    "paginado", "parsear", "paso", "pendientes", "perfil", "peticion",
    "ponderado", "prerequisitos", "programacion", "promedio", "propio",
    "propuesto", "prueba", "pruebas", "puede", "rango", "receptor",
    "recuperacion", "recuperar", "recursivo", "registro", "remitente",
    "reporte", "responder", "restauracion", "resultado", "resumen",
    "retencion", "seudonimizar", "sugerir", "supresion", "suspendible",
    "suspender", "sustentaciones", "tablas", "tamano", "telefono",
    "tematica", "tematicas", "tiempo", "tiene", "tipo", "titulacion",
    "titular", "tolerante", "tribunal", "tutorado", "tutores", "ultima",
    "ultimo", "vacio", "valido", "ver",
}

# --- Palabras funcionales en espanol (preposiciones, articulos, etc.) ------
FUNCIONALES = {
    "como", "con", "de", "desde", "es", "hasta", "mi", "mis", "no", "o",
    "para", "por", "sin", "todas", "todo", "todos", "un", "y",
}

# --- Ambiguos ingles/espanol, resueltos por contexto del repositorio -------
# Se cuentan solo en la definicion AMPLIA, y se listan aparte para que el
# lector decida si acepta la resolucion.
AMBIGUOS = {
    # AppUserActualService = "servicio del usuario ACTUAL" (no "actual service")
    "actual": "usuario actual (AppUserActualService, obtainActual)",
    # EstadoTiempoRealController = "estado en tiempo real"
    "real": "tiempo real (EstadoTiempoRealController)",
    # EvaluationFinal = "evaluacion final" (orden sustantivo-adjetivo espanol)
    "final": "evaluacion final (EvaluationFinal, orden espanol)",
    # MeController es ingles, pero obtainMe/me aparece tambien como pronombre
    "me": "ambiguo: MeController (ingles) vs uso pronominal",
    "error": "cognado exacto ingles/espanol",
    "base": "cognado exacto ingles/espanol",
}


def tokens(name):
    return [t.lower() for t in TOKEN_RE.findall(name)]


def clasificar(name, lexico):
    return [t for t in tokens(name) if t in lexico]


def contar(items, lexico):
    hits = []
    for path, name in items:
        m = clasificar(name, lexico)
        if m:
            hits.append((path, name, m))
    return hits


def pct(a, b):
    return (a / b * 100) if b else 0.0


def bloque(titulo, tipos, metodos, lexico):
    ht = contar(tipos, lexico)
    hm = contar(metodos, lexico)
    print(f"  {titulo}")
    print(
        f"    tipos   {len(ht):5d}/{len(tipos):<5d} ({pct(len(ht), len(tipos)):5.1f}%)"
    )
    print(
        f"    metodos {len(hm):5d}/{len(metodos):<5d} ({pct(len(hm), len(metodos)):5.1f}%)"
    )
    return ht, hm


def bytecode(lexico):
    """Conteo sobre bytecode (incluye los metodos que genera Lombok).

    Hipotesis que este bloque pone a prueba: el 72.2% del ingeniero sale de
    contar tambien los accesores que Lombok genera. Esos accesores heredan el
    nombre del campo (getEstado, setTitulacion, getObservaciones...), y los
    campos del dominio si estan en espanol, asi que el porcentaje sube respecto
    del conteo por texto fuente en vez de bajar.

    REVISION FINAL DEL 19-SEP (bloque de VERIFICACION.md que "ya no imprime lo que dice")
    ------------------------------------------------------------------------------------
    Este bloque publicaba UNA cifra: `nombres distintos 102/1923 (5.3 %)`, sobre la
    definicion AMPLIA. Esa definicion suma las palabras ambiguas (actual, base, error,
    final, me, real), que el propio expediente declara palabras INGLESAS y no renombra;
    contarlas como espanolas infla el resultado, y la cifra quedaba por encima del techo
    del 5 % en un punto que el criterio de la guia mide con el nucleo. Ahora se
    informan las tres definiciones por separado, con el criterio marcado, para que
    ninguna cifra tenga que leerse fuera de su definicion.

    Y depende del entorno: los accesores existen en el bytecode solo si Lombok estuvo
    activo al compilar. En una maquina donde no compila (JDK 25), los .class no los
    traen, el universo es otro y las cifras dejan de ser comparables: por eso, sin
    Lombok, se avisa y no se imprime ningun porcentaje.
    """
    _js = importlib.util.spec_from_file_location(
        "p4_javap", HERE / "p4-rename-scan-javap.py"
    )
    _javap = importlib.util.module_from_spec(_js)
    _js.loader.exec_module(_javap)

    backend = Path("backend") if Path("backend/pom.xml").exists() else Path(".")
    variantes = {
        "solo main": [backend / "target/classes"],
        "main + test": [backend / "target/classes", backend / "target/test-classes"],
    }
    definiciones = (
        ("NUCLEO (dominio: el criterio de la guia)", NUCLEO),
        ("+ funcionales (de, con, no, por...)", NUCLEO | FUNCIONALES),
        ("+ ambiguas (" + ", ".join(sorted(AMBIGUOS)) + "): tambien son palabras inglesas, cota superior",
         lexico),
    )
    print("CONTEO SOBRE BYTECODE (javap, incluye metodos generados por Lombok)")
    for etiqueta, roots in variantes.items():
        classfiles = []
        for r in roots:
            classfiles.extend(sorted(r.rglob("*.class")))
        if not classfiles:
            print(f"  {etiqueta}: sin clases compiladas"
                  " (corre 'cd backend && ./mvnw -q test-compile')")
            continue
        nombres = []
        for cf in classfiles:
            nombres.extend(_javap.methods_in_class(cf))
        # Sin Lombok activo no existe canEqual(): solo lo genera @Data/@EqualsAndHashCode.
        if "canEqual" not in set(nombres):
            print(f"  {etiqueta}: AVISO -- las clases compiladas no traen los metodos de Lombok"
                  " (sin canEqual()); las cifras no serian comparables y no se imprimen."
                  " Se necesita un JDK con el que Lombok compile (el proyecto usa 21).")
            continue
        unicos = set(nombres)
        print(f"  {etiqueta}: {len(classfiles)} clases")
        for titulo, lex in definiciones:
            ocurr = sum(1 for n in nombres if clasificar(n, lex))
            dist = sum(1 for n in unicos if clasificar(n, lex))
            print(f"    {titulo}")
            print(f"      nombres distintos {dist:5d}/{len(unicos):<5d} ({pct(dist, len(unicos)):5.1f}%)"
                  f"   ocurrencias {ocurr:5d}/{len(nombres):<5d} ({pct(ocurr, len(nombres)):5.1f}%)")
    print()


def main():
    all_types, _, all_methods, _ = _fuente.scan()
    main_types = [x for x in all_types if "src\\main" in x[0] or "src/main" in x[0]]
    main_methods = [x for x in all_methods if "src\\main" in x[0] or "src/main" in x[0]]

    amplio = NUCLEO | FUNCIONALES | set(AMBIGUOS)

    print("=" * 74)
    print("P4 -- Identificadores con palabra en espanol (metodo declarado)")
    print("=" * 74)
    print(f"Universo: {len(all_types)} tipos y {len(all_methods)} metodos"
          " en backend/src/{main,test}/java")
    print(f"Lexico: {len(NUCLEO)} terminos de dominio, {len(FUNCIONALES)}"
          f" funcionales, {len(AMBIGUOS)} ambiguos")
    print()

    print("DEFINICION NUCLEO (solo dominio, sin funcionales ni ambiguos)")
    bloque("main + test:", all_types, all_methods, NUCLEO)
    bloque("solo src/main:", main_types, main_methods, NUCLEO)
    print()

    print("DEFINICION AMPLIA (+ funcionales + ambiguos resueltos por contexto)")
    ht, hm = bloque("main + test:", all_types, all_methods, amplio)
    bloque("solo src/main:", main_types, main_methods, amplio)
    print()

    print("CONTRASTE con la evaluacion del 2026-09-17 (AST del ingeniero)")
    print("    el ing reporto: tipos 121/339 (35.7%), metodos 1325/1836 (72.2%)")
    print(f"    este script:    tipos {len(ht)}/{len(all_types)}"
          f" ({pct(len(ht), len(all_types)):.1f}%),"
          f" metodos {len(hm)}/{len(all_methods)}"
          f" ({pct(len(hm), len(all_methods)):.1f}%)")
    print("    El universo de tipos coincide exacto (339). La diferencia en el")
    print("    total de metodos (693 aqui vs 1836) es porque este conteo es por")
    print("    texto fuente y no ve los metodos que Lombok genera; ver")
    print("    p4-rename-scan-javap.py para el conteo sobre bytecode.")
    print()

    if "--bytecode" in sys.argv:
        bytecode(amplio)

    if "--tokens" in sys.argv:
        vistos = set()
        for _, n in all_types + all_methods:
            vistos.update(tokens(n))
        sin_clasificar = sorted(vistos - amplio)
        print(f"TOKENS NO CLASIFICADOS COMO ESPANOL ({len(sin_clasificar)}):")
        for i in range(0, len(sin_clasificar), 6):
            print("  " + " ".join(f"{t:16s}" for t in sin_clasificar[i:i + 6]))
        print()

    if "--list" in sys.argv:
        print("TIPOS con token en espanol (definicion amplia):")
        for path, name, m in sorted(ht, key=lambda x: x[1]):
            print(f"  {name:45s} <- {','.join(m)}")
        print()
        print("METODOS con token en espanol (definicion amplia):")
        for path, name, m in sorted(hm, key=lambda x: x[1]):
            print(f"  {name:45s} <- {','.join(m)}")
        print()
        print("AMBIGUOS y su resolucion:")
        for t, razon in sorted(AMBIGUOS.items()):
            print(f"  {t:10s} {razon}")


if __name__ == "__main__":
    main()
