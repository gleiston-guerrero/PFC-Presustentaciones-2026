package ec.edu.uteq.presustentaciones;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test real: {@code @SpringBootTest} levanta el contexto completo de Spring contra la
 * base de datos y Redis reales inyectados por variables de entorno (ver el job {@code backend}
 * de {@code .github/workflows/ci.yml}, que provisiona contenedores de servicio
 * {@code postgres:15-alpine} y {@code redis:7-alpine}).
 *
 * <p>Hallazgo real corregido (auditoría de reproducibilidad 2026-08-30): antes de esta clase,
 * este archivo era un {@code assertTrue(true)} sin ninguna anotación de Spring -- no arrancaba
 * ningún contexto, así que los contenedores de Postgres/Redis que CI levanta nunca se ejercían
 * de verdad. El Criterio R1 de reproducibilidad se apoyaba solo en {@code make wait-backend}
 * (local) para probar que Flyway migra limpio desde una base de datos vacía; en CI no había
 * ninguna prueba real de esto.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} también es real, no cosmético: sin él,
 * Spring Boot detecta el driver H2 en el classpath de test (declarado en {@code pom.xml} para
 * generación offline de DDL) y reemplaza silenciosamente el DataSource real por una BD H2 en
 * memoria -- confirmado localmente: sin esta anotación, el test falla porque Flyway intenta
 * execute PL/pgSQL (procedimientos almacenados) contra H2, que no lo entiende. Con la
 * anotación, el contexto usa el Postgres real y Flyway aplica las 18 migraciones desde cero.
 */
@SpringBootTest(properties = "spring.test.database.replace=NONE")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PreDefenseApplicationTests {

    @Test
    void contextLoads() {
        // Sin cuerpo a propósito: la aserción real es que Spring pueda levantar el contexto
        // completo (todos los beans, Flyway migrado, conexión a Postgres y Redis reales) sin
        // lanzar una excepción -- si @SpringBootTest falla al arrancar, JUnit reporta el test
        // como fallido con la causa real, sin necesidad de una aserción explícita aquí.
    }
}
