/**
 * Crea el formulario SUS completo en Google Forms, con las 13 preguntas ya
 * configuradas. Evita escribirlas a mano: un enunciado mal copiado haría que
 * esta ronda deje de ser comparable con la de papel.
 *
 * CÓMO USARLO (2 minutos)
 *   1. Entra a  https://script.google.com  con la cuenta que va a ser dueña
 *      del formulario (preferiblemente la institucional de la UTEQ).
 *   2. "Nuevo proyecto".
 *   3. Borra lo que haya y pega TODO este archivo.
 *   4. Arriba, elige la función  crearFormularioSUS  y pulsa "Ejecutar".
 *   5. Google pedirá autorización la primera vez: acéptala (es tu propio
 *      script creando un formulario en tu propia cuenta).
 *   6. Cuando termine, abre "Registro de ejecución": ahí salen dos enlaces.
 *      El de RESPONDER es el que se reparte; el de EDITAR es para ti.
 *
 * No hay que configurar nada más. La hora de cada respuesta la pone Google
 * automáticamente, y es justamente lo que se necesita: una marca de tiempo
 * que no depende del equipo.
 */

// Los 10 ítems de Brooke, transcritos palabra por palabra de la hoja en papel
// que ya se aplicó (docs/mediciones/sus/respuestas-crudas/). NO cambiar el
// texto: si varía el enunciado, las dos rondas dejan de ser comparables.
var ITEMS = [
  'Creo que me gustaría utilizar este sistema con frecuencia.',
  'Encontré el sistema innecesariamente complejo.',
  'Pensé que el sistema era fácil de usar.',
  'Creo que necesitaría el apoyo de un técnico para poder utilizar este sistema.',
  'Encontré que las diversas funciones de este sistema estaban bien integradas.',
  'Pensé que había demasiada inconsistencia en este sistema.',
  'Imagino que la mayoría de las personas aprenderían a usar este sistema muy rápidamente.',
  'Encontré el sistema muy pesado/incómodo de usar.',
  'Me sentí muy confiado/seguro al usar el sistema.',
  'Necesité aprender muchas cosas antes de poder empezar a usar este sistema.'
];

var CONSENTIMIENTO =
  'Tu participación es voluntaria y anónima: este formulario no pide tu nombre, ' +
  'tu correo ni ningún dato que permita identificarte. Las respuestas se usan ' +
  'únicamente con fines académicos, para evaluar la usabilidad del sistema en el ' +
  'marco de este proyecto de titulación. Puedes abandonar el cuestionario en ' +
  'cualquier momento sin dar explicaciones. Toma entre 3 y 5 minutos.';

function crearFormularioSUS() {
  var form = FormApp.create('Cuestionario de Usabilidad (SUS)');

  form.setDescription(
    'Sistema de Gestión de Pre-Sustentaciones — Universidad Técnica Estatal de Quevedo\n\n' +
    'CONSENTIMIENTO INFORMADO\n' + CONSENTIMIENTO);

  // El instrumento en papel era anónimo. Si esta ronda recogiera el correo no
  // sería la misma medición, y además contradiría el consentimiento de arriba.
  try { form.setCollectEmail(false); } catch (e) {}
  try { form.setLimitOneResponsePerUser(false); } catch (e) {}
  form.setProgressBar(true);
  form.setAllowResponseEdits(false);

  // 1. Consentimiento individual. Corrige el defecto señalado en la evaluación
  //    del 17-sep: "no hay consentimiento individual, solo una nota impresa".
  form.addMultipleChoiceItem()
      .setTitle('He leído el consentimiento informado y acepto participar en estas condiciones.')
      .setChoiceValues(['Sí, acepto participar'])
      .setRequired(true);

  // 2. Rol
  form.addMultipleChoiceItem()
      .setTitle('Tu rol dentro del sistema')
      .setChoiceValues(['Estudiante', 'Docente', 'Coordinador', 'Administrador'])
      .setRequired(true);

  // 3. Participación previa. No identifica a nadie: pregunta por la
  //    participación, no por la persona. Permite decir con honestidad cuántas
  //    respuestas nuevas vienen de gente que ya había participado.
  form.addMultipleChoiceItem()
      .setTitle('¿Habías respondido antes este cuestionario en papel?')
      .setChoiceValues(['Sí', 'No', 'No recuerdo'])
      .setRequired(true);

  // 4-13. Los 10 ítems, escala 1-5, todos obligatorios.
  for (var i = 0; i < ITEMS.length; i++) {
    form.addScaleItem()
        .setTitle(ITEMS[i])
        .setBounds(1, 5)
        .setLabels('Totalmente en desacuerdo', 'Totalmente de acuerdo')
        .setRequired(true);
  }

  var responder = form.getPublishedUrl();
  var editar = form.getEditUrl();

  Logger.log('');
  Logger.log('=======================================================');
  Logger.log(' FORMULARIO CREADO');
  Logger.log('=======================================================');
  Logger.log('');
  Logger.log('ENLACE PARA REPARTIR (el que reciben los participantes):');
  Logger.log(responder);
  Logger.log('');
  Logger.log('ENLACE PARA EDITAR / VER RESPUESTAS (solo para ti):');
  Logger.log(editar);
  Logger.log('');
  Logger.log('Preguntas creadas: ' + (3 + ITEMS.length) + ' (1 consentimiento,');
  Logger.log('1 rol, 1 participacion previa, ' + ITEMS.length + ' items de Brooke).');
  Logger.log('');
  Logger.log('Cuando tengas respuestas: en el formulario, pestana "Respuestas"');
  Logger.log('-> menu de tres puntos -> "Descargar respuestas (.csv)".');
  Logger.log('Ese archivo se entrega tal cual, sin editar, a:');
  Logger.log('   python scripts/sus-ingesta.py <archivo.csv>');
  Logger.log('=======================================================');

  return responder;
}
