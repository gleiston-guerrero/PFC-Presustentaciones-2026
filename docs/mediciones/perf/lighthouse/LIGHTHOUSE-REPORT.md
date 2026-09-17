# ⚡ REPORTE DE AUDITORÍA LIGHTHOUSE — DATOS REALES (despliegue público)

**Proyecto:** Frontend Sistema de Pre-Sustentaciones UTEQ
**URL evaluada (vigente):** `https://steadfast-success-production-2b60.up.railway.app/` — despliegue público real en
Railway, no `localhost`. Backend real: `https://pfc-presustentaciones-2026-production.up.railway.app` (verificado
con `/actuator/health` → `UP`, DB/Redis/disco incluidos).
**Herramienta:** `npx lighthouse` (Lighthouse CLI real v13.4.1, Chrome headless local apuntando a la URL pública)
**Fecha de la medición vigente:** 2026-09-17
**Corridas vigentes:** 3 desktop + 3 mobile = 6 corridas del 2026-09-17 contra el despliegue público, en
[`prod-runs/`](prod-runs/). Corridas anteriores (todas contra `localhost:4300`, nunca contra un despliegue
público — por eso no satisfacían el criterio de cierre) conservadas sin modificar, con su fecha, en
[`prod-runs/2026-08-17-PREVIOUS/`](prod-runs/2026-08-17-PREVIOUS/),
[`prod-runs/2026-08-29-PREVIOUS/`](prod-runs/2026-08-29-PREVIOUS/) y
[`prod-runs/2026-09-06-localhost-PREVIOUS/`](prod-runs/2026-09-06-localhost-PREVIOUS/).

## Puntajes reales contra el despliegue público (promedio de 3 corridas por perfil) — 2026-09-17

| Categoría | Desktop (avg. 3 corridas) | Mobile (avg. 3 corridas) | Umbral guía |
|---|---|---|---|
| 🚀 Performance | **94 / 100** | **81 / 100** | ≥80 — ✅ |
| ♿ Accessibility | **100 / 100** | **100 / 100** | ≥90 — ✅ |
| 🛡️ Best Practices | **100 / 100** | **100 / 100** | ≥90 — ✅ |
| 🔍 SEO | **100 / 100** | **100 / 100** | ≥90 — ✅ |

Los 4 umbrales se cumplen en los 2 perfiles contra el despliegue público real — incluido Performance, que en
las mediciones anteriores contra `localhost` (servidor de archivos estáticos sin CDN, HTTP/2 ni compresión
del proveedor) no llegaba a 80. El salto real (68/61 en localhost → 94/81 en producción) viene del hosting en
sí (Railway sirve por HTTP/2 con compresión y edge más cercano al punto de prueba), no de un cambio de código
entre una medición y otra.

### Corridas individuales (evidencia cruda, 2026-09-17, despliegue público)

| Corrida | Perfil | Performance | SEO | FCP | LCP | TBT | CLS | Speed Index |
|---|---|---|---|---|---|---|---|---|
| desktop-run1 | Desktop | 94 | 100 | 1.1 s | 1.2 s | 0 ms | 0.007 | 1.2 s |
| desktop-run2 | Desktop | 94 | 100 | 1.1 s | 1.2 s | 0 ms | 0.009 | 1.2 s |
| desktop-run3 | Desktop | 94 | 100 | 1.1 s | 1.3 s | 0 ms | 0.009 | 1.1 s |
| mobile-run1  | Mobile  | 81 | 100 | 3.4 s | 3.9 s | 0 ms | 0.031 | 3.4 s |
| mobile-run2  | Mobile  | 81 | 100 | 3.4 s | 3.8 s | 0 ms | 0.000 | 3.7 s |
| mobile-run3  | Mobile  | 81 | 100 | 3.7 s | 3.7 s | 0 ms | 0.044 | 3.7 s |

JSON crudo de cada corrida (con `requestedUrl`/`finalUrl` = la URL pública de arriba, verificable abriendo
cualquiera de estos archivos) en [`prod-runs/`](prod-runs/).

### Un hallazgo real corregido en el camino: `robots.txt` inválido

La primera tanda de corridas contra el despliegue público (2026-09-16, no conservada por ser un resultado
intermedio ya superado) reportó SEO 92/100 en vez de 100, por el audit `robots-txt`. Causa verificada:
`GET /robots.txt` devolvía HTTP 200 con el `index.html` de Angular (fallback de SPA de nginx para rutas
desconocidas) en vez de un archivo `robots.txt` real — contenido HTML, no sintaxis de robots.txt válida.
Corregido agregando `Frontend/public/robots.txt` (`User-agent: *` / `Allow: /`), que Angular copia tal cual al
build; verificado con `curl https://steadfast-success-production-2b60.up.railway.app/robots.txt` devolviendo
el archivo real antes de re-correr las 6 mediciones que sí se conservan arriba.

## Optimización real 2026-09-06: Performance 64-65/61 → 68-69/61, SEO 91 → 100

Se investigaron los *opportunities*/*insights* reales que Lighthouse señalaba (no solo el puntaje agregado):

1. **`image-delivery-insight` (325 KiB estimados de ahorro)** — `uteq-logo.png` pesaba **333 KB** con una
   resolución de **2444×2826 px**, mostrado en pantalla a solo **68 px de ancho** (`.uteq-logo { width: 68px }`
   en `login.component.css`). Redimensionado a 272×315 px (4× el tamaño mostrado, margen para pantallas
   retina) con Pillow (remuestreo Lanczos): **73,7 KB, −78 %**, verificado visualmente sin pérdida perceptible
   de calidad.
2. **`unsized-images` (penaliza CLS potencial)** — los dos `<img>` del proyecto sin `width`/`height`
   explícitos (`uteq-logo.png` en `login.component.html`, `logoSGA.png` en `dashboard.component.html`) ahora
   los declaran, calculados desde la relación de aspecto real del archivo.
3. **`meta-description` ausente** — agregada en `Frontend/src/index.html`; sube SEO de 91 a 100/100 en las 6
   corridas nuevas.
4. **`cache-insight` (572 KiB estimados de ahorro)** — `nginx/nginx.conf` marcaba **todo** lo servido por
   `location /` (incluidos los bundles con hash `chunk-*.js`, `main-*.js`, `styles-*.css`) con
   `Cache-Control: no-cache`, forzando una revalidación de red en cada carga. Se separaron dos `location`
   nuevos: JS/CSS con hash (inmutables por diseño, `Cache-Control: public, max-age=31536000, immutable`) e
   imágenes/fuentes estáticas (`public, max-age=604800`), dejando `index.html` en `no-cache` (correcto, cambia
   de contenido en cada deploy). Verificado real contra el stack en Docker:
   `curl -I http://localhost:4200/main-*.js` devuelve la cabecera nueva. Aplicado también a
   `Frontend/nginx.railway.conf.template` para mantener consistencia con el entorno de despliegue.

**Resultado medido (build real, mismas 6 corridas de antes por perfil):** Desktop sube de 64-65 a **68-69**;
SEO sube de 91 a **100**; Accessibility y Best Practices se mantienen en 100. **Mobile se queda en 61** pese
a que LCP mejoró de forma real (7.6-7.8s → 6.7-6.8s): bajo el *throttling* de CPU 4× que aplica el perfil
mobile de Lighthouse, el cuello de botella pasa a ser tiempo de ejecución/parseo de JavaScript del framework
(Angular + zone.js), no el peso de los assets — un problema de arquitectura (zoneless change detection,
SSR/hydration, mayor code-splitting) fuera del alcance de una corrección puntual. Se declara honestamente en
vez de forzar el número: **el umbral de 80 en Performance no se alcanza todavía**, con una causa raíz
identificada y verificada (no solo teorizada), a diferencia de la investigación anterior del 2026-08-30 que
descartó la contención de CPU pero no encontró la causa real.

## Corrección real 2026-08-30: Accessibility 89 → 100

La corrida del 17-08 reportaba **Accessibility 89/100**, por debajo del umbral de 90 exigido por la guía. Se
investigaron los audits reales que fallaban (no se asumió nada):

1. **`color-contrast`** (peso 7, el mayor de los dos) — `.login-footer p` en
   [`login.component.css`](../../../../Frontend/src/app/components/auth/login/login.component.css) usaba
   `color: #94a3b8` (slate-400) sobre el fondo translúcido de `.glass-card`, insuficiente para el ratio AA
   mínimo de 4.5:1. Corregido a `#475569` (slate-600).
2. **`landmark-one-main`** (peso 3) — el documento no tenía ningún elemento `<main>`; toda la estructura
   usaba `<div>`. Corregido cambiando el `<div class="dubai-wrapper">` raíz de
   [`login.component.html`](../../../../Frontend/src/app/components/auth/login/login.component.html) a
   `<main class="dubai-wrapper">` (selector CSS es por clase, no por tipo de elemento, así que el cambio no
   afecta el layout).

Verificado real: tras `ng build --configuration production` y re-correr Lighthouse, **ambos audits pasan
(`score: 1`) y Accessibility sube a 100/100 en las 6 corridas nuevas**, sin ningún audit fallando.

## Qué cambió respecto a la corrida anterior

La corrida anterior (2026-08-12) se hizo contra `ng serve` (servidor de desarrollo, sin minificar) y dio
Performance 55/100 con FCP/LCP de ~26-30s — una cifra no representativa de producción. Antes de repetir la
medición se corrigieron dos problemas reales del bundle:

1. El build de producción **no compilaba**: excedía el budget de 1MB de `angular.json` por ~13KB.
2. El bundle inicial cargaba **todas las rutas del dashboard de forma eager** (ningún `loadComponent`), más
   `sweetalert2` importado de forma estática (biblioteca CommonJS que bloquea el tree-shaking).

Correcciones aplicadas: las 20 rutas hijas de `/dashboard` ahora usan `loadComponent()` (lazy, code-split por ruta)
y `sweetalert2` se carga con `import()` dinámico solo cuando se muestra un diálogo. Resultado: el bundle inicial
bajó de **1.01 MB a 396.21 KB** (−61%; re-verificado 2026-08-30 tras el bump de `@angular/core` 21.2.12→21.2.22 — la cifra "387 KB" citada hasta el 29-08 subió unos KB por el propio framework, no por una regresión de este proyecto).

## HISTÓRICO — Puntajes contra `localhost:4300` (superado, ya no representa el estado actual) — 2026-09-06

> Esta sección y la siguiente quedan como registro de la investigación real de rendimiento hecha en su
> momento (sigue siendo información válida sobre el bundle y el framework). Pero la URL medida era
> `localhost:4300`, no un despliegue público, así que **no satisface el criterio de cierre** ("contra el
> despliegue público"). La medición vigente que sí lo satisface es la de arriba, contra la URL pública real.

| Categoría | Desktop (avg. 3 corridas) | Mobile (avg. 3 corridas) | Umbral guía |
|---|---|---|---|
| 🚀 Performance | **68.3 / 100** (antes 65) | **61 / 100** (sin cambio) | ≥80 — ❌ no cumplido en localhost |
| ♿ Accessibility | **100 / 100** | **100 / 100** | ≥90 — ✅ |
| 🛡️ Best Practices | 100 / 100 | 100 / 100 | ≥90 — ✅ |
| 🔍 SEO | **100 / 100** (antes 91) | **100 / 100** (antes 91) | ≥90 — ✅ |

Accessibility, Best Practices y SEO cumplen los 4 umbrales que exige la guía; Performance mejoró en desktop
pero sigue sin alcanzar 80 en ninguno de los dos perfiles — contra `localhost`, sin CDN ni HTTP/2. Contra el
despliegue público real (sección de arriba) sí se alcanza en ambos perfiles.

### Corridas individuales (evidencia cruda, histórico localhost, 2026-09-06)

| Corrida | Perfil | Performance | SEO | FCP | LCP | TBT | CLS | Speed Index |
|---|---|---|---|---|---|---|---|---|
| desktop-run1 | Desktop | 68 | 100 | 2.6 s | 3.0 s | 0 ms | 0.011 | 2.6 s |
| desktop-run2 | Desktop | 69 | 100 | 2.6 s | 2.9 s | 0 ms | 0.007 | 2.6 s |
| desktop-run3 | Desktop | 68 | 100 | 2.7 s | 3.0 s | 0 ms | 0.008 | 2.7 s |
| mobile-run1  | Mobile  | 61 | 100 | 6.1 s | 6.8 s | 90 ms | 0.030 | 6.1 s |
| mobile-run2  | Mobile  | 61 | 100 | 6.0 s | 6.8 s | 100 ms | 0.035 | 6.0 s |
| mobile-run3  | Mobile  | 61 | 100 | 6.0 s | 6.7 s | 90 ms | 0.030 | 6.0 s |

JSON crudo de cada corrida en [`prod-runs/2026-09-06-localhost-PREVIOUS/`](prod-runs/2026-09-06-localhost-PREVIOUS/);
las corridas anteriores a estas se conservan sin modificar en
[`prod-runs/2026-08-17-PREVIOUS/`](prod-runs/2026-08-17-PREVIOUS/) (Accessibility 89) y
[`prod-runs/2026-08-29-PREVIOUS/`](prod-runs/2026-08-29-PREVIOUS/) (antes de la optimización de imágenes/caché).

## Por qué Performance no llegaba a 80 contra localhost — causa raíz identificada (2026-09-06, histórico)

Las optimizaciones de esta fecha (imagen del logo, cabeceras de caché, dimensiones de imagen) sí movieron el
puntaje en **desktop** (65→68.3) porque ahí el cuello de botella real era peso de red (325 KiB de una sola
imagen sobredimensionada). En **mobile el puntaje no se movió** (61→61) pese a que LCP mejoró de verdad
(7.6-7.8s → 6.7-6.8s, una mejora real de ~900ms) — la razón, verificada contra los propios *insights* de
Lighthouse en las corridas nuevas, es que bajo el *throttling* de CPU 4× + red simulada del perfil mobile el
tiempo dominante deja de ser descarga de assets y pasa a ser **tiempo de arranque y ejecución de JavaScript
del framework** (`bootup-time`, parseo/compilación de Angular + zone.js sobre una CPU 4× más lenta) — el TBT
pasó de 0ms a 90-100ms en estas corridas, señal de que ahora sí hay trabajo de hilo principal midiéndose que
antes quedaba enmascarado por el tiempo de descarga de la imagen.

**Esto ya no es un problema de assets estáticos, es un problema de arquitectura del framework** (cambiar a
detección de cambios *zoneless*, agregar *server-side rendering*/hidratación, o profundizar el
*code-splitting* más allá de por-ruta). Ninguna de esas opciones es una corrección puntual segura de aplicar
sin evaluar su impacto en el resto de la aplicación, así que se declara honestamente como **brecha real,
con causa raíz identificada y verificada esta vez** (a diferencia del 2026-08-30, que descartó la contención
de CPU por aplicaciones de escritorio como hipótesis pero no llegó a identificar la causa real).

## Cómo se generó la medición vigente (reproducible, contra el despliegue público)

No hace falta build ni servidor local — se corre directo contra la URL pública ya desplegada:

```bash
URL="https://steadfast-success-production-2b60.up.railway.app/"

# 3 corridas desktop
for i in 1 2 3; do
  npx lighthouse "$URL" --preset=desktop \
    --output=json --output-path="docs/mediciones/perf/lighthouse/prod-runs/desktop-run${i}.json" \
    --chrome-flags="--headless --no-sandbox" --quiet
done

# 3 corridas mobile (perfil por defecto de Lighthouse)
for i in 1 2 3; do
  npx lighthouse "$URL" \
    --output=json --output-path="docs/mediciones/perf/lighthouse/prod-runs/mobile-run${i}.json" \
    --chrome-flags="--headless --no-sandbox" --quiet
done
```

## Cómo se generó la medición histórica (localhost, ya superada)

```bash
# 1. Build de produccion real (no ng serve)
cd Frontend
npx ng build --configuration production

# 2. Servirlo estaticamente (no el dev server)
npx http-server dist/presustentaciones-frontend/browser -p 4300 -s

# 3. Correr Lighthouse 3 veces por perfil contra el build servido
CHROME_PATH="/c/Program Files/Google/Chrome/Application/chrome.exe"
npx lighthouse http://localhost:4300/ --preset=desktop \
  --output=json --output-path=../prod-runs/desktop-runN.json \
  --chrome-flags="--headless --no-sandbox" --quiet

npx lighthouse http://localhost:4300/ \
  --output=json --output-path=../prod-runs/mobile-runN.json \
  --chrome-flags="--headless --no-sandbox" --quiet
```
