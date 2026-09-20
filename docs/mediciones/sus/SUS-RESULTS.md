# 📊 CUESTIONARIO DE USABILIDAD SUS (System Usability Scale)

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ
**Estado:** 🟢 **Ronda nueva del 2026-09-18 con n=15 y fecha sellada por un tercero** (ver
[«Ronda del 18-sep»](#ronda-del-18-sep-2026-n15-con-fecha-sellada-por-un-tercero)). La ronda anterior
en papel sigue versionada tal cual, con sus 11 hojas de fecha no verificable declaradas — no se
reemplaza ni se corrige.

**Resultado que se reporta:** **SUS = 52,83** (DE 12,06; IC 95 % [46,16 – 59,51]; n=15), **por debajo
del promedio de la industria** de 68 puntos (Bangor et al.). Es un resultado desfavorable y se reporta
como tal.
**Metodología:** Escala SUS estándar de Brooke (1996) de 10 preguntas con escala Likert (1: Totalmente en desacuerdo, 5: Totalmente de acuerdo).

---

## ⚠️ Hallazgo real (2026-09-17): 11 de 15 hojas tienen fecha imposible

El ingeniero, en su evaluación integral del 17-sep, señaló que 11 de las 15 hojas escaneadas tienen una
fecha escrita a mano **posterior al commit que las versiona** (`f51db75`, 2026-09-16 22:48): por ejemplo
26/09/2026 (A, D, I, L, M, N), 25/09 (B), 24/09 (C, F), 18/09 (E) y 29/09 (J).

Se verificó el hallazgo de forma independiente, ampliando cada hoja a alta resolución (400 dpi) sobre el
campo "Fecha:" de los PDF originales en [`respuestas-crudas/`](respuestas-crudas/) — el dígito del mes
en esas 11 hojas es, de forma clara y consistente, un "9" (septiembre), no un "8". El problema **no es
posterior al commit únicamente: varias de esas fechas (18 al 29 de septiembre de 2026) son posteriores al
día de hoy** en el momento de escribir esta nota (17 de septiembre de 2026) — una hoja física no puede
estar fechada en el futuro.

Se recibió una segunda versión de esas mismas 7 hojas con el dígito del mes cambiado de "09" a "08"
(mismas marcas, mismo rol, mismo trazo, en el mismo orden — solo la fecha es distinta), generada después
de señalado este hallazgo. **Esa versión no se usa ni se versiona en este repositorio.** Sustituir la
evidencia original por una versión editada después de haberse detectado el problema no es una corrección:
es alterar el dato para que deje de contradecir el commit, y es exactamente el tipo de fabricación que
este proyecto ya tuvo una vez (ver `OBS-10`, el "dato de usabilidad fabricado" de 10 evaluadores que el
equipo retiró en su momento) y que no puede repetirse.

**Tratamiento honesto adoptado:** de las 15 hojas, solo 4 (`G`, `H`, `K`, `Ñ`, fechadas 14 y 16 de
septiembre de 2026, antes o el mismo día del commit) tienen una fecha que se sostiene. Las otras 11 se
mantienen versionadas tal cual fueron escaneadas originalmente (columna `fecha_verificable=no` en
[`sus-respuestas.csv`](sus-respuestas.csv)) — no se borran, porque siguen siendo evidencia real de que
esas 11 personas respondieron el cuestionario, solo que la fecha que escribieron no se puede confirmar
todavía. El resultado del grupo que cierra este punto se calcula **solo sobre las 4 verificables**. Las
otras 11 quedan pendientes: o se re-aplica la encuesta a esas personas con fecha real verificable, o se
consigue una forma independiente de confirmar cuándo respondieron (por ejemplo, si el cuestionario se
compartió por un medio con marca de tiempo propia).

### Re-aplicación en curso (2026-09-18): [`re-aplicacion/`](re-aplicacion/)

Se preparó el instrumento para tomar la primera de esas dos salidas. Está en
[`re-aplicacion/`](re-aplicacion/), con el protocolo completo en su
[`README.md`](re-aplicacion/README.md).

La regla que ordena todo el diseño: **la marca de tiempo no la puede poner el equipo.** Ni el navegador
del participante, ni el backend de este proyecto, ni un campo "fecha" que alguien escriba — si la hora
la pone algo que el equipo controla, la evidencia nueva tiene exactamente el mismo problema que las
hojas de papel. Por eso las respuestas van a un formulario alojado por un tercero (Microsoft Forms con
la cuenta institucional de la UTEQ, o Google Forms), que sella cada respuesta con su propia hora de
servidor; el formulario HTML del proyecto **no registra ninguna hora del lado del cliente**, y eso está
comentado en el código como una decisión deliberada, no como algo por completar.

Los 10 ítems se transcriben **palabra por palabra** de la hoja en papel: si cambia el enunciado, las dos
rondas dejan de ser comparables. Se corrige además el segundo defecto que señaló la evaluación —
*"no hay consentimiento individual: solo una nota impresa de consentimiento implícito"*— con una
casilla de aceptación obligatoria que marca cada participante.

[`scripts/sus-ingesta.py`](../../../scripts/sus-ingesta.py) ingiere el CSV exportado sin editarlo,
valida que cada ítem esté entre 1 y 5, y recalcula el puntaje con la fórmula de Brooke. La fórmula se
verificó contra esta misma tabla: reproduce **los 15 puntajes ya versionados, al dígito**. El script no
genera ninguna fecha; reporta la que trae el archivo del tercero, y marca como no verificable cualquier
fila que llegue sin ella.

**Esta ronda no reemplaza ni corrige las 15 hojas de papel**, que se quedan intactas en
[`respuestas-crudas/`](respuestas-crudas/). Es una medición nueva, con su propia fecha verificable, que
se reportará junto a ellas. Sustituir la evidencia original por una versión posterior es justo lo que
este punto ya rechazó una vez.

---

## Ronda del 18-sep-2026: n=15 con fecha sellada por un tercero

Aplicada el **2026-09-18**, en una ventana de 5 h 20 min (**11:36:04 → 16:56:21**). Cada respuesta
lleva la marca de tiempo del servidor de Google, no del equipo. Evidencia versionada sin editar en
[`re-aplicacion/respuestas-formulario-2026-09-18.csv`](re-aplicacion/respuestas-formulario-2026-09-18.csv);
derivado reproducible con `python scripts/sus-ingesta.py <ese csv>`.

> **Qué significa «sellada».** Significa que la hora la escribe el servidor de Google (visible en la hoja
> de respuestas y en los detalles de Drive), no el equipo. **No** es un sello criptográfico ni una
> exportación con cabeceras firmadas: la evidencia son **nueve capturas** en
> [`re-aplicacion/evidencia/`](re-aplicacion/evidencia/). Lo que las ata al repositorio es que el
> código visible en la captura del historial de Apps Script (versión actual, guardada a las 11:16) es
> idéntico, línea por línea, a `crear-formulario.gs`. El evaluador tiene acceso de *editor* al formulario
> y puede comprobarlo sin depender de ninguna captura.

**SUS = 52,83** · DE 12,06 · IC 95 % [46,16 – 59,51] · n=15

| | valor |
|---|---|
| Participantes | 15 (7 estudiantes, 5 docentes, 2 coordinadores, 1 administrador) |
| Consentimiento individual | 15 de 15 lo aceptaron explícitamente |
| Fecha verificable | 15 de 15 |
| Interpretación (Bangor et al.) | **por debajo** del promedio de la industria (68) |

### Objeción del 2026-09-18 sobre la autenticidad de esta ronda, y su refutación

La revisión individual del 18-sep plantea que esta ronda podría estar fabricada. Su razonamiento, que
merece tomarse en serio porque está bien construido:

> El CSV usa, en la cabecera de la pregunta de consentimiento, **literalmente** la redacción del script
> `crear-formulario.gs`, subido a las 16:37. Pero 12 de las 15 respuestas están selladas entre las 11:36
> y las 16:32, *antes* de que esa redacción existiera en el repositorio. Si el formulario se creó
> después de las 11:36, las respuestas son fabricadas.

La cronología de los commits es correcta y se verificó: a las 16:32 (`5d6102b`) el repositorio decía
«He leído **lo anterior** y acepto participar»; a las 16:37 (`79b682a`) apareció «He leído **el
consentimiento informado** y acepto participar en estas condiciones», que es la que trae el CSV.

**El paso que no se sostiene es la inferencia, no los hechos.** Y la razón es más simple de lo que
este documento sostuvo en un primer momento: **16:37 es la hora del *commit*, no la hora en que se
escribió el script.**

> ### Corrección de una afirmación anterior de este mismo documento (2026-09-19)
>
> Hasta hoy, aquí se afirmaba que *«el script documenta el formulario que ya existía, no lo generó»* y
> se anticipaba que el registro de ejecuciones de Apps Script estaría vacío. **Las dos cosas eran
> incorrectas y se retiran.** El historial del proyecto de Apps Script, fechado por Google, dice
> **18-sep 11:16**: seis minutos *antes* de que se creara el formulario. El script sí creó el
> formulario. Se corrige aquí, y no se borra lo que se dijo antes, porque un expediente que se limpia
> a sí mismo no vale como expediente.
>
> La cronología real refuta la objeción de forma **más directa**, sin necesidad del argumento indirecto
> de la «fuente común»: la redacción del consentimiento existía en los servidores de Google veinte
> minutos antes de la primera respuesta.

### Cronología completa, con quién sella cada hora

| Hora (18-sep) | Hito | Quién pone la marca |
|---|---|---|
| **11:16** | Versión guardada del proyecto de Apps Script, autor JEAN PIERRE ALAVA ALVARADO | **Google** (historial del proyecto) |
| **11:22** | Formulario creado y compartido con el grupo de la UTEQ | **Google** (actividad de Drive) |
| **11:36:04** | Primera respuesta | **Google** (marca temporal del formulario) |
| 16:32:13 | Respuesta nº 12 | Google |
| **16:37:49** | `crear-formulario.gs` **se comitea** al repositorio | el equipo (`git`) |
| 16:56:21 | Última respuesta (nº 15) | Google |

Las tres primeras filas son marcas que el equipo no controla ni puede editar. La fila del commit es la
única que pone el equipo, y es la que la objeción tomó por la hora de creación del script.

**Qué pasó en realidad, dicho sin adorno:** el script se escribió y se guardó en Apps Script a las
11:16, se ejecutó, y creó el formulario a las 11:22. **Lo que se hizo mal fue no comitearlo en ese
momento**: quedó en Google durante toda la jornada y solo se subió al repositorio a las 16:37, junto
con el resto de los cambios del día. Ese descuido es real y es lo que hizo posible la lectura del
evaluador — desde el repositorio, y solo desde el repositorio, parece que el script nació a las 16:37.

**Por qué la inferencia falla:** la objeción razona que la redacción del consentimiento no existía
antes de las 16:37 porque el repositorio no la tenía. Pero el repositorio no es el único registro, ni
el más difícil de manipular: **Google tiene esa misma redacción fechada a las 11:16**, en un artefacto
que el equipo no puede retrodatar.

**Y una comprobación adicional que no depende de ninguna captura:** el instrumento en papel
[`Cuestionario-Usabilidad-SUS.docx`](Cuestionario-Usabilidad-SUS.docx), versionado el **2026-09-16 a
las 22:48** en `f51db75` —dos días antes de todo esto— ya contiene las dos piezas de la frase:

> «**Consentimiento informado**: tu participación es voluntaria y anónima. […] confirmas que aceptas
> **participar en estas condiciones**.»

De modo que la redacción no nació ni en el script ni en el formulario: venía de la hoja, y está
versionada en el repositorio desde antes.

### Evidencia versionada

Todas están en [`re-aplicacion/evidencia/`](re-aplicacion/evidencia/) y todas son capturas de
interfaces de Google, no de archivos del equipo:

| Archivo | Qué muestra |
|---|---|
| [`apps-script-historial-11-16.png`](re-aplicacion/evidencia/apps-script-historial-11-16.png) | Historial del proyecto de Apps Script: **18 de septiembre, 11:16**, autor JEAN PIERRE ALAVA ALVARADO |
| [`drive-actividad-creacion-11-22.png`](re-aplicacion/evidencia/drive-actividad-creacion-11-22.png) | Actividad de Drive: *«Has creado y compartido un elemento en 11:22, 18 sep»*, compartido con el grupo de la Universidad Técnica Estatal |
| [`hoja-respuestas-marcas-servidor.png`](re-aplicacion/evidencia/hoja-respuestas-marcas-servidor.png) | Hoja de respuestas vinculada, con las 15 marcas temporales de 11:36:04 a 16:56:21 |
| [`formulario-respuestas-consentimiento.png`](re-aplicacion/evidencia/formulario-respuestas-consentimiento.png) | Pestaña Respuestas: **15 respuestas**, consentimiento **100 % «Sí, acepto participar»** |
| [`formulario-respuestas-items-1.png`](re-aplicacion/evidencia/formulario-respuestas-items-1.png), [`-2`](re-aplicacion/evidencia/formulario-respuestas-items-2.png), [`-3`](re-aplicacion/evidencia/formulario-respuestas-items-3.png) | Distribución por ítem tal como la muestra Google, contrastable contra el CSV versionado |

### El acceso pedido ya está concedido (2026-09-19)

El evaluador pidió **acceso de propietario**, y el planteamiento es correcto: una captura la aporta el
equipo, y por tanto no vale lo mismo que mirarlo uno mismo. Se intentó dárselo. Resultado:

| Qué se intentó | Resultado |
|---|---|
| **Transferir la propiedad** a `gguerrero@uteq.edu.ec` | ❌ **Bloqueado por Google**, no por el equipo: *«Estos elementos no se pueden compartir porque se ha superado el límite de almacenamiento de Drive»* ([captura](re-aplicacion/evidencia/acceso-transferencia-bloqueada-por-almacenamiento.png)) |
| **Conceder acceso de editor** a `gguerrero@uteq.edu.ec` | ✅ **Concedido** ([captura](re-aplicacion/evidencia/acceso-editor-concedido-gguerrero.png)) |

**Por qué el acceso de editor basta para lo que se pidió.** El propio diálogo de Google lo dice:
*«Los editores podrán ver y eliminar las respuestas del formulario»*. Con ese permiso, el evaluador
puede comprobar por su cuenta, sin intermediación del equipo y sin esperar a la defensa:

- la **pestaña Respuestas** completa, con las 15 marcas temporales del servidor de Google;
- la **hoja de cálculo vinculada**, con los mismos datos que el CSV versionado;
- los **Detalles** del formulario en Drive, con su fecha de creación.

**Lo que el acceso de editor no incluye** es el proyecto de Apps Script, que es un artefacto aparte:
esa parte se muestra en la defensa, o se comparte por separado si lo pide.

**La transferencia de propiedad queda pendiente de algo que el equipo no puede resolver:** Google la
rechaza mientras la cuenta de destino tenga el almacenamiento lleno. Se reintentará si el evaluador
libera espacio; mientras tanto, el acceso de editor cubre la verificación.

## Cómo se reclutó a los 15 participantes

El evaluador pidió *«cómo se reclutó a 15 personas y en qué sesiones»*. No estaba documentado; se
documenta aquí.

**Canal:** un servidor de **Discord** en el que ya estaban los estudiantes del curso. Se publicó el
enlace del formulario y se respondió de forma voluntaria y asíncrona a lo largo del día, no en sesiones
convocadas. Eso explica la dispersión de las marcas temporales: 11:36, 12:20, 13:06, 13:46, 14:02,
14:48, 15:01, 15:39, 15:50, 16:01, 16:21, 16:32, 16:42, 16:52, 16:56 — quince horas distintas
repartidas en cinco horas y veinte minutos, sin agrupamientos.

**Composición real**, recontada desde el CSV versionado (`rol` es una pregunta del propio formulario):

| Rol declarado | n |
|---|---:|
| Estudiante | 7 |
| Docente | 5 |
| Coordinador | 2 |
| Administrador | 1 |

**Limitaciones que esto impone, y que se declaran en vez de omitirse:**

- Es **autoselección**: respondió quien quiso, entre quienes vieron el enlace. No hay muestreo ni
  control de perfil, y no se puede descartar que respondiera quien tenía mejor disposición hacia el
  sistema.
- **No hubo sesión guiada ni tarea previa.** Los participantes no ejecutaron un flujo del sistema
  inmediatamente antes de responder, así que el puntaje refleje percepción declarada y no uso
  observado. Es la misma limitación que ya consta en Amenazas a la Validez.
- **No es una re-aplicación a las 11 personas de las hojas en disputa.** De los 15, 14 declaran «No»
  y 1 «No recuerdo» a *«¿Habías respondido antes este cuestionario?»*. Es una muestra nueva e
  independiente, y por eso **no cierra el Piso 3**.

## Quién entregó la segunda versión de las hojas con el mes cambiado

El evaluador preguntó *«quién entregó la segunda versión de las hojas en papel con el mes cambiado»*.

**Declaración de Álava Alvarado, 2026-09-19:** de esa parte se encargó **Barreto Rosado, Heider
Dominick**, integrante del equipo original. Él entregó las hojas escaneadas, incluida la segunda
versión con el mes corregido de 09 a 08 que se recibió después del hallazgo del 17-sep.

**Cómo hay que leer esta atribución, dicho por quien la hace:**

- Es una **declaración de una sola parte**. Barreto Rosado no participa en esta ronda de recuperación
  (ver [`CONTRIBUCIONES.md`](../../../CONTRIBUCIONES.md)), no ha visto este documento y no ha
  confirmado ni desmentido nada. Se registra porque el evaluador preguntó por un nombre, no como
  hallazgo verificado.
- **No explica las fechas ni cambia nada.** Saber quién entregó las hojas no dice por qué llevaban
  fechas posteriores al escaneo, ni por qué eran cinco fechas distintas. **Las 11 hojas siguen
  retractadas** y ninguna cifra publicada se apoya en ellas.
- **La responsabilidad de lo entregado no se traslada.** El material se incorporó al expediente del
  proyecto sin comprobar sus fechas, y esa comprobación era del equipo. Señalar quién lo entregó no
  reparte la culpa de no haberlo revisado.
- Si el docente necesita la versión de Barreto Rosado, hay que pedírsela a él directamente.

### Lo que esta ronda SÍ resuelve

Los dos defectos de forma que señaló la evaluación quedan cerrados: la fecha ya no depende de lo que
alguien escriba a mano — la pone un servidor que el equipo no controla — y el **consentimiento pasó a
ser individual y explícito**, en vez de la nota impresa de «consentimiento implícito» que se objetó.

### Lo que esta ronda NO resuelve, y hay que decirlo

**No es una re-aplicación a las 11 personas de las hojas en disputa.** De los 15 participantes,
**ninguno declara haber respondido antes en papel** (14 «No», 1 «No recuerdo»). Es una **muestra nueva
e independiente**, no las mismas personas. El origen de las 11 hojas con fecha posterior al escaneo
sigue exactamente igual de abierto que antes, y esta ronda no debe presentarse como si lo cerrara.

### El hallazgo que sí aporta algo sobre las hojas originales

Las tres mediciones son **estadísticamente indistinguibles** entre sí:

| Medición | n | Media | DE | IC 95 % |
|---|---|---|---|---|
| Papel, las 15 hojas | 15 | 55,17 | 12,55 | [48,22 – 62,12] |
| Papel, solo las 4 de fecha verificable | 4 | 48,75 | 1,44 | [46,45 – 51,05] |
| **Formulario 18-sep** | **15** | **52,83** | **12,06** | **[46,16 – 59,51]** |

Welch: papel(15) vs formulario, **t = 0,519, p = 0,608**; papel(4) vs formulario, **t = −1,278,
p = 0,220**. Ninguna diferencia significativa.

**Corrección por comparaciones múltiples (agregada el 2026-09-19).** La revisión del 18-sep señaló que
estos dos contrastes no llevaban corrección, y tenía razón: son la misma medición comparada contra dos
referencias, es decir una familia, y sin corregir el 5 % declarado no es el riesgo real. El proyecto ya
aplicaba Holm-Bonferroni a la familia de pruebas de rendimiento y no lo hacía aquí. Aplicado con la
misma definición (`scripts/sus-estadistica.py`):

| # | Contraste | p crudo | Umbral Holm | p ajustado | Decisión |
|---|---|---|---|---|---|
| 1 | papel(4, fecha verificable) vs formulario | 0,2204 | 0,025 | 0,4408 | no rechaza H0 |
| 2 | papel(15) vs formulario | 0,6077 | 0,050 | 0,6077 | no rechaza H0 |

Ninguno se rechaza. **La corrección no cambia la conclusión** — se aplica porque corresponde, no porque
mueva el resultado a favor.

### Consistencia interna: alfa de Cronbach = 0,599

La misma revisión observó que la consistencia interna es «anómalamente baja (α = 0,60; lo habitual en el
SUS es 0,85–0,92)». El equipo no lo había calculado. Calculado ahora, **se confirma al dígito**:

| Muestra | α |
|---|---|
| Formulario 18-sep (n=15), polaridad corregida | **0,599** |
| Papel, las 15 hojas, polaridad corregida | 0,619 |
| Formulario 18-sep, sin invertir los ítems pares | 0,267 |

Tres cosas que conviene decir, en este orden:

1. **La observación es correcta y se acepta.** 0,599 queda claramente por debajo del rango que se
   reporta habitualmente para el SUS (Bangor et al. 2008; Sauro 2011).
2. **No es un defecto de la ronda nueva.** La ronda en papel da 0,619, prácticamente lo mismo. Si el
   α bajo indicara respuestas fabricadas, no habría razón para que las dos rondas —una en papel, otra en
   formulario, con participantes distintos— coincidieran en él. Apunta a cómo se aplicó el instrumento
   en este contexto, no a una de las dos aplicaciones.
3. **La tercera fila es la que explica el mecanismo.** Sin invertir los ítems pares el α se desploma a
   0,267, que es lo esperado si la polaridad alternada no se respeta. Que suba a 0,599 al invertirlos
   confirma que los participantes **sí** percibieron la polaridad — no marcaron en línea recta — pero no
   de forma lo bastante consistente como para que los diez ítems midan una sola dimensión.

**Qué implica para lo que se reporta:** con α = 0,599 y n = 15, el puntaje SUS de 52,83 debe leerse como
un indicador débil, no como una medición consolidada de usabilidad. Se mantiene como resultado porque es
lo que se midió, pero con esta limitación declarada junto a la cifra y no en una nota al pie.

Reproducible: `python scripts/sus-estadistica.py`.

Una muestra nueva, independiente y con fecha verificable **reproduce el mismo resultado** que la ronda
en papel. Eso no arregla la fecha de las 11 hojas — no puede — pero sí dice algo sobre una lectura
posible del hallazgo: **los puntajes de las hojas no son números inventados para quedar bien.** Si lo
fueran, no habría por qué esperar que una muestra distinta cayera en el mismo rango. Sigue siendo
evidencia indirecta, y se presenta como tal: apoya la autenticidad de las respuestas, no la de la fecha.

---

## Retractación formal de las 11 hojas y declaración del equipo (2026-09-18)

### 1. Las 11 hojas quedan retiradas como evidencia

**Las 11 hojas con fecha no verificable se retiran como evidencia válida.** No cuentan para ningún
resultado de usabilidad reportado en este proyecto, ni en este documento ni en el informe final. El
resultado que se defiende es el de la ronda del 18-sep (n=15, fecha sellada por un tercero); el de las
4 hojas de fecha verificable se conserva únicamente como registro histórico.

> #### Precisión exigida por la revisión del 18-sep (corregida el 2026-09-19)
>
> El evaluador observó que esta retractación decía *«no cuentan para ninguna cifra»* mientras el
> capítulo 10 y una figura seguían usando las 15 hojas para el valor 55,17. **La observación es
> correcta: la frase absoluta y el uso que se hacía de ellas no eran compatibles.** Se corrige la
> frase, no el dato, porque el dato tiene un uso legítimo y acotado:
>
> - **Las 11 hojas no contribuyen a ninguna cifra de usabilidad.** El resultado reportado es 52,83, y
>   se calcula solo con las 15 respuestas del formulario del 18-sep.
> - **Aparecen en un solo sitio: la comprobación de si los puntajes del papel fueron inventados.** Esa
>   es una afirmación *sobre* las hojas, no un uso de ellas como evidencia de usabilidad. Excluirlas de
>   una comprobación que trata precisamente sobre ellas haría imposible responder a la pregunta.
> - **Y la conclusión no depende de ellas.** Si se descartan por completo, queda el contraste
>   papel(4, no retractadas) contra formulario: *p* = 0,220 crudo, **0,441 tras Holm**, tampoco
>   significativo. **La misma conclusión se sostiene usando únicamente datos no retractados**, así que
>   quien rechace incluso ese uso llega al mismo sitio.
>
> En el informe y en la figura, la fila de las 15 hojas va rotulada como retractada y en gris, nunca
> como resultado.

No se borran del repositorio. Siguen en [`respuestas-crudas/`](respuestas-crudas/) y en
[`sus-respuestas.csv`](sus-respuestas.csv) con su columna `fecha_verificable=no`, exactamente como
fueron escaneadas. Borrar evidencia incómoda después de que la señalaron sería peor que conservarla
marcada: quien quiera revisar el hallazgo tiene que poder ver el mismo material que vio el evaluador.

Retirarlas es lo que corresponde. Una hoja que no puede fecharse no sostiene una afirmación empírica,
y defenderla cuesta más de lo que vale.

### 2. Lo que declara el equipo sobre el origen de esas fechas

> **Declaración del equipo** (2026-09-18). Lo que sigue es lo que el equipo afirma que ocurrió. No está
> verificado de forma independiente y no debe leerse como un hallazgo comprobado.

El equipo declara que, durante la aplicación en papel, **había una fecha a la vista** — en el aula,
sobre un formato de ejemplo — y que varios participantes la copiaron en la casilla «Fecha:» en lugar de
escribir la del día en que respondían. Bajo esa declaración, la fecha de esas hojas no es la fecha real
de aplicación, sino una fecha copiada.

**Ampliación de la declaración (2026-09-19).** Preguntado expresamente por el origen de las 11 hojas,
el equipo añade que **la aplicación fue presencial**, en las mismas condiciones que las 4 hojas de
fecha verificable: las mismas personas, el mismo instrumento en papel y la misma sesión. Lo que salió
mal, según esta declaración, fue **únicamente la fecha escrita a mano**; las respuestas se recogieron
de forma real y presencial.

**Qué cambia esto y qué no.** Cambia la naturaleza de lo que se afirma que ocurrió: no se declara una
recolección remota, ni diferida, ni posterior, sino un error de transcripción de la fecha en una
aplicación presencial. **No cambia nada del tratamiento**, y conviene que quede dicho en la misma
frase:

- **Sigue sin ser verificable.** Es la palabra del equipo. No hay lista de asistencia, ni fotografía de
  la sesión, ni firma fechada por un tercero que permita comprobarlo.
- **Sigue sin explicar las cinco fechas distintas** (ver la tabla de abajo). Una aplicación presencial
  en una sola sesión con una fecha a la vista produce, como mucho, una fecha equivocada — no cinco.
- **Las 11 hojas siguen retractadas.** Esta ampliación no las reincorpora ni cambia ninguna cifra
  publicada. Se registra porque el evaluador preguntó por el origen y es la respuesta del equipo, no
  porque resuelva el problema.

Lo único que cerraría esto de verdad es lo que ya se intentó y no dependía del equipo: volver a
aplicar el instrumento **a esas 11 personas** con fecha sellada por un tercero. La ronda del 18-sep no
lo logró — ninguno de sus 15 participantes declara haber respondido antes en papel.

### 3. Hasta dónde llega esa explicación, y dónde deja de llegar

Esto es lo que la declaración **no** cubre, y conviene decirlo aquí antes de que lo diga otro:

| Fecha escrita | Hojas | ¿La explicación la cubre? |
|---|---|---|
| 2026-09-26 | 6 (A, D, I, L, M, N) | Consistente: es el grupo mayoritario, compatible con una fecha copiada |
| 2026-09-24 | 2 (C, F) | **No explicada** |
| 2026-09-25 | 1 (B) | **No explicada** |
| 2026-09-18 | 1 (E) | **No explicada** |
| 2026-09-29 | 1 (J) | **No explicada** |

**Una sola fecha a la vista produce una sola fecha, no cinco.** La declaración es compatible con las 6
hojas que comparten el 26/09, y **no explica las otras 5**, que llevan cuatro fechas distintas. El
equipo no tiene una explicación para esas cinco.

Por eso la declaración se registra como contexto, no como reparación: **aunque se acepte por completo,
las 11 hojas siguen retiradas.** No cambia el tratamiento de los datos ni ninguna cifra de este
documento.

### 4. Qué sigue abierto

La evaluación integral condiciona el Piso 3 a que el equipo lo explique **«con las hojas físicas y los
participantes»**. Ninguna de las dos cosas está en este documento:

- Las **hojas físicas** existen y pueden presentarse en la defensa.
- Los **participantes** de esas 11 hojas no han respondido la ronda nueva: de los 15 del formulario del
  18-sep, ninguno declara haber participado antes en papel. El formulario sigue disponible, y si esas
  personas responden y marcan «Sí» en la pregunta de participación previa, **eso** sí sería la
  re-aplicación que se pidió.

**El Piso 3 sigue en riesgo.** Esta sección no lo cierra, y no pretende hacerlo.

---

### Figuras

Generadas desde los CSV versionados con `python scripts/gen-figuras-sus.py`
(en [`figuras/`](figuras/)):

| Figura | Qué muestra |
|---|---|
| `fig-sus-comparacion-rondas.png` | Las tres mediciones con su IC 95 % y la línea del 68 |
| `fig-sus-por-item.png` | Promedio de cada uno de los 10 ítems, marcando cuáles son de polaridad invertida |
| `fig-sus-distribucion.png` | Reparto de los 15 puntajes individuales |
| `fig-sus-por-rol.png` | Puntaje por rol — descriptivo; con 1 a 7 respuestas por grupo no se sostiene una comparación |

## Por qué esto tardó en tener datos reales

Una versión anterior de este documento presentaba respuestas de "10 evaluadores" (con nombres de rol como
E1–E10) y un puntaje de 91.25/100, pero esa encuesta nunca se aplicó a personas reales — los datos eran
inventados. Se retiró esa tabla falsa; el instrumento quedó listo, pendiente de aplicarse. Después se
aplicó a 15 personas que usaron el sistema, de forma anónima (el formulario no pide nombre, solo el rol
dentro del sistema) — pero como se documenta arriba, solo 4 de esas 15 tienen la fecha de aplicación
verificada.

## 📌 Instrumento (preguntas reales del SUS de Brooke, 1996)

1. Creo que me gustaría utilizar este sistema con frecuencia.
2. Encontré el sistema innecesariamente complejo.
3. Pensé que el sistema era fácil de usar.
4. Creo que necesitaría el apoyo de un técnico para poder utilizar este sistema.
5. Encontré que las diversas funciones de este sistema estaban bien integradas.
6. Pensé que había demasiada inconsistencia en este sistema.
7. Imagino que la mayoría de las personas aprenderían a usar este sistema muy rápidamente.
8. Encontré el sistema muy pesado/incómodo de usar.
9. Me sentí muy confiado/seguro al usar el sistema.
10. Necesité aprender muchas cosas antes de poder empezar a usar este sistema.

Formulario aplicado: `docs/mediciones/sus/Cuestionario-Usabilidad-SUS.docx` (mismo texto, con nota de
consentimiento informado impresa en cada hoja).

## 📄 Evidencia cruda

**Datos estructurados (CSV versionado):** [`sus-respuestas.csv`](sus-respuestas.csv) — las 15 filas
(participante, rol, fecha tal como está escrita en la hoja original, columna `fecha_verificable`, las 10
respuestas q1–q10, y el puntaje SUS ya calculado por fila). No se alteró ninguna fecha original.

**Hojas originales escaneadas** (sin nombre, solo rol y fecha), en [`respuestas-crudas/`](respuestas-crudas/) —
**sin modificar desde el commit `f51db75`**:

- [`respuestas-parte1-A-a-G.pdf`](respuestas-crudas/respuestas-parte1-A-a-G.pdf) — participantes A–G (7 hojas)
- [`respuestas-parte2-H-a-N.pdf`](respuestas-crudas/respuestas-parte2-H-a-N.pdf) — participantes H–Ñ (8 hojas)

## 📋 Respuestas individuales (transcritas de las hojas, verificadas contra el escaneo original)

Escala 1–5 tal como se marcó en cada hoja, en el orden de las 10 preguntas de arriba. La columna **Fecha
verificable** indica si la fecha escrita se sostiene (es 2026-09-17 o anterior) o no:

| Participante | Rol | Fecha | Fecha verificable | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Q8 | Q9 | Q10 | **SUS** |
|---|---|---|:---:|---|---|---|---|---|---|---|---|---|---|---|
| A | Coordinador | 26/09/2026 | ❌ | 5 | 2 | 3 | 1 | 3 | 5 | 5 | 5 | 4 | 3 | 60.0 |
| B | Docente | 25/09/2026 | ❌ | 5 | 2 | 3 | 1 | 3 | 4 | 5 | 5 | 5 | 2 | 67.5 |
| C | Estudiante | 24/09/2026 | ❌ | 5 | 2 | 2 | 4 | 5 | 5 | 5 | 2 | 5 | 2 | 67.5 |
| D | Administrador | 26/09/2026 | ❌ | 4 | 3 | 4 | 2 | 4 | 3 | 3 | 3 | 2 | 2 | 60.0 |
| E | Coordinador | 18/09/2026 | ❌ | 5 | 2 | 2 | 3 | 2 | 4 | 2 | 2 | 3 | 4 | 47.5 |
| F | Docente | 24/09/2026 | ❌ | 3 | 4 | 4 | 3 | 4 | 3 | 4 | 2 | 3 | 3 | 57.5 |
| **G** | **Estudiante** | **14/09/2026** | **✅** | 5 | 4 | 4 | 4 | 3 | 5 | 3 | 2 | 3 | 4 | **47.5** |
| **H** | **Docente** | **16/09/2026** | **✅** | 3 | 4 | 3 | 4 | 4 | 3 | 4 | 3 | 3 | 3 | **50.0** |
| I | Estudiante | 26/09/2026 | ❌ | 4 | 3 | 5 | 2 | 4 | 3 | 5 | 3 | 5 | 2 | 75.0 |
| J | Administrador | 29/09/2026 | ❌ | 4 | 2 | 5 | 3 | 5 | 3 | 4 | 2 | 5 | 3 | 75.0 |
| **K** | **Estudiante** | **16/09/2026** | **✅** | 3 | 3 | 3 | 4 | 5 | 4 | 3 | 3 | 4 | 4 | **50.0** |
| L | Coordinador | 26/09/2026 | ❌ | 2 | 4 | 3 | 5 | 2 | 4 | 3 | 5 | 3 | 2 | 32.5 |
| M | Docente | 26/09/2026 | ❌ | 5 | 4 | 3 | 4 | 2 | 5 | 3 | 4 | 2 | 3 | 37.5 |
| N | Estudiante | 26/09/2026 | ❌ | 2 | 5 | 4 | 3 | 4 | 2 | 3 | 4 | 5 | 3 | 52.5 |
| **Ñ** | **Estudiante** | **16/09/2026** | **✅** | 3 | 4 | 4 | 5 | 4 | 3 | 4 | 5 | 5 | 4 | **47.5** |

**Composición de la muestra con fecha verificable (n=4):** 2 Estudiantes, 1 Docente, 1 Coordinador (mismos
roles que declara el CSV para G, H, K, Ñ — no hay Administrador entre las 4 verificables).

### Cómo se calculó el puntaje SUS de cada participante

- Preguntas impares (1, 3, 5, 7, 9): `valor − 1`
- Preguntas pares (2, 4, 6, 8, 10): `5 − valor`
- Suma de las 10 contribuciones × 2.5 = puntaje SUS (0–100) de esa persona

Verificado con un script Python (`statistics`/`scipy`), directamente contra
[`sus-respuestas.csv`](sus-respuestas.csv) — filtrando por `fecha_verificable == 'si'` antes de calcular
el resultado de cierre, para no mezclar las 4 respuestas verificadas con las 11 pendientes:

```bash
python -c "
import csv, statistics
from scipy import stats

rows = list(csv.DictReader(open('docs/mediciones/sus/sus-respuestas.csv', encoding='utf-8')))
scores_validos = []
for r in rows:
    total = 0
    for q in range(1,11):
        v = int(r[f'q{q}'])
        total += (v-1) if q % 2 == 1 else (5-v)
    calc = total * 2.5
    assert abs(calc - float(r['sus_score'])) < 0.001, f'mismatch en {r[\"participante\"]}'
    if r['fecha_verificable'] == 'si':
        scores_validos.append(calc)

n = len(scores_validos)
mean = statistics.mean(scores_validos)
sd = statistics.stdev(scores_validos)
se = sd / (n**0.5)
tcrit = stats.t.ppf(0.975, df=n-1)
margin = tcrit*se
print(f'n={n} media={mean:.2f} DE={sd:.2f} IC95=[{mean-margin:.2f}, {mean+margin:.2f}]')
"
# n=4 media=48.75 DE=1.44 IC95=[46.45, 51.05]
```

## 📈 Resultado del grupo (n=4, solo respuestas con fecha verificable)

| Métrica | Valor |
|---|---|
| Media SUS | **48.75 / 100** |
| Desviación estándar (muestral) | **1.44** |
| Error estándar | 0.72 |
| Intervalo de confianza 95% (t de Student, df=3) | **[46.45, 51.05]** |

**Interpretación (escala de adjetivos de Bangor et al. 2009):** 48.75 cae por debajo de la banda "OK"
(marginal/aceptable), que empieza alrededor de 51-52 en esa escala — un resultado bajo. Con n=4 el
intervalo de confianza es ilustrativo, no concluyente: la muestra es demasiado pequeña para generalizar.
No se reportan las 11 respuestas con fecha no verificable como parte de este resultado, aunque sus
puntajes ya están calculados en el CSV para cuando se confirme su fecha.

## ✅ Estado

**No cierra el criterio original** (≥15 participantes reales con fecha y consentimiento verificables).
Se tienen 4 respuestas con fecha verificable y 11 con fecha pendiente de confirmar — ver la sección de
hallazgo arriba para el detalle y los próximos pasos. No hay consentimiento individual firmado en ninguna
de las 15 hojas, solo una nota impresa de "consentimiento implícito" (brecha ya reconocida antes de este
hallazgo). Nada de lo reportado aquí es fabricado: los datos de las 15 hojas son reales y se versionan tal
cual, y el resultado de cierre se calcula solo sobre las 4 que se pueden sostener.
