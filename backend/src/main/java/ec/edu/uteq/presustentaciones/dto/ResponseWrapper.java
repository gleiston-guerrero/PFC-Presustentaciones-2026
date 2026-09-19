package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response wrapper.
 * @param <T> tipo generico que parametriza la clase
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseWrapper<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Success.
     */
    @JsonProperty("success")
    private boolean success;
    /**
     * Data.
     */
    @JsonProperty("data")
    private T data;
    /**
     * Message.
     */
    @JsonProperty("message")
    private String message;
    /**
     * Errors.
     */
    @JsonProperty("errors")
    private Object errors;
    /**
     * Meta.
     */
    @JsonProperty("meta")
    private Object meta;

    /**
     * Success.
     * @param data  contenido de la respuesta exitosa
     * @param <T>   tipo del contenido
     * @return un wrapper con {@code success = true} y un mensaje genérico
     */
    public static <T> ResponseWrapper<T> success(T data) {
        return ResponseWrapper.<T>builder()
                .success(true)
                .data(data)
                .message("Operación exitosa")
                .build();
    }

    /**
     * Success.
     * @param data    contenido de la respuesta exitosa
     * @param message mensaje descriptivo de la operación
     * @param <T>     tipo del contenido
     * @return un wrapper con {@code success = true} y el mensaje indicado
     */
    public static <T> ResponseWrapper<T> success(T data, String message) {
        return ResponseWrapper.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    /**
     * Error.
     * @param message mensaje de error
     * @param <T>     tipo del contenido (no llevará datos)
     * @return un wrapper con {@code success = false} y sin detalle de errores
     */
    public static <T> ResponseWrapper<T> error(String message) {
        return ResponseWrapper.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    /**
     * Error.
     * @param message mensaje de error
     * @param errors  detalle estructurado de los errores (p. ej. errores de validación)
     * @param <T>     tipo del contenido (no llevará datos)
     * @return un wrapper con {@code success = false} y el detalle de errores indicado
     */
    public static <T> ResponseWrapper<T> error(String message, Object errors) {
        return ResponseWrapper.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .build();
    }
}
