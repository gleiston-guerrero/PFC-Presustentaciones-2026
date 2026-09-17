package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceTitulacionDTO;

import java.util.List;

public interface ResourceTitulacionService {

    /** Resources visibles para una program (los generales + los de esa program). null = todos. */
    List<ResourceTitulacionDTO> list(Integer programId);

    /**
     * Create.
     * @param request request
     * @return el ResourceTitulacionDTO correspondiente
     */
    ResourceTitulacionDTO create(SaveResourceRequest request);

    /**
     * Update.
     * @param id id
     * @param request request
     * @return el ResourceTitulacionDTO correspondiente
     */
    ResourceTitulacionDTO update(Integer id, SaveResourceRequest request);

    /**
     * Delete.
     * @param id id
     */
    void delete(Integer id);
}
