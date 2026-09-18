package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Audit;
import ec.edu.uteq.presustentaciones.repositories.AuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Consulta del history de auditoría (ver V15__audit.sql) -- solo lectura, los triggers escriben. */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.tienePermission(authentication, 'AUDITORIA_VER')")
public class AuditController {

    private final AuditRepository auditRepository;

    /**
     * History de auditoría paginado y filtrable. El tamaño de página se acota entre 1 y
     * 100 para que un cliente no pueda pedir la tabla completa de una vez, y el orden es
     * siempre por fecha descendente.
     *
     * @param page      número de página (0 por defecto); los negativos se tratan como 0
     * @param size      filas por página (20 por defecto); se limita a un máximo de 100
     * @param tabla     filtra por tabla auditada, opcional
     * @param accion    filtra por acción (CREAR, MODIFICAR, ELIMINAR, ...), opcional
     * @param appUserId filtra por el appUser que ejecutó el cambio, opcional
     * @param q         búsqueda de texto libre sobre el registro, opcional
     * @return 200 con la página de eventos de auditoría
     */
    @GetMapping("/paginado")
    public ResponseEntity<Page<Audit>> listPaginado(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "tabla", required = false) String tabla,
            @RequestParam(name = "accion", required = false) String accion,
            @RequestParam(name = "appUserId", required = false) Long appUserId,
            @RequestParam(name = "q", required = false) String q) {
        int tamanioSeguro = Math.min(Math.max(size, 1), 100);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), tamanioSeguro, Sort.by(Sort.Direction.DESC, "fecha"));
        return ResponseEntity.ok(auditRepository.searchConFiltros(tabla, accion, appUserId, q, pageable));
    }

    /**
     * Distintos valores de "tabla" ya registrados, para poblar el filtro del frontend sin
     * hardcodear la lista de tablas auditadas.
     *
     * @return nombres de tabla presentes hoy en el history de auditoría
     */
    @GetMapping("/tablas")
    public java.util.List<String> tablasAuditadas() {
        return java.util.List.of("usuarios", "roles_usuario", "permisos", "rol_permisos", "estudiante", "solicitud", "actas", "evaluaciones_finales", "facultades", "carreras", "modalidades_titulacion", "periodos_academicos");
    }
}
