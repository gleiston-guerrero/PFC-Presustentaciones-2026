package ec.edu.uteq.presustentaciones.config;

import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "ec.edu.uteq.presustentaciones.controllers")
public class ResponseWrappingAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // No aplicar si el tipo de retorno ya es ResponseWrapper o es un tipo de archivo
        return !ResponseWrapper.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // Evitar doble envoltura
        if (body instanceof ResponseWrapper) {
            return body;
        }

        // Respuestas binarias (byte[] / Resource, p. ej. la descarga de un PDF de minutes o de
        // un .dump de backup): el converter las escribe crudas y hace (byte[]) body -- si
        // aquí se envolvieran en un ResponseWrapper, ese cast revienta con ClassCastException
        // y el cliente recibe un 400 genérico en vez del archivo o de un 404 limpio. También
        // se deja pasar un body nulo (ResponseEntity ...build() sin cuerpo): no hay nada que
        // envolver.
        if (body == null
                || body instanceof byte[]
                || body instanceof org.springframework.core.io.Resource) {
            return body;
        }

        // Evitar envolver endpoints de documentación (Swagger), monitoreo (Actuator) y archivos binarios (PDF)
        String path = request.getURI().getPath();
        if (path.contains("/v3/api-docs") || 
            path.contains("/swagger-ui") || 
            path.contains("/actuator") || 
            selectedContentType.isCompatibleWith(MediaType.APPLICATION_PDF) || 
            selectedContentType.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM)) {
            return body;
        }

        // Manejo especial para respuestas de tipo String para evitar ClassCastException en StringHttpMessageConverter
        if (body instanceof String) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(ResponseWrapper.success(body));
            } catch (Exception e) {
                return body;
            }
        }

        // Envoltura genérica exitosa
        return ResponseWrapper.success(body);
    }
}
