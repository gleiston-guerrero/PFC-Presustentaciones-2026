package ec.edu.uteq.presustentaciones.enums;

public enum EstadoSubmission {
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
     * @param estado estado
     * @return true si se cumple la condición, false si no
     */
    public static boolean esSuspendible(EstadoSubmission estado) {
        return estado != null 
            && estado != COMPLETADA 
            && estado != SUSPENDIDA 
            && estado != RECHAZADA;
    }

    /**
     * Es suspendible.
     * @return true si se cumple la condición, false si no
     */
    public boolean esSuspendible() {
        return esSuspendible(this);
    }
}
