# Pruebas de carga k6 — datos reales

## Historial

Una versión anterior de `run1-summary.json`, `run2-summary.json` y `run3-summary.json` contenía números inventados. Se reemplazaron por 2 corridas reales (`run1`, `run2`) contra `/api` (sin versionar, antes de implementar el versionado dinámico); ambas resultaron **inválidas** (ver "Corridas inválidas conservadas como evidencia" abajo). Tras cerrar la brecha de reproducibilidad/CI de la Unidad IV (Fase 5) se agregaron **3 corridas válidas** (`run3`, `run4`, `run5`), y en la auditoría de reproducibilidad de 2026-08-29 se agregaron **2 corridas válidas más** (`run6`, `run7`) para llegar a las **5 corridas independientes válidas** que exige la guía (`run3`-`run7`), más un análisis estadístico de caché frío vs caliente.

## Corridas inválidas conservadas como evidencia (`run1`, `run2`)

`run1-summary.json` y `run2-summary.json` tuvieron **0 éxitos / 2938 fallos (100%)** en el check
`"status is 200 or 401"` — el script de esa época hacía login en cada iteración contra
`/api/auth/login` (sin versionar) y apuntaba a `/catalogos/carreras`, un endpoint que **nunca
existió** en el backend (`CatalogoController` solo expone `/modalidades`, `/convocatorias` y
`/convocatoria-activa`). No se depuran ni se sobrescriben estos dos archivos — quedan como
evidencia real de un fallo real, ya diagnosticado (ver sección siguiente) y corregido en `run3`
en adelante. **No se usan para ninguna estadística ni figura de rendimiento**: `scripts/gen-figuras.py`
grafica explícitamente solo `run3`-`run7`, y la tabla de resultados de abajo solo reporta las 5
corridas válidas.

## Cambio de metodología entre run1/run2 y run3/run4/run5

Entre las corridas 1-2 y las corridas 3-5 el backend cambió de forma que rompe el script original:

1. **Versionado dinámico (`/api/v1/`)**: `/api/auth/login` sin versión ahora devuelve 403. `BASE_URL` se actualizó a `http://localhost:8080/api/v1`.
2. **Rate limiting en login** (6 intentos/60s → 429): el script original hacía login en **cada iteración**. Con 50 VUs concurrentes eso agota el límite casi de inmediato — el primer intento de corrida 3 con el script viejo dio **99.8% de fallos**, todos por rate limiting, no por falta de capacidad. Esa corrida se conserva como evidencia de que el rate limiter funciona: [`rate-limiter-evidence-run.json`](rate-limiter-evidence-run.json). El script se rediseñó con un `setup()` de k6 que hace **un solo login** y reutiliza el token; las iteraciones prueban el flujo real de un usuario ya autenticado.
3. **Endpoint inexistente**: el script original apuntaba a `/api/catalogos/carreras`, que **nunca existió** en el backend (`CatalogoController` solo expone `/modalidades`, `/convocatorias` y `/convocatoria-activa`). Como la petición iba sin token, siempre devolvía 403 antes de llegar al enrutador, por lo que el 404 real nunca se notó. Se corrigió a `/catalogos/modalidades` (endpoint real) y se agregó `/universidades` (el endpoint con caché Redis, Requisito E2) para ejercitar más superficie de la API.

## Cómo se ejecutaron (runs 3-7, las 5 válidas)

```bash
# k6 nativo, backend real accesible en localhost:8080 (docker compose up -d --build)
cd k6 && BASE_URL=http://localhost:8080/api/v1 k6 run --quiet --summary-export=runN-summary.json load-test.js
```

`run3`-`run5` se ejecutaron el 2026-08-17 contra un backend levantado con `mvnw spring-boot:run`.
`run6`-`run7` se ejecutaron el 2026-08-29 contra el stack completo de `docker compose up -d --build`
(nginx → backend → Postgres/Redis), como parte de la verificación de reproducibilidad end-to-end —
mismo script `load-test.js`, sin cambios, para que las 5 corridas sean comparables entre sí.

## Resultados reales — 5 corridas válidas (~2 minutos cada una: 30s ramp-up a 20 VUs, 1min en 50 VUs, 30s ramp-down)

| Métrica | Run 3 | Run 4 | Run 5 | Run 6 | Run 7 |
|---|---|---|---|---|---|
| Fecha | 2026-08-17 | 2026-08-17 | 2026-08-17 | 2026-08-29 | 2026-08-29 |
| Requests totales | 6,231 | 6,237 | 6,241 | 6,255 | 6,271 |
| `http_req_duration` p95 | 7.70 ms | 7.39 ms | 7.47 ms | 8.53 ms | 6.17 ms |
| `http_req_duration` avg | 7.48 ms | 6.26 ms | 6.30 ms | 5.00 ms | 3.87 ms |
| `http_req_failed` | 0% | 0% | 0% | 0% | 0% |
| Umbral `p(95)<500ms` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Umbral `http_req_failed<1%` | ✅ | ✅ | ✅ | ✅ | ✅ |

Media de p95 sobre las 5 corridas válidas: **7.45 ms**. Las corridas `run1`/`run2` (metodología
distinta, 100% de fallos en su check principal) se excluyen de este promedio — ver la sección
anterior.

## Corrida 8 (2026-09-06) — con datos crudos por petición (NDJSON)

Las corridas `run3`-`run7` solo archivan el resumen agregado que exporta `--summary-export`; ninguna
conserva el detalle petición-por-petición. Se agrega `run8` con ambos archivos, ejecutada contra el
stack real en Docker (`BASE_URL=http://localhost:4200/api/v1`, misma configuración de `load-test.js`
sin modificar):

```bash
k6 run --out json=k6/run8-raw.ndjson --summary-export=k6/run8-summary.json k6/load-test.js
```

| Métrica | Run 8 |
|---|---|
| Fecha | 2026-09-06 |
| Requests totales | 6,249 |
| `http_req_duration` p95 | 8.77 ms |
| `http_req_duration` avg | 5.58 ms |
| `http_req_failed` | 0.00 % |
| Umbral `p(95)<500ms` | ✅ |
| Umbral `http_req_failed<1%` | ✅ |

Consistente con el rango de `run3`-`run7` (p95 6.17-8.53 ms). Datos crudos:
[`run8-summary.json`](run8-summary.json) (resumen agregado, igual que las corridas anteriores) y
[`run8-raw.ndjson.gz`](run8-raw.ndjson.gz) (**75.243 líneas**, un evento JSON por punto de métrica y
por petición HTTP real — descomprimir con `gunzip run8-raw.ndjson.gz`; se versiona comprimido, 434 KB
en vez de 21,9 MB sin comprimir, sin perder ni un byte del contenido original).

## Análisis estadístico: caché fría vs caché caliente (`GET /api/v1/universidades`)

Requisito: comparar los escenarios de caché fría (sin entrada en Redis, dispara la llamada real a la API externa de Hipo Labs) y caché caliente (respuesta servida desde Redis, TTL 10 min) con percentiles, media, IC 95% y un test no paramétrico (Wilcoxon/Mann-Whitney) con tamaño de efecto.

**Método:** 30 muestras independientes por escenario. Frío: `redis-cli DEL universidades::ecuador` inmediatamente antes de cada petición (fuerza una llamada real a la API externa en cada muestra). Caliente: una petición de precalentamiento, luego 30 peticiones consecutivas dentro del TTL de 10 minutos. Mismo token JWT, mismo proceso backend, misma máquina. Datos crudos: [`cache-cold-samples.txt`](cache-cold-samples.txt), [`cache-warm-samples.txt`](cache-warm-samples.txt).

| Métrica | Caché fría (n=30) | Caché caliente (n=30) |
|---|---|---|
| Media | 109.62 ms | 7.86 ms |
| Desv. estándar | 18.88 ms | 0.75 ms |
| IC 95% de la media | [102.57, 116.67] ms | [7.58, 8.14] ms |
| p50 (mediana) | 106.02 ms | 7.60 ms |
| p90 | 107.04 ms | 8.42 ms |
| p95 | 108.07 ms | 8.89 ms |
| p99 | 180.31 ms | 10.50 ms |
| Mínimo / Máximo | 104.96 / 209.51 ms | 7.27 / 11.15 ms |

**Test de Wilcoxon rank-sum (Mann-Whitney U), dos muestras independientes:**

- U = 0 (de un máximo posible de 900 = 30×30) — **separación perfecta**, ninguna muestra fría fue más rápida que ninguna muestra caliente.
- z = −6.65, **p < 0.0001** (dos colas) — la diferencia es estadísticamente significativa muy por debajo de α=0.05.
- Correlación biserial de rangos (tamaño de efecto) r = **1.00** — efecto máximo posible según las convenciones de Cohen para r (>0.5 ya se considera grande).

**Familia de comparaciones + corrección por comparaciones múltiples (P6):** sobre este mismo par de
muestras también se prueba si difiere la mediana (p50) y si difiere la cola (p95), cada una con un
test de permutación de dos colas ($10^5$ permutaciones, semilla fija). Probar 3 hipótesis
relacionadas sobre los mismos 60 datos sin corregir infla el error familiar por encima de
$\alpha=0.05$; se aplica **corrección de Holm-Bonferroni** (step-down, Holm 1979), preferida sobre
Bonferroni simple por tener más potencia con la misma garantía, y sobre Benjamini-Hochberg porque
con una familia de tamaño 3 interesa el control familywise (cero falsos positivos), no una tasa de
falsos descubrimientos tolerable (pensada para familias de docenas/cientos de pruebas):

| Prueba | p crudo | Umbral Holm | p ajustado | Decisión (α=0.05) |
|---|---|---|---|---|
| Mann-Whitney U (distribución completa) | 3.02×10⁻¹¹ | 0.0167 | 9.06×10⁻¹¹ | Significativo |
| Permutación, diferencia de medianas (p50) | 1×10⁻⁵ | 0.025 | 2×10⁻⁵ | Significativo |
| Permutación, diferencia de p95 | 1×10⁻⁵ | 0.05 | 2×10⁻⁵ | Significativo |

Los p-valores de permutación se reportan como 1×10⁻⁵ (piso reportable con 10⁵ permutaciones —
ninguna de las 10⁵ reasignaciones aleatorias igualó o superó la diferencia observada) en vez de 0
exacto. Las tres pruebas siguen significativas tras la corrección, consistente con que el mínimo en
frío (104.96 ms) ya supera el máximo en caliente (11.15 ms). Reproducible con
[`scripts/perf-analysis.ipynb`](../scripts/perf-analysis.ipynb).

**Conclusión:** la caché Redis reduce la latencia de este endpoint en ~14× (109.6 ms → 7.9 ms de media), con una diferencia estadísticamente significativa y de tamaño de efecto máximo, robusta a la corrección por comparaciones múltiples. Esto valida cuantitativamente el Requisito E2 (caché Redis activa con TTL).

### Cómo se reprodujo

```bash
# La contraseña se toma del entorno: ninguna credencial de demostración se versiona
# en este repositorio público (RNF-15 del SRS, observación docente OBS-11).
ADMIN_PASSWORD=${ADMIN_PASSWORD:?exporta ADMIN_PASSWORD antes de ejecutar}
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"admin@uteq.edu.ec\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>console.log(JSON.parse(d).data.auth.token))")

# Frio: flush + request, 30 veces
for i in $(seq 1 30); do
  docker exec <contenedor-redis> redis-cli DEL "universidades::ecuador" > /dev/null
  curl -s -o /dev/null -w "%{time_total}\n" http://localhost:8080/api/v1/universidades \
    -H "Authorization: Bearer $TOKEN"
done > cache-cold-samples.txt

# Caliente: precalentar + 30 requests consecutivos
curl -s -o /dev/null http://localhost:8080/api/v1/universidades -H "Authorization: Bearer $TOKEN"
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{time_total}\n" http://localhost:8080/api/v1/universidades \
    -H "Authorization: Bearer $TOKEN"
done > cache-warm-samples.txt
```

Percentiles, IC 95% (aproximación t, df=29) y el test de Mann-Whitney/Wilcoxon (con corrección por empates y aproximación normal para el p-valor) se calcularon con un script de Node sin dependencias externas.
