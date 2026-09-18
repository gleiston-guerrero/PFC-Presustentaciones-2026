package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Fila de auditoría, escrita por los triggers de Postgres (ver V15__audit.sql) --
 * el backend nunca hace INSERT directo aquí, solo lee. "Quién" se resuelve porque
 * AuditService.marcarActorActual() fija el GUC de sesión presus.appUser_actual
 * justo antes de la operación que dispara el trigger.
 */
@Entity
@Table(name = "auditoria", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    private Long id;

    @Column(name = "tabla", nullable = false, length = 60)
    @JsonProperty("tabla")
    private String tabla;

    @Column(name = "registro_id")
    @JsonProperty("registroId")
    private Long recordId;

    @Column(name = "accion", nullable = false, length = 20)
    @JsonProperty("accion")
    private String accion;

    @Column(name = "usuario_id")
    @JsonProperty("usuarioId")
    private Long appUserId;

    @Column(name = "usuario_nombre", length = 200)
    @JsonProperty("usuarioNombre")
    private String appUserNombre;

    @Column(name = "fecha", nullable = false)
    @JsonProperty("fecha")
    private LocalDateTime date;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_anteriores", columnDefinition = "jsonb")
    @JsonProperty("datosAnteriores")
    private String dataAnteriores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_nuevos", columnDefinition = "jsonb")
    @JsonProperty("datosNuevos")
    private String dataNuevos;
}
