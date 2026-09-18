package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.UpdateProgressRequest;
import ec.edu.uteq.presustentaciones.dto.ProgressDegreeDTO;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
import ec.edu.uteq.presustentaciones.services.ProgressDegreeService;
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
public class ProgressDegreeController {

    private final ProgressDegreeService progressService;
    private final CurrentAppUserService currentAppUser;

    /**
     * Ruta de titulación del student autenticado. El id sale del token, no de la URL, así
     * que un student nunca puede consultar el progress de otro.
     *
     * @return 200 con el checklist de progress del student autenticado
     */
    @GetMapping
    public ResponseEntity<ProgressDegreeDTO> myProgress() {
        return ResponseEntity.ok(progressService.obtain(currentAppUser.student().getId()));
    }

    /**
     * Actualiza el checklist del student autenticado (marcar/desmarcar hitos).
     *
     * @param request hitos a update, validado con Bean Validation
     * @return 200 con el progress ya actualizado
     */
    @PutMapping
    public ResponseEntity<ProgressDegreeDTO> update(@RequestBody @Valid UpdateProgressRequest request) {
        return ResponseEntity.ok(
                progressService.update(currentAppUser.student().getId(), request.getPasos()));
    }
}
