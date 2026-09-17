package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.UniversityDto;
import java.util.List;

public interface ExternalApiService {
    /**
     * Get universities of ecuador.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<UniversityDto> getUniversitiesOfEcuador();
}
