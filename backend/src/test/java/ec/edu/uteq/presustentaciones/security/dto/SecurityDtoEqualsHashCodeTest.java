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
    void loginResponseMeetsContractEqualsHashCode() {
        EqualsVerifier.forClass(LoginResponse.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void registerRequestMeetsContractEqualsHashCode() {
        EqualsVerifier.forClass(RegisterRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void loginRequestMeetsContractEqualsHashCode() {
        EqualsVerifier.forClass(LoginRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void changePasswordRequestMeetsContractEqualsHashCode() {
        EqualsVerifier.forClass(ChangePasswordRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void recoverPasswordRequestMeetsContractEqualsHashCode() {
        EqualsVerifier.forClass(RecoverPasswordRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    void resetPasswordRequestMeetsContractEqualsHashCode() {
        EqualsVerifier.forClass(ResetPasswordRequest.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }
}
