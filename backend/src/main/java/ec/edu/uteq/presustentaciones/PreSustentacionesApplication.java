package ec.edu.uteq.presustentaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * Sistema de Gestión de Pre-Sustentaciones de Trabajos de Titulación
 * Universidad Técnica Estatal de Quevedo
 *
 * <p>RNF-15: la siembra de datos de demostración (con contraseñas literales) y el bootstrap de
 * la cuenta administrativa de un despliegue real ya no viven aquí -- ver
 * {@code ec.edu.uteq.presustentaciones.bootstrap.DemoDataSeeder} (perfil {@code dev}) y
 * {@code ec.edu.uteq.presustentaciones.bootstrap.AdminBootstrap} (cualquier otro perfil).
 *
 * @author Equipo de Desarrollo
 * @version 1.0.0
 */
@SpringBootApplication
@Slf4j
public class PreSustentacionesApplication {

    /**
     * Main.
     * @param args args
     */
    public static void main(String[] args) {
        SpringApplication.run(PreSustentacionesApplication.class, args);
    }
}
