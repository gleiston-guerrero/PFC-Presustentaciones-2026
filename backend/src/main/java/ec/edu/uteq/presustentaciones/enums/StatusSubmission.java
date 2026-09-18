package ec.edu.uteq.presustentaciones.enums;

public enum StatusSubmission {
    CREADA,
    ENVIADA,
    APROBADA,
    RECHAZADA,
    SUSPENDIDA,
    TUTORIA,
    EVALUACION,
    CALIFICADA,
    COMPLETADA;

    /**
     * Es suspendible.
     * @param status status
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
