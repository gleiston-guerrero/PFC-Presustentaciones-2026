package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.Estudiante;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.EstudianteRepository;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resuelve el usuario / estudiante autenticado a partir del SecurityContext.
 * Centraliza el patrón que ya usaban TutoriaController y TemaController para
 * evitar IDOR: nunca se confía en un id que venga por la URL para operaciones
 * "sobre mí mismo".
 */
@Service
@RequiredArgsConstructor
public class UsuarioActualService {

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;

    /**
     * @return el usuario autenticado, resuelto desde el {@code SecurityContext}
     * @throws IllegalStateException si no hay un usuario autenticado, o el email del
     *                                contexto de seguridad no corresponde a ningún usuario
     */
    public Usuario usuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado en el sistema"));
    }

    /**
     * @return el perfil de estudiante del usuario autenticado
     * @throws IllegalStateException    si no hay un usuario autenticado
     * @throws IllegalArgumentException si el usuario autenticado no tiene perfil de estudiante
     */
    public Estudiante estudiante() {
        return estudianteRepository.findByUsuarioId(usuario().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El usuario autenticado no tiene un perfil de estudiante asociado"));
    }

    /**
     * @return el id del estudiante autenticado, o {@code null} si no hay un usuario
     *         autenticado o quien consulta no tiene perfil de estudiante
     */
    public Long estudianteIdOrNull() {
        try {
            return estudianteRepository.findByUsuarioId(usuario().getId())
                    .map(Estudiante::getId)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
