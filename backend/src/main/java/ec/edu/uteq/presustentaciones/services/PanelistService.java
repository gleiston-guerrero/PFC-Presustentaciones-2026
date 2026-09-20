package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Servicio de panelist.
 */
public interface PanelistService {

    // ── Panelists ──────────────────────────────────────────────────────────────

    /**
     * Asigna (upsert) un teacher como panelist de una submission con el role indicado.
     *
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param teacherId   id del teacher a assign
     * @param role         código de role de panelist ({@code PRESIDENTE}, {@code VOCAL_1} o
     *                    {@code VOCAL_2})
     * @return el registro de panelist creado o actualizado
     * @throws RuntimeException si el role no es uno de los tres válidos
     */
    Panelist assignPanelist(Long submissionId, Long teacherId, String role);

    /**
     * List by submission.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return los panelists asignados a esa submission (0 a 3 registros)
     */
    List<Panelist> listBySubmission(Long submissionId);

    /**
     * List all.
     * @param pageable configuración de paginación
     * @return página de todos los registros de panelist del sistema
     */
    Page<Panelist> listAll(Pageable pageable);

    /**
     * Elimina los registros con panelist.
     * @param panelistId id del registro de panelist a delete
     */
    void deletePanelist(Long panelistId);

    // ── Tutor ─────────────────────────────────────────────────────────────────

    /**
     * Assign tutor.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param teacherId   id del teacher que actuará como tutor
     * @return el registro de tutoría creado
     */
    Tutor assignTutor(Long submissionId, Long teacherId);

    /**
     * Obtain tutor of submission.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return el tutor asignado, si existe
     */
    Optional<Tutor> obtainTutorOfSubmission(Long submissionId);

    /**
     * Elimina los registros con tutor.
     * @param tutorId id del registro de tutoría a delete
     */
    void deleteTutor(Long tutorId);

    // ── Sugerencia automática ─────────────────────────────────────────────────

    /**
     * Sugiere teachers candidatos a panelist para una submission (excluyendo al tutor asignado y a
     * quienes ya tengan conflicto de horario), sin asignarlos todavía.
     *
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param cantidad    número máximo de teachers a sugerir
     * @return lista de teachers candidatos, tamaño ≤ {@code cantidad}
     */
    List<Teacher> suggestTeachers(Long submissionId, int cantidad);

    /**
     * Asigna automáticamente los 3 roles de tribunal (PRESIDENTE, VOCAL_1, VOCAL_2) para una
     * submission, usando la misma lógica de sugerencia que {@link #suggestTeachers}.
     *
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @throws RuntimeException si no hay suficientes teachers disponibles para completar el
     *                          tribunal
     */
    void assignPanelistsAutomatically(Long submissionId);

    // ── Asignación masiva vía procedimiento almacenado (sp_assign_panelist_masivo) ─

    /**
     * Asigna en lote pares (submissionId, teacherId) al role indicado, invocando
     * sp_assign_panelist_masivo una vez por par. Toda la operación corre dentro
     * de una única transacción: si un par falla (role inválido, FK inexistente),
     * se revierten también los pares ya procesados en esa misma llamada.
     *
     * @param submissionIds ids de las submissions, en el mismo orden que {@code teacherIds}
     * @param teacherIds   ids de los teachers a assign, uno por cada submission del arreglo
     * @param roleCode    código de role aplicado a todos los pares del lote
     * @throws RuntimeException si los dos arreglos no tienen la misma longitud, o si el
     *                          procedimiento almacenado rechaza algún par (role inválido, FK
     *                          inexistente, o conflicto de horario)
     */
    void assignPanelistBulk(List<Long> submissionIds, List<Long> teacherIds, String roleCode);

    // ── Vista del teacher ─────────────────────────────────────────────────────

    /**
     * List by teacher.
     * @param teacherId identificador del docente
     * @return las asignaciones de panelist de ese teacher, en cualquier submission
     */
    List<Panelist> listByTeacher(Long teacherId);

    /**
     * List tutorings by teacher.
     * @param teacherId identificador del docente
     * @return las tutorías activas de ese teacher
     */
    List<Tutor> listTutoringsByTeacher(Long teacherId);

    /**
     * Obtain info panelist.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param appUserId   id del appUser autenticado (se resuelve contra el teacher vinculado)
     * @return la asignación de panelist de ese appUser en esa submission, si existe
     */
    Optional<Panelist> obtainInfoPanelist(Long submissionId, Long appUserId);

    /**
     * Variante de {@link #assignPanelistBulk} que invoca directamente la sobrecarga de
     * {@code sp_assign_panelist_masivo} que recibe arreglos SQL ({@code BIGINT[]}) en una sola
     * llamada, en vez de iterar en Java. Ver la nota de fusión de ramas en
     * {@code docs/basedatos/CATALOGO-SP.md} sobre por qué la variante scaler (iterando en
     * Java) es la que queda verificada end-to-end, no esta.
     *
     * @param submissionIds arreglo de ids de submission
     * @param teacherIds   arreglo de ids de teacher, en el mismo orden
     * @param role          código de role aplicado a todo el lote
     */
    void assignPanelistBulkSP(Long[] submissionIds, Long[] teacherIds, String role);
}
