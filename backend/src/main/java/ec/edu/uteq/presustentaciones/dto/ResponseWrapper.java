package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseWrapper<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("success")
    private boolean success;
    @JsonProperty("data")
    private T data;
    @JsonProperty("message")
    private String message;
    @JsonProperty("errors")
    private Object errors;
    @JsonProperty("meta")
    private Object meta;

    /**
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
