package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Panelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de panelist.
 */
@Repository
public interface PanelistRepository extends JpaRepository<Panelist, Long> {

    /**
     * Invoca el procedimiento almacenado PostgreSQL sp_assign_panelist_masivo
     * (backend/src/main/resources/db/migration/V2__stored_procedures.sql),
     * que hace el upsert en members_tribunal resolviendo role_panelist_id
     * a partir del código de role. Se llama una vez por par submission/teacher
     * dentro de una transacción Spring (@Transactional en el servicio) para
     * que el lote completo se confirme o revierta como una unidad.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param teacherId identificador del docente
     * @param roleCode código del rol con el que el docente integra el tribunal
     */
    @Procedure(procedureName = "sp_asignar_jurado_masivo")
    void spAssignPanelistBulk(@Param("p_solicitud_id") Long submissionId,
                                @Param("p_docente_id") Long teacherId,
                                @Param("p_rol_codigo") String roleCode);

    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT j FROM Panelist j JOIN FETCH j.teacher d JOIN FETCH d.appUser u JOIN FETCH j.submission s JOIN FETCH j.rolePanelist r WHERE s.id = :submissionId")
    List<Panelist> findBySubmissionId(@Param("submissionId") Long submissionId);

    /**
     * Invoca sp_validate_conflicto_panelist (FUNCTION scaler, categoría "validaciones
     * cruzadas" del Block A.2): true si el teacher NO tiene otra defensa asignada que se
     * solape con el horario dado.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param teacherId identificador del docente
     * @param dateStart fecha y hora de inicio de la programación
     * @param duracionMin duración de la programación, en minutos
     * @param availableInicial inicio de la franja de disponibilidad del docente
     * @return el valor de tipo {@code los} correspondiente
     */
    @Procedure(name = "Jurado.validarConflictoJurado")
    Boolean validateConflictoPanelist(@Param("p_solicitud_id") Long submissionId,
                                    @Param("p_docente_id") Long teacherId,
                                    @Param("p_fecha_inicio") LocalDateTime dateStart,
                                    @Param("p_duracion_min") Integer duracionMin,
                                    @Param("p_disponible") Boolean availableInicial);

    /**
     * Busca el/los registro(s) con teacher id.
     * @param teacherId identificador del docente
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT j FROM Panelist j JOIN FETCH j.teacher d JOIN FETCH d.appUser u JOIN FETCH j.submission s JOIN FETCH j.rolePanelist r WHERE d.id = :teacherId")
    List<Panelist> findByTeacherId(@Param("teacherId") Long teacherId);

    /**
     * Count asignaciones activas by teacher.
     * @param teacherId identificador del docente
     * @return la cantidad de registros
     */
    @Query("SELECT COUNT(j) FROM Panelist j WHERE j.teacher.id = :teacherId AND j.submission.status.code != 'RECHAZADA'")
    long countAsignacionesActiveByTeacher(Long teacherId);

    /**
     * Busca el/los registro(s) con submission id y app user id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param appUserId identificador del usuario del sistema
     * @return el registro si existe, vacío si no
     */
    @Query("SELECT j FROM Panelist j JOIN j.teacher d JOIN d.appUser u " +
           "WHERE j.submission.id = :submissionId AND u.id = :appUserId")
    Optional<Panelist> findBySubmissionIdAndAppUserId(@Param("submissionId") Long submissionId, 
                                                    @Param("appUserId") Long appUserId);

    /**
     * Sp assign panelist masivo.
     * @param submissionIds identificadores de las solicitudes a las que se asigna el tribunal
     * @param teacherIds identificadores de los docentes que integran el tribunal
     * @param role código del rol con el que el docente integra el tribunal
     */
    @org.springframework.data.jpa.repository.query.Procedure(procedureName = "presus.sp_asignar_jurado_masivo")
    void spAssignPanelistBulk(
            @Param("p_solicitud_ids") Long[] submissionIds,
            @Param("p_docente_ids") Long[] teacherIds,
            @Param("p_rol") String role
    );

    // ── Reportes: actividad por teacher (GROUP BY en la base) ────────────────
    /**
     * Count asignaciones por teacher.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT d.id, u.nombre, u.apellido, COUNT(j) " +
           "FROM Panelist j JOIN j.teacher d JOIN d.appUser u " +
           "GROUP BY d.id, u.nombre, u.apellido")
    List<Object[]> countAsignacionesByTeacher();

    /**
     * Minutes totalmente firmadas de pre-sustentaciones donde el teacher fue panelist.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT j.teacher.id, COUNT(DISTINCT a.id) " +
           "FROM Minutes a, Panelist j WHERE j.submission = a.submission AND a.firmada = true " +
           "GROUP BY j.teacher.id")
    List<Object[]> countMinutesFirmadasByTeacher();

    /**
     * Indica si existe algún registro con submission id y teacher app user email.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param email correo electrónico del usuario docente
     * @return true si se cumple la condición, false si no
     */
    boolean existsBySubmissionIdAndTeacherAppUserEmail(Long submissionId, String email);
}
