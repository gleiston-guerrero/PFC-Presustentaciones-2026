#!/bin/sh
# EV-2 -- verificador reproducible del examen suspenso.
#
# QUE CAMBIO Y POR QUE (2026-09-19)
# ---------------------------------
# La revision individual del 18-sep declaro EV-2 "No cumple" con cuatro defectos:
#
#   1. `exit 0` incondicional: podia imprimir [FAIL] y dar la corrida por buena.
#      "En 17 alteraciones, 17 terminaron en 0."
#   2. Nueve lineas [OK] escritas a mano, sin calcular nada.
#   3. No ejecutaba pruebas, ni Lighthouse, ni el cuaderno de P6.
#   4. Dejaba un .pyc sin versionar en el arbol.
#
# Los cuatro estan corregidos aqui. El (1) ya se cerro el 18-sep. El (2) es el
# que importa de fondo: un [OK] que no calcula nada no es una comprobacion, es
# una afirmacion con otro formato -- y afirmar era justamente lo que estaba en
# cuestion. Ahora cada [OK] sale de medir algo en esta misma corrida.
#
# EJECUCION
#   scripts/verify.sh            corre TODO, incluida la suite de pruebas real
#   scripts/verify.sh --rapido   omite lo que necesita Docker o red, con [WARN]
#
# Por defecto ejecuta. Si algo que deberia correr no puede correr, es [FAIL], no
# un comentario: el modo --rapido existe para trabajar en local, no para que la
# verificacion de cierre se salte la parte cara.
set -e

# Un verificador no debe ensuciar el arbol que verifica. Sin esto, los propios
# scripts de aqui dejan __pycache__ al importarse entre si, y el chequeo de
# higiene de mas abajo se detectaria a si mismo.
export PYTHONDONTWRITEBYTECODE=1

# Los temporales van en el repositorio, no en /tmp: bajo Git Bash el shell
# resuelve /tmp a %TEMP% mientras que el Python de Windows lo lee como la raiz
# del disco C, asi que un archivo escrito por uno resulta invisible para el otro.
#
# Y se limpia al final a mano, no con `trap ... EXIT`: esa trampa tambien se
# dispara al cerrarse cada subshell de $( ), asi que borraba el directorio a
# mitad de la corrida y los pasos siguientes no encontraban sus temporales.
TMPV=".verify-tmp"
rm -rf "$TMPV"; mkdir -p "$TMPV"

RAPIDO=0
for a in "$@"; do
  [ "$a" = "--rapido" ] && RAPIDO=1
done

FAIL=0
ok()   { printf "  [OK]   %s\n" "$1"; }
warn() { printf "  [WARN] %s\n" "$1"; }
fail() { printf "  [FAIL] %s\n" "$1"; FAIL=1; }

# Para lo pesado: en modo normal no poder ejecutarlo es un fallo.
pesado_no_corrio() {
  if [ "$RAPIDO" = "1" ]; then
    warn "$1 (omitido por --rapido)"
  else
    fail "$1"
  fi
}

echo "=== P1 -- SUS (cifra de cierre = ronda del 18-sep, fecha sellada por un tercero) ==="
# La cifra que se publica es la del formulario del 18-sep. La ronda en papel se
# recalcula igual, pero como contexto: sus 11 hojas de fecha no verificable estan
# retractadas y no cuentan para ninguna cifra del informe.
python -c "
import csv, statistics
from scipy import stats

def resumen(ruta, filtro=None):
    rows = list(csv.DictReader(open(ruta, encoding='utf-8')))
    xs = [float(r['sus_score']) for r in rows if filtro is None or filtro(r)]
    n = len(xs); mean = statistics.mean(xs); sd = statistics.stdev(xs)
    m = stats.t.ppf(0.975, df=n-1) * sd / n**0.5
    return n, mean, sd, mean-m, mean+m

n, mean, sd, lo, hi = resumen('docs/mediciones/sus/re-aplicacion/sus-respuestas-formulario.csv')
print(f'  cierre (18-sep): n={n} media={mean:.2f} DE={sd:.2f} IC95=[{lo:.2f},{hi:.2f}]')
assert n == 15, f'se esperaban 15 respuestas selladas, hay {n}'
assert abs(mean - 52.83) < 0.01, f'la media publicada (52.83) no cuadra: {mean:.2f}'

n2, m2, sd2, lo2, hi2 = resumen('docs/mediciones/sus/sus-respuestas.csv',
                                lambda r: r['fecha_verificable'] == 'si')
print(f'  papel, solo fecha verificable: n={n2} media={m2:.2f} DE={sd2:.2f} IC95=[{lo2:.2f},{hi2:.2f}]')
" || fail "P1: la cifra SUS publicada no se reproduce desde los CSV versionados"
# Holm sobre la familia de contrastes y alfa de Cronbach: las dos cosas que la
# revision del 18-sep echo en falta en el analisis del SUS.
python scripts/sus-estadistica.py || fail "P1: sus-estadistica.py fallo"
warn "P1: consistencia interna alfa = 0,599, por debajo del 0,85-0,92 habitual en el SUS -- declarado en SUS-RESULTS.md y en el informe"
warn "P1: la ronda del 18-sep es una muestra nueva (ninguno de los 15 respondio antes en papel): el origen de las 11 hojas retractadas sigue abierto -- ver docs/mediciones/sus/SUS-RESULTS.md"
echo

echo "=== P2 -- Cobertura ==="
# Misma corrida canonica que usa scripts/cifras-publicadas.py: si se cambia una,
# hay que cambiar la otra, y el gate de P11 lo detecta.
python -c "
import xml.etree.ElementTree as ET
tree = ET.parse('docs/mediciones/jacoco/2026-09-19-cierre-definitivo/jacoco.xml')
root = tree.getroot()
for c in root.findall('counter'):
    if c.get('type') in ('LINE','BRANCH'):
        covered=int(c.get('covered')); missed=int(c.get('missed')); total=covered+missed
        print(f\"  {c.get('type')}: {covered}/{total} ({covered/total*100:.2f}%)\")
" || fail "P2: no se pudo parsear jacoco.xml"

# Antes esto era un [OK] escrito a mano. Ahora se comprueba que la regla exista
# de verdad y con el umbral que se declara.
if grep -q "jacoco-check" backend/pom.xml && grep -q "0\.70" backend/pom.xml; then
  ok "P2: regla jacoco:check con umbral 0.70 presente en backend/pom.xml"
else
  fail "P2: backend/pom.xml no declara la regla jacoco:check con umbral 0.70"
fi

# La suite real. No poder correrla es un fallo, no una nota al pie.
if [ "$RAPIDO" = "1" ]; then
  warn "P2: suite de pruebas omitida por --rapido"
elif ! docker compose ps >/dev/null 2>&1; then
  pesado_no_corrio "P2: Docker no responde, no se pudo correr la suite real (levanta con 'docker compose up -d db redis')"
else
  echo "  corriendo la suite real (cd backend && ./mvnw -q clean test)..."
  # Hay que cargar .env, igual que hace el target `test` del Makefile. Sin esto,
  # DB_PASSWORD cae al valor por defecto de application.properties y los tests
  # con @SpringBootTest real fallan con "password authentication failed" contra
  # el Postgres correcto: 804 pruebas, 0 fallos y 15 errores de contexto. Es un
  # defecto ya documentado en el Makefile desde el 2026-08-31, y este script lo
  # habia vuelto a introducir por correr ./mvnw en crudo.
  if (set -a; [ -f .env ] && . ./.env; set +a; \
      cd backend && ./mvnw -q clean test > "../$TMPV/verify-test.log" 2>&1); then
    python -c "
import xml.etree.ElementTree as ET
nuevo = ET.parse('backend/target/site/jacoco/jacoco.xml').getroot()
canon = ET.parse('docs/mediciones/jacoco/2026-09-19-cierre-definitivo/jacoco.xml').getroot()
def cifras(r):
    d = {}
    for c in r.findall('counter'):
        if c.get('type') in ('LINE','BRANCH'):
            co, mi = int(c.get('covered')), int(c.get('missed'))
            d[c.get('type')] = (co, co+mi)
    return d
a, b = cifras(nuevo), cifras(canon)
for k in ('LINE','BRANCH'):
    print(f'  {k}: corrida de ahora {a[k][0]}/{a[k][1]}   expediente {b[k][0]}/{b[k][1]}')
assert a == b, 'la corrida de ahora no coincide con la cifra publicada'
" && ok "P2: la suite corrio y su cobertura coincide con la cifra publicada" \
       || fail "P2: la suite corrio pero su cobertura NO coincide con la publicada -- regenera docs/mediciones/jacoco/2026-09-19-cierre-definitivo/"
  else
    fail "P2: la suite de pruebas fallo -- ver $TMPV/verify-test.log"
  fi
fi
# La revision final contrasto los 809 @Test del AST con las 806 pruebas de la corrida: tres estaban en
# una clase static anidada sin @Nested, que JUnit no descubre. Toda prueba anotada tiene que correr.
if PYTHONIOENCODING=utf-8 python scripts/p2-pruebas-ejecutadas.py; then
  ok "P2: toda prueba anotada @Test se ejecuta (y ninguna clase estatica anidada las esconde)"
else
  fail "P2: hay pruebas anotadas @Test que la suite no ejecuta -- ver arriba"
fi
warn "P2: ~2.4 de los 3.49 puntos de margen en ramas vienen de equals/hashCode de Lombok en security/dto/* (fuera de la exclusion de JaCoCo)"
echo

echo "=== P3 -- Javadoc ==="
python scripts/javadoc-scan.py || fail "P3: javadoc-scan.py fallo"
python scripts/javadoc-scan-amplio.py > $TMPV/verify-jd.txt 2>&1 || fail "P3: javadoc-scan-amplio.py fallo"
cat $TMPV/verify-jd.txt

# Antes: [OK] "doclint reactivado". Ahora se comprueba que nadie lo haya apagado.
if grep -rq "<doclint>\s*none" backend/pom.xml 2>/dev/null; then
  fail "P3: algun pom.xml apaga doclint (<doclint>none</doclint>)"
else
  ok "P3: ningun pom.xml apaga doclint"
fi

# Antes: [OK] "95.2%, arriba del 90%" escrito a mano. Ahora se lee de la corrida.
PCT=$(grep -oE "Con Javadoc COMPLETO: [0-9]+ \([0-9.]+%\)" $TMPV/verify-jd.txt | grep -oE "[0-9.]+%" | tr -d '%')
if [ -n "$PCT" ] && python -c "import sys; sys.exit(0 if float('$PCT') >= 90 else 1)"; then
  ok "P3: $PCT% de cobertura de Javadoc (AST amplio), sobre el umbral del 90%"
else
  fail "P3: cobertura de Javadoc ${PCT:-desconocida}%, por debajo del 90%"
fi

# El defecto concreto que midio el ing: bloques colocados DESPUES de la anotacion,
# que javac no asocia. Un contador que solo mira "hay un /** cerca" no los ve.
if python scripts/ev2-javadoc-colocacion.py; then
  ok "P3: ningun bloque Javadoc quedo huerfano debajo de una anotacion"
else
  fail "P3: hay Javadoc que javac no asocia -- corrige con scripts/ev2-javadoc-recolocar.py --aplicar"
fi

# La revision del 19-sep dio P3 "con reservas": el Javadoc estaba completo por AST
# pero "el 29 % de los @param son tautologicos". Se mide, con el mismo criterio, y
# se comprueban dos defectos estructurales que doclint no ve (bloques apilados y
# Javadoc metido dentro de una consulta).
if python scripts/p3-param-tautologicos.py; then
  ok "P3: los @param dicen algo mas que el nombre del parametro, y no hay Javadoc apilado ni dentro de una consulta"
else
  fail "P3: hay @param tautologicos por encima del umbral, o Javadoc mal colocado -- ver arriba"
fi

# El arbitro de P3 no es un contador propio, es javadoc. Se corre de verdad y se
# levanta el tope de avisos: por defecto javadoc corta en 100, que es justo la
# cifra que vio la revision del 18-sep ("100 avisos, el tope") y que por eso no
# decia cuantos hay en realidad.
if [ "$RAPIDO" = "1" ]; then
  warn "P3: javadoc real omitido por --rapido"
else
  echo "  corriendo javadoc real con doclint y sin tope de avisos..."
  # Hay que borrar la salida anterior: si target/site/apidocs existe, el plugin
  # dice "everything is up to date" y NO genera nada, con lo que esta
  # comprobacion reportaria "0 avisos" sin haber mirado una sola clase. Se
  # verifico que lo hacia.
  rm -rf backend/target/site/apidocs
  if (cd backend && ./mvnw -o javadoc:javadoc "-DadditionalJOption=-Xmaxwarns 100000" \
        > "../$TMPV/javadoc.log" 2>&1); then
    JDERR=$(grep -c ": error:" "$TMPV/javadoc.log" || true)
    JDWARN=$(grep -c ": warning:" "$TMPV/javadoc.log" || true)
    echo "  javadoc: $JDERR errores, $JDWARN avisos (sin tope)"
    if [ "$JDERR" = "0" ]; then
      ok "P3: javadoc:javadoc pasa con doclint activo y 0 errores"
    else
      fail "P3: javadoc:javadoc reporta $JDERR error(es) con doclint"
    fi
    warn "P3: $JDWARN avisos de javadoc sin el tope de 100 -- el detalle y el plan estan en VERIFICACION.md"
  else
    fail "P3: javadoc:javadoc fallo -- ver $TMPV/javadoc.log"
  fi
fi
echo

echo "=== P4 -- Nombres en espanol ==="
if [ -d backend/target/classes ]; then
  python scripts/p4-rename-scan-fuente.py
  python scripts/p4-rename-scan-javap.py --include-test 2>/dev/null || warn "P4: corre 'cd backend && ./mvnw -q test-compile' primero para el conteo javap con clases de test"
else
  warn "P4: backend/target/classes no existe -- corre 'cd backend && ./mvnw -q test-compile' antes para el conteo completo (javap)"
fi

# Antes eran tres [OK] a mano sobre procedimientos, @RequestParam y DTOs. La
# regresion `desde/hasta` que encontro el ing paso POR DEBAJO de esas tres
# lineas: estaban escritas, no calculadas.
if python scripts/ev2-contrato-wire.py; then
  ok "P4: ningun nombre de cable depende de un identificador Java renombrable"
else
  fail "P4: hay nombres de cable implicitos o parametros de procedimiento inexistentes -- ver arriba"
fi

echo "--- Contrato JSON backend <-> Angular (auditoria sistematica) ---"
if python scripts/p4-contrato-json.py; then
  ok "P4: sin desajustes de contrato JSON entre el backend y Angular"
else
  fail "P4: hay contratos JSON rotos entre el backend y Angular -- ver salida de arriba"
fi

python scripts/p4-nombres-espanol.py > $TMPV/verify-p4.txt 2>&1 || fail "P4: p4-nombres-espanol.py fallo"
cat $TMPV/verify-p4.txt
# Antes: tres [OK] con los porcentajes escritos a mano. Ahora se leen de la corrida
# y se contrastan contra el techo del 5% que fija la guia.
python -c "
import re, sys
t = open('$TMPV/verify-p4.txt', encoding='utf-8', errors='replace').read()
# El bloque CONTRASTE reproduce a proposito las cifras del ing (35.7%, 72.2%)
# para compararlas. Son suyas, no del estado actual: no entran en el umbral.
t = t.split('CONTRASTE')[0]
m = re.findall(r'tipos\s+\d+/\d+\s+\(\s*([\d.]+)%\)', t)
n = re.findall(r'metodos\s+\d+/\d+\s+\(\s*([\d.]+)%\)', t)
if not m or not n:
    print('  no se pudo leer el porcentaje de la salida de p4-nombres-espanol.py'); sys.exit(1)
tipos, metodos = max(map(float, m)), max(map(float, n))
print(f'  peor caso publicado: tipos {tipos}%  metodos {metodos}%  (techo de la guia: 5%)')
sys.exit(0 if tipos <= 5.0 and metodos <= 5.0 else 1)
" && ok "P4: tipos y metodos bajo el techo del 5%, medido en esta corrida" \
   || fail "P4: el porcentaje de nombres en espanol supera el techo del 5%"
# La cifra "436 de 807" que estaba aqui era una advertencia escrita a mano; el conteo real
# era 789 de 809. Ahora se mide, con tolerancia cero, contra un diccionario espanol->ingles.
if PYTHONIOENCODING=utf-8 python scripts/p4-tests-espanol.py; then
  ok "P4: ningun metodo @Test tiene palabras en espanol en su nombre (diccionario de scripts/p4-diccionario-es-en.txt)"
else
  fail "P4: hay metodos @Test con nombre en espanol -- ver arriba"
fi
echo

echo "=== P5 -- Lighthouse (recalculado desde los JSON versionados) ==="
python -c "
import glob, json, statistics, sys
perf = {}
for f in sorted(glob.glob('docs/mediciones/perf/lighthouse/prod-runs/*.json')):
    d = json.load(open(f, encoding='utf-8'))
    perfil = 'mobile' if 'mobile' in f else 'desktop'
    perf.setdefault(perfil, []).append(round(d['categories']['performance']['score'] * 100))
if not perf:
    print('  no hay corridas versionadas'); sys.exit(1)
pub = open('docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md', encoding='utf-8').read()
malo = 0
for p in sorted(perf):
    xs = perf[p]
    media = round(statistics.mean(xs))
    print(f'  {p}: {len(xs)} corridas {xs} -> promedio {media}')
    if f'**{media} / 100**' not in pub:
        print(f'     el reporte no publica {media}/100 para {p}')
        malo = 1
sys.exit(malo)
" && ok "P5: los puntajes publicados coinciden con los JSON versionados" \
   || fail "P5: el reporte de Lighthouse publica un puntaje que sus JSON no respaldan"
# La revision del 19-sep encontro que el informe dibujaba 68/61 (localhost) bajo un
# pie que decia "despliegue publico": la figura se regenero en docs/ y nadie la
# copio a Informe-Final/. Comprobar los numeros publicados no lo detectaba porque
# el defecto estaba en la IMAGEN, no en el texto.
if cmp -s docs/mediciones/perf/figuras/fig-lighthouse-scores.png Informe-Final/figuras/fig-lighthouse-scores.png; then
  ok "P5: la figura del informe es la generada desde los JSON (misma imagen)"
else
  fail "P5: Informe-Final/figuras/fig-lighthouse-scores.png difiere de la generada (make docs regenera y copia; luego make pdf)"
fi
echo "  (volver a medir contra la URL publica: make bench-lh)"
echo

echo "=== P6 -- Correccion por comparaciones multiples ==="
if [ "$RAPIDO" = "1" ]; then
  warn "P6: cuaderno omitido por --rapido"
elif ! python -c "import nbconvert" 2>/dev/null; then
  pesado_no_corrio "P6: nbconvert no esta instalado, no se pudo ejecutar scripts/perf-analysis.ipynb"
else
  echo "  ejecutando scripts/perf-analysis.ipynb de punta a punta..."
  if python -m nbconvert --to notebook --execute --stdout scripts/perf-analysis.ipynb > $TMPV/verify-nb.json 2>$TMPV/verify-nb.err; then
    ok "P6: el cuaderno de analisis corre de punta a punta sin errores"
  else
    fail "P6: scripts/perf-analysis.ipynb fallo al ejecutarse -- ver $TMPV/verify-nb.err"
  fi
fi
echo

echo "=== EV-2 -- Documentos contra sus datos (cifras, p-valores, Lighthouse, evidencia citada) ==="
# La revision del 19-sep probo el verificador con 15 mutaciones y sobrevivieron 10:
# era fuerte comparando codigo contra codigo y ciego comparando documento contra
# medicion. Esto cierra ese lado: recalcula y contrasta lo que los documentos publican.
NB_JSON="$TMPV/verify-nb.json"
if [ -s "$NB_JSON" ]; then
  ARG_NB="--nb $NB_JSON"
else
  ARG_NB=""
  warn "EV-2: la familia de p-valores de rendimiento no se contrasto con el cuaderno (no se ejecuto)"
fi
if PYTHONIOENCODING=utf-8 python scripts/ev2-documental.py $ARG_NB; then
  ok "EV-2: ningun documento vigente contradice a su dato (SUS, Holm, Lighthouse, evidencia citada)"
else
  fail "EV-2: un documento publica algo que su dato no respalda -- ver arriba"
fi
if [ "$RAPIDO" = "1" ]; then
  warn "EV-2: arnes de mutaciones omitido por --rapido (python scripts/mutaciones-gate.py)"
elif [ ! -s "$NB_JSON" ]; then
  fail "EV-2: sin la salida del cuaderno no se puede correr el arnes de mutaciones"
else
  # El verificador se prueba a si mismo: inyecta defectos (36 hoy) y exige que cada uno
  # haga salir a algun detector distinto de 0. Restaura byte a byte.
  if PYTHONIOENCODING=utf-8 python scripts/mutaciones-gate.py --nb "$NB_JSON" > "$TMPV/mutaciones.txt" 2>&1; then
    ok "EV-2: $(tail -1 "$TMPV/mutaciones.txt")"
  else
    cat "$TMPV/mutaciones.txt"; fail "EV-2: sobrevive alguna mutacion -- el verificador no ve un defecto que deberia ver"
  fi
fi
echo

echo "=== EV-1 -- Los bloques de VERIFICACION.md se reproducen literalmente ==="
# La revision final reprodujo ocho bloques y tres no cuadraban por cifras obsoletas
# (Javadoc 734/768, "403 commits", "3 pruebas"): eran salidas pegadas a mano. Ahora cada
# bloque marcado <!-- ev1:run --> se ejecuta y su salida tiene que ser LA MISMA, y la
# tabla del principio tiene que coincidir con el resumen del final.
if [ "$RAPIDO" = "1" ]; then ARG_EV1="--rapido"; else ARG_EV1=""; fi
if PYTHONIOENCODING=utf-8 python scripts/ev1-verificacion.py $ARG_EV1; then
  ok "EV-1: cada bloque marcado de VERIFICACION.md reproduce literalmente y la tabla coincide con el resumen"
else
  fail "EV-1: una salida documentada ya no es la que imprime su comando, o la tabla contradice al resumen (python scripts/ev1-verificacion.py --actualizar regenera las salidas)"
fi
echo

echo "=== P8 -- Autorizacion de endpoints de escritura ==="
python docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py || fail "P8: audit-endpoints-autorizacion.py fallo"
# Lo anterior comprueba que la anotacion ESTE. Esto comprueba que lo que hay
# dentro signifique algo: la revision del 18-sep cerro P8 diciendo que "el
# verificador no detecta un SpEL roto", y tenia razon. Una anotacion presente
# pero rota no protege el endpoint, ademas lo rompe -- y es justo el modo de
# fallo que introdujo el renombrado de P4.
if python scripts/p8-spel-vivo.py; then
  ok "P8: toda expresion SpEL apunta a un bean, metodo y rol que existen"
else
  fail "P8: hay expresiones @PreAuthorize que apuntan a algo inexistente -- ver arriba"
fi
echo

echo "=== P9 -- Etiqueta v1.1.0 ==="
if git rev-parse v1.1.0 >/dev/null 2>&1; then
  ok "tag v1.1.0 existe -> $(git rev-list -n1 v1.1.0)"
  # Antes esto solo avisaba: el evaluador movio la etiqueta y el verificador siguio
  # en verde. Ahora es [FAIL] salvo en --rapido (trabajo local, donde ir por delante
  # de la etiqueta es lo normal hasta el ultimo paso).
  if [ "$RAPIDO" = "1" ]; then ARG_TAG="--rapido"; else ARG_TAG=""; fi
  ETQ=$(python scripts/p9-etiqueta.py $ARG_TAG) && RC=0 || RC=$?
  echo "$ETQ"
  case "$ETQ" in
    *"[WARN]"*) warn "P9: la etiqueta no esta en HEAD -- hay que reubicarla antes del cierre" ;;
    *"[OK]"*)   ok "P9: la etiqueta anotada apunta a HEAD" ;;
    *)          fail "P9: la etiqueta v1.1.0 no esta en HEAD o no es anotada (esto ya no solo avisa)" ;;
  esac
else
  fail "P9: no existe el tag v1.1.0"
fi
grep -q 'version: "1.1.0"' CITATION.cff && ok "CITATION.cff declara version 1.1.0" || fail "P9: CITATION.cff no declara 1.1.0"
grep -q 'v1.1.0' Informe-Final/secciones/00-portada.tex && ok "portada declara v1.1.0" || fail "P9: portada no declara v1.1.0"
DOI_V=$(python -c "
import re, io
t = io.open('docs/ZENODO.md', encoding='utf-8').read()
print(re.search(r'DOI de esta versi[oó]n \| \[10\.5281/zenodo\.(\d+)\]', t).group(1))
" 2>/dev/null) || DOI_V=""
# El DOI de la version archivada sale de docs/ZENODO.md (donde se registra al publicar), no de una
# constante aqui: cada vez que se archiva una version nueva, este script seguia citando la anterior.
if [ -n "$DOI_V" ] && grep -q "zenodo.$DOI_V" CITATION.cff; then ok "CITATION.cff cita el DOI de la version v1.1.0 archivada (zenodo.$DOI_V)"; else fail "P9: CITATION.cff no cita el DOI que docs/ZENODO.md registra para v1.1.0"; fi
# Un DOI no existe hasta publicarse, asi que los commits que lo registran son
# posteriores al snapshot que archiva. Esto acota esa diferencia: lo unico que
# puede separar el commit archivado del etiquetado es el registro del DOI.
if PYTHONIOENCODING=utf-8 python scripts/p9-snapshot-zenodo.py; then
  ok "P9: el tag no se adelanta al snapshot de Zenodo con nada que no sea el registro del DOI"
else
  fail "P9: el tag y el snapshot archivado en Zenodo dejaron de describir lo mismo -- ver arriba"
fi
echo

# La revision final dio P9 "con reservas": el tarball es bit a bit el del commit, pero los
# metadatos del REGISTRO estaban mal en tres ejes (cuenta antigua, v1.0.0, sin dataset).
# Se comprueba contra la API publica de Zenodo, no contra lo que el repositorio dice
# haber puesto en el formulario.
if [ "$RAPIDO" = "1" ]; then ARG_ZEN="--rapido"; else ARG_ZEN=""; fi
if PYTHONIOENCODING=utf-8 python scripts/p9-zenodo-registro.py $ARG_ZEN; then
  ok "P9: los metadatos del registro de Zenodo coinciden con el repositorio (cuenta vigente, v1.1.0, dataset enlazado)"
else
  fail "P9: el registro de Zenodo contradice al repositorio -- ver arriba (se corrige en zenodo.org > Editar)"
fi
echo

echo "=== P10 -- Caratula solo con identificacion + URL ==="
PORT=Informe-Final/secciones/00-portada.tex
PORT_DOI=$(grep -c "zenodo\|doi.org" "$PORT" || true)
PORT_NOTAS=$(grep -cE "motivo del tag|notas del proceso|nota real sobre el DOI" "$PORT" || true)
echo "  Portada: $(wc -l < "$PORT") lineas, $PORT_DOI referencias DOI, $PORT_NOTAS notas de proceso"
[ "$PORT_NOTAS" = "0" ] && ok "portada sin recuadro de notas de proceso" || fail "P10: la portada todavia lleva notas de proceso"
[ "$PORT_DOI" = "0" ] && ok "portada sin listado de DOI (solo identificacion + URL del repositorio)" || fail "P10: la portada lista $PORT_DOI DOI; el criterio pide solo identificacion + URL"
grep -q "REPOSITORIO" "$PORT" && ok "portada declara la URL del repositorio" || fail "P10: la portada no declara la URL del repositorio"
echo

echo "=== P11 -- Cifras unicas ==="
CTRL=$(find backend/src/main/java -iname "*Controller.java" | wc -l)
RUT=$(grep -rhoE "CREATE (OR REPLACE )?(PROCEDURE|FUNCTION) [a-zA-Z0-9_.]+" backend/src/main/resources/db/migration/V*.sql | awk '{print $NF}' | sed 's/.*\.//' | sort -u | wc -l)
echo "  Controladores: $CTRL   Rutinas SQL (nombre distinto): $RUT"
[ "$CTRL" = "31" ] && ok "31 controladores (cifra esperada)" || fail "P11: se esperaban 31 controladores, se encontraron $CTRL"
[ "$RUT" = "10" ] && ok "10 rutinas SQL (cifra esperada)" || fail "P11: se esperaban 10 rutinas, se encontraron $RUT"
if python scripts/p11-citas-clases.py; then
  ok "ninguna clase citada en el informe o el SRS apunta a un .java inexistente"
else
  fail "P11: el informe cita clases que ya no existen -- ver la lista de arriba"
fi
# Las cifras de cobertura y el conteo de pruebas estan escritos a mano en decenas
# de documentos: la revision del 18-sep encontro 82,10 / 82,03 / 82,96 conviviendo.
if PYTHONIOENCODING=utf-8 python scripts/cifras-publicadas.py; then
  ok "toda cifra de cobertura publicada es la de cierre o declara de que corrida es"
else
  fail "P11: hay cifras publicadas que el expediente no respalda -- ver la lista de arriba"
fi
echo

echo "=== P12 -- Commits vacios (desde f3d1ff4, el commit que reviso la guia) ==="
EMPTY=$(git log --pretty=format:"%H" f3d1ff4..HEAD 2>/dev/null | while read h; do
  changed=$(git show --stat --format="" "$h" | tail -1)
  parents=$(git show -s --format="%P" "$h" | wc -w)
  if [ "$parents" = "1" ] && ! echo "$changed" | grep -q "file"; then echo "$h"; fi
done | wc -l)
if [ "$EMPTY" = "0" ]; then
  ok "ningun commit vacio nuevo desde f3d1ff4"
else
  fail "P12: $EMPTY commit(s) vacio(s) nuevo(s) sin explicar desde f3d1ff4"
fi
warn "P12: la conversacion con el docente y el equipo completo sigue sin ocurrir -- no es algo que este script pueda verificar como resuelto"
echo

echo "=== EV-2 / EV-4 -- Higiene del arbol y titularidad ==="
# Defecto 4 de la revision del 18-sep: un .pyc sin versionar tirado en el arbol.
PYC=$(find . -name "*.pyc" -not -path "./node_modules/*" -not -path "./Frontend/node_modules/*" 2>/dev/null | wc -l)
PYCACHE=$(find . -type d -name "__pycache__" -not -path "./node_modules/*" -not -path "./Frontend/node_modules/*" 2>/dev/null | wc -l)
if [ "$PYC" = "0" ] && [ "$PYCACHE" = "0" ]; then
  ok "EV-2: ningun .pyc ni __pycache__ en el arbol de trabajo"
else
  fail "EV-2: hay $PYC archivo(s) .pyc y $PYCACHE carpeta(s) __pycache__ en el arbol -- borra con: find . -name '__pycache__' -type d -exec rm -rf {} +"
fi
# Las dos contrasenas en claro que senalo la revision del 18-sep ya se
# retiraron; esto existe para que no vuelvan.
if PYTHONIOENCODING=utf-8 python scripts/ev2-credenciales.py; then
  ok "Seguridad: ninguna credencial en claro en los archivos versionados"
else
  fail "Seguridad: hay credenciales en claro en archivos versionados -- ver arriba"
fi
if PYTHONIOENCODING=utf-8 python scripts/ev4-contribuciones.py --check; then
  ok "EV-4: CONTRIBUCIONES.md cuadra con el historial"
else
  fail "EV-4: CONTRIBUCIONES.md no cuadra con el historial -- regenera con scripts/ev4-contribuciones.py"
fi
echo

# Los temporales se borran solo si todo paso: si algo fallo, el mensaje remite a
# un log, y borrarlo dejaria al que verifica sin lo unico que explica el fallo.
if [ "$FAIL" = "1" ]; then
  echo "(se conserva $TMPV/ con los logs de esta corrida para poder revisar los fallos)"
else
  rm -rf "$TMPV"
fi

if [ "$RAPIDO" = "1" ]; then
  echo "(corrida en modo --rapido: lo pesado quedo en [WARN], no verificado)"
fi

# Sale distinto de 0 si algo fallo. Hasta la revision del 18-sep este script
# terminaba en `exit 0` incondicional y lo declaraba ("esto es un reporte, no un
# gate"), de modo que podia imprimir [FAIL] y aun asi dar por buena la corrida.
# Un verificador que nunca falla no verifica nada.
if [ "$FAIL" = "1" ]; then
  echo "make verify: FALLO -- hay hallazgos [FAIL] arriba. Revisa VERIFICACION.md."
  exit 1
fi
echo "make verify: OK -- ningun [FAIL]. Los [WARN] son brechas ya declaradas."
exit 0
