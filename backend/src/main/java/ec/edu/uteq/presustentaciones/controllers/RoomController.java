package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Room;
import ec.edu.uteq.presustentaciones.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/salas")
@RequiredArgsConstructor
public class RoomController {
    private final RoomRepository roomRepository;

    /**
     * @return todas las rooms registradas
     */
    @GetMapping
    public List<Room> list() { return roomRepository.findAll(); }

    /**
     * Versión paginada, misma convención que /api/v1/submissions/paginado y
     * /api/v1/appUsers/paginado.
     *
     * @param pageable página y tamaño solicitados
     * @return página de rooms
     */
    @GetMapping("/paginado")
    public Page<Room> listPaged(Pageable pageable) {
        return roomRepository.findAll(pageable);
    }

    /**
     * Registra una room nueva para programar defensas.
     *
     * @param room datos de la room (código, nombre, capacidad, availability)
     * @return la room persistida, con su id asignado
     */
    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'SALA_GESTIONAR')")
    public Room create(@RequestBody Room room) { return roomRepository.save(room); }

    /**
     * Elimina una room del catálogo.
     *
     * @param id room a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'SALA_GESTIONAR')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        roomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
