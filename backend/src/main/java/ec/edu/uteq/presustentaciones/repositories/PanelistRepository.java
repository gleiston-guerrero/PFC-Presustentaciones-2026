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

@Repository
public interface PanelistRepository extends JpaRepository<Panelist, Long> {

    /**
     * Invoca el procedimiento almacenado PostgreSQL sp_assign_panelist_masivo
     * (backend/src/main/resources/db/migration/V2__stored_procedures.sql),
     * que hace el upsert en members_tribunal resolviendo role_panelist_id
     * a partir del código de role. Se llama una vez por par submission/teacher
     * dentro de una transacción Spring (@Transactional en el servicio) para
     * que el lote completo se confirme o revierta como una unidad.
     */
    @Procedure(procedureName = "sp_asignar_jurado_masivo")
    void spAssignPanelistMasivo(@Param("p_submission_id") Long submissionId,
                                @Param("p_teacher_id") Long teacherId,
                                @Param("p_rol_codigo") String roleCodigo);

    @Query("SELECT j FROM Panelist j JOIN FETCH j.teacher d JOIN FETCH d.appUser u JOIN FETCH j.submission s JOIN FETCH j.rolePanelist r WHERE s.id = :submissionId")
    List<Panelist> findBySubmissionId(@Param("submissionId") Long submissionId);

    /**
     * Invoca sp_validate_conflicto_panelist (FUNCTION scaler, categoría "validaciones
     * cruzadas" del Block A.2): true si el teacher NO tiene otra defensa asignada que se
     * solape con el horario dado.
     */
    @Procedure(name = "Jurado.validarConflictoJurado")
    Boolean validateConflictoPanelist(@Param("p_submission_id") Long submissionId,
                                    @Param("p_teacher_id") Long teacherId,
                                    @Param("p_fecha_inicio") LocalDateTime fechaInicio,
                                    @Param("p_duracion_min") Integer duracionMin,
                                    @Param("p_disponible") Boolean disponibleInicial);

    @Query("SELECT j FROM Panelist j JOIN FETCH j.teacher d JOIN FETCH d.appUser u JOIN FETCH j.submission s JOIN FETCH j.rolePanelist r WHERE d.id = :teacherId")
    List<Panelist> findByTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT COUNT(j) FROM Panelist j WHERE j.teacher.id = :teacherId AND j.submission.estado.codigo != 'RECHAZADA'")
    long countAsignacionesActivasByTeacher(Long teacherId);

    @Query("SELECT j FROM Panelist j JOIN j.teacher d JOIN d.appUser u " +
           "WHERE j.submission.id = :submissionId AND u.id = :appUserId")
    Optional<Panelist> findBySubmissionIdAndAppUserId(@Param("submissionId") Long submissionId, 
                                                    @Param("appUserId") Long appUserId);

    @org.springframework.data.jpa.repository.query.Procedure(procedureName = "presus.sp_asignar_jurado_masivo")
    void spAssignPanelistMasivo(
            @Param("p_submission_ids") Long[] submissionIds,
            @Param("p_teacher_ids") Long[] teacherIds,
            @Param("p_rol") String role
    );

    // ── Reportes: actividad por teacher (GROUP BY en la base) ────────────────
    @Query("SELECT d.id, u.nombre, u.apellido, COUNT(j) " +
           "FROM Panelist j JOIN j.teacher d JOIN d.appUser u " +
           "GROUP BY d.id, u.nombre, u.apellido")
    List<Object[]> countAsignacionesPorTeacher();

    /** Minutes totalmente firmadas de pre-sustentaciones donde el teacher fue panelist. */
    @Query("SELECT j.teacher.id, COUNT(DISTINCT a.id) " +
           "FROM Minutes a, Panelist j WHERE j.submission = a.submission AND a.firmada = true " +
           "GROUP BY j.teacher.id")
    List<Object[]> countMinutesFirmadasPorTeacher();

    boolean existsBySubmissionIdAndTeacherAppUserEmail(Long submissionId, String email);
}
