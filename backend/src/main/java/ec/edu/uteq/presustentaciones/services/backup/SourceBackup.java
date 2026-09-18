package ec.edu.uteq.presustentaciones.services.backup;

/**
 * Cómo se originó el backup.
 * <ul>
 *   <li>{@code MANUAL}     — un administrador pulsó "Generar respaldo ahora".</li>
 *   <li>{@code AUTOMATICO} — lo generó el schedule programado ({@code BackupScheduler}).</li>
 *   <li>{@code EVENTO}     — disparado tras un hecho de negocio (p. ej. cierre de una
 *       announcement de defensas). Fase 1 no lo dispara solo todavía, pero el tipo ya
 *       existe para etiquetarlo si se genera desde la UI.</li>
 * </ul>
 * La retención automática (GFS) solo borra copias {@code AUTOMATICO}: {@code MANUAL} y
 * {@code EVENTO} las conserva siempre.
 */
public enum SourceBackup {
    MANUAL,
    AUTOMATICO,
    EVENTO
}
