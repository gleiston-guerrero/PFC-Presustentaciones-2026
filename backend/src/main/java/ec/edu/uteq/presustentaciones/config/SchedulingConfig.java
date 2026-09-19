package ec.edu.uteq.presustentaciones.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas programadas de Spring (usado por BackupScheduler). */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Constructor sin argumentos: Spring instancia la configuracion de tareas programadas.
     * Se declara explicitamente porque javadoc avisa del constructor
     * por defecto, que no puede llevar comentario.
     */
    public SchedulingConfig() {
        // sin estado que inicializar
    }
}
