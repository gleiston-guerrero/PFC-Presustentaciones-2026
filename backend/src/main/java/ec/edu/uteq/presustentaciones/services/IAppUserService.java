package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.AppUser;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Servicio de app user.
 */
public interface IAppUserService {

    /**
     * List paged.
     * @param page número de página, base 0
     * @param size tamaño de página (se acota a un máximo de 100)
     * @param q    texto libre de búsqueda por nombre/apellido/email, o {@code null}
     * @return página de appUsers que cumplen el filtro
     */
    Page<AppUser> listPaged(int page, int size, String q);

    /**
     * Crea un appUser, encriptando su contraseña con BCrypt y resolviendo su
     * {@code RoleAppUser} (FK) a partir del código de role textual.
     *
     * @param appUser datos del appUser a create (contraseña en texto plano)
     * @return el appUser creado, con la contraseña ya encriptada
     * @throws RuntimeException si ya existe un appUser con ese email, o el role no es válido
     */
    AppUser create(AppUser appUser);

    /**
     * Actualiza los datos de un appUser existente. Si el role cambia, sincroniza también la FK
     * {@code RoleAppUser} para que ambas columnas paralelas queden consistentes.
     *
     * @param id      id del appUser a update
     * @param appUser datos nuevos (nombre, apellido, email, role, teléfono)
     * @return el appUser actualizado
     * @throws RuntimeException si el appUser no existe o el nuevo role no es válido
     */
    AppUser update(Long id, AppUser appUser);

    /**
     * Elimina los registros.
     * @param id id del appUser a delete
     * @throws RuntimeException si el appUser no existe */
    void delete(Long id);

    /**
     * Obtain by id.
     * @param id id del appUser
     * @return el appUser si existe
     */
    Optional<AppUser> obtainById(Long id);

    /**
     * Obtain by email.
     * @param email email del appUser
     * @return el appUser con ese email, si existe
     */
    Optional<AppUser> obtainByEmail(String email);

    /**
     * List all.
     * @return todos los appUsers del sistema, sin paginar
     */
    List<AppUser> listAll();

    /**
     * List active.
     * @return los appUsers con {@code activo = true}
     */
    List<AppUser> listActive();

    /**
     * Indica si existe algún registro con email.
     * @param email email a verify
     * @return {@code true} si ya existe un appUser registrado con ese email
     */
    boolean existsByEmail(String email);

    /**
     * Activate.
     * @param id id del appUser a activate
     * @throws RuntimeException si el appUser no existe */
    void activate(Long id);

    /**
     * Deactivate.
     * @param id id del appUser a deactivate
     * @throws RuntimeException si el appUser no existe */
    void deactivate(Long id);

    /**
     * Actualiza solo los campos de perfil autoeditables por el propio appUser (no requiere
     * role ADMIN), sin tocar role ni email institucional.
     *
     * @param id                  id del appUser
     * @param emailNotifications correo alterno para recibir notifications, o {@code null}
     * @param phone            teléfono de contacto, o {@code null}
     * @return el appUser actualizado
     * @throws RuntimeException si el appUser no existe
     */
    AppUser updateProfile(Long id, String emailNotifications, String phone);
}
