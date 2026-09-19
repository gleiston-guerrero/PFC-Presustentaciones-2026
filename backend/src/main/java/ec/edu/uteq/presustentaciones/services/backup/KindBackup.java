package ec.edu.uteq.presustentaciones.services.backup;

/** Tipo de backup. FASE 1 solo produce FULL; DIFERENCIAL queda reservado para la fase 2. */
public enum KindBackup {
    /** Copia completa de la base. Es la unica que produce la fase 1. */
    FULL,
    /** Copia de lo cambiado desde la ultima completa. Reservado para la fase 2. */
    DIFERENCIAL
}
