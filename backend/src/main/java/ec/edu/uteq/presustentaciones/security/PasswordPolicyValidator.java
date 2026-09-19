package ec.edu.uteq.presustentaciones.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Política de contraseñas del sistema (RNF-06): longitud mínima de 8 caracteres y rechazo de
 * contraseñas comunes, contra la lista de {@code security/common-passwords.txt}. Componente
 * aparte -- no una anotación {@code @Size} más en cada DTO -- porque RF-04 (alta), RF-06
 * (cambio de contraseña propia) y RF-05 (restablecimiento) deben aplicar exactamente la misma
 * regla; una copia por endpoint es justo el tipo de deuda que hizo que, antes de esto, el alta
 * fuera la ÚNICA vía con algún control (6 caracteres, sin lista de comunes) y el cambio/
 * restablecimiento no existieran en absoluto.
 *
 * <p>La comprobación es case-insensitive (una variante en mayúsculas de una contraseña común
 * sigue siendo débil) pero no hace ningún otro "normalizado" (leetspeak, espacios, etc.): la
 * lista de comunes es literal a propósito, para que el comportamiento sea predecible y auditable.
 */
@Component
@Slf4j
public class PasswordPolicyValidator {

    /**
     * L o n g i t u d  m i n i m a.
     */
    public static final int LONGITUD_MINIMA = 8;
    private static final String RUTA_LISTA = "classpath:security/common-passwords.txt";

    private final Set<String> common;

    /**
     * Construye PasswordPolicyValidator sin dependencias inyectadas.
     */
    public PasswordPolicyValidator() {
        this.common = Collections.unmodifiableSet(loadCommon());
    }

    /**
     * Valida una contraseña contra la política. No hace nada si la contraseña cumple.
     *
     * @param password contraseña en texto plano, nunca registrada ni incluida en la excepción
     * @throws IllegalArgumentException con la regla incumplida (400 vía
     *         {@code GlobalExceptionHandler}) -- el mensaje nombra la regla, nunca la contraseña
     */
    public void validate(String password) {
        if (password == null || password.length() < LONGITUD_MINIMA) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        if (common.contains(password.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Esa contraseña es demasiado común. Elige una diferente.");
        }
    }

    /**
     * Meets.
     * @param password contraseña en texto plano a evaluar
     * @return true si la contraseña cumple la política, sin lanzar excepción.
     */
    public boolean meets(String password) {
        try {
            validate(password);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Set<String> loadCommon() {
        Set<String> result = new HashSet<>();
        try {
            Resource resource = new PathMatchingResourcePatternResolver().getResource(RUTA_LISTA);
            try (BufferedReader lector = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lector.readLine()) != null) {
                    String limpia = line.strip().toLowerCase();
                    if (!limpia.isEmpty() && !limpia.startsWith("#")) {
                        result.add(limpia);
                    }
                }
            }
            log.info("PasswordPolicyValidator: {} contraseñas comunes cargadas de {}", result.size(), RUTA_LISTA);
        } catch (IOException e) {
            log.error("No se pudo cargar la lista de contraseñas comunes ({}): {}", RUTA_LISTA, e.getMessage());
        }
        return result;
    }
}
