#!/usr/bin/env python3
"""P4 -- Renombra tokens espanoles en identificadores Java, sin tocar el contrato.

POR QUE NO ES UN sed
--------------------
`49adaee` hizo reemplazo de subcadena sobre el texto completo. Produjo dos
clases de dano documentadas: plurales corrompidos (`roles`->`rolees`,
`solicitudes`->`submissiones`) y contratos rotos, porque el reemplazo entraba
tambien dentro de las cadenas que definen el nombre de una columna, de un query
param o de un campo JSON.

LAS DOS REGLAS DE ESTE SCRIPT
-----------------------------
1. **Limites CamelCase, no subcadena.** Un token en mayuscula solo coincide si
   lo que sigue no es minuscula ni `_`: `Estado(?![a-z_])` acepta
   `EstadoSubmission` y `countByEstadoCodigo`, y rechaza `Estados` (token
   propio; por eso se aplican de mayor a menor longitud). Un token en minuscula
   ademas exige que lo anterior no sea letra ni `_`, lo que deja intacto todo
   identificador snake_case de base de datos: `estados_acta`, `p_solicitud_id`.

2. **Toda cadena de texto se enmascara, salvo la JPQL.** Es la regla clave, y
   es mas simple y mas segura que enumerar anotaciones: *todo* nombre que viaja
   fuera de Java -- columna, campo JSON, query param, ruta HTTP, parametro de
   procedimiento, nombre de `@SqlResultSetMapping` -- vive dentro de una cadena.
   Protegiendo las cadenas se protegen todos de una vez, incluidos los que uno
   no penso en enumerar. Lo unico que se deja expuesto es la JPQL de una
   `@Query` no nativa, porque referencia nombres de entidad y de campo Java que
   tienen que renombrarse con ellos; dentro de esa JPQL se vuelven a enmascarar
   los `:parametros`, que estan anclados al valor de su `@Param`.

   Consecuencia buscada: una referencia a clase (`targetClass = X.class`) no es
   una cadena, asi que si se renombra. Un intento anterior enmascaraba
   anotaciones enteras por regex y fallaba con `@SqlResultSetMapping`, que
   anida tres niveles de parentesis: dejaba el nombre del mapping renombrado y
   la referencia que lo usa sin renombrar.

Uso:
    python scripts/p4-renombrar.py --lote <nombre> [--dry-run]
    python scripts/p4-renombrar.py --todos [--dry-run]
    python scripts/p4-renombrar.py --listar
"""
import os
import re
import subprocess
import sys

RAIZ = "backend/src"
MARCA = "\x00%d\x00"

LOTES = {
    "estado": {"Estados": "Statuses", "estados": "statuses",
               "Estado": "Status", "estado": "status"},
    "titulacion": {"Titulacion": "Degree", "titulacion": "degree"},
    "reporte": {"Reportes": "Reports", "reportes": "reports",
                "Reporte": "Report", "reporte": "report"},
    "criterio": {"Criterios": "Criteria", "criterios": "criteria",
                 "Criterio": "Criterion", "criterio": "criterion"},
    "mensaje": {"Mensajes": "Messages", "mensajes": "messages",
                "Mensaje": "Message", "mensaje": "message"},
    "observaciones": {"Observaciones": "Observations",
                      "observaciones": "observations",
                      "Observacion": "Observation", "observacion": "observation"},
    "supresion": {"Supresion": "Erasure", "supresion": "erasure"},
    "propuesto": {"Propuestos": "Proposed", "propuestos": "proposed",
                  "Propuesto": "Proposed", "propuesto": "proposed"},
    "tipo": {"Tipos": "Kinds", "tipos": "kinds", "Tipo": "Kind", "tipo": "kind"},
    "bitacora": {"Bitacora": "Log", "bitacora": "log"},
    "academico": {"Academicos": "Academic", "academicos": "academic",
                  "Academico": "Academic", "academico": "academic"},
    "catalogo": {"Catalogos": "Catalogs", "catalogos": "catalogs",
                 "Catalogo": "Catalog", "catalogo": "catalog"},
    "resultado": {"Resultados": "Results", "resultados": "results",
                  "Resultado": "Result", "resultado": "result"},
    "resumen": {"Resumen": "Summary", "resumen": "summary"},
    "restauracion": {"Restauracion": "Restore", "restauracion": "restore",
                     "Pruebas": "Drills", "pruebas": "drills",
                     "Prueba": "Drill", "prueba": "drill"},
    "fase": {"Fases": "Phases", "fases": "phases",
             "Fase": "Phase", "fase": "phase"},
    "varios-tipos": {
        "Tematica": "Thematic", "tematica": "thematic",
        "Horario": "TimeSlot", "horario": "timeSlot",
        "Investigacion": "Research", "investigacion": "research",
        "Guardado": "Saved", "guardado": "saved",
        "Etapa": "Stage", "etapa": "stage",
        "Detalle": "Detail", "detalle": "detail",
        "Tribunal": "Panel", "tribunal": "panel",
        "Tutorado": "Tutee", "tutorado": "tutee",
        "Coordinador": "Coordinator", "coordinador": "coordinator",
        "Perfil": "Profile", "perfil": "profile",
        "Paso": "Step", "paso": "step",
        "Promedio": "Average", "promedio": "average",
        "Actividad": "Activity", "actividad": "activity",
        "Defensa": "Defense", "defensa": "defense",
        "Conexion": "Connection", "conexion": "connection",
        "Origen": "Source", "origen": "source",
        "Recuperar": "Recover", "recuperar": "recover",
        "Degradacion": "Degradation", "degradacion": "degradation",
        "Fisica": "Physical", "fisica": "physical",
        "Datos": "Data", "datos": "data",
        "Nuevo": "New", "nuevo": "target",
        "Tiempo": "Live", "tiempo": "live",
        "TiempoReal": "Live", "tiempoReal": "live",
    },
    "especiales": {
        # Nombres compuestos donde el reemplazo token a token daria un
        # resultado torpe ("StatusLiveRealController"). Se escriben enteros.
        "StatusLiveRealController": "LiveStatusController",
        "StatusTiempoRealController": "LiveStatusController",
        "EstadoTiempoRealController": "LiveStatusController",
        "AreaThematic": "Subject", "areaThematic": "subject",
        "AreaTematica": "Subject", "areaTematica": "subject",
        "PreSustentacionesApplication": "PreDefenseApplication",
        "BlockTimeSlot": "TimeBlock", "blockTimeSlot": "timeBlock",
        "LineResearch": "ResearchLine", "lineResearch": "researchLine",
    },
    "funcionales": {
        # Palabras funcionales espanolas dentro de identificadores.
        # Se mapean solo las formas seguras: `No` NO se mapea de forma
        # generica porque `No(?![a-z_])` coincidiria con la clase de Spring
        # `NoResourceFoundException`; `sin` tampoco, porque coincidiria con
        # `Math.sin(`. Esos casos se renombran por identificador completo en
        # el lote "funcionales-manual".
        "Como": "As", "como": "as",
        "Para": "For",
        "Sin": "Without",
        "Por": "By", "por": "by",
        "Con": "With", "con": "with",
        "Todo": "All", "todo": "all",
    },
    "funcionales-manual": {
        # Identificadores completos, donde el mapeo token a token seria
        # incorrecto o daria un nombre peor que el original.
        "handleNoResource": "handleMissingResource",
        # Referenciados desde SpEL en @PreAuthorize (136 usos). El metodo Java
        # se renombra aqui; la cadena SpEL, al estar protegida, se alinea en un
        # paso aparte (ver el commit) para que ambos lados queden en sync.
        "tienePermission": "hasPermission",
        "esPropioTeacher": "isOwnTeacher",
        "esOwnTeacher": "isOwnTeacher",
        "countNoRead": "countUnread",
        "countNoLeidas": "countUnread",
        "modifyPhoneDeUnAppUserDemo": "modifyDemoAppUserPhone",
        # `De`, `Es`, `O`, `Y`, `Mi` no se mapean de forma generica: son de una
        # o dos letras y coincidirian con acronimos reales -- `DTO` termina en
        # `O`, y `O(?![a-z_])` lo convertia en `DTOr`. Se listan enteros.
        "obtainTutorDeSubmission": "obtainTutorOfSubmission",
        "updatePermissionsDeRole": "updatePermissionsOfRole",
        "panelistDeSubmission": "panelistOfSubmission",
        "permissionsDe": "permissionsOf",
        "submissionDe": "submissionOf",
        "wrapperDe": "wrapperOf",
        "dataDe": "dataOf",
        "errorDe": "errorOf",
        "startDe": "startOfDay",
        "endDe": "endOfDay",
        "fromDe": "fromOrMin",
        "toDe": "toOrMax",
        "assertEsPdfDownloadable": "assertIsPdfDownloadable",
        "esAppUserActualOAdmin": "isCurrentAppUserOrAdmin",
        "esAppUserActual": "isCurrentAppUser",
        "esAdminOCoordinator": "isAdminOrCoordinator",
        "esAdminActual": "isCurrentAdmin",
        "esSuspendable": "isSuspendable",
        "validateAccessStudentOwnOAdmin": "validateAccessStudentOwnOrAdmin",
        "validateAccessOwnOAdministrative": "validateAccessOwnOrAdministrative",
        "validateAccessOwnOAdmin": "validateAccessOwnOrAdmin",
        "createPanelistSinNotify": "createPanelistWithoutNotify",
        "calculateSha256YSave": "calculateSha256AndSave",
        "MiStudentTuteeDTO": "MyStudentTuteeDTO",
        "miProgress": "myProgress",
        "de": "from",
    },
    "metodos2": {
        "Automaticamente": "Automatically",
        "Institucionales": "Institutional",
        "automaticamente": "automatically",
        "institucionales": "institutional",
        "Administrativo": "Administrative",
        "AreasTematicas": "Subjects",
        "administrativo": "administrative",
        "Prerequisitos": "Prerequisites",
        "prerequisitos": "prerequisites",
        "Programacion": "Scheduling",
        "Recuperacion": "Recovery",
        "Seudonimizar": "Pseudonymize",
        "programacion": "scheduling",
        "recuperacion": "recovery",
        "seudonimizar": "pseudonymize",
        "Autenticado": "Authenticated",
        "Descargable": "Downloadable",
        "Descripcion": "Description",
        "Diferencial": "Differential",
        "Disponibles": "Available",
        "Inicializar": "Initialize",
        "Suspendible": "Suspendable",
        "autenticado": "authenticated",
        "descargable": "downloadable",
        "descripcion": "description",
        "diferencial": "differential",
        "disponibles": "available",
        "inicializar": "initialize",
        "suspendible": "suspendable",
        "Automatico": "Automatic",
        "Comentario": "Comment",
        "Directorio": "Directory",
        "Disponible": "Available",
        "Integridad": "Integrity",
        "Pendientes": "Pending",
        "automatico": "automatic",
        "comentario": "comment",
        "directorio": "directory",
        "disponible": "available",
        "integridad": "integrity",
        "pendientes": "pending",
        "Auditadas": "Audited",
        "Corregido": "Corrected",
        "Describir": "Describe",
        "Existente": "Existing",
        "Firmantes": "Signers",
        "Guardados": "Saved",
        "Humanizar": "Humanize",
        "Intervalo": "Interval",
        "Modificar": "Modify",
        "Ponderado": "Weighted",
        "Recursivo": "Recursive",
        "Remitente": "Sender",
        "Responder": "Respond",
        "Retencion": "Retention",
        "Suspender": "Suspend",
        "Tematicas": "Thematic",
        "Tolerante": "Tolerant",
        "auditadas": "audited",
        "corregido": "corrected",
        "describir": "describe",
        "existente": "existing",
        "firmantes": "signers",
        "guardados": "saved",
        "humanizar": "humanize",
        "intervalo": "interval",
        "modificar": "modify",
        "ponderado": "weighted",
        "recursivo": "recursive",
        "remitente": "sender",
        "responder": "respond",
        "retencion": "retention",
        "suspender": "suspend",
        "tolerante": "tolerant",
        "Capturar": "Capture",
        "Completa": "Complete",
        "Completo": "Complete",
        "Defensas": "Defenses",
        "Escribir": "Write",
        "Explorar": "Explore",
        "Peticion": "Request",
        "Receptor": "Receiver",
        "Registro": "Record",
        "Telefono": "Phone",
        "capturar": "capture",
        "completa": "complete",
        "completo": "complete",
        "defensas": "defenses",
        "escribir": "write",
        "explorar": "explore",
        "peticion": "request",
        "receptor": "receiver",
        "registro": "record",
        "telefono": "phone",
        "Activas": "Active",
        "Activos": "Active",
        "Aplicar": "Apply",
        "Archivo": "File",
        "Barrido": "Sweep",
        "Comunes": "Common",
        "Defecto": "Default",
        "Evaluar": "Evaluate",
        "Formato": "Format",
        "Franjas": "Slots",
        "Gestion": "Management",
        "Limpiar": "Clean",
        "Parsear": "Parse",
        "Sugerir": "Suggest",
        "Titular": "Holder",
        "Tutores": "Tutors",
        "activas": "active",
        "activos": "active",
        "aplicar": "apply",
        "archivo": "file",
        "barrido": "sweep",
        "comunes": "common",
        "defecto": "default",
        "evaluar": "evaluate",
        "formato": "format",
        "franjas": "slots",
        "gestion": "management",
        "limpiar": "clean",
        "parsear": "parse",
        "sugerir": "suggest",
        "titular": "holder",
        "tutores": "tutors",
        "Activa": "Active",
        "Avance": "Progress",
        "Correr": "Run",
        "Cumple": "Meets",
        "Existe": "Exists",
        "Firmas": "Signatures",
        "Forzar": "Force",
        "Inicio": "Start",
        "Leidas": "Read",
        "Leidos": "Read",
        "Masivo": "Bulk",
        "Propio": "Own",
        "Tablas": "Tables",
        "Tamano": "Size",
        "Ultima": "Last",
        "Ultimo": "Last",
        "Valido": "Valid",
        "activa": "active",
        "avance": "progress",
        "correr": "run",
        "cumple": "meets",
        "existe": "exists",
        "firmas": "signatures",
        "forzar": "force",
        "inicio": "start",
        "leidas": "read",
        "leidos": "read",
        "masivo": "bulk",
        "propio": "own",
        "tablas": "tables",
        "tamano": "size",
        "ultima": "last",
        "ultimo": "last",
        "valido": "valid",
        "Areas": "Subjects",
        "Bases": "BaseBackups",
        "Caido": "Down",
        "Celda": "Cell",
        "Desde": "From",
        "Fecha": "Date",
        "Firma": "Signature",
        "Hasta": "To",
        "Ideas": "Suggestions",
        "Leida": "Read",
        "Libre": "Free",
        "Puede": "Can",
        "Rango": "Range",
        "Vacio": "Empty",
        "bases": "baseBackups",
        "caido": "down",
        "celda": "cell",
        "desde": "from",
        "fecha": "date",
        "firma": "signature",
        "hasta": "to",
        "ideas": "suggestions",
        "leida": "read",
        "libre": "free",
        "puede": "can",
        "rango": "range",
        "vacio": "empty",
        "Esta": "Is",
        "Fila": "Row",
        "Leer": "Read",
        "esta": "is",
        "fila": "row",
        "leer": "read",
        "Fin": "End",
        "Ver": "View",
        "fin": "end",
        "ver": "view",
    },
    "final-pass": {
        # Se corre AL FINAL, cuando los identificadores ya tienen su forma
        # definitiva. Varias entradas de "funcionales-manual" no llegaron a
        # aplicarse porque ese lote corre antes que "metodos2": nombraban
        # formas que todavia no existian (`startDe` era `inicioDe`,
        # `validateAccessOwnOAdmin` era `validateAccessPropioOAdmin`).
        "AppUserActualService": "CurrentAppUserService",
        "appUserActualService": "currentAppUserService",
        "studentActualIdOrNull": "currentStudentIdOrNull",
        "appUserActual": "currentAppUser",
        "studentActual": "currentStudent",
        "validateAccessStudentOwnOAdmin": "validateAccessStudentOwnOrAdmin",
        "validateAccessOwnOAdministrative": "validateAccessOwnOrAdministrative",
        "validateAccessOwnOAdmin": "validateAccessOwnOrAdmin",
        "assertEsPdfDownloadable": "assertIsPdfDownloadable",
        "esSuspendable": "isSuspendable",
        "modifyPhoneDeUnAppUserDemo": "modifyDemoAppUserPhone",
        "startDe": "startOfDay",
        "endDe": "endOfDay",
        "fromDe": "fromOrMin",
        "toDe": "toOrMax",
        "dataDe": "dataOf",
        "errorDe": "errorOf",
        "wrapperDe": "wrapperOf",
        "permissionsDe": "permissionsOf",
        "submissionDe": "submissionOf",
        "panelistDeSubmission": "panelistOfSubmission",
        "obtainTutorDeSubmission": "obtainTutorOfSubmission",
        "updatePermissionsDeRole": "updatePermissionsOfRole",
        "calculateSha256YSave": "calculateSha256AndSave",
        "createPanelistSinNotify": "createPanelistWithoutNotify",
        "miProgress": "myProgress",
    },
    "metodos": {
        "Autenticar": "Authenticate", "autenticar": "authenticate",
        "Paginado": "Paged", "paginado": "paged",
        "Acceso": "Access", "acceso": "access",
        "Marcar": "Mark", "marcar": "mark",
        "Codigo": "Code", "codigo": "code",
        "Estadisticas": "Stats", "estadisticas": "stats",
        "Nota": "Grade", "nota": "grade",
        "Todos": "All", "todos": "all",
        "Todas": "All", "todas": "all",
        "Mis": "My", "mis": "my",
        "Sustentaciones": "Defenses", "sustentaciones": "defenses",
        "Sustentacion": "Defense", "sustentacion": "defense",
    },
}


# --------------------------------------------------------------------------
def _cierre(texto, i):
    """Indice del parentesis que cierra el abierto en `i`, saltando cadenas."""
    prof, n = 0, len(texto)
    while i < n:
        c = texto[i]
        if c == '"':
            i += 1
            while i < n and texto[i] != '"':
                i += 2 if texto[i] == '\\' else 1
        elif c == '(':
            prof += 1
        elif c == ')':
            prof -= 1
            if prof == 0:
                return i
        i += 1
    return n - 1


def _regiones_query(texto):
    """[(ini, fin, es_nativa)] de cada @Query(...), con parentesis balanceados."""
    out = []
    # Acepta tambien la forma totalmente cualificada
    # (@org.springframework.data.jpa.repository.Query). Hay 3 en el proyecto y
    # con el patron corto quedaban tratadas como cadena normal: su JPQL no se
    # renombraba y la consulta seguia nombrando entidades que ya no existen.
    for m in re.finditer(r'@(?:[\w.]*\.)?Query\s*\(', texto):
        ini = texto.index('(', m.start())
        fin = _cierre(texto, ini)
        cuerpo = texto[ini:fin + 1]
        out.append((m.start(), fin + 1, 'nativeQuery' in cuerpo))
    return out


def _cadenas(texto):
    """[(ini, fin, clase)] de cada region que NO es codigo.

    Un solo recorrido, porque las cuatro clases se excluyen entre si: un `//`
    dentro de una cadena no abre un comentario, y unas comillas dentro de un
    comentario no abren una cadena.

    Los comentarios importan tanto como las cadenas. La evaluacion del
    2026-09-17 senala que el renombrado anterior "dano" 164 comentarios
    ("para openlo en el navegador sin downloadlo"): el reemplazo entro en la
    prosa en espanol de los Javadoc y la dejo medio traducida. Aqui se
    enmascaran, asi que la documentacion queda intacta.
    """
    out, i, n = [], 0, len(texto)
    while i < n:
        c = texto[i]
        if c == '"' and texto.startswith('""""', i) is False and texto.startswith('"""', i):
            # Bloque de texto de Java 15+. Hay 5 en el proyecto, y tratarlos
            # como comillas sueltas partia el bloque: su contenido quedaba
            # expuesto y se renombraba ("Por favor" -> "By favor"), mientras
            # que el codigo posterior al cierre quedaba enmascarado y NO se
            # renombraba, lo que rompia la compilacion.
            j = texto.find('"""', i + 3)
            j = n if j == -1 else j + 3
            out.append((i, j, "text"))
            i = j
        elif c == '"':
            j = i + 1
            while j < n and texto[j] != '"':
                j += 2 if texto[j] == '\\' else 1
            out.append((i, min(j + 1, n), "str"))
            i = min(j + 1, n)
        elif c == "'":
            j = i + 1
            while j < n and texto[j] != "'":
                j += 2 if texto[j] == '\\' else 1
            out.append((i, min(j + 1, n), "char"))
            i = min(j + 1, n)
        elif c == '/' and i + 1 < n and texto[i + 1] == '/':
            j = texto.find('\n', i)
            j = n if j == -1 else j
            out.append((i, j, "line"))
            i = j
        elif c == '/' and i + 1 < n and texto[i + 1] == '*':
            j = texto.find('*/', i + 2)
            j = n if j == -1 else j + 2
            out.append((i, j, "block"))
            i = j
        else:
            i += 1
    return out


def patron(tok):
    if tok[0].isupper():
        return re.compile(re.escape(tok) + r'(?![a-z_])')
    return re.compile(r'(?<![A-Za-z_])' + re.escape(tok) + r'(?![a-z_])')


def sustituir(texto, mapa):
    queries = _regiones_query(texto)

    def jpql_expuesta(a, b):
        """La cadena [a,b) esta dentro de una @Query NO nativa."""
        return any(qi <= a and b <= qf and not nat for qi, qf, nat in queries)

    guardadas, piezas, pos = [], [], 0
    for a, b, clase in _cadenas(texto):
        piezas.append(texto[pos:a])
        if clase in ("str", "text") and jpql_expuesta(a, b):
            # JPQL: se deja expuesta, pero sus :parametros se anclan
            jp = texto[a:b]
            def anclar(m):
                guardadas.append(m.group(0))
                return MARCA % (len(guardadas) - 1)
            piezas.append(re.sub(r':[A-Za-z_]\w*', anclar, jp))
        else:
            guardadas.append(texto[a:b])
            piezas.append(MARCA % (len(guardadas) - 1))
        pos = b
    piezas.append(texto[pos:])
    t = "".join(piezas)

    n = 0
    for tok in sorted(mapa, key=len, reverse=True):
        t, k = patron(tok).subn(mapa[tok], t)
        n += k
    t = re.sub(r"\x00(\d+)\x00", lambda m: guardadas[int(m.group(1))], t)
    return t, n


def archivos():
    return sorted(os.path.join(dp, fn)
                  for dp, _, fns in os.walk(RAIZ)
                  for fn in fns if fn.endswith(".java"))


def aplicar(lote, mapa, dry):
    tocados = total = 0
    for p in archivos():
        txt = open(p, encoding="utf-8", errors="replace").read()
        nuevo, n = sustituir(txt, mapa)
        if n:
            tocados += 1
            total += n
            if not dry:
                open(p, "w", encoding="utf-8").write(nuevo)
    renombres = []
    for p in archivos():
        d, b = os.path.split(p)
        nb, n = sustituir(b, mapa)
        if n and nb != b:
            renombres.append((p, os.path.join(d, nb)))
    if not dry:
        for v, nv in renombres:
            subprocess.run(["git", "mv", v, nv], capture_output=True)
    print(f"  {lote:14s} {total:5d} sustituciones / {tocados:3d} archivos"
          f" / {len(renombres):2d} renombrados" + ("  [DRY]" if dry else ""))
    return renombres


def main():
    if "--listar" in sys.argv:
        for k in LOTES:
            print(f"  {k}")
        return 0
    dry = "--dry-run" in sys.argv
    if "--todos" in sys.argv:
        lotes = list(LOTES)
    elif "--lote" in sys.argv:
        lotes = [sys.argv[sys.argv.index("--lote") + 1]]
    else:
        print(__doc__)
        return 2
    for l in lotes:
        if l not in LOTES:
            print(f"Lote desconocido: {l}")
            return 2
        aplicar(l, LOTES[l], dry)
    return 0


if __name__ == "__main__":
    sys.exit(main())
