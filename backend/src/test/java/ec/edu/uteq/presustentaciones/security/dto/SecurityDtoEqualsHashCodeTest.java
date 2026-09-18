package ec.edu.uteq.presustentaciones.security.dto;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

/**
 * Los 6 DTOs de security.dto son @Data de Lombok -- equals/hashCode/canEqual generados
 * automaticamente, sin ningun test dedicado (0% de ramas, 188 ramas sin ejercitar segun
 * JaCoCo). EqualsVerifier prueba el contrato completo (reflexividad, simetria,
 * transitividad, null-safety) explorando todas las combinaciones de campos que Lombok
 * genera, en vez de adivinar manualmente cada caso con valores nulos.
 */
class SecurityDtoEqualsHashCodeTest {

    @Test
    void loginResponseMeetsElContratoEqualsHashCode() {
        EqualsVerifier.forClass(LoginResponse.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void registerRequestMeetsElContratoEqualsHashCode() {
        EqualsVerifier.forClass(RegisterRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void loginRequestMeetsElContratoEqualsHashCode() {
        EqualsVerifier.forClass(LoginRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void changePasswordRequestMeetsElContratoEqualsHashCode() {
        EqualsVerifier.forClass(ChangePasswordRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void recoverPasswordRequestMeetsElContratoEqualsHashCode() {
        EqualsVerifier.forClass(RecoverPasswordRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void resetPasswordRequestMeetsElContratoEqualsHashCode() {
        EqualsVerifier.forClass(ResetPasswordRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }
}
