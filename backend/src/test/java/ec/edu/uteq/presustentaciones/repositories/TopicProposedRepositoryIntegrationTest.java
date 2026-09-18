package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TopicProposed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración real (mismo criterio que {@code PreDefenseApplicationTests}:
 * {@code @SpringBootTest} + {@code @AutoConfigureTestDatabase(replace = NONE)} para usar el
 * Postgres real, no un repositorio mockeado) para {@link TopicProposedRepository#searchWithFiltros}.
 *
 * <p>Hallazgo real (verificación manual contra el backend en Docker, 2026-09-04): con
 * {@code nivelDificultad == null}, la consulta fallaba con
 * "ERROR: function lower(bytea) does not exist" -- Postgres no podía inferir el tipo del
 * parámetro dentro de {@code LOWER(?)} cuando el valor bindeado era null. Ninguna prueba lo
 * detectaba porque {@code TopicServiceImplTest} mockea {@code TopicProposedRepository} por
 * completo: {@code when(repo.searchWithFiltros(...)).thenReturn(...)} nunca ejecuta el JPQL
 * real contra Postgres, así que un bug de traducción JPQL-a-SQL como este es invisible a nivel
 * de mock. Esta clase reproduce el JPQL real contra la base real -- exactamente el escenario
 * que hizo fallar la petición HTTP -- y se corrigió con {@code CAST(:nivel AS string)} en el
 * repositorio (ver su comentario).
 */
@SpringBootTest(properties = "spring.test.database.replace=NONE")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TopicProposedRepositoryIntegrationTest {

    @Autowired
    private TopicProposedRepository topicProposedRepository;

    @Test
    void searchWithFiltros_sinNingunFiltro_noLanzaYDevuelveLista() {
        List<TopicProposed> result = assertDoesNotThrow(
                () -> topicProposedRepository.searchWithFiltros(null, null, null, null));
        assertNotNull(result);
    }

    @Test
    void searchWithFiltros_soloNivelDificultadNull_noLanza() {
        // El caso exacto que rompía: algún filtro presente, nivel ausente.
        assertDoesNotThrow(() -> topicProposedRepository.searchWithFiltros(1, null, null, null));
    }

    @Test
    void searchWithFiltros_soloProgramId_noLanza() {
        assertDoesNotThrow(() -> topicProposedRepository.searchWithFiltros(1, null, null, null));
    }

    @Test
    void searchWithFiltros_soloResearchLineId_noLanza() {
        assertDoesNotThrow(() -> topicProposedRepository.searchWithFiltros(null, 1, null, null));
    }

    @Test
    void searchWithFiltros_soloNivelDificultad_noLanzaYFiltraCorrectamente() {
        List<TopicProposed> result = assertDoesNotThrow(
                () -> topicProposedRepository.searchWithFiltros(null, null, null, "BASICO"));
        assertNotNull(result);
        for (TopicProposed t : result) {
            assertTrue("BASICO".equalsIgnoreCase(t.getNivelDificultad()));
        }
    }

    @Test
    void searchWithFiltros_nivelDificultadEnMinusculas_esCaseInsensitive() {
        // LOWER(...) = LOWER(CAST(...)) debe seguir siendo insensible a mayúsculas/minúsculas.
        List<TopicProposed> mayus = topicProposedRepository.searchWithFiltros(null, null, null, "BASICO");
        List<TopicProposed> minus = topicProposedRepository.searchWithFiltros(null, null, null, "basico");
        assertNotNull(minus);
        assertTrue(mayus.size() == minus.size());
    }

    @Test
    void searchWithFiltros_combinacionDeFiltros_noLanza() {
        assertDoesNotThrow(() -> topicProposedRepository.searchWithFiltros(1, 1, null, "INTERMEDIO"));
    }

    @Test
    void searchWithFiltros_areaIdWithoutNivel_noLanza() {
        assertDoesNotThrow(() -> topicProposedRepository.searchWithFiltros(null, null, 1, null));
    }
}
