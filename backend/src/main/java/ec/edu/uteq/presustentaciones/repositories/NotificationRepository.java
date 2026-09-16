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

    @Query(value = "SELECT n FROM Notification n JOIN FETCH n.appUser", countQuery = "SELECT COUNT(n) FROM Notification n")
    Page<Notification> findAll(Pageable pageable);

    @Query("SELECT n FROM Notification n JOIN FETCH n.appUser WHERE n.appUser.id = :appUserId ORDER BY n.fecha DESC")
    List<Notification> findByAppUserIdOrderByFechaDesc(@Param("appUserId") Long appUserId);

    @Query(value = "SELECT n FROM Notification n JOIN FETCH n.appUser WHERE n.appUser.id = :appUserId ORDER BY n.fecha DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.appUser.id = :appUserId")
    Page<Notification> findByAppUserIdOrderByFechaDesc(@Param("appUserId") Long appUserId, Pageable pageable);

    long countByAppUserIdAndLeidaFalse(Long appUserId);

    /**
     * UPDATE en block en vez de traer + iterar + volver a save cada fila -- marcarTodasLeidas
     * antes cargaba TODAS las notifications del appUser (leídas incluidas) solo para reescribirlas.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.leida = true WHERE n.appUser.id = :appUserId AND n.leida = false")
    int marcarTodasLeidasPorAppUser(@Param("appUserId") Long appUserId);
}