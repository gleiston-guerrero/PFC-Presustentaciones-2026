package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.BackupInfoDTO;
import ec.edu.uteq.presustentaciones.dto.RegisterPruebaRestauracionRequest;
import ec.edu.uteq.presustentaciones.dto.BackupConfigDTO;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.services.BackupService;
import ec.edu.uteq.presustentaciones.services.WalPitrService;
import ec.edu.uteq.presustentaciones.services.backup.OrigenBackup;
import ec.edu.uteq.presustentaciones.services.backup.TipoBackup;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Apartado "Gestión de Respaldos de Base de Datos" del administrador. Fase 1 del plan
 * (ver {@code docs/basedatos/PLAN-RESPALDOS-RECUPERACION.md}):
 * <ul>
 *   <li>Backup FULL bajo demanda + programado (schedule cron editable).</li>
 *   <li>Retención automática GFS de las copias automáticas.</li>
 *   <li>Panel de estado y bitácora de pruebas de restauración.</li>
 * </ul>
 * Todo el controlador exige {@code BACKUPS_GESTIONAR} (V27), que por defecto solo tiene ADMIN.
 * Prefijo real: {@code /api/v1/backups} (lo añade {@code CustomWebMvcRegistrations}).
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.tienePermission(authentication, 'BACKUPS_GESTIONAR')")
public class BackupController {

    private final BackupService backupService;
    private final WalPitrService walPitrService;

    // ── Copias ──────────────────────────────────────────────────────────────

    /** @return 200 con la lista de backups, del más reciente al más antiguo */
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(ResponseWrapper.success(backupService.list()));
    }

    /**
     * Genera un backup FULL ahora.
     *
     * @param origen opcional: MANUAL (por defecto) o EVENTO
     * @return 200 con los metadatos del backup, o 400/409 si {@code pg_dump} falla
     */
    @PostMapping
    public ResponseEntity<?> generate(@RequestParam(defaultValue = "MANUAL") String origen) {
        OrigenBackup o = "EVENTO".equalsIgnoreCase(origen) ? OrigenBackup.EVENTO : OrigenBackup.MANUAL;
        BackupInfoDTO info = backupService.generate(TipoBackup.FULL, o);
        return ResponseEntity.ok(ResponseWrapper.success(info, "Respaldo generado correctamente"));
    }

    /**
     * Genera un backup DIFERENCIAL (filas cambiadas desde el último FULL). Fase 2.
     *
     * @param origen opcional: MANUAL (por defecto) o EVENTO
     * @return 200 con los metadatos del diferencial generado
     */
    @PostMapping("/diferencial")
    public ResponseEntity<?> generateDiferencial(@RequestParam(defaultValue = "MANUAL") String origen) {
        OrigenBackup o = "EVENTO".equalsIgnoreCase(origen) ? OrigenBackup.EVENTO : OrigenBackup.MANUAL;
        BackupInfoDTO info = backupService.generateDiferencial(o);
        return ResponseEntity.ok(ResponseWrapper.success(info, "Respaldo diferencial generado"));
    }

    /**
     * Descarga un backup como archivo adjunto.
     *
     * @param nombre nombre del archivo de backup
     * @return 200 con el contenido del archivo como {@code application/octet-stream}
     */
    @GetMapping("/{nombre}/descargar")
    public ResponseEntity<byte[]> download(@PathVariable String nombre) {
        byte[] contenido = backupService.leer(nombre);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(contenido);
    }

    /**
     * Restaura la base desde un backup (operación destructiva).
     *
     * @param nombre nombre del backup FULL a restore
     * @return 200 confirmando la restauración
     */
    @PostMapping("/{nombre}/restaurar")
    public ResponseEntity<?> restore(@PathVariable String nombre) {
        backupService.restore(nombre);
        return ResponseEntity.ok(ResponseWrapper.success(null,
                "Base de datos restaurada desde el respaldo. Se recomienda reiniciar el backend "
                + "para descartar datos en caché."));
    }

    /**
     * Elimina permanentemente un archivo de backup.
     *
     * @param nombre nombre del backup a delete
     * @return 200 confirmando el borrado
     */
    @DeleteMapping("/{nombre}")
    public ResponseEntity<?> delete(@PathVariable String nombre) {
        backupService.delete(nombre);
        return ResponseEntity.ok(ResponseWrapper.success(null, "Respaldo eliminado"));
    }

    // ── Panel de estado ─────────────────────────────────────────────────────

    /**
     * Resumen para el panel: última copia, próxima programada, espacio, RPO, última prueba.
     *
     * @return 200 con el estado consolidado de backups
     */
    @GetMapping("/estado")
    public ResponseEntity<?> estado() {
        return ResponseEntity.ok(ResponseWrapper.success(backupService.estado()));
    }

    // ── Schedule (programación + retención) ────────────────────────────────

    /** @return 200 con la configuración vigente del schedule de backups */
    @GetMapping("/config")
    public ResponseEntity<?> obtainConfig() {
        return ResponseEntity.ok(ResponseWrapper.success(backupService.configDTO()));
    }

    /**
     * Actualiza el schedule: activo/pausado, expresión cron y política de retención GFS.
     *
     * @param dto nueva configuración del schedule
     * @return 200 con la config aplicada, o 400 si el cron es inválido
     */
    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@Valid @RequestBody BackupConfigDTO dto) {
        return ResponseEntity.ok(ResponseWrapper.success(
                backupService.updateConfig(dto), "Cronograma actualizado"));
    }

    /** Aplica la retención GFS ahora mismo. @return 200 con los nombres eliminados */
    @PostMapping("/retencion")
    public ResponseEntity<?> aplicarRetencion() {
        List<String> eliminados = backupService.aplicarRetencion();
        String msg = eliminados.isEmpty()
                ? "Retención aplicada: no había copias para eliminar."
                : "Retención aplicada: " + eliminados.size() + " copia(s) eliminada(s).";
        return ResponseEntity.ok(ResponseWrapper.success(eliminados, msg));
    }

    // ── Bitácora de pruebas de restauración ─────────────────────────────────

    /** @return 200 con las últimas 50 pruebas de restauración registradas */
    @GetMapping("/pruebas")
    public ResponseEntity<?> listPruebas() {
        return ResponseEntity.ok(ResponseWrapper.success(backupService.pruebas()));
    }

    /**
     * @param req datos de la prueba de restauración a register
     * @return 200 con la prueba registrada
     */
    @PostMapping("/pruebas")
    public ResponseEntity<?> registerPrueba(@Valid @RequestBody RegisterPruebaRestauracionRequest req) {
        return ResponseEntity.ok(ResponseWrapper.success(
                backupService.registerPrueba(req.getBackupNombre(), req.getResultado(),
                        req.getResponsable(), req.getNotas()),
                "Prueba de restauración registrada"));
    }

    // ── Fase 2: WAL / PITR y base física ────────────────────────────────────

    /**
     * Estado del archivado de WAL, del directorio compartido y de las bases físicas.
     *
     * @return 200 con el estado del archivado de WAL
     */
    @GetMapping("/wal")
    public ResponseEntity<?> estadoWal() {
        return ResponseEntity.ok(ResponseWrapper.success(walPitrService.estado()));
    }

    /**
     * Cierra el segmento de WAL actual para que se archive de inmediato.
     *
     * @return 200 con el nombre del segmento cerrado
     */
    @PostMapping("/wal/switch")
    public ResponseEntity<?> switchWal() {
        String wal = walPitrService.forzarSwitchWal();
        return ResponseEntity.ok(ResponseWrapper.success(wal, "Segmento " + wal + " cerrado y en cola de archivado"));
    }

    /**
     * Limpia el WAL archivado más antiguo que la retención configurada.
     *
     * @return 200 con la cantidad de segmentos eliminados
     */
    @PostMapping("/wal/limpiar")
    public ResponseEntity<?> limpiarWal() {
        int dias = backupService.config().getRetenerDiasWal();
        int borrados = walPitrService.limpiarWal(dias);
        return ResponseEntity.ok(ResponseWrapper.success(borrados,
                borrados == 0 ? "No había WAL para limpiar." : borrados + " segmento(s) de WAL eliminados."));
    }

    /**
     * Genera un backup físico base ({@code pg_basebackup}), la base para PITR.
     *
     * @return 200 con los metadatos de la base física generada
     */
    @PostMapping("/bases")
    public ResponseEntity<?> generateBaseFisica() {
        return ResponseEntity.ok(ResponseWrapper.success(
                walPitrService.generateBaseFisica(), "Base física generada"));
    }

    /**
     * @param nombre nombre de la base física a delete
     * @return 200 confirmando el borrado
     */
    @DeleteMapping("/bases/{nombre}")
    public ResponseEntity<?> deleteBase(@PathVariable String nombre) {
        walPitrService.deleteBase(nombre);
        return ResponseEntity.ok(ResponseWrapper.success(null, "Base física eliminada"));
    }
}
