package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.PeriodAcademico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PeriodAcademicoRepository extends JpaRepository<PeriodAcademico, Integer> {
    Optional<PeriodAcademico> findByCodigo(String codigo);
}
