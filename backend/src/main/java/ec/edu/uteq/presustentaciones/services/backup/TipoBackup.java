package ec.edu.uteq.presustentaciones.services.backup;

/** Tipo de backup. FASE 1 solo produce FULL; DIFERENCIAL queda reservado para la fase 2. */
public enum TipoBackup {
    FULL,
    DIFERENCIAL
}
