package ec.edu.uteq.presustentaciones.enums;

/**
 * Valores posibles de status submission.
 */
public enum StatusSubmission {
    /** Borrador creado por el estudiante; aun no se envia a revision. */
    CREADA,
    /** Enviada a revision y pendiente de decision del coordinador. */
    ENVIADA,
    /** Aprobada por el coordinador; habilita las fases siguientes. */
    APROBADA,
    /** Rechazada en revision; el estudiante puede corregir y reenviar. */
    RECHAZADA,
    /** Detenida temporalmente sin cerrarse ni rechazarse. */
    SUSPENDIDA,
    /** En fase de tutoria con el docente asignado. */
    TUTORIA,
    /** En evaluacion por el tribunal de pre-sustentacion. */
    EVALUACION,
    /** Con nota registrada por el tribunal. */
    CALIFICADA,
    /** Cerrada: el flujo termino y el acta esta firmada. */
    COMPLETADA;

    /**
     * Es suspendible.
     * @param status estado de la solicitud que se evalúa
     * @return true si se cumple la condición, false si no
     */
    public static boolean isSuspendable(StatusSubmission status) {
        return status != null 
            && status != COMPLETADA 
            && status != SUSPENDIDA 
            && status != RECHAZADA;
    }

    /**
     * Es suspendible.
     * @return true si se cumple la condición, false si no
     */
    public boolean isSuspendable() {
        return isSuspendable(this);
    }
}
