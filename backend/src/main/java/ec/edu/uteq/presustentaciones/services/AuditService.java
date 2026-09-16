package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Identifica al "quién" para los triggers de auditoría (V15__audit.sql). Se llama
 * al inicio de cada método @Transactional que hace una escritura auditable, ANTES del
 * save()/delete() que dispara el trigger -- set_config(..., true) fija el GUC solo para
 * la transacción/conexión actual, así que tiene que correr en la misma transacción que
 * la escritura para que el trigger lo vea (por eso no se puede hacer una sola vez por
 * request en un filtro: el filtro corre antes de que Spring abra la transacción).
 *
 * IMPORTANTE -- debe ser literalmente la PRIMERA operación del método, antes de mutar
 * cualquier entidad ya gestionada (ej. antes de submission.setEstado(...)): esta llamada
 * ejecuta una query nativa, y Hibernate hace auto-flush de los cambios pendientes ANTES
 * de correr cualquier query nativa (no puede saber si esa query depende de ellos). Si el
 * set_config corre después de modificar la entidad, el flush -- y el trigger -- ya
 * dispararon con el GUC todavía sin fijar, y la fila de auditoría queda sin autor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AppUserRepository appUserRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** Fija en la conexión actual el appUser autenticado que va a quedar como autor en la
     * fila de auditoría que dispare el siguiente trigger (o {@code NULL} si no hay appUser
     * autenticado). Sin parámetros ni valor de retorno: opera sobre el contexto de seguridad
     * y la conexión JDBC actuales. */
    public void marcarActorActual() {
        Long appUserId = resolveAppUserIdActual();
        String valor = appUserId != null ? appUserId.toString() : "";
        try {
            entityManager.createNativeQuery("SELECT set_config('presus.usuario_actual', :valor, true)")
                    .setParameter("valor", valor)
                    .getSingleResult();
        } catch (Exception e) {
            log.warn("No se pudo fijar el actor de auditoría: {}", e.getMessage());
        }
    }

    private Long resolveAppUserIdActual() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                return null;
            }
            return appUserRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
