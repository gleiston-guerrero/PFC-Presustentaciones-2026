package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles_usuario", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleAppUser {

    /**
     * Sin @GeneratedValue a propósito: los 4 roles base se siembran con id explícito vía SQL
     * crudo (fuera de Hibernate, ver PreSustentacionesApplication / migración V13), y mezclar
     * eso con GenerationType.AUTO sobre un Short produjo ids corruptos (negativos) la primera
     * vez que se creó un role nuevo desde "Gestionar Roles" -- el generador hi/lo de Hibernate
     * no tenía forma de saber que esos ids ya estaban tomados. RoleController.create() asigna
     * el siguiente id disponible a mano, mismo patrón que Permission.
     */
    @Id
    @Column(name = "id")
    private Short id;

    @JsonValue
    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    /**
     * Contraparte de @JsonValue: sin esto, Jackson serializa RoleAppUser como el string
     * plano de su código pero no puede rebuildlo de vuelta -- rompía la deserialización
     * al leer un AppUser cacheado en Redis ("Cannot construct instance of RolUsuario...
     * no String-argument constructor"). Solo repuebla el código; id/nombre quedan null,
     * pero eso es invisible para el cliente porque @JsonValue vuelve a colapsar la
     * respuesta al string de todos modos.
     *
     * @param codigo código del role de appUser
     * @return una instancia con solo el código repoblado (id/nombre quedan null)
     */
    @JsonCreator
    public static RoleAppUser fromCodigo(String codigo) {
        return RoleAppUser.builder().codigo(codigo).build();
    }
}
