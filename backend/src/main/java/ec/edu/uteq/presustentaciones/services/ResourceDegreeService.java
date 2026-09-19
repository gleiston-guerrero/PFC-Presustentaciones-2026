package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceDegreeDTO;

import java.util.List;

/**
 * Contrato. Servicio de resource degree.
 */
public interface ResourceDegreeService {

    /**
     * Resources visibles para una program (los generales + los de esa program). null = todos.
     * @param programId program id
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<ResourceDegreeDTO> list(Integer programId);

    /**
     * Create.
     * @param request request
     * @return el ResourceTitulacionDTO correspondiente
     */
    ResourceDegreeDTO create(SaveResourceRequest request);

    /**
     * Update.
     * @param id id
     * @param request request
     * @return el ResourceTitulacionDTO correspondiente
     */
    ResourceDegreeDTO update(Integer id, SaveResourceRequest request);

    /**
     * Delete.
     * @param id id
     */
    void delete(Integer id);
}
