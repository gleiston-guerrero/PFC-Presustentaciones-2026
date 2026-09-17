package ec.edu.uteq.presustentaciones.config;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class CustomWebMvcRegistrations implements WebMvcRegistrations {

    @Override
    /**
     * Get request mapping handler mapping.
     * @return el RequestMappingHandlerMapping correspondiente
     */
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new RequestMappingHandlerMapping() {
            @Override
            protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
                RequestMappingInfo info = super.getMappingForMethod(method, handlerType);
                if (info != null && (handlerType.isAnnotationPresent(RestController.class))) {
                    Set<PathPattern> patterns = info.getPathPatternsCondition().getPatterns();
                    
                    if (patterns.isEmpty()) {
                        return info;
                    }

                    String[] newPatterns = patterns.stream()
                            .map(PathPattern::getPatternString)
                            .map(pattern -> {
                                if (pattern.startsWith("/api/v1")) {
                                    return pattern;
                                } else if (pattern.startsWith("/api")) {
                                    return "/api/v1" + pattern.substring(4);
                                } else {
                                    return "/api/v1" + (pattern.startsWith("/") ? pattern : "/" + pattern);
                                }
                            })
                            .toArray(String[]::new);

                    RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
                    options.setPatternParser(PathPatternParser.defaultInstance);

                    return info.mutate()
                            .options(options)
                            .paths(newPatterns)
                            .build();
                }
                return info;
            }
        };
    }
}
