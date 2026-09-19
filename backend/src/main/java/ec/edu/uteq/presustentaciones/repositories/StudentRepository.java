package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de student.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    /** Listado del panel de admin -- 41,000+ filas, siempre pagina. Busca por nombre,
     * apellido, email, expediente o nombre de program.
     * @param q q
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT e FROM Student e JOIN FETCH e.appUser u JOIN FETCH e.programEntidad c " +
           "JOIN FETCH e.statusAcademic WHERE :q IS NULL OR :q = '' " +
           "OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(e.expedienteCode) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Student> searchPaged(@Param("q") String q, Pageable pageable);

    /** Última submission (el "proyecto" vigente) de cada student de la página actual,
     * en un solo query -- evita N+1 al pedir el proyecto por separado por cada fila.
     * @param ids ids
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT DISTINCT ON (s.estudiante_id) s.estudiante_id, s.titulo_tema, s.estado " +
           "FROM presus.solicitud s WHERE s.estudiante_id IN :ids " +
           "ORDER BY s.estudiante_id, s.fecha_registro DESC", nativeQuery = true)
    List<Object[]> findLastProyectoByStudentIds(@Param("ids") List<Long> ids);

    /**
     * Invoca sp_generate_codigo_expediente (FUNCTION scaler, categoría "generación de
     * códigos secuenciales" del Block A.2): usa nextval() sobre una secuencia dedicada,
     * atómico a nivel de motor -- sin condiciones de program entre altas concurrentes.
     * @param anio anio
     * @param codeInicial code inicial
     * @return el valor encontrado, o null si no existe
     */
    @Procedure(name = "Estudiante.generarCodigoExpediente")
    String generateCodeExpediente(@Param("p_anio") Integer anio, @Param("p_codigo") String codeInicial);

    /**
     * Busca el/los registro(s) con expediente codigo.
     * @param expedienteCode expedienteCode
     * @return el registro si existe, vacío si no
     */
    Optional<Student> findByExpedienteCode(String expedienteCode);
    
    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId appUserId
     * @return el registro si existe, vacío si no
     */
    Optional<Student> findByAppUserId(Long appUserId);
    
    /**
     * Busca el/los registro(s) con program.
     * @param program program
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Student> findByProgram(String program);
    
    /**
     * Busca el/los registro(s) con id with app user.
     * @param id id
     * @return el registro si existe, vacío si no
     */
    @Query("SELECT e FROM Student e JOIN FETCH e.appUser WHERE e.id = :id")
    Optional<Student> findByIdWithAppUser(Long id);
}
