package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AppUserServiceImpl implements IAppUserService {

    private final AppUserRepository appUserRepository;
    private final RoleAppUserRepository roleAppUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** role (string) y roleAppUser (FK a roles_appUser) son dos columnas paralelas para el mismo dato — hay que mantenerlas sincronizadas. */
    private RoleAppUser resolveRole(String codigoRole) {
        if (codigoRole == null || codigoRole.trim().isEmpty()) {
            throw new IllegalArgumentException("El rol del usuario es requerido");
        }
        return roleAppUserRepository.findByCodigo(codigoRole)
                .orElseThrow(() -> new IllegalArgumentException("Rol inválido o no existe: " + codigoRole));
    }

    /**
     * @param page número de página, base 0
     * @param size tamaño de página (se acota a un máximo de 100)
     * @param q    texto libre de búsqueda por nombre/apellido/email, o {@code null}
     * @return página de appUsers que cumplen el filtro
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AppUser> listPaginado(int page, int size, String q) {
        int paginaSegura = Math.max(page, 0);
        int tamanioSeguro = Math.min(Math.max(size, 1), 100);
        return appUserRepository.searchPaginado(q, PageRequest.of(paginaSegura, tamanioSeguro));
    }

    /**
     * Crea un appUser, encriptando su contraseña con BCrypt y resolviendo su
     * {@code RoleAppUser} (FK) a partir del código de role textual.
     *
     * @param appUser datos del appUser a create (contraseña en texto plano)
     * @return el appUser creado, con la contraseña ya encriptada
     * @throws RuntimeException si ya existe un appUser con ese email, o el role no es válido
     */
    @Override
    public AppUser create(AppUser appUser) {
        log.info("Creando usuario con email: {}", appUser.getEmail());

        if (existePorEmail(appUser.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email: " + appUser.getEmail());
        }

        // Hallazgo real de auditoría (2026-09-04): save el "usuario" recibido tal cual, con
        // save(appUser), es seguro solo si el caller garantiza id=null. Si algún caller (actual
        // o futuro) reenvía un id -- por bug de frontend o un body manipulado -- Spring Data JPA
        // ve isNew()=false (id no nulo) y hace entityManager.merge() en vez de persist(): en vez
        // de create un appUser nuevo, SOBRESCRIBE silenciosamente la fila existente con ese id
        // (incluida potencialmente la del propio ADMIN). "crear" nunca debe poder update un
        // registro existente, así que se fuerza id=null aquí mismo, sin depender del caller.
        appUser.setId(null);
        auditService.marcarActorActual();
        appUser.setPassword(passwordEncoder.encode(appUser.getPassword()));
        appUser.setRoleAppUser(resolveRole(appUser.getRole()));
        return appUserRepository.save(appUser);
    }

    /**
     * Actualiza los datos de un appUser existente. Si el role cambia, sincroniza también la FK
     * {@code RoleAppUser} para que ambas columnas paralelas queden consistentes.
     *
     * @param id      id del appUser a update
     * @param appUser datos nuevos (nombre, apellido, email, role, teléfono)
     * @return el appUser actualizado
     * @throws RuntimeException si el appUser no existe o el nuevo role no es válido
     */
    @Override
    public AppUser update(Long id, AppUser appUser) {
        log.info("Actualizando usuario con ID: {}", id);

        auditService.marcarActorActual();
        AppUser existente = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        existente.setNombre(appUser.getNombre());
        existente.setApellido(appUser.getApellido());
        existente.setEmail(appUser.getEmail());
        if (appUser.getRole() != null && !appUser.getRole().equals(existente.getRole())) {
            existente.setRole(appUser.getRole());
            existente.setRoleAppUser(resolveRole(appUser.getRole()));
        }
        if (appUser.getTelefono() != null) {
            existente.setTelefono(appUser.getTelefono());
        }

        return appUserRepository.save(existente);
    }

    /**
     * @param id id del appUser a delete
     * @throws RuntimeException si el appUser no existe
     */
    @Override
    public void delete(Long id) {
        log.info("Eliminando usuario con ID: {}", id);

        if (!appUserRepository.existsById(id)) {
            throw new IllegalArgumentException("Usuario no encontrado con ID: " + id);
        }

        auditService.marcarActorActual();
        appUserRepository.deleteById(id);
    }

    /**
     * @param id id del appUser
     * @return el appUser si existe
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AppUser> obtainPorId(Long id) {
        return appUserRepository.findById(id);
    }

    /**
     * @param email email del appUser
     * @return el appUser con ese email, si existe
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AppUser> obtainPorEmail(String email) {
        return appUserRepository.findByEmail(email);
    }

    /** @return todos los appUsers del sistema, sin paginar */
    @Override
    @Transactional(readOnly = true)
    public List<AppUser> listTodos() {
        return appUserRepository.findAll();
    }

    /** @return los appUsers con {@code activo = true} */
    @Override
    @Transactional(readOnly = true)
    public List<AppUser> listActivos() {
        return appUserRepository.findByActivoTrue();
    }

    /**
     * @param email email a verify
     * @return {@code true} si ya existe un appUser registrado con ese email
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existePorEmail(String email) {
        return appUserRepository.existsByEmail(email);
    }

    /**
     * @param id id del appUser a activate
     * @throws RuntimeException si el appUser no existe
     */
    @Override
    public void activate(Long id) {
        auditService.marcarActorActual();
        AppUser appUser = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        appUser.setActivo(true);
        appUserRepository.save(appUser);
    }

    /**
     * @param id id del appUser a deactivate
     * @throws RuntimeException si el appUser no existe
     */
    @Override
    public void deactivate(Long id) {
        auditService.marcarActorActual();
        AppUser appUser = appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        appUser.setActivo(false);
        appUserRepository.save(appUser);
    }

    /**
     * Actualiza solo los campos de perfil autoeditables por el propio appUser (no requiere
     * role ADMIN), sin tocar role ni email institucional.
     *
     * @param id                  id del appUser
     * @param emailNotifications correo alterno para recibir notifications, o {@code null}
     * @param telefono            teléfono de contacto, o {@code null}
     * @return el appUser actualizado
     * @throws RuntimeException si el appUser no existe
     */
    @Override
    @Transactional
    public AppUser updatePerfil(Long id, String emailNotifications, String telefono) {
        int updated = appUserRepository.updatePerfil(id, emailNotifications, telefono);
        if (updated == 0) {
            throw new IllegalArgumentException("Usuario no encontrado con ID: " + id);
        }
        return appUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
    }
}
