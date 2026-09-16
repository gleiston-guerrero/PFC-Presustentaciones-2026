package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.UniversityDto;
import ec.edu.uteq.presustentaciones.services.ExternalApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/universidades")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "API Externa - Universidades", description = "Endpoints para consumir la lista de universidades de Hipo Labs")
public class ExternalApiController {

    private final ExternalApiService externalApiService;

    /**
     * Universidades de Ecuador consumidas de la API externa de Hipo Labs. La llamada la hace
     * el backend (no el navegador) y su resultado se cachea en Redis, de modo que este
     * endpoint responde desde caché salvo la primera consulta de cada ventana.
     *
     * @return 200 con la lista de universidades; si la API externa falla tras los reintentos,
     *         el servicio devuelve lo que tenga en caché en vez de propagar el error
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()") // Permite acceso a cualquier appUser autenticado en la plataforma
    @Operation(summary = "Obtener listado de universidades de Ecuador", description = "Consume la API de Hipo Labs con timeouts, reintentos y caché Redis activa.")
    public ResponseEntity<List<UniversityDto>> getUniversities() {
        log.info("GET /api/v1/universidades - Procesando solicitud");
        List<UniversityDto> universities = externalApiService.getUniversitiesOfEcuador();
        return ResponseEntity.ok(universities);
    }
}
