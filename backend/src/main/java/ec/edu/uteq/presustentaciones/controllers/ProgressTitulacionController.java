package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.UpdateProgressRequest;
import ec.edu.uteq.presustentaciones.dto.ProgressTitulacionDTO;
import ec.edu.uteq.presustentaciones.security.service.AppUserActualService;
import ec.edu.uteq.presustentaciones.services.ProgressTitulacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Ruta de titulación / checklist del student. Exclusivo del role ESTUDIANTE y
 * siempre sobre el student autenticado (el id sale del JWT, no de la URL).
 */
@RestController
@RequestMapping("/api/v1/orientacion/progreso")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ESTUDIANTE')")
public class ProgressTitulacionController {

    private final ProgressTitulacionService progressService;
    private final AppUserActualService appUserActual;

    /**
     * Ruta de titulación del student autenticado. El id sale del token, no de la URL, así
     * que un student nunca puede consultar el progress de otro.
     *
     * @return 200 con el checklist de progress del student autenticado
     */
    @GetMapping
    public ResponseEntity<ProgressTitulacionDTO> miProgress() {
        return ResponseEntity.ok(progressService.obtain(appUserActual.student().getId()));
    }

    /**
     * Actualiza el checklist del student autenticado (marcar/desmarcar hitos).
     *
     * @param request hitos a update, validado con Bean Validation
     * @return 200 con el progress ya actualizado
     */
    @PutMapping
    public ResponseEntity<ProgressTitulacionDTO> update(@RequestBody @Valid UpdateProgressRequest request) {
        return ResponseEntity.ok(
                progressService.update(appUserActual.student().getId(), request.getPasos()));
    }
}
