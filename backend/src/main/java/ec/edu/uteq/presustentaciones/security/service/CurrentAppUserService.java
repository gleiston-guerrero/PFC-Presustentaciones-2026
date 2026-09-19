package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resuelve el appUser / student autenticado a partir del SecurityContext.
 * Centraliza el patrón que ya usaban TutoringController y TopicController para
 * evitar IDOR: nunca se confía en un id que venga por la URL para operaciones
 * "sobre mí mismo".
 */
@Service
@RequiredArgsConstructor
public class CurrentAppUserService {

    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;

    /**
     * App user.
     * @return el appUser autenticado, resuelto desde el {@code SecurityContext}
     * @throws IllegalStateException si no hay un appUser autenticado, o el email del
     *                                contexto de seguridad no corresponde a ningún appUser
     */
    public AppUser appUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return appUserRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado en el sistema"));
    }

    /**
     * Student.
     * @return el perfil de student del appUser autenticado
     * @throws IllegalStateException    si no hay un appUser autenticado
     * @throws IllegalArgumentException si el appUser autenticado no tiene perfil de student
     */
    public Student student() {
        return studentRepository.findByAppUserId(appUser().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El usuario autenticado no tiene un perfil de estudiante asociado"));
    }

    /**
     * Student id or null.
     * @return el id del student autenticado, o {@code null} si no hay un appUser
     *         autenticado o quien consulta no tiene perfil de student
     */
    public Long studentIdOrNull() {
        try {
            return studentRepository.findByAppUserId(appUser().getId())
                    .map(Student::getId)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
