package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find all.
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT n FROM Notification n JOIN FETCH n.appUser", countQuery = "SELECT COUNT(n) FROM Notification n")
    Page<Notification> findAll(Pageable pageable);

    /**
     * Busca el/los registro(s) con app user id o der by fecha desc.
     * @param appUserId appUserId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT n FROM Notification n JOIN FETCH n.appUser WHERE n.appUser.id = :appUserId ORDER BY n.date DESC")
    List<Notification> findByAppUserIdOrderByDateDesc(@Param("appUserId") Long appUserId);

    /**
     * Busca el/los registro(s) con app user id o der by fecha desc.
     * @param appUserId appUserId
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT n FROM Notification n JOIN FETCH n.appUser WHERE n.appUser.id = :appUserId ORDER BY n.date DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.appUser.id = :appUserId")
    Page<Notification> findByAppUserIdOrderByDateDesc(@Param("appUserId") Long appUserId, Pageable pageable);

    /**
     * Cuenta los registros con app user id y leida false.
     * @param appUserId appUserId
     * @return la cantidad de registros
     */
    long countByAppUserIdAndReadFalse(Long appUserId);

    /**
     * UPDATE en block en vez de traer + iterar + volver a save cada fila -- marcarTodasLeidas
     * antes cargaba TODAS las notifications del appUser (leídas incluidas) solo para reescribirlas.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.appUser.id = :appUserId AND n.read = false")
    int markAllReadByAppUser(@Param("appUserId") Long appUserId);
}