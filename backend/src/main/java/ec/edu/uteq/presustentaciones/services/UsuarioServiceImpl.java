package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.RolUsuario;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.RolUsuarioRepository;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
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
public class UsuarioServiceImpl implements IUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolUsuarioRepository rolUsuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    /** rol (string) y rolUsuario (FK a roles_usuario) son dos columnas paralelas para el mismo dato — hay que mantenerlas sincronizadas. */
    private RolUsuario resolverRol(String codigoRol) {
        if (codigoRol == null || codigoRol.trim().isEmpty()) {
            throw new IllegalArgumentException("El rol del usuario es requerido");
        }
        return rolUsuarioRepository.findByCodigo(codigoRol)
                .orElseThrow(() -> new IllegalArgumentException("Rol inválido o no existe: " + codigoRol));
    }

    /**
     * @param page número de página, base 0
     * @param size tamaño de página (se acota a un máximo de 100)
     * @param q    texto libre de búsqueda por nombre/apellido/email, o {@code null}
     * @return página de usuarios que cumplen el filtro
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Usuario> listarPaginado(int page, int size, String q) {
        int paginaSegura = Math.max(page, 0);
        int tamanioSeguro = Math.min(Math.max(size, 1), 100);
        return usuarioRepository.buscarPaginado(q, PageRequest.of(paginaSegura, tamanioSeguro));
    }

    /**
     * Crea un usuario, encriptando su contraseña con BCrypt y resolviendo su
     * {@code RolUsuario} (FK) a partir del código de rol textual.
     *
     * @param usuario datos del usuario a crear (contraseña en texto plano)
     * @return el usuario creado, con la contraseña ya encriptada
     * @throws RuntimeException si ya existe un usuario con ese email, o el rol no es válido
     */
    @Override
    public Usuario crear(Usuario usuario) {
        log.info("Creando usuario con email: {}", usuario.getEmail());

        if (existePorEmail(usuario.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email: " + usuario.getEmail());
        }

        // Hallazgo real de auditoría (2026-09-04): guardar el "usuario" recibido tal cual, con
        // save(usuario), es seguro solo si el caller garantiza id=null. Si algún caller (actual
        // o futuro) reenvía un id -- por bug de frontend o un body manipulado -- Spring Data JPA
        // ve isNew()=false (id no nulo) y hace entityManager.merge() en vez de persist(): en vez
        // de crear un usuario nuevo, SOBRESCRIBE silenciosamente la fila existente con ese id
        // (incluida potencialmente la del propio ADMIN). "crear" nunca debe poder actualizar un
        // registro existente, así que se fuerza id=null aquí mismo, sin depender del caller.
        usuario.setId(null);
        auditoriaService.marcarActorActual();
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setRolUsuario(resolverRol(usuario.getRol()));
        return usuarioRepository.save(usuario);
    }

    /**
     * Actualiza los datos de un usuario existente. Si el rol cambia, sincroniza también la FK
     * {@code RolUsuario} para que ambas columnas paralelas queden consistentes.
     *
     * @param id      id del usuario a actualizar
     * @param usuario datos nuevos (nombre, apellido, email, rol, teléfono)
     * @return el usuario actualizado
     * @throws RuntimeException si el usuario no existe o el nuevo rol no es válido
     */
    @Override
    public Usuario actualizar(Long id, Usuario usuario) {
        log.info("Actualizando usuario con ID: {}", id);

        auditoriaService.marcarActorActual();
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        existente.setNombre(usuario.getNombre());
        existente.setApellido(usuario.getApellido());
        existente.setEmail(usuario.getEmail());
        if (usuario.getRol() != null && !usuario.getRol().equals(existente.getRol())) {
            existente.setRol(usuario.getRol());
            existente.setRolUsuario(resolverRol(usuario.getRol()));
        }
        if (usuario.getTelefono() != null) {
            existente.setTelefono(usuario.getTelefono());
        }

        return usuarioRepository.save(existente);
    }

    /**
     * @param id id del usuario a eliminar
     * @throws RuntimeException si el usuario no existe
     */
    @Override
    public void eliminar(Long id) {
        log.info("Eliminando usuario con ID: {}", id);

        if (!usuarioRepository.existsById(id)) {
            throw new IllegalArgumentException("Usuario no encontrado con ID: " + id);
        }

        auditoriaService.marcarActorActual();
        usuarioRepository.deleteById(id);
    }

    /**
     * @param id id del usuario
     * @return el usuario si existe
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> obtenerPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    /**
     * @param email email del usuario
     * @return el usuario con ese email, si existe
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> obtenerPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    /** @return todos los usuarios del sistema, sin paginar */
    @Override
    @Transactional(readOnly = true)
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    /** @return los usuarios con {@code activo = true} */
    @Override
    @Transactional(readOnly = true)
    public List<Usuario> listarActivos() {
        return usuarioRepository.findByActivoTrue();
    }

    /**
     * @param email email a verificar
     * @return {@code true} si ya existe un usuario registrado con ese email
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existePorEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    /**
     * @param id id del usuario a activar
     * @throws RuntimeException si el usuario no existe
     */
    @Override
    public void activar(Long id) {
        auditoriaService.marcarActorActual();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    /**
     * @param id id del usuario a desactivar
     * @throws RuntimeException si el usuario no existe
     */
    @Override
    public void desactivar(Long id) {
        auditoriaService.marcarActorActual();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    /**
     * Actualiza solo los campos de perfil autoeditables por el propio usuario (no requiere
     * rol ADMIN), sin tocar rol ni email institucional.
     *
     * @param id                  id del usuario
     * @param emailNotificaciones correo alterno para recibir notificaciones, o {@code null}
     * @param telefono            teléfono de contacto, o {@code null}
     * @return el usuario actualizado
     * @throws RuntimeException si el usuario no existe
     */
    @Override
    @Transactional
    public Usuario actualizarPerfil(Long id, String emailNotificaciones, String telefono) {
        int updated = usuarioRepository.actualizarPerfil(id, emailNotificaciones, telefono);
        if (updated == 0) {
            throw new IllegalArgumentException("Usuario no encontrado con ID: " + id);
        }
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
    }
}
