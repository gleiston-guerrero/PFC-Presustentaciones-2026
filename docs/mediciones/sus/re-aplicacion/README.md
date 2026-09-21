# Re-aplicación del SUS con fecha verificable por un tercero

## Por qué existe esta carpeta

La evaluación integral del 2026-09-17 no discute el cálculo del SUS — lo recalculó y coincide. Lo que
no puede aceptar es **la fecha**: 11 de las 15 hojas escaneadas llevan una fecha escrita a mano
posterior al commit que las versiona (`f51db75`, 2026-09-16 22:48), y algunas posteriores al día en que
se escribió esa evaluación. Su petición es concreta:

> *"re-aplicar el SUS a las 11 personas con fecha dudosa, o conseguir una forma independiente de
> verificar su fecha real"*

Esta carpeta implementa la primera opción.

> ### Nota sobre las horas de los commits de esta carpeta (2026-09-19)
>
> La revisión individual del 18-sep observó que **a las 16:32 este protocolo todavía daba el formulario
> por crear**, y que `crear-formulario.gs` no apareció hasta las 16:37 — cuando ya había 12 respuestas
> selladas. De ahí infirió que el formulario no podía existir antes.
>
> Los hechos sobre los commits son correctos. **Lo que no se sigue es la conclusión, porque 16:37 es la
> hora en que el archivo se subió al repositorio, no la hora en que se escribió.** El historial del
> proyecto de Apps Script, fechado por Google, lo sitúa en **11:16**, y el formulario se creó a las
> **11:22**: seis minutos después del script y catorce antes de la primera respuesta.
>
> **El descuido es real y es del equipo:** el script se escribió y se ejecutó por la mañana, pero no se
> comiteó en ese momento — quedó en Google toda la jornada y se subió a las 16:37 junto con el resto de
> los cambios del día. Visto solo desde el repositorio, parece que nació a esa hora. Por eso esta nota
> existe: el protocolo de abajo describe lo que efectivamente se hizo el 18-sep por la mañana, no un
> plan escrito después.
>
> Cronología y capturas en [`../SUS-RESULTS.md`](../SUS-RESULTS.md) y en [`evidencia/`](evidencia/).

## La regla que manda sobre todo lo demás

**La marca de tiempo no la puede poner el equipo.** Ni el navegador del participante, ni el backend del
propio proyecto, ni un campo "fecha" que alguien escriba. Si la hora la pone algo que el equipo
controla, la evidencia tiene exactamente el mismo problema que las hojas de papel: no hay forma de que
un tercero la verifique.

Por eso:

- El formulario **no registra ninguna hora del lado del cliente**. Es deliberado, está comentado en el
  código, y no debe "arreglarse".
- Las respuestas se envían a un formulario alojado por un tercero (Microsoft Forms o Google Forms), que
  sella cada respuesta con **su propia hora de servidor** y la incluye en el CSV de exportación.
- Ese CSV exportado se versiona **tal cual sale**, sin editar, junto al derivado.

Si la UTEQ tiene Office 365, **Microsoft Forms con la cuenta institucional es la mejor opción**: el
sello de tiempo es de un servidor de Microsoft y la cuenta es de la universidad, no del equipo. Google
Forms sirve igual de bien.

## Paso 1 — crear el formulario destino

### Forma rápida: que lo cree el script (2 minutos, recomendado)

[`crear-formulario.gs`](crear-formulario.gs) construye el formulario entero, con las 13 preguntas ya
configuradas. Evita transcribir los enunciados a mano, que es donde se cuela el error que rompe la
comparabilidad entre rondas.

1. Entra a <https://script.google.com> con la cuenta que será dueña del formulario (preferiblemente la
   institucional de la UTEQ).
2. **Nuevo proyecto**. Borra lo que haya y pega todo el contenido de `crear-formulario.gs`.
3. Arriba, elige la función `crearFormularioSUS` y pulsa **Ejecutar**. Google pedirá autorización la
   primera vez: es tu propio script creando un formulario en tu propia cuenta.
4. Abre **Registro de ejecución**. Ahí salen dos enlaces: el de **responder** (el que se reparte) y el
   de **editar** (para ti).

No hay que tocar nada más. El script ya desactiva la recolección de correo y marca las 13 preguntas
como obligatorias.

### Forma manual (si prefieres hacerlo a mano)

Crea un formulario nuevo con **exactamente** estos campos, en este orden. La redacción de los 10 ítems
tiene que ser **idéntica** a la de la hoja en papel (`../respuestas-crudas/`): si cambia una palabra,
las dos rondas dejan de ser comparables y el ejercicio pierde sentido.

**Título:** `Cuestionario de Usabilidad (SUS)`
**Descripción:** `Sistema de Gestión de Pre-Sustentaciones — Universidad Técnica Estatal de Quevedo`

**Texto de consentimiento** (ponlo en la descripción, y añade una pregunta de opción obligatoria
«He leído lo anterior y acepto participar» → `Sí`):

> Tu participación es voluntaria y anónima: este formulario no pide tu nombre, tu correo ni ningún dato
> que permita identificarte. Las respuestas se usan únicamente con fines académicos, para evaluar la
> usabilidad del sistema en el marco de este proyecto de titulación. Puedes abandonar el cuestionario en
> cualquier momento sin dar explicaciones.

> ⚠️ **Desactiva la recolección de correo** (Microsoft Forms: "Aceptar respuestas de cualquier persona";
> Google Forms: desmarcar "Recopilar direcciones de correo"). El instrumento en papel era anónimo; si
> esta ronda deja de serlo, no es la misma medición y además contradice el consentimiento.

| # | Pregunta | Tipo | Opciones |
|---|---|---|---|
| 1 | Tu rol dentro del sistema | Opción única, obligatoria | Estudiante · Docente · Coordinador · Administrador |
| 2 | ¿Habías respondido antes este cuestionario en papel? | Opción única, obligatoria | Sí · No · No recuerdo |

Y los 10 ítems, todos **obligatorios**, escala lineal **1 a 5**, con etiquetas
`1 = Totalmente en desacuerdo` y `5 = Totalmente de acuerdo`:

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

La pregunta 2 no identifica a nadie: pregunta por la participación, no por la persona. Sirve para poder
decir con honestidad cuántas de las respuestas nuevas vienen de gente que ya había participado.

## Paso 2 — repartir el enlace

Con el enlace del formulario del paso 1 ya puedes aplicar la encuesta. **Eso basta**: el paso 3 es
opcional y solo cambia la apariencia.

Reparte el enlace por un medio que deje rastro propio (correo institucional, grupo de la asignatura).
Ese rastro es evidencia adicional de *cuándo* se convocó, independiente del equipo.

## Paso 3 (opcional) — usar el formulario con la identidad visual del proyecto

`formulario-sus.html` es el mismo instrumento con el formato del proyecto. Envía al formulario del paso
1, que sigue siendo quien pone la hora. Para conectarlo:

1. Abre el formulario destino y copia el `action` de su `<form>` (termina en `/formResponse`) y el
   `name` de cada campo (`entry.XXXXXXXX`). En Google Forms se leen con «Obtener enlace
   prerrellenado» o inspeccionando el HTML.
2. Pégalos en el bloque `CONFIGURACIÓN` al inicio de `formulario-sus.html`.
3. Publícalo donde sea accesible (GitHub Pages sobre este mismo repositorio es gratis y suficiente).

Mientras `ACTION_URL` esté vacío, el archivo funciona en **modo vista previa**: valida y muestra el
resumen, pero no envía nada y lo dice en pantalla. Así se puede revisar la redacción sin ensuciar los
datos.

## Paso 4 — ingerir las respuestas

Exporta el CSV desde el formulario **sin tocarlo** y guárdalo aquí como
`respuestas-formulario-<fecha>.csv`. Después:

```bash
python scripts/sus-ingesta.py docs/mediciones/sus/re-aplicacion/respuestas-formulario-<fecha>.csv
```

El script valida que cada ítem esté entre 1 y 5, recalcula el puntaje con la fórmula de Brooke
(`(impares − 1) + (5 − pares)`, por 2,5), compara la hora de cada respuesta contra el commit que la
versiona, y emite el CSV derivado más la media, la desviación y el IC del 95 %. No inventa ninguna
fecha: la que reporta es la que trae el archivo del tercero.

## Cómo comprobar la procedencia sin creerle a nadie (2026-09-21)

La revisión del 21-sep dejó P1 en el 70 % porque *«la procedencia se apoya en capturas y no en una
exportación del servidor»*. La exportación estaba versionada desde el 18-sep, pero nada ataba las cifras
publicadas a ella. Ahora hay dos exportaciones del mismo formulario, por caminos distintos, y `make verify`
comprueba que la tabla publicada sale de ellas.

| Archivo | Cómo se obtuvo | sha256 |
|---|---|---|
| `respuestas-formulario-2026-09-18.csv` | Hoja de respuestas → Archivo → Descargar → CSV | `bf1cf916f52a6ec3c7b3bfc02b4767529534a1f4216cfed563e594cbdcef31ec` (saltos normalizados a LF) |
| `respuestas-descarga-formulario-2026-09-21.csv` | Formulario → Respuestas → ⋮ → Descargar respuestas (.csv) | se contrasta por **datos**, no por bytes (ver abajo) |

**Quien tenga acceso al formulario puede comprobarlo por su cuenta**, que es el punto: el evaluador ya
tiene acceso de editor (`evidencia/acceso-editor-concedido-gguerrero.png`). Basta con exportar la hoja de
respuestas y comparar la huella con la de la primera fila:

```bash
python -c "import hashlib,sys;print(hashlib.sha256(open(sys.argv[1],'rb').read().replace(b'\r\n',b'\n')).hexdigest())" <su-descarga>.csv
```

Se normalizan los saltos de línea antes de la huella porque Windows y Git los reescriben al sacar el
archivo, y eso cambiaría el hash sin cambiar un solo dato. Google entrega el CSV con saltos CRLF: si se
le calcula la huella tal cual se descarga, sin normalizar, da
`4d1aa7c88842e1e605fc4f9cba2d0b6bda1f16ffe5a35cb9107a91dba956fbb1`, que es también la del archivo
versionado en un árbol de trabajo de Windows.

La segunda exportación se contrasta **por datos y no por bytes** a propósito: el CSV que genera el
formulario no sale idéntico byte a byte entre descargas —cambia detalles de formato sin cambiar ninguna
respuesta—, así que exigir bytes iguales haría fallar al verificador por cómo Google dibuja el archivo y
no por lo que dice. Lo que se compara es fecha, hora, consentimiento, rol y los diez ítems de las quince
respuestas, que coinciden en las dos.

**Lo que esto prueba y lo que no.** Prueba que las cifras publicadas (n=15, media 52,83) salen de la
exportación del tercero sin pasar por ninguna edición nuestra, y que dos caminos de exportación
independientes traen el mismo dato. No prueba quién respondió: eso no lo puede cerrar el repositorio.

## Qué se versiona, y qué no

| Archivo | Se versiona | Se edita a mano |
|---|---|---|
| CSV exportado del formulario | **sí**, tal cual sale | **nunca** |
| CSV derivado que genera el script | sí | nunca (se regenera) |
| `formulario-sus.html` | sí | solo la configuración |

Las 15 hojas de papel originales **se quedan donde están**, sin cambios, en `../respuestas-crudas/`.
Esta ronda no las reemplaza ni las corrige: es una medición nueva, con su propia fecha verificable, que
se reporta junto a ellas. Sustituir la evidencia original por una versión posterior es justo lo que este
punto ya rechazó una vez (ver `../SUS-RESULTS.md`).
